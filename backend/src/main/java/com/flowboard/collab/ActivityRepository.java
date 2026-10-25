package com.flowboard.collab;

import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ActivityRepository extends JpaRepository<ActivityLog, UUID> {

    @Query(value = "select a from ActivityLog a join fetch a.actor where a.taskId = :taskId order by a.createdAt desc",
            countQuery = "select count(a) from ActivityLog a where a.taskId = :taskId")
    Page<ActivityLog> findByTask(UUID taskId, Pageable pageable);

    @Query(value = "select a from ActivityLog a join fetch a.actor where a.projectId = :projectId order by a.createdAt desc",
            countQuery = "select count(a) from ActivityLog a where a.projectId = :projectId")
    Page<ActivityLog> findByProject(UUID projectId, Pageable pageable);

    @Query(value = "select a from ActivityLog a join fetch a.actor where a.workspaceId = :wsId and a.projectId in :projectIds order by a.createdAt desc",
            countQuery = "select count(a) from ActivityLog a where a.workspaceId = :wsId and a.projectId in :projectIds")
    Page<ActivityLog> findByProjects(UUID wsId, Collection<UUID> projectIds, Pageable pageable);
}
