package com.flowboard.user;

import com.flowboard.common.ApiException;
import com.flowboard.security.JwtService;
import com.flowboard.user.UserDtos.*;
import com.flowboard.workspace.InvitationService;
import com.flowboard.workspace.WorkspaceService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final List<String> PALETTE =
            List.of("#6366f1", "#8b5cf6", "#ec4899", "#f59e0b", "#10b981", "#0ea5e9", "#ef4444", "#14b8a6");

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final WorkspaceService workspaces;
    private final InvitationService invitations;

    public UserService(UserRepository users, PasswordEncoder encoder, JwtService jwt,
                       WorkspaceService workspaces, InvitationService invitations) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.workspaces = workspaces;
        this.invitations = invitations;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim();
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        String color = PALETTE.get(Math.floorMod(email.toLowerCase().hashCode(), PALETTE.size()));
        User user = users.save(new User(email, req.displayName().trim(), encoder.encode(req.password()), color));
        if (req.invitationToken() != null && !req.invitationToken().isBlank()) {
            invitations.accept(req.invitationToken(), user);
        } else if (req.workspaceName() != null && !req.workspaceName().isBlank()) {
            workspaces.create(req.workspaceName().trim(), user);
        }
        return new AuthResponse(jwt.issue(user.getId()), jwt.ttlSeconds(), UserSummary.of(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmail(req.email().trim())
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        return new AuthResponse(jwt.issue(user.getId()), jwt.ttlSeconds(), UserSummary.of(user));
    }

    @Transactional(readOnly = true)
    public User get(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.unauthorized("Unknown user"));
    }

    @Transactional
    public UserSummary updateProfile(UUID id, UpdateProfileRequest req) {
        User user = get(id);
        user.updateProfile(req.displayName().trim(), req.jobTitle(), req.avatarColor());
        return UserSummary.of(user);
    }
}
