package com.flowboard.collab;

import com.flowboard.collab.CollabDtos.NotificationResponse;
import com.flowboard.common.ApiException;
import com.flowboard.common.PageResponse;
import com.flowboard.security.AccessService;
import com.flowboard.security.CurrentUser;
import com.flowboard.user.User;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository repo;
    private final AccessService access;

    public NotificationService(NotificationRepository repo, AccessService access) {
        this.repo = repo;
        this.access = access;
    }

    /** Creates a notification unless the recipient is the person who caused the event. */
    @Transactional
    public void notify(UUID workspaceId, UUID recipientId, NotificationType type, User actor, UUID taskId,
                       UUID projectId, String message) {
        if (recipientId == null || recipientId.equals(actor.getId())) {
            return;
        }
        repo.save(new Notification(workspaceId, recipientId, type, actor, taskId, projectId, message));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> inbox(UUID wsId, boolean unreadOnly, int page, int size) {
        access.membership(wsId);
        return PageResponse.of(
                repo.inbox(CurrentUser.id(), wsId, unreadOnly, PageRequest.of(page, Math.min(size, 100))),
                NotificationResponse::of);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID wsId) {
        access.membership(wsId);
        return repo.unreadCount(CurrentUser.id(), wsId);
    }

    /** Idempotent: marking an already-read notification read is a no-op. */
    @Transactional
    public NotificationResponse markRead(UUID id) {
        Notification n = repo.findById(id)
                .filter(x -> x.getUserId().equals(CurrentUser.id()))
                .orElseThrow(() -> ApiException.notFound("Notification"));
        n.markRead();
        return NotificationResponse.of(n);
    }

    @Transactional
    public long markAllRead(UUID wsId) {
        access.membership(wsId);
        return repo.markAllRead(CurrentUser.id(), wsId, Instant.now());
    }
}
