package com.flowboard.collab;

import com.flowboard.user.UserDtos.UserSummary;
import java.time.Instant;
import java.util.UUID;

public final class CollabDtos {

    private CollabDtos() {
    }

    public record ActivityResponse(UUID id, ActivityAction action, String summary, String oldValue, String newValue,
                                   UserSummary actor, UUID taskId, UUID projectId, Instant createdAt) {
        public static ActivityResponse of(ActivityLog a) {
            return new ActivityResponse(a.getId(), a.getAction(), a.getSummary(), a.getOldValue(), a.getNewValue(),
                    UserSummary.of(a.getActor()), a.getTaskId(), a.getProjectId(), a.getCreatedAt());
        }
    }

    public record NotificationResponse(UUID id, NotificationType type, String message, UserSummary actor,
                                       UUID taskId, UUID projectId, boolean read, Instant createdAt) {
        public static NotificationResponse of(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getMessage(), UserSummary.of(n.getActor()),
                    n.getTaskId(), n.getProjectId(), n.getReadAt() != null, n.getCreatedAt());
        }
    }

    public record UnreadCount(long count) {
    }
}
