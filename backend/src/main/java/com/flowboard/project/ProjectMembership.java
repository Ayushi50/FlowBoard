package com.flowboard.project;

import com.flowboard.common.BaseEntity;
import com.flowboard.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "project_memberships")
public class ProjectMembership extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt = Instant.now();

    protected ProjectMembership() {
    }

    public ProjectMembership(Project project, User user) {
        this.project = project;
        this.user = user;
    }

    public Project getProject() { return project; }
    public User getUser() { return user; }
    public Instant getAddedAt() { return addedAt; }
}
