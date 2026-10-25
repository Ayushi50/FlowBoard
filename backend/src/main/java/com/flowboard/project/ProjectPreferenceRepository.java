package com.flowboard.project;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Per-user favorites and recently-visited projects (composite-key tables, plain JDBC upserts). */
@Repository
public class ProjectPreferenceRepository {

    private final JdbcTemplate jdbc;

    public ProjectPreferenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void favorite(UUID userId, UUID projectId) {
        jdbc.update("insert into project_favorites (user_id, project_id, created_at) values (?, ?, ?) on conflict do nothing",
                userId, projectId, Timestamp.from(Instant.now()));
    }

    public void unfavorite(UUID userId, UUID projectId) {
        jdbc.update("delete from project_favorites where user_id = ? and project_id = ?", userId, projectId);
    }

    public Set<UUID> favoriteIds(UUID userId) {
        return Set.copyOf(jdbc.queryForList("select project_id from project_favorites where user_id = ?", UUID.class, userId));
    }

    public void visit(UUID userId, UUID projectId) {
        jdbc.update("""
                insert into project_visits (user_id, project_id, visited_at) values (?, ?, ?)
                on conflict (user_id, project_id) do update set visited_at = excluded.visited_at
                """, userId, projectId, Timestamp.from(Instant.now()));
    }

    public List<UUID> recentIds(UUID userId, UUID wsId, int limit) {
        return jdbc.queryForList("""
                select v.project_id from project_visits v join projects p on p.id = v.project_id
                where v.user_id = ? and p.workspace_id = ? and p.archived = false
                order by v.visited_at desc limit ?
                """, UUID.class, userId, wsId, limit);
    }
}
