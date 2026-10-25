package com.flowboard.workspace;

import com.flowboard.common.BaseEntity;
import com.flowboard.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "workspace_memberships")
public class WorkspaceMembership extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    protected WorkspaceMembership() {
    }

    public WorkspaceMembership(Workspace workspace, User user, Role role) {
        this.workspace = workspace;
        this.user = user;
        this.role = role;
    }

    public Workspace getWorkspace() { return workspace; }
    public User getUser() { return user; }
    public Role getRole() { return role; }
    public Instant getJoinedAt() { return joinedAt; }
    public void setRole(Role role) { this.role = role; }
}
