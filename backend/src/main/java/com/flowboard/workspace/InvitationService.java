package com.flowboard.workspace;

import com.flowboard.common.ApiException;
import com.flowboard.security.AccessService;
import com.flowboard.security.CurrentUser;
import com.flowboard.user.User;
import com.flowboard.user.UserRepository;
import com.flowboard.workspace.WorkspaceDtos.*;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final WorkspaceInvitationRepository invitations;
    private final WorkspaceMembershipRepository memberships;
    private final WorkspaceRepository workspaces;
    private final AccessService access;
    private final InvitationMailer mailer;
    private final UserRepository users;

    public InvitationService(WorkspaceInvitationRepository invitations, WorkspaceMembershipRepository memberships,
                             WorkspaceRepository workspaces, AccessService access,
                             InvitationMailer mailer, UserRepository users) {
        this.invitations = invitations;
        this.memberships = memberships;
        this.workspaces = workspaces;
        this.access = access;
        this.mailer = mailer;
        this.users = users;
    }

    /** Emails a committed invitation; a mail failure never loses the invitation. Call after {@link #invite}. */
    public InvitationResponse notifyInvitee(UUID wsId, InvitationResponse created) {
        String workspaceName = workspaces.findById(wsId).map(Workspace::getName).orElse("a workspace");
        String inviter = users.findById(CurrentUser.id()).map(User::getDisplayName).orElse("A teammate");
        return created.withEmailSent(mailer.send(created.email(), workspaceName, inviter, created.role(), created.token()));
    }

    @Transactional
    public InvitationResponse invite(UUID wsId, InviteRequest req) {
        access.requireRole(wsId, Role.ADMIN);
        String email = req.email().trim();
        invitations.findPendingByEmail(wsId, email).ifPresent(i -> i.setStatus(WorkspaceInvitation.Status.REVOKED));
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        WorkspaceInvitation inv = new WorkspaceInvitation(workspaces.getReferenceById(wsId), email, req.role(),
                HexFormat.of().formatHex(bytes), CurrentUser.id(), Instant.now().plus(Duration.ofDays(7)));
        return InvitationResponse.of(invitations.save(inv));
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> pending(UUID wsId) {
        access.requireRole(wsId, Role.ADMIN);
        return invitations.findPending(wsId).stream().map(InvitationResponse::of).toList();
    }

    @Transactional
    public void revoke(UUID wsId, UUID invitationId) {
        access.requireRole(wsId, Role.ADMIN);
        WorkspaceInvitation inv = invitations.findById(invitationId)
                .filter(i -> i.getWorkspace().getId().equals(wsId))
                .orElseThrow(() -> ApiException.notFound("Invitation"));
        inv.setStatus(WorkspaceInvitation.Status.REVOKED);
    }

    @Transactional(readOnly = true)
    public InvitationPreview preview(String token) {
        WorkspaceInvitation inv = usable(token);
        return new InvitationPreview(inv.getWorkspace().getName(), inv.getEmail(), inv.getRole(), false);
    }

    /** Accepts an invitation on behalf of {@code user}; the invited email must match the account email. */
    @Transactional
    public WorkspaceResponse accept(String token, User user) {
        WorkspaceInvitation inv = usable(token);
        if (!inv.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw ApiException.forbidden("This invitation was issued to a different email address");
        }
        UUID wsId = inv.getWorkspace().getId();
        if (!memberships.isMember(wsId, user.getId())) {
            memberships.save(new WorkspaceMembership(inv.getWorkspace(), user, inv.getRole()));
        }
        inv.setStatus(WorkspaceInvitation.Status.ACCEPTED);
        return new WorkspaceResponse(wsId, inv.getWorkspace().getName(), inv.getRole(), memberships.countByWorkspace(wsId));
    }

    private WorkspaceInvitation usable(String token) {
        WorkspaceInvitation inv = invitations.findByToken(token).orElseThrow(() -> ApiException.notFound("Invitation"));
        if (inv.getStatus() != WorkspaceInvitation.Status.PENDING || inv.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.conflict("INVITATION_UNUSABLE", "This invitation is no longer valid");
        }
        return inv;
    }
}
