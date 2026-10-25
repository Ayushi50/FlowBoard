package com.flowboard.task;

import com.flowboard.common.PageResponse;
import com.flowboard.security.CurrentUser;
import com.flowboard.task.TaskDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Tasks")
public class TaskController {

    private final TaskService tasks;
    private final TaskDetailService details;
    private final Clock clock;

    public TaskController(TaskService tasks, TaskDetailService details, Clock clock) {
        this.tasks = tasks;
        this.details = details;
        this.clock = clock;
    }

    @Operation(summary = "List/filter/sort tasks of one project (board and list views share this endpoint)")
    @GetMapping("/projects/{projectId}/tasks")
    public PageResponse<TaskResponse> listForProject(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) Set<TaskStatus> status,
            @RequestParam(required = false) Set<TaskPriority> priority,
            @RequestParam(required = false) Set<UUID> labelId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(defaultValue = "false") boolean includeSubtasks,
            @RequestParam(defaultValue = "position") String sort,
            @RequestParam(defaultValue = "asc") String dir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        TaskFilter f = new TaskFilter(List.of(projectId), assigneeId, status, priority, labelId, q, dueFrom, dueTo,
                overdue, includeSubtasks, null, sort, "desc".equalsIgnoreCase(dir), LocalDate.now(clock));
        return tasks.listForProject(projectId, f, page, size);
    }

    @Operation(summary = "Cross-project task query within a workspace (My Tasks, dashboard drill-downs)")
    @GetMapping("/workspaces/{workspaceId}/tasks")
    public PageResponse<TaskResponse> listForWorkspace(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) Set<TaskStatus> status,
            @RequestParam(required = false) Set<TaskPriority> priority,
            @RequestParam(required = false) Set<UUID> labelId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant completedSince,
            @RequestParam(defaultValue = "false") boolean includeSubtasks,
            @RequestParam(defaultValue = "dueDate") String sort,
            @RequestParam(defaultValue = "asc") String dir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        UUID assignee = mine ? CurrentUser.id() : assigneeId;
        return tasks.listForWorkspace(workspaceId, projectId,
                (List<UUID> ids) -> new TaskFilter(ids, assignee, status, priority, labelId, q, dueFrom, dueTo,
                        overdue, includeSubtasks, completedSince, sort, "desc".equalsIgnoreCase(dir),
                        LocalDate.now(clock)),
                page, size);
    }

    @PostMapping("/projects/{projectId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@PathVariable UUID projectId, @Valid @RequestBody CreateTaskRequest req,
                               @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return tasks.create(projectId, req, idempotencyKey);
    }

    @GetMapping("/tasks/{id}")
    public TaskDetail get(@PathVariable UUID id) {
        return details.get(id);
    }

    @Operation(summary = "Edit task fields. Requires the current `version`; a stale version yields 409 CONCURRENT_MODIFICATION")
    @PutMapping("/tasks/{id}")
    public TaskResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTaskRequest req) {
        return tasks.update(id, req);
    }

    @Operation(summary = "Move a task to a column/position (drag and drop, status change)")
    @PostMapping("/tasks/{id}/move")
    public TaskResponse move(@PathVariable UUID id, @Valid @RequestBody MoveTaskRequest req) {
        return tasks.move(id, req);
    }

    @DeleteMapping("/tasks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        tasks.delete(id);
    }

    @PostMapping("/tasks/{id}/subtasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse createSubtask(@PathVariable UUID id, @Valid @RequestBody CreateSubtaskRequest req) {
        return tasks.createSubtask(id, req.title());
    }

    // ---- comments & history

    @GetMapping("/tasks/{id}/comments")
    public PageResponse<CommentResponse> comments(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "50") int size) {
        return details.comments(id, page, size);
    }

    @PostMapping("/tasks/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(@PathVariable UUID id, @Valid @RequestBody CreateCommentRequest req) {
        return details.addComment(id, req);
    }

    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable UUID id) {
        details.deleteComment(id);
    }

    @GetMapping("/tasks/{id}/activity")
    public PageResponse<com.flowboard.collab.CollabDtos.ActivityResponse> history(
            @PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return details.history(id, page, size);
    }

    // ---- checklist

    @PostMapping("/tasks/{id}/checklist")
    @ResponseStatus(HttpStatus.CREATED)
    public ChecklistItemDto addChecklist(@PathVariable UUID id, @Valid @RequestBody ChecklistRequest req) {
        return details.addChecklistItem(id, req);
    }

    @PatchMapping("/checklist/{itemId}")
    public ChecklistItemDto updateChecklist(@PathVariable UUID itemId, @Valid @RequestBody ChecklistUpdateRequest req) {
        return details.updateChecklistItem(itemId, req);
    }

    @DeleteMapping("/checklist/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChecklist(@PathVariable UUID itemId) {
        details.deleteChecklistItem(itemId);
    }

    // ---- attachments (metadata)

    @PostMapping("/tasks/{id}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentDto addAttachment(@PathVariable UUID id, @Valid @RequestBody AttachmentRequest req) {
        return details.addAttachment(id, req);
    }

    @DeleteMapping("/attachments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@PathVariable UUID id) {
        details.deleteAttachment(id);
    }
}
