package com.flowboard.task;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyRepository {

    private final JdbcTemplate jdbc;

    public IdempotencyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID find(UUID userId, String key) {
        List<UUID> ids = jdbc.queryForList("select task_id from idempotency_keys where user_id = ? and idem_key = ?",
                UUID.class, userId, key);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** @return true if this call claimed the key, false if another request already did. */
    public boolean claim(UUID userId, String key, UUID taskId) {
        return jdbc.update("insert into idempotency_keys (user_id, idem_key, task_id, created_at) values (?, ?, ?, ?) on conflict do nothing",
                userId, key, taskId, Timestamp.from(Instant.now())) == 1;
    }
}
