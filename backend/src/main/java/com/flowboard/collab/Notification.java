package com.flowboard.collab;

import com.flowboard.common.BaseEntity;
import com.flowboard.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(nullable = false)
    private String message;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Notification() {
    }

    public Notification(UUID workspaceId, UUID userId, NotificationType type, User actor, UUID taskId,
                        UUID projectId, String message) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.type = type;
        this.actor = actor;
        this.taskId = taskId;
        this.projectId = projectId;
        this.message = message;
    }

    public UUID getUserId() { return userId; }
    public NotificationType getType() { return type; }
    public User getActor() { return actor; }
    public UUID getTaskId() { return taskId; }
    public UUID getProjectId() { return projectId; }
    public String getMessage() { return message; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void markRead() { if (readAt == null) { readAt = Instant.now(); } }
}
