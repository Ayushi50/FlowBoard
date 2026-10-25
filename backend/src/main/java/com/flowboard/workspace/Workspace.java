package com.flowboard.workspace;

import com.flowboard.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "workspaces")
public class Workspace extends AuditedEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    protected Workspace() {
    }

    public Workspace(String name, UUID createdBy) {
        this.name = name;
        this.createdBy = createdBy;
    }

    public String getName() { return name; }
    public UUID getCreatedBy() { return createdBy; }
    public void rename(String name) { this.name = name; }
}
