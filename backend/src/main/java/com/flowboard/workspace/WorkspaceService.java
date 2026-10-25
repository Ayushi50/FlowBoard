package com.flowboard.workspace;

import com.flowboard.common.ApiException;
import com.flowboard.project.ProjectMembershipRepository;
import com.flowboard.security.AccessService;
import com.flowboard.security.CurrentUser;
import com.flowboard.task.LabelService;
import com.flowboard.task.TaskRepository;
import com.flowboard.user.User;
import com.flowboard.user.UserDtos.UserSummary;
import com.flowboard.workspace.WorkspaceDtos.*;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaces;
    private final WorkspaceMembershipRepository memberships;
    private final ProjectMembershipRepository projectMemberships;
    private final TaskRepository tasks;
    private final LabelService labels;
    private final AccessService access;

    public WorkspaceService(WorkspaceRepository workspaces, WorkspaceMembershipRepository memberships,
                            ProjectMembershipRepository projectMemberships, TaskRepository tasks,
                            LabelService labels, AccessService access) {
        this.workspaces = workspaces;
        this.memberships = memberships;
        this.projectMemberships = projectMemberships;
        this.tasks = tasks;
        this.labels = labels;
        this.access = access;
    }

    @Transactional
    public Workspace create(String name, User creator) {
        Workspace ws = workspaces.save(new Workspace(name, creator.getId()));
        memberships.save(new WorkspaceMembership(ws, creator, Role.ADMIN));
        labels.createDefaults(ws.getId());
        return ws;
    }

    @Transactional
    public WorkspaceResponse createForCurrentUser(String name, User user) {
        Workspace ws = create(name.trim(), user);
        return new WorkspaceResponse(ws.getId(), ws.getName(), Role.ADMIN, 1);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> mine() {
        return memberships.findByUser(CurrentUser.id()).stream()
                .map(m -> new WorkspaceResponse(m.getWorkspace().getId(), m.getWorkspace().getName(), m.getRole(),
                        memberships.countByWorkspace(m.getWorkspace().getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse get(UUID id) {
        WorkspaceMembership m = access.membership(id);
        return new WorkspaceResponse(id, m.getWorkspace().getName(), m.getRole(), memberships.countByWorkspace(id));
    }

    @Transactional
    public WorkspaceResponse rename(UUID id, String name) {
        WorkspaceMembership m = access.requireRole(id, Role.ADMIN);
        m.getWorkspace().rename(name.trim());
        return new WorkspaceResponse(id, m.getWorkspace().getName(), m.getRole(), memberships.countByWorkspace(id));
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> members(UUID wsId) {
        access.membership(wsId);
        return memberships.findByWorkspace(wsId).stream()
                .map(m -> new MemberResponse(UserSummary.of(m.getUser()), m.getRole(), m.getJoinedAt()))
                .toList();
    }

    @Transactional
    public MemberResponse changeRole(UUID wsId, UUID userId, Role role) {
        access.requireRole(wsId, Role.ADMIN);
        WorkspaceMembership target = memberships.find(wsId, userId).orElseThrow(() -> ApiException.notFound("Member"));
        if (target.getRole() == Role.ADMIN && role != Role.ADMIN && memberships.countByRole(wsId, Role.ADMIN) <= 1) {
            throw ApiException.conflict("LAST_ADMIN", "A workspace needs at least one admin");
        }
        target.setRole(role);
        return new MemberResponse(UserSummary.of(target.getUser()), role, target.getJoinedAt());
    }

    @Transactional
    public void removeMember(UUID wsId, UUID userId) {
        WorkspaceMembership caller = access.membership(wsId);
        boolean self = caller.getUser().getId().equals(userId);
        if (!self && caller.getRole() != Role.ADMIN) {
            throw ApiException.forbidden("Only admins can remove other members");
        }
        WorkspaceMembership target = memberships.find(wsId, userId).orElseThrow(() -> ApiException.notFound("Member"));
        if (target.getRole() == Role.ADMIN && memberships.countByRole(wsId, Role.ADMIN) <= 1) {
            throw ApiException.conflict("LAST_ADMIN", "A workspace needs at least one admin");
        }
        projectMemberships.deleteByUserInWorkspace(userId, wsId);
        tasks.unassignUserInWorkspace(userId, wsId);
        memberships.delete(target);
    }
}
