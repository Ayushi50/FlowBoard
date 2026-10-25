package com.flowboard.workspace;

import com.flowboard.user.UserDtos.UserSummary;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class WorkspaceDtos {

    private WorkspaceDtos() {
    }

    public record WorkspaceResponse(UUID id, String name, Role myRole, long memberCount) {
    }

    public record MemberResponse(UserSummary user, Role role, Instant joinedAt) {
    }

    public record CreateWorkspaceRequest(@NotBlank @Size(max = 120) String name) {
    }

    public record ChangeRoleRequest(@NotNull Role role) {
    }

    public record InviteRequest(@NotBlank @Email String email, @NotNull Role role) {
    }

    public record InvitationResponse(UUID id, String email, Role role, String token, String status,
                                     Instant createdAt, Instant expiresAt, boolean emailSent) {
        public static InvitationResponse of(WorkspaceInvitation i) {
            return new InvitationResponse(i.getId(), i.getEmail(), i.getRole(), i.getToken(),
                    i.getStatus().name(), i.getCreatedAt(), i.getExpiresAt(), false);
        }

        public InvitationResponse withEmailSent(boolean sent) {
            return new InvitationResponse(id, email, role, token, status, createdAt, expiresAt, sent);
        }
    }

    public record InvitationPreview(String workspaceName, String email, Role role, boolean expired) {
    }
}
