package com.flowboard.project;

import com.flowboard.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "milestones")
public class Milestone extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String name;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Milestone() {
    }

    public Milestone(UUID projectId, String name, LocalDate dueDate) {
        this.projectId = projectId;
        this.name = name;
        this.dueDate = dueDate;
    }

    public UUID getProjectId() { return projectId; }
    public String getName() { return name; }
    public LocalDate getDueDate() { return dueDate; }
}
