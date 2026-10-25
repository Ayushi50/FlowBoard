package com.flowboard.task;

import com.flowboard.collab.ActivityAction;
import com.flowboard.collab.ActivityService;
import com.flowboard.collab.CollabDtos.ActivityResponse;
import com.flowboard.collab.ActivityRepository;
import com.flowboard.collab.NotificationService;
import com.flowboard.collab.NotificationType;
import com.flowboard.common.ApiException;
import com.flowboard.common.PageResponse;
import com.flowboard.project.ProjectMembershipRepository;
import com.flowboard.security.AccessService;
import com.flowboard.security.AccessService.ProjectAccess;
import com.flowboard.task.TaskDtos.*;
import com.flowboard.user.User;
import com.flowboard.user.UserRepository;
import com.flowboard.workspace.Role;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Everything shown in the task side panel: detail, subtasks, checklist, comments, attachments, history. */
@Service
public class TaskDetailService {

    private final TaskRepository tasks;
    private final CommentRepository comments;
    private final ChecklistRepository checklists;
    private final AttachmentRepository attachments;
    private final ActivityRepository activityRepo;
    private final ProjectMembershipRepository projectMemberships;
    private final UserRepository users;
    private final AccessService access;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final TaskAssembler assembler;

    public TaskDetailService(TaskRepository tasks, CommentRepository comments, ChecklistRepository checklists,
                             AttachmentRepository attachments, ActivityRepository activityRepo,
                             ProjectMembershipRepository projectMemberships, UserRepository users,
                             AccessService access, ActivityService activity, NotificationService notifications,
                             TaskAssembler assembler) {
        this.tasks = tasks;
        this.comments = comments;
        this.checklists = checklists;
        this.attachments = attachments;
        this.activityRepo = activityRepo;
        this.projectMemberships = projectMemberships;
        this.users = users;
        this.access = access;
        this.activity = activity;
        this.notifications = notifications;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true)
    public TaskDetail get(UUID taskId) {
        Task task = load(taskId);
        access.project(task.getProject().getId());
        return detail(task);
    }

    private TaskDetail detail(Task task) {
        return new TaskDetail(
                assembler.toResponse(task),
                assembler.toResponses(tasks.findSubtasks(task.getId())),
                checklists.findByTaskIdOrderByPosition(task.getId()).stream().map(ChecklistItemDto::of).toList(),
                attachments.findByTask(task.getId()).stream().map(AttachmentDto::of).toList());
    }

    // ------------------------------------------------------------------ comments

    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> comments(UUID taskId, int page, int size) {
        Task task = load(taskId);
        access.project(task.getProject().getId());
        return PageResponse.of(comments.findByTask(taskId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        org.springframework.data.domain.Sort.by("createdAt"))), CommentResponse::of);
    }

    @Transactional
    public CommentResponse addComment(UUID taskId, CreateCommentRequest req) {
        Task task = load(taskId);
        ProjectAccess a = access.projectWritable(task.getProject().getId());
        User actor = users.getReferenceById(a.userId());
        Comment comment = comments.save(new Comment(task, actor, req.body().trim()));
        UUID wsId = a.project().getWorkspaceId();
        UUID pid = a.project().getId();
        String id = task.identifier();

        Set<UUID> mentioned = new HashSet<>(req.mentionedUserIds() == null ? Set.of() : req.mentionedUserIds());
        for (UUID uid : mentioned) {
            if (!projectMemberships.isMember(pid, uid)) {
                throw ApiException.badRequest("Only project members can be mentioned");
            }
            notifications.notify(wsId, uid, NotificationType.MENTIONED, actor, taskId, pid,
                    actor.getDisplayName() + " mentioned you on " + id);
        }
        for (User u : new User[] {task.getAssignee(), task.getReporter()}) {
            if (u != null && !mentioned.contains(u.getId())) {
                notifications.notify(wsId, u.getId(), NotificationType.COMMENTED, actor, taskId, pid,
                        actor.getDisplayName() + " commented on " + id);
                mentioned.add(u.getId());
            }
        }
        activity.record(wsId, pid, taskId, actor, ActivityAction.COMMENT_ADDED, "commented on " + id, null, null);
        return CommentResponse.of(comment);
    }

    @Transactional
    public void deleteComment(UUID commentId) {
        Comment c = comments.findById(commentId).orElseThrow(() -> ApiException.notFound("Comment"));
        ProjectAccess a = access.projectWritable(c.getTask().getProject().getId());
        if (!c.getAuthor().getId().equals(a.userId()) && a.role() != Role.ADMIN) {
            throw ApiException.forbidden("You can only delete your own comments");
        }
        comments.delete(c);
    }

    // ------------------------------------------------------------------ history

    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> history(UUID taskId, int page, int size) {
        Task task = load(taskId);
        access.project(task.getProject().getId());
        return PageResponse.of(activityRepo.findByTask(taskId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))),
                ActivityResponse::of);
    }

    // ------------------------------------------------------------------ checklist

    @Transactional
    public ChecklistItemDto addChecklistItem(UUID taskId, ChecklistRequest req) {
        Task task = load(taskId);
        access.projectWritable(task.getProject().getId());
        int pos = checklists.maxPosition(taskId) + 1;
        return ChecklistItemDto.of(checklists.save(new ChecklistItem(taskId, req.text().trim(), pos)));
    }

    @Transactional
    public ChecklistItemDto updateChecklistItem(UUID itemId, ChecklistUpdateRequest req) {
        ChecklistItem item = checklists.findById(itemId).orElseThrow(() -> ApiException.notFound("Checklist item"));
        access.projectWritable(load(item.getTaskId()).getProject().getId());
        if (req.text() != null && !req.text().isBlank()) {
            item.setText(req.text().trim());
        }
        if (req.done() != null) {
            item.setDone(req.done());
        }
        return ChecklistItemDto.of(item);
    }

    @Transactional
    public void deleteChecklistItem(UUID itemId) {
        ChecklistItem item = checklists.findById(itemId).orElseThrow(() -> ApiException.notFound("Checklist item"));
        access.projectWritable(load(item.getTaskId()).getProject().getId());
        checklists.delete(item);
    }

    // ------------------------------------------------------------------ attachments (metadata only)

    @Transactional
    public AttachmentDto addAttachment(UUID taskId, AttachmentRequest req) {
        Task task = load(taskId);
        ProjectAccess a = access.projectWritable(task.getProject().getId());
        AttachmentMetadata meta = attachments.save(new AttachmentMetadata(taskId, req.fileName().trim(),
                req.contentType(), req.sizeBytes(), users.getReferenceById(a.userId())));
        return AttachmentDto.of(meta);
    }

    @Transactional
    public void deleteAttachment(UUID attachmentId) {
        AttachmentMetadata meta = attachments.findById(attachmentId).orElseThrow(() -> ApiException.notFound("Attachment"));
        access.projectWritable(load(meta.getTaskId()).getProject().getId());
        attachments.delete(meta);
    }

    private Task load(UUID id) {
        return tasks.findDetail(id).orElseThrow(() -> ApiException.notFound("Task"));
    }

}
