package com.flowboard.collab;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query(value = "select n from Notification n left join fetch n.actor where n.userId = :userId and n.workspaceId = :wsId and (:unreadOnly = false or n.readAt is null) order by n.createdAt desc",
            countQuery = "select count(n) from Notification n where n.userId = :userId and n.workspaceId = :wsId and (:unreadOnly = false or n.readAt is null)")
    Page<Notification> inbox(UUID userId, UUID wsId, boolean unreadOnly, Pageable pageable);

    @Query("select count(n) from Notification n where n.userId = :userId and n.workspaceId = :wsId and n.readAt is null")
    long unreadCount(UUID userId, UUID wsId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.workspaceId = :wsId and n.readAt is null")
    int markAllRead(UUID userId, UUID wsId, Instant now);
}
