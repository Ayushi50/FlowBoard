package com.flowboard.task;

import com.flowboard.collab.ActivityAction;
import com.flowboard.collab.ActivityService;
import com.flowboard.collab.NotificationService;
import com.flowboard.collab.NotificationType;
import com.flowboard.common.ApiException;
import com.flowboard.common.PageResponse;
import com.flowboard.project.Project;
import com.flowboard.project.ProjectMembershipRepository;
import com.flowboard.project.ProjectRepository;
import com.flowboard.security.AccessService;
import com.flowboard.security.AccessService.ProjectAccess;
import com.flowboard.security.CurrentUser;
import com.flowboard.task.TaskDtos.*;
import com.flowboard.user.User;
import com.flowboard.user.UserRepository;
import com.flowboard.workspace.Role;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Task lifecycle: query, create, edit, move between columns, delete. */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    private static final double GAP = 1024.0;
    private static final double MIN_GAP = 1e-6;
    public static final int MAX_PAGE_SIZE = 500;

    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final ProjectMembershipRepository projectMemberships;
    private final LabelRepository labels;
    private final UserRepository users;
    private final IdempotencyRepository idempotency;
    private final TaskNumberGenerator numbers;
    private final AccessService access;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final TaskAssembler assembler;

    public TaskService(TaskRepository tasks, ProjectRepository projects, ProjectMembershipRepository projectMemberships,
                       LabelRepository labels, UserRepository users, IdempotencyRepository idempotency,
                       TaskNumberGenerator numbers, AccessService access, ActivityService activity, NotificationService notifications,
                       TaskAssembler assembler) {
        this.tasks = tasks;
        this.projects = projects;
        this.projectMemberships = projectMemberships;
        this.labels = labels;
        this.users = users;
        this.idempotency = idempotency;
        this.numbers = numbers;
        this.access = access;
        this.activity = activity;
        this.notifications = notifications;
        this.assembler = assembler;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> listForProject(UUID projectId, TaskFilter filter, int page, int size) {
        access.project(projectId);
        return page(filter, page, size);
    }

    /** Cross-project query (My Tasks, search results, dashboards) limited to projects the caller can see. */
    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> listForWorkspace(UUID workspaceId, UUID onlyProject, TaskFilterBuilder builder,
                                                       int page, int size) {
        var membership = access.membership(workspaceId);
        List<UUID> visible = visibleProjectIds(workspaceId, membership.getRole(), false);
        if (onlyProject != null) {
            visible = visible.contains(onlyProject) ? List.of(onlyProject) : List.of();
        }
        if (visible.isEmpty()) {
            return new PageResponse<>(List.of(), page, size, 0, 0);
        }
        return page(builder.build(visible), page, size);
    }

    @FunctionalInterface
    public interface TaskFilterBuilder {
        TaskFilter build(List<UUID> projectIds);
    }

    public List<UUID> visibleProjectIds(UUID workspaceId, Role role, boolean includeArchived) {
        return projects.findVisible(workspaceId, CurrentUser.id(), role == Role.ADMIN, includeArchived, "")
                .stream().map(Project::getId).toList();
    }

    private PageResponse<TaskResponse> page(TaskFilter filter, int page, int size) {
        int bounded = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Page<Task> result = tasks.findAll(TaskSpecifications.from(filter), PageRequest.of(Math.max(page, 0), bounded));
        List<TaskResponse> items = assembler.toResponses(result.getContent());
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    // ------------------------------------------------------------------ create

    @Transactional
    public TaskResponse create(UUID projectId, CreateTaskRequest req, String idempotencyKey) {
        ProjectAccess a = access.projectWritable(projectId);
        User actor = users.getReferenceById(a.userId());
        if (idempotencyKey != null) {
            UUID existing = idempotency.find(a.userId(), idempotencyKey);
            if (existing != null) {
                return assembler.toResponse(loadTask(existing));
            }
        }
        validateDates(req.startDate(), req.dueDate());
        User assignee = resolveAssignee(a.project(), req.assigneeId());

        Project locked = a.project();
        Task task = new Task(locked, numbers.next(projectId), req.title().trim(), actor);
        task.setDescription(blankToNull(req.description()));
        TaskStatus status = req.status() == null ? TaskStatus.TODO : req.status();
        task.setStatus(status);
        task.setPriority(req.priority() == null ? TaskPriority.NONE : req.priority());
        task.setType(req.type() == null ? TaskType.TASK : req.type());
        task.setAssignee(assignee);
        task.setStartDate(req.startDate());
        task.setDueDate(req.dueDate());
        task.getLabels().addAll(resolveLabels(locked.getWorkspaceId(), req.labelIds()));
        Double max = tasks.maxPosition(projectId, status, task.getId());
        task.setPosition((max == null ? 0 : max) + GAP);
        tasks.saveAndFlush(task);

        if (idempotencyKey != null && !idempotency.claim(a.userId(), idempotencyKey, task.getId())) {
            throw ApiException.conflict("IDEMPOTENT_REPLAY", "A request with this Idempotency-Key is already in flight; retry");
        }
        activity.record(locked.getWorkspaceId(), projectId, task.getId(), actor, ActivityAction.TASK_CREATED,
                "created " + task.identifier() + " \"" + task.getTitle() + "\"", null, null);
        if (assignee != null) {
            notifyAssigned(task, actor, assignee);
        }
        log.info("task created id={} identifier={}", task.getId(), task.identifier());
        return assembler.toResponse(task);
    }

    @Transactional
    public TaskResponse createSubtask(UUID parentId, String title) {
        Task parent = loadTask(parentId);
        ProjectAccess a = access.projectWritable(parent.getProject().getId());
        if (parent.getParent() != null) {
            throw ApiException.badRequest("Subtasks cannot be nested more than one level");
        }
        User actor = users.getReferenceById(a.userId());
        Project locked = a.project();
        Task sub = new Task(locked, numbers.next(locked.getId()), title.trim(), actor);
        sub.setParent(parent);
        sub.setStatus(TaskStatus.TODO);
        sub.setPosition(GAP);
        tasks.saveAndFlush(sub);
        activity.record(locked.getWorkspaceId(), locked.getId(), parent.getId(), actor, ActivityAction.SUBTASK_ADDED,
                "added subtask " + sub.identifier() + " to " + parent.identifier(), null, sub.identifier());
        return assembler.toResponse(sub);
    }

    // ------------------------------------------------------------------ update

    @Transactional
    public TaskResponse update(UUID taskId, UpdateTaskRequest req) {
        Task task = loadTask(taskId);
        ProjectAccess a = access.projectWritable(task.getProject().getId());
        checkVersion(task, req.version());
        validateDates(req.startDate(), req.dueDate());
        User actor = users.getReferenceById(a.userId());
        UUID wsId = a.project().getWorkspaceId();
        UUID pid = a.project().getId();
        String id = task.identifier();

        User newAssignee = resolveAssignee(a.project(), req.assigneeId());
        Set<Label> newLabels = resolveLabels(wsId, req.labelIds());

        if (!Objects.equals(task.getTitle(), req.title().trim())
                || !Objects.equals(task.getDescription(), blankToNull(req.description()))
                || task.getType() != req.type()
                || !Objects.equals(task.getStartDate(), req.startDate())) {
            activity.record(wsId, pid, taskId, actor, ActivityAction.TASK_UPDATED, "edited " + id, null, null);
        }
        if (task.getPriority() != req.priority()) {
            activity.record(wsId, pid, taskId, actor, ActivityAction.PRIORITY_CHANGED,
                    "changed priority of " + id + " from " + task.getPriority() + " to " + req.priority(),
                    task.getPriority().name(), req.priority().name());
            notifications.notify(wsId, idOf(task.getAssignee()), NotificationType.TASK_UPDATED, actor, taskId, pid,
                    actor.getDisplayName() + " set " + id + " to " + req.priority() + " priority");
        }
        if (!Objects.equals(task.getDueDate(), req.dueDate())) {
            activity.record(wsId, pid, taskId, actor, ActivityAction.DUE_DATE_CHANGED,
                    "changed due date of " + id + " to " + (req.dueDate() == null ? "none" : req.dueDate()),
                    Objects.toString(task.getDueDate(), null), Objects.toString(req.dueDate(), null));
            notifications.notify(wsId, idOf(task.getAssignee()), NotificationType.TASK_UPDATED, actor, taskId, pid,
                    actor.getDisplayName() + " changed the due date of " + id);
        }
        if (!Objects.equals(idOf(task.getAssignee()), idOf(newAssignee))) {
            String from = task.getAssignee() == null ? "nobody" : task.getAssignee().getDisplayName();
            String to = newAssignee == null ? "nobody" : newAssignee.getDisplayName();
            activity.record(wsId, pid, taskId, actor, ActivityAction.ASSIGNEE_CHANGED,
                    "reassigned " + id + " from " + from + " to " + to, from, to);
            if (newAssignee != null) {
                notifyAssigned(task, actor, newAssignee);
            }
        }
        if (!task.getLabels().equals(newLabels)) {
            activity.record(wsId, pid, taskId, actor, ActivityAction.LABELS_CHANGED,
                    "updated labels on " + id + ": " + (newLabels.isEmpty() ? "none"
                            : newLabels.stream().map(Label::getName).sorted().collect(Collectors.joining(", "))),
                    null, null);
        }

        task.setTitle(req.title().trim());
        task.setDescription(blankToNull(req.description()));
        task.setPriority(req.priority());
        task.setType(req.type());
        task.setAssignee(newAssignee);
        task.setStartDate(req.startDate());
        task.setDueDate(req.dueDate());
        task.getLabels().clear();
        task.getLabels().addAll(newLabels);
        tasks.saveAndFlush(task);
        return assembler.toResponse(task);
    }

    // ------------------------------------------------------------------ move (drag & drop / status change)

    @Transactional
    public TaskResponse move(UUID taskId, MoveTaskRequest req) {
        Task task = loadTask(taskId);
        ProjectAccess a = access.projectWritable(task.getProject().getId());
        checkVersion(task, req.version());
        User actor = users.getReferenceById(a.userId());
        UUID pid = a.project().getId();
        TaskStatus from = task.getStatus();
        TaskStatus to = req.status();

        if (task.getParent() == null) {
            task.setPosition(computePosition(pid, to, task.getId(), req.afterTaskId(), req.beforeTaskId()));
        }
        if (from != to) {
            task.setStatus(to);
            String id = task.identifier();
            activity.record(a.project().getWorkspaceId(), pid, taskId, actor, ActivityAction.STATUS_CHANGED,
                    "moved " + id + " from " + from.label() + " to " + to.label(), from.name(), to.name());
            String msg = actor.getDisplayName() + " moved " + id + " to " + to.label();
            notifications.notify(a.project().getWorkspaceId(), idOf(task.getAssignee()), NotificationType.STATUS_CHANGED,
                    actor, taskId, pid, msg);
            if (!Objects.equals(idOf(task.getReporter()), idOf(task.getAssignee()))) {
                notifications.notify(a.project().getWorkspaceId(), idOf(task.getReporter()),
                        NotificationType.STATUS_CHANGED, actor, taskId, pid, msg);
            }
        }
        tasks.saveAndFlush(task);
        return assembler.toResponse(task);
    }

    /** Fractional indexing: a task's position is the midpoint between its new neighbours. */
    private double computePosition(UUID projectId, TaskStatus status, UUID self, UUID afterId, UUID beforeId) {
        return computePosition(projectId, status, self, afterId, beforeId, true);
    }

    private double computePosition(UUID projectId, TaskStatus status, UUID self, UUID afterId, UUID beforeId,
                                   boolean mayRebalance) {
        Double prev;
        Double next;
        if (afterId != null) {
            Task anchor = neighbour(afterId, projectId, status, self);
            prev = tasks.positionOf(anchor.getId());
            next = tasks.nextPositionAfter(projectId, status, prev, self);
        } else if (beforeId != null) {
            Task anchor = neighbour(beforeId, projectId, status, self);
            next = tasks.positionOf(anchor.getId());
            prev = tasks.prevPositionBefore(projectId, status, next, self);
        } else {
            prev = tasks.maxPosition(projectId, status, self);
            next = null;
        }
        if (prev == null && next == null) {
            return GAP;
        }
        if (next == null) {
            return prev + GAP;
        }
        if (prev == null) {
            return next - GAP;
        }
        if (next - prev < MIN_GAP && mayRebalance) {
            rebalance(projectId, status);
            return computePosition(projectId, status, self, afterId, beforeId, false);
        }
        return prev + (next - prev) / 2;
    }

    private Task neighbour(UUID id, UUID projectId, TaskStatus status, UUID self) {
        Task t = tasks.findById(id).orElseThrow(() -> ApiException.badRequest("Neighbour task does not exist"));
        if (t.getId().equals(self) || !t.getProject().getId().equals(projectId) || t.getStatus() != status
                || t.getParent() != null) {
            throw ApiException.badRequest("Neighbour task is not in the target column");
        }
        return t;
    }

    private void rebalance(UUID projectId, TaskStatus status) {
        log.info("rebalancing column project={} status={}", projectId, status);
        List<UUID> ordered = tasks.columnOrder(projectId, status);
        for (int i = 0; i < ordered.size(); i++) {
            tasks.updatePosition(ordered.get(i), (i + 1) * GAP);
        }
    }

    // ------------------------------------------------------------------ delete

    @Transactional
    public void delete(UUID taskId) {
        Task task = loadTask(taskId);
        ProjectAccess a = access.projectWritable(task.getProject().getId());
        boolean allowed = a.role() != Role.MEMBER || task.getReporter().getId().equals(a.userId());
        if (!allowed) {
            throw ApiException.forbidden("Members can only delete tasks they reported");
        }
        // Recorded against the project (task_id is cleared) because the task row is about to disappear.
        activity.record(a.project().getWorkspaceId(), a.project().getId(), null, users.getReferenceById(a.userId()),
                ActivityAction.TASK_DELETED, "deleted " + task.identifier() + " \"" + task.getTitle() + "\"", null, null);
        tasks.delete(task);
    }

    // ------------------------------------------------------------------ helpers

    Task loadTask(UUID id) {
        return tasks.findDetail(id).orElseThrow(() -> ApiException.notFound("Task"));
    }

    private void checkVersion(Task task, long expected) {
        if (task.getVersion() != expected) {
            throw ApiException.conflict("CONCURRENT_MODIFICATION",
                    task.identifier() + " was changed by someone else (version " + task.getVersion()
                            + ", yours " + expected + "). Reload to see the latest changes.");
        }
    }

    private void validateDates(LocalDate start, LocalDate due) {
        if (start != null && due != null && due.isBefore(start)) {
            throw ApiException.badRequest("Due date cannot be before the start date");
        }
    }

    private User resolveAssignee(Project project, UUID assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        if (!projectMemberships.isMember(project.getId(), assigneeId)) {
            throw ApiException.badRequest("Assignee must be a member of the project");
        }
        return users.getReferenceById(assigneeId);
    }

    private Set<Label> resolveLabels(UUID workspaceId, Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashSet<>();
        }
        List<Label> found = labels.findByWorkspaceIdAndIdIn(workspaceId, ids);
        if (found.size() != ids.size()) {
            throw ApiException.badRequest("Unknown label");
        }
        return new HashSet<>(found);
    }

    private void notifyAssigned(Task task, User actor, User assignee) {
        notifications.notify(task.getProject().getWorkspaceId(), assignee.getId(), NotificationType.ASSIGNED, actor,
                task.getId(), task.getProject().getId(),
                actor.getDisplayName() + " assigned you " + task.identifier() + ": " + task.getTitle());
    }

    private static UUID idOf(User u) {
        return u == null ? null : u.getId();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
