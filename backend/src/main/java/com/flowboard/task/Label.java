package com.flowboard.task;

import com.flowboard.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "labels")
public class Label extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String color;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Label() {
    }

    public Label(UUID workspaceId, String name, String color) {
        this.workspaceId = workspaceId;
        this.name = name;
        this.color = color;
    }

    public UUID getWorkspaceId() { return workspaceId; }
    public String getName() { return name; }
    public String getColor() { return color; }
}
