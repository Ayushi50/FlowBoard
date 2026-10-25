package com.flowboard.project;

import com.flowboard.collab.ActivityAction;
import com.flowboard.collab.ActivityService;
import com.flowboard.common.ApiException;
import com.flowboard.project.ProjectDtos.*;
import com.flowboard.security.AccessService;
import com.flowboard.security.AccessService.ProjectAccess;
import com.flowboard.security.CurrentUser;
import com.flowboard.task.TaskAssembler;
import com.flowboard.task.TaskFilter;
import com.flowboard.task.TaskRepository;
import com.flowboard.task.TaskSpecifications;
import com.flowboard.task.TaskStatus;
import com.flowboard.user.User;
import com.flowboard.user.UserDtos.UserSummary;
import com.flowboard.user.UserRepository;
import com.flowboard.workspace.Role;
import com.flowboard.workspace.WorkspaceMembership;
import com.flowboard.workspace.WorkspaceMembershipRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final ProjectMembershipRepository memberships;
    private final MilestoneRepository milestones;
    private final ProjectPreferenceRepository prefs;
    private final WorkspaceMembershipRepository workspaceMemberships;
    private final TaskRepository tasks;
    private final UserRepository users;
    private final AccessService access;
    private final ActivityService activity;
    private final TaskAssembler assembler;
    private final Clock clock;

    public ProjectService(ProjectRepository projects, ProjectMembershipRepository memberships,
                          MilestoneRepository milestones, ProjectPreferenceRepository prefs,
                          WorkspaceMembershipRepository workspaceMemberships, TaskRepository tasks,
                          UserRepository users, AccessService access, ActivityService activity,
                          TaskAssembler assembler, Clock clock) {
        this.projects = projects;
        this.memberships = memberships;
        this.milestones = milestones;
        this.prefs = prefs;
        this.workspaceMemberships = workspaceMemberships;
        this.tasks = tasks;
        this.users = users;
        this.access = access;
        this.activity = activity;
        this.assembler = assembler;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(UUID wsId, boolean includeArchived, String q) {
        WorkspaceMembership m = access.membership(wsId);
        List<Project> found = projects.findVisible(wsId, CurrentUser.id(), m.getRole() == Role.ADMIN,
                includeArchived, q == null ? "" : q.trim());
        return toResponses(found, m.getRole());
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> recent(UUID wsId, int limit) {
        WorkspaceMembership m = access.membership(wsId);
        List<UUID> ids = prefs.recentIds(CurrentUser.id(), wsId, Math.min(Math.max(limit, 1), 10));
        Map<UUID, ProjectResponse> byId = list(wsId, false, null).stream()
                .collect(Collectors.toMap(ProjectResponse::id, p -> p));
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID projectId) {
        ProjectAccess a = access.project(projectId);
        return toResponses(List.of(a.project()), a.role()).get(0);
    }

    @Transactional(readOnly = true)
    public ProjectOverview overview(UUID projectId) {
        ProjectAccess a = access.project(projectId);
        LocalDate today = LocalDate.now(clock);
        Map<TaskStatus, Long> byStatus = new EnumMap<>(TaskStatus.class);
        for (TaskStatus s : TaskStatus.values()) {
            byStatus.put(s, 0L);
        }
        tasks.countByStatus(projectId).forEach(r -> byStatus.put((TaskStatus) r[0], ((Number) r[1]).longValue()));
        TaskFilter upcoming = new TaskFilter(List.of(projectId), null,
                Set.of(TaskStatus.BACKLOG, TaskStatus.TODO, TaskStatus.IN_PROGRESS), null, null, null,
                today, today.plusDays(14), false, false, null, "dueDate", false, today);
        var upcomingTasks = tasks.findAll(TaskSpecifications.from(upcoming), PageRequest.of(0, 8)).getContent();
        return new ProjectOverview(
                toResponses(List.of(a.project()), a.role()).get(0),
                byStatus,
                tasks.countProjectOverdue(projectId, TaskStatus.DONE, today),
                tasks.countProjectCompletedSince(projectId, TaskStatus.DONE, Instant.now(clock).minus(Duration.ofDays(7))),
                assembler.toResponses(upcomingTasks),
                milestones.findByProjectIdOrderByDueDate(projectId).stream().map(MilestoneResponse::of).toList());
    }

    // ------------------------------------------------------------------ write

    @Transactional
    public ProjectResponse create(UUID wsId, CreateProjectRequest req) {
        WorkspaceMembership m = access.requireRole(wsId, Role.ADMIN, Role.MANAGER);
        if (projects.existsByWorkspaceIdAndProjectKey(wsId, req.key())) {
            throw ApiException.conflict("PROJECT_KEY_TAKEN", "Project key " + req.key() + " is already in use");
        }
        validateDates(req.startDate(), req.targetDate());
        User owner = m.getUser();
        Project project = new Project(wsId, req.key(), req.name().trim(), owner);
        project.update(req.name().trim(), blankToNull(req.description()),
                req.status() == null ? ProjectStatus.ACTIVE : req.status(), req.startDate(), req.targetDate());
        projects.save(project);
        memberships.save(new ProjectMembership(project, owner));
        if (req.memberIds() != null) {
            for (UUID uid : req.memberIds()) {
                if (!uid.equals(owner.getId())) {
                    addMemberInternal(project, wsId, uid);
                }
            }
        }
        activity.record(wsId, project.getId(), null, owner, ActivityAction.PROJECT_CREATED,
                "created project " + project.getName() + " (" + project.getKey() + ")", null, null);
        return toResponses(List.of(project), m.getRole()).get(0);
    }

    @Transactional
    public ProjectResponse update(UUID projectId, UpdateProjectRequest req) {
        ProjectAccess a = access.projectManage(projectId);
        validateDates(req.startDate(), req.targetDate());
        Project p = a.project();
        p.update(req.name().trim(), blankToNull(req.description()), req.status(), req.startDate(), req.targetDate());
        projects.saveAndFlush(p);
        activity.record(p.getWorkspaceId(), p.getId(), null, users.getReferenceById(a.userId()),
                ActivityAction.PROJECT_UPDATED, "updated project " + p.getName(), null, null);
        return toResponses(List.of(p), a.role()).get(0);
    }

    @Transactional
    public ProjectResponse setArchived(UUID projectId, boolean archived) {
        ProjectAccess a = access.projectManage(projectId);
        Project p = a.project();
        p.setArchived(archived);
        projects.saveAndFlush(p);
        activity.record(p.getWorkspaceId(), p.getId(), null, users.getReferenceById(a.userId()),
                ActivityAction.PROJECT_ARCHIVED, (archived ? "archived" : "restored") + " project " + p.getName(), null, null);
        return toResponses(List.of(p), a.role()).get(0);
    }

    @Transactional
    public void delete(UUID projectId) {
        ProjectAccess a = access.projectManage(projectId);
        projects.delete(a.project());
    }

    // ------------------------------------------------------------------ membership

    @Transactional
    public ProjectResponse addMember(UUID projectId, UUID userId) {
        ProjectAccess a = access.project(projectId);
        if (!a.canManageMembers()) {
            throw ApiException.forbidden("Only workspace admins or managers of this project can manage members");
        }
        if (memberships.isMember(projectId, userId)) {
            return toResponses(List.of(a.project()), a.role()).get(0);
        }
        User added = addMemberInternal(a.project(), a.project().getWorkspaceId(), userId);
        activity.record(a.project().getWorkspaceId(), projectId, null, users.getReferenceById(a.userId()),
                ActivityAction.MEMBER_ADDED, "added " + added.getDisplayName() + " to " + a.project().getName(), null, null);
        return toResponses(List.of(a.project()), a.role()).get(0);
    }

    private User addMemberInternal(Project project, UUID wsId, UUID userId) {
        if (!workspaceMemberships.isMember(wsId, userId)) {
            throw ApiException.badRequest("User is not a member of this workspace");
        }
        User user = users.getReferenceById(userId);
        memberships.save(new ProjectMembership(project, user));
        return user;
    }

    @Transactional
    public void removeMember(UUID projectId, UUID userId) {
        ProjectAccess a = access.project(projectId);
        if (!a.canManageMembers() && !a.userId().equals(userId)) {
            throw ApiException.forbidden("Only workspace admins or managers of this project can manage members");
        }
        if (a.project().getOwner().getId().equals(userId)) {
            throw ApiException.conflict("OWNER_CANNOT_LEAVE", "The project owner cannot be removed");
        }
        ProjectMembership pm = memberships.find(projectId, userId).orElseThrow(() -> ApiException.notFound("Member"));
        tasks.unassignUserInProject(userId, projectId);
        memberships.delete(pm);
        activity.record(a.project().getWorkspaceId(), projectId, null, users.getReferenceById(a.userId()),
                ActivityAction.MEMBER_REMOVED, "removed " + pm.getUser().getDisplayName() + " from " + a.project().getName(),
                null, null);
    }

    // ------------------------------------------------------------------ preferences

    @Transactional
    public void setFavorite(UUID projectId, boolean favorite) {
        access.project(projectId);
        if (favorite) {
            prefs.favorite(CurrentUser.id(), projectId);
        } else {
            prefs.unfavorite(CurrentUser.id(), projectId);
        }
    }

    @Transactional
    public void visit(UUID projectId) {
        access.project(projectId);
        prefs.visit(CurrentUser.id(), projectId);
    }

    // ------------------------------------------------------------------ milestones

    @Transactional(readOnly = true)
    public List<MilestoneResponse> milestones(UUID projectId) {
        access.project(projectId);
        return milestones.findByProjectIdOrderByDueDate(projectId).stream().map(MilestoneResponse::of).toList();
    }

    @Transactional
    public MilestoneResponse addMilestone(UUID projectId, MilestoneRequest req) {
        access.projectManage(projectId);
        return MilestoneResponse.of(milestones.save(new Milestone(projectId, req.name().trim(), req.dueDate())));
    }

    @Transactional
    public void deleteMilestone(UUID milestoneId) {
        Milestone m = milestones.findById(milestoneId).orElseThrow(() -> ApiException.notFound("Milestone"));
        access.projectManage(m.getProjectId());
        milestones.delete(m);
    }

    // ------------------------------------------------------------------ mapping

    private List<ProjectResponse> toResponses(List<Project> list, Role role) {
        if (list.isEmpty()) {
            return List.of();
        }
        UUID userId = CurrentUser.id();
        List<UUID> ids = list.stream().map(Project::getId).toList();
        Map<UUID, long[]> progress = new HashMap<>();
        tasks.progressByProject(ids, TaskStatus.DONE).forEach(r -> progress.put((UUID) r[0],
                new long[] {((Number) r[1]).longValue(), r[2] == null ? 0 : ((Number) r[2]).longValue()}));
        Map<UUID, List<UserSummary>> members = memberships.findByProjects(ids).stream()
                .collect(Collectors.groupingBy(pm -> pm.getProject().getId(),
                        Collectors.mapping(pm -> UserSummary.of(pm.getUser()), Collectors.toList())));
        Set<UUID> favorites = prefs.favoriteIds(userId);
        return list.stream().map(p -> {
            long[] pr = progress.getOrDefault(p.getId(), new long[2]);
            List<UserSummary> ms = members.getOrDefault(p.getId(), List.of());
            boolean isMember = ms.stream().anyMatch(u -> u.id().equals(userId));
            boolean canManage = role == Role.ADMIN || (role == Role.MANAGER && isMember);
            return new ProjectResponse(p.getId(), p.getWorkspaceId(), p.getKey(), p.getName(), p.getDescription(),
                    UserSummary.of(p.getOwner()), p.getStatus(), p.isArchived(), p.getStartDate(), p.getTargetDate(),
                    pr[0], pr[1], pr[0] == 0 ? 0 : (int) Math.round(100.0 * pr[1] / pr[0]),
                    favorites.contains(p.getId()), ms, canManage, p.getVersion(), p.getCreatedAt(), p.getUpdatedAt());
        }).toList();
    }

    private void validateDates(LocalDate start, LocalDate target) {
        if (start != null && target != null && target.isBefore(start)) {
            throw ApiException.badRequest("Target date cannot be before the start date");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
