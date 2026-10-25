package com.flowboard.workspace;

import com.flowboard.security.CurrentUser;
import com.flowboard.user.UserService;
import com.flowboard.workspace.WorkspaceDtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaces;
    private final InvitationService invitations;
    private final UserService users;

    public WorkspaceController(WorkspaceService workspaces, InvitationService invitations, UserService users) {
        this.workspaces = workspaces;
        this.invitations = invitations;
        this.users = users;
    }

    @GetMapping("/workspaces")
    public List<WorkspaceResponse> mine() {
        return workspaces.mine();
    }

    @PostMapping("/workspaces")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceResponse create(@Valid @RequestBody CreateWorkspaceRequest req) {
        return workspaces.createForCurrentUser(req.name(), users.get(CurrentUser.id()));
    }

    @GetMapping("/workspaces/{id}")
    public WorkspaceResponse get(@PathVariable UUID id) {
        return workspaces.get(id);
    }

    @PatchMapping("/workspaces/{id}")
    public WorkspaceResponse rename(@PathVariable UUID id, @Valid @RequestBody CreateWorkspaceRequest req) {
        return workspaces.rename(id, req.name());
    }

    @GetMapping("/workspaces/{id}/members")
    public List<MemberResponse> members(@PathVariable UUID id) {
        return workspaces.members(id);
    }

    @PatchMapping("/workspaces/{id}/members/{userId}")
    public MemberResponse changeRole(@PathVariable UUID id, @PathVariable UUID userId,
                                     @Valid @RequestBody ChangeRoleRequest req) {
        return workspaces.changeRole(id, userId, req.role());
    }

    @DeleteMapping("/workspaces/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable UUID id, @PathVariable UUID userId) {
        workspaces.removeMember(id, userId);
    }

    @GetMapping("/workspaces/{id}/invitations")
    public List<InvitationResponse> pending(@PathVariable UUID id) {
        return invitations.pending(id);
    }

    @PostMapping("/workspaces/{id}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse invite(@PathVariable UUID id, @Valid @RequestBody InviteRequest req) {
        return invitations.invite(id, req);
    }

    @DeleteMapping("/workspaces/{id}/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id, @PathVariable UUID invitationId) {
        invitations.revoke(id, invitationId);
    }

    @GetMapping("/invitations/{token}")
    public InvitationPreview preview(@PathVariable String token) {
        return invitations.preview(token);
    }

    @PostMapping("/invitations/{token}/accept")
    public WorkspaceResponse accept(@PathVariable String token) {
        return invitations.accept(token, users.get(CurrentUser.id()));
    }
}
