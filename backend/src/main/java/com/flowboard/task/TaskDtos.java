package com.flowboard.task;

import com.flowboard.user.UserDtos.UserSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class TaskDtos {

    private TaskDtos() {
    }

    public record LabelDto(UUID id, String name, String color) {
        public static LabelDto of(Label l) {
            return new LabelDto(l.getId(), l.getName(), l.getColor());
        }
    }

    public record ChecklistItemDto(UUID id, String text, boolean done, int position) {
        public static ChecklistItemDto of(ChecklistItem c) {
            return new ChecklistItemDto(c.getId(), c.getText(), c.isDone(), c.getPosition());
        }
    }

    public record AttachmentDto(UUID id, String fileName, String contentType, long sizeBytes, UserSummary uploadedBy,
                                Instant createdAt) {
        public static AttachmentDto of(AttachmentMetadata a) {
            return new AttachmentDto(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes(),
                    UserSummary.of(a.getUploadedBy()), a.getCreatedAt());
        }
    }

    public record TaskResponse(
            UUID id, UUID projectId, String projectKey, String identifier, int number,
            String title, String description, TaskStatus status, TaskPriority priority, TaskType type,
            UserSummary assignee, UserSummary reporter, UUID parentId, String parentIdentifier,
            LocalDate startDate, LocalDate dueDate, double position, long version,
            List<LabelDto> labels, long commentCount, long attachmentCount,
            long subtaskTotal, long subtaskDone, long checklistTotal, long checklistDone,
            boolean overdue, Instant createdAt, Instant updatedAt, Instant completedAt) {
    }

    public record TaskDetail(TaskResponse task, List<TaskResponse> subtasks, List<ChecklistItemDto> checklist,
                             List<AttachmentDto> attachments) {
    }

    public record CommentResponse(UUID id, UserSummary author, String body, Instant createdAt) {
        public static CommentResponse of(Comment c) {
            return new CommentResponse(c.getId(), UserSummary.of(c.getAuthor()), c.getBody(), c.getCreatedAt());
        }
    }

    public record CreateTaskRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 20000) String description,
            TaskStatus status, TaskPriority priority, TaskType type,
            UUID assigneeId, LocalDate startDate, LocalDate dueDate, Set<UUID> labelIds) {
    }

    /** Full replacement of the user-editable fields; {@code version} guards against lost updates. */
    public record UpdateTaskRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 20000) String description,
            @NotNull TaskPriority priority, @NotNull TaskType type,
            UUID assigneeId, LocalDate startDate, LocalDate dueDate, Set<UUID> labelIds,
            @NotNull @PositiveOrZero Long version) {
    }

    /**
     * Places a task in a column. {@code afterTaskId} / {@code beforeTaskId} name the neighbour in the
     * target column; with neither the task goes to the bottom.
     */
    public record MoveTaskRequest(@NotNull TaskStatus status, UUID afterTaskId, UUID beforeTaskId,
                                  @NotNull @PositiveOrZero Long version) {
    }

    public record CreateSubtaskRequest(@NotBlank @Size(max = 255) String title) {
    }

    public record CreateCommentRequest(@NotBlank @Size(max = 5000) String body, Set<UUID> mentionedUserIds) {
    }

    public record ChecklistRequest(@NotBlank @Size(max = 255) String text) {
    }

    public record ChecklistUpdateRequest(@Size(max = 255) String text, Boolean done) {
    }

    public record AttachmentRequest(@NotBlank @Size(max = 255) String fileName,
                                    @NotBlank @Size(max = 120) String contentType,
                                    @NotNull @PositiveOrZero Long sizeBytes) {
    }

    public record CreateLabelRequest(@NotBlank @Size(max = 40) String name,
                                     @NotBlank @jakarta.validation.constraints.Pattern(regexp = "^#[0-9a-fA-F]{6}$") String color) {
    }
}
