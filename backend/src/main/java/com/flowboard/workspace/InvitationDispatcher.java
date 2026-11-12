package com.flowboard.workspace;

import com.flowboard.workspace.WorkspaceInvitation.EmailStatus;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Sends invitation emails off the request thread and records the outcome on the invitation. */
@Component
public class InvitationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(InvitationDispatcher.class);

    private final InvitationMailer mailer;
    private final WorkspaceInvitationRepository invitations;

    public InvitationDispatcher(InvitationMailer mailer, WorkspaceInvitationRepository invitations) {
        this.mailer = mailer;
        this.invitations = invitations;
    }

    @Async
    public void deliver(UUID invitationId, String to, String workspaceName, String inviterName, Role role, String token) {
        EmailStatus result = EmailStatus.FAILED;
        try {
            result = mailer.send(to, workspaceName, inviterName, role, token) ? EmailStatus.SENT : EmailStatus.FAILED;
        } catch (RuntimeException e) {
            log.warn("Invitation email to {} failed unexpectedly: {}", to, e.getMessage());
        }
        try {
            invitations.updateEmailStatus(invitationId, result);
        } catch (RuntimeException e) {
            log.error("Could not record email status {} for invitation {}", result, invitationId, e);
        }
    }

    /** Anything still QUEUED at startup was interrupted by a restart; surface it so it can be resent. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        int n = invitations.failStaleQueued();
        if (n > 0) {
            log.warn("Marked {} interrupted invitation email(s) as FAILED", n);
        }
    }
}
