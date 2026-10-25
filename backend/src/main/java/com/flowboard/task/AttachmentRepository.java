package com.flowboard.task;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AttachmentRepository extends JpaRepository<AttachmentMetadata, UUID> {

    @Query("select a from AttachmentMetadata a join fetch a.uploadedBy where a.taskId = :taskId order by a.createdAt")
    List<AttachmentMetadata> findByTask(UUID taskId);

    @Query("select a.taskId, count(a) from AttachmentMetadata a where a.taskId in :ids group by a.taskId")
    List<Object[]> countsByTask(Collection<UUID> ids);
}
