package com.flowboard.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowboard.task.Task;
import com.flowboard.task.TaskPriority;
import com.flowboard.task.TaskStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TaskDomainTest {

    @Test
    void overdueMeansPastDueAndNotDone() {
        Task t = new Task(null, 1, "x", null);
        LocalDate today = LocalDate.of(2026, 5, 10);
        t.setDueDate(today.minusDays(1));
        assertThat(t.isOverdue(today)).isTrue();
        t.setStatus(TaskStatus.DONE);
        assertThat(t.isOverdue(today)).isFalse();
        t.setStatus(TaskStatus.TODO);
        t.setDueDate(today);
        assertThat(t.isOverdue(today)).isFalse();
        t.setDueDate(null);
        assertThat(t.isOverdue(today)).isFalse();
    }

    @Test
    void completionTimestampFollowsStatus() {
        Task t = new Task(null, 1, "x", null);
        t.setStatus(TaskStatus.DONE);
        assertThat(t.getCompletedAt()).isNotNull();
        t.setStatus(TaskStatus.IN_PROGRESS);
        assertThat(t.getCompletedAt()).isNull();
    }

    @Test
    void priorityOrdinalsRankUrgentFirst() {
        assertThat(TaskPriority.URGENT.ordinal()).isLessThan(TaskPriority.HIGH.ordinal());
        assertThat(TaskPriority.LOW.ordinal()).isLessThan(TaskPriority.NONE.ordinal());
    }
}
