package com.flowboard.task;

import com.flowboard.task.TaskDtos.LabelDto;
import com.flowboard.task.TaskDtos.TaskResponse;
import com.flowboard.user.UserDtos.UserSummary;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Maps tasks to responses, resolving all per-task counters with one grouped query each (no N+1). */
@Component
public class TaskAssembler {

    private final CommentRepository comments;
    private final AttachmentRepository attachments;
    private final ChecklistRepository checklists;
    private final TaskRepository tasks;
    private final Clock clock;

    public TaskAssembler(CommentRepository comments, AttachmentRepository attachments,
                         ChecklistRepository checklists, TaskRepository tasks, Clock clock) {
        this.comments = comments;
        this.attachments = attachments;
        this.checklists = checklists;
        this.tasks = tasks;
        this.clock = clock;
    }

    public TaskResponse toResponse(Task t) {
        return toResponses(List.of(t)).get(0);
    }

    public List<TaskResponse> toResponses(List<Task> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = list.stream().map(Task::getId).toList();
        Map<UUID, long[]> commentCounts = counts(comments.countsByTask(ids), false);
        Map<UUID, long[]> attachmentCounts = counts(attachments.countsByTask(ids), false);
        Map<UUID, long[]> checklistCounts = counts(checklists.countsByTask(ids), true);
        Map<UUID, long[]> subtaskCounts = counts(tasks.subtaskCounts(ids, TaskStatus.DONE), true);
        LocalDate today = LocalDate.now(clock);
        return list.stream().map(t -> {
            long[] sub = subtaskCounts.getOrDefault(t.getId(), new long[2]);
            long[] chk = checklistCounts.getOrDefault(t.getId(), new long[2]);
            Task parent = t.getParent();
            return new TaskResponse(
                    t.getId(), t.getProject().getId(), t.getProject().getKey(), t.identifier(), t.getNumber(),
                    t.getTitle(), t.getDescription(), t.getStatus(), t.getPriority(), t.getType(),
                    UserSummary.of(t.getAssignee()), UserSummary.of(t.getReporter()),
                    parent == null ? null : parent.getId(), parent == null ? null : parent.identifier(),
                    t.getStartDate(), t.getDueDate(), t.getPosition(), t.getVersion(),
                    t.getLabels().stream().sorted(Comparator.comparing(Label::getName)).map(LabelDto::of).toList(),
                    commentCounts.getOrDefault(t.getId(), new long[2])[0],
                    attachmentCounts.getOrDefault(t.getId(), new long[2])[0],
                    sub[0], sub[1], chk[0], chk[1],
                    t.isOverdue(today), t.getCreatedAt(), t.getUpdatedAt(), t.getCompletedAt());
        }).toList();
    }

    /** Rows are [id, total] or [id, total, done]. */
    private static Map<UUID, long[]> counts(Collection<Object[]> rows, boolean withDone) {
        Map<UUID, long[]> m = new HashMap<>();
        for (Object[] r : rows) {
            m.put((UUID) r[0], new long[] {((Number) r[1]).longValue(),
                    withDone && r[2] != null ? ((Number) r[2]).longValue() : 0});
        }
        return m;
    }
}
