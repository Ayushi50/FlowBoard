package com.flowboard.collab;

import com.flowboard.common.BaseEntity;
import com.flowboard.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activity_logs")
public class ActivityLog extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "project_id", updatable = false)
    private UUID projectId;

    @Column(name = "task_id", updatable = false)
    private UUID taskId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", updatable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityAction action;

    @Column(nullable = false)
    private String summary;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ActivityLog() {
    }

    public ActivityLog(UUID workspaceId, UUID projectId, UUID taskId, User actor, ActivityAction action,
                       String summary, String oldValue, String newValue) {
        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.taskId = taskId;
        this.actor = actor;
        this.action = action;
        this.summary = summary;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public UUID getProjectId() { return projectId; }
    public UUID getTaskId() { return taskId; }
    public User getActor() { return actor; }
    public ActivityAction getAction() { return action; }
    public String getSummary() { return summary; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public Instant getCreatedAt() { return createdAt; }
}
