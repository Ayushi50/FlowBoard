package com.flowboard.task;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    @Query("select t from Task t join fetch t.project join fetch t.reporter left join fetch t.assignee where t.id = :id")
    Optional<Task> findDetail(UUID id);

    @Query("select t from Task t join fetch t.project left join fetch t.assignee left join fetch t.reporter where t.parent.id = :parentId order by t.number")
    List<Task> findSubtasks(UUID parentId);

    @Query("select t from Task t join fetch t.project p where p.id in :projectIds and upper(p.projectKey) = :key and t.number = :number")
    List<Task> findByIdentifier(Collection<UUID> projectIds, String key, int number);

    // ---- ordering helpers for drag-and-drop (all scoped to one project column, excluding the moved task)

    @Query("select min(t.position) from Task t where t.project.id = :projectId and t.status = :status and t.parent is null and t.id <> :excludeId")
    Double minPosition(UUID projectId, TaskStatus status, UUID excludeId);

    @Query("select max(t.position) from Task t where t.project.id = :projectId and t.status = :status and t.parent is null and t.id <> :excludeId")
    Double maxPosition(UUID projectId, TaskStatus status, UUID excludeId);

    @Query("select min(t.position) from Task t where t.project.id = :projectId and t.status = :status and t.parent is null and t.id <> :excludeId and t.position > :pos")
    Double nextPositionAfter(UUID projectId, TaskStatus status, double pos, UUID excludeId);

    @Query("select max(t.position) from Task t where t.project.id = :projectId and t.status = :status and t.parent is null and t.id <> :excludeId and t.position < :pos")
    Double prevPositionBefore(UUID projectId, TaskStatus status, double pos, UUID excludeId);

    @Query("select t.position from Task t where t.id = :id")
    double positionOf(UUID id);

    @Query("select t.id from Task t where t.project.id = :projectId and t.status = :status and t.parent is null order by t.position, t.id")
    List<UUID> columnOrder(UUID projectId, TaskStatus status);

    /** Bulk position rewrite: deliberately bypasses @Version so a re-spacing never invalidates other users' edits. */
    @Modifying
    @Query("update Task t set t.position = :pos where t.id = :id")
    void updatePosition(UUID id, double pos);

    @Modifying
    @Query("update Task t set t.assignee = null where t.assignee.id = :userId and t.project.id in (select p.id from Project p where p.workspaceId = :wsId)")
    int unassignUserInWorkspace(UUID userId, UUID wsId);

    @Modifying
    @Query("update Task t set t.assignee = null where t.assignee.id = :userId and t.project.id = :projectId")
    int unassignUserInProject(UUID userId, UUID projectId);

    // ---- aggregates

    @Query("select t.project.id, count(t), sum(case when t.status = :done then 1 else 0 end) from Task t where t.project.id in :ids and t.parent is null group by t.project.id")
    List<Object[]> progressByProject(Collection<UUID> ids, TaskStatus done);

    @Query("select t.parent.id, count(t), sum(case when t.status = :done then 1 else 0 end) from Task t where t.parent.id in :ids group by t.parent.id")
    List<Object[]> subtaskCounts(Collection<UUID> ids, TaskStatus done);

    @Query("select count(t) from Task t where t.project.id in :projectIds and t.assignee.id = :userId and t.status <> :done and t.parent is null")
    long countOpenAssigned(Collection<UUID> projectIds, UUID userId, TaskStatus done);

    @Query("select count(t) from Task t where t.project.id in :projectIds and t.assignee.id = :userId and t.status <> :done and t.dueDate = :date")
    long countDueOn(Collection<UUID> projectIds, UUID userId, TaskStatus done, java.time.LocalDate date);

    @Query("select count(t) from Task t where t.project.id in :projectIds and t.assignee.id = :userId and t.status <> :done and t.dueDate < :date")
    long countOverdue(Collection<UUID> projectIds, UUID userId, TaskStatus done, java.time.LocalDate date);

    @Query("select count(t) from Task t where t.project.id in :projectIds and t.assignee.id = :userId and t.status = :done and t.completedAt >= :since")
    long countCompletedSince(Collection<UUID> projectIds, UUID userId, TaskStatus done, Instant since);

    @Query("select count(t) from Task t where t.project.id = :projectId and t.status <> :done and t.dueDate < :date and t.parent is null")
    long countProjectOverdue(UUID projectId, TaskStatus done, java.time.LocalDate date);

    @Query("select t.status, count(t) from Task t where t.project.id = :projectId and t.parent is null group by t.status")
    List<Object[]> countByStatus(UUID projectId);

    @Query("select count(t) from Task t where t.project.id = :projectId and t.status = :done and t.completedAt >= :since")
    long countProjectCompletedSince(UUID projectId, TaskStatus done, Instant since);
}
