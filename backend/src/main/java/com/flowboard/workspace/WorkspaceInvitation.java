package com.flowboard.workspace;

import com.flowboard.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace_invitations")
public class WorkspaceInvitation extends BaseEntity {

    public enum Status { PENDING, ACCEPTED, REVOKED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected WorkspaceInvitation() {
    }

    public WorkspaceInvitation(Workspace workspace, String email, Role role, String token, UUID invitedBy,
                               Instant expiresAt) {
        this.workspace = workspace;
        this.email = email;
        this.role = role;
        this.token = token;
        this.invitedBy = invitedBy;
        this.expiresAt = expiresAt;
    }

    public Workspace getWorkspace() { return workspace; }
    public String getEmail() { return email; }
    public Role getRole() { return role; }
    public String getToken() { return token; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setStatus(Status status) { this.status = status; }
}
