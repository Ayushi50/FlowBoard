package com.flowboard.task;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Database-level task query. Every field is optional except {@code projectIds}, which is the authorization scope. */
public record TaskFilter(
        Collection<UUID> projectIds,
        UUID assigneeId,
        Set<TaskStatus> statuses,
        Set<TaskPriority> priorities,
        Set<UUID> labelIds,
        String q,
        LocalDate dueFrom,
        LocalDate dueTo,
        boolean overdueOnly,
        boolean includeSubtasks,
        Instant completedSince,
        String sort,
        boolean desc,
        LocalDate today) {
}
