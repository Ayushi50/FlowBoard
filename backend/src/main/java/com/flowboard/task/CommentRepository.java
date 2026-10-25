package com.flowboard.task;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    @Query(value = "select c from Comment c join fetch c.author where c.task.id = :taskId",
            countQuery = "select count(c) from Comment c where c.task.id = :taskId")
    Page<Comment> findByTask(UUID taskId, Pageable pageable);

    @Query("select c.task.id, count(c) from Comment c where c.task.id in :ids group by c.task.id")
    List<Object[]> countsByTask(Collection<UUID> ids);
}
