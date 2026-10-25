package com.flowboard.task;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChecklistRepository extends JpaRepository<ChecklistItem, UUID> {

    List<ChecklistItem> findByTaskIdOrderByPosition(UUID taskId);

    @Query("select c.taskId, count(c), sum(case when c.done = true then 1 else 0 end) from ChecklistItem c where c.taskId in :ids group by c.taskId")
    List<Object[]> countsByTask(Collection<UUID> ids);

    @Query("select coalesce(max(c.position), 0) from ChecklistItem c where c.taskId = :taskId")
    int maxPosition(UUID taskId);
}
