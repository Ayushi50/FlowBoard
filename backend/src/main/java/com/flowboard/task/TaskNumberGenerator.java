package com.flowboard.task;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hands out per-project task numbers with a single atomic {@code UPDATE ... RETURNING}.
 * The row lock is held until the caller's transaction ends, so concurrent creators serialise on the
 * project row and numbers are gap-free for committed tasks. Bypasses the entity (and its @Version) on purpose:
 * taking a number is not a user-visible edit of the project.
 */
@Component
public class TaskNumberGenerator {

    private final JdbcTemplate jdbc;

    public TaskNumberGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int next(UUID projectId) {
        Integer n = jdbc.queryForObject("update projects set task_seq = task_seq + 1 where id = ? returning task_seq",
                Integer.class, projectId);
        return n == null ? 0 : n;
    }
}
