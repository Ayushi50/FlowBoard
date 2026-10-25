package com.flowboard.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserSummary(UUID id, String displayName, String email, String avatarColor, String jobTitle) {
        public static UserSummary of(User u) {
            return u == null ? null
                    : new UserSummary(u.getId(), u.getDisplayName(), u.getEmail(), u.getAvatarColor(), u.getJobTitle());
        }
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters") String password,
            @NotBlank @Size(max = 120) String displayName,
            @Size(max = 120) String workspaceName,
            String invitationToken) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record AuthResponse(String token, long expiresInSeconds, UserSummary user) {
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 120) String displayName,
            @Size(max = 120) String jobTitle,
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a hex color") String avatarColor) {
    }
}
