package com.flowboard.project;

import com.flowboard.common.AuditedEntity;
import com.flowboard.user.User;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class Project extends AuditedEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "project_key", nullable = false, updatable = false)
    private String projectKey;

    @Column(nullable = false)
    private String name;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status = ProjectStatus.ACTIVE;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "task_seq", nullable = false, insertable = false, updatable = false)
    private int taskSeq;  // maintained by TaskNumberGenerator

    @Version
    private long version;

    protected Project() {
    }

    public Project(UUID workspaceId, String key, String name, User owner) {
        this.workspaceId = workspaceId;
        this.projectKey = key;
        this.name = name;
        this.owner = owner;
    }

    public UUID getWorkspaceId() { return workspaceId; }
    public String getKey() { return projectKey; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public User getOwner() { return owner; }
    public ProjectStatus getStatus() { return status; }
    public boolean isArchived() { return archived; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getTargetDate() { return targetDate; }
    public long getVersion() { return version; }

    public void update(String name, String description, ProjectStatus status, LocalDate start, LocalDate target) {
        this.name = name;
        this.description = description;
        this.status = status;
        this.startDate = start;
        this.targetDate = target;
    }

    public void setArchived(boolean archived) { this.archived = archived; }

    public void setOwner(User owner) { this.owner = owner; }

}
