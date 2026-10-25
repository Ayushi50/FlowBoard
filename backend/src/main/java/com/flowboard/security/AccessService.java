package com.flowboard.security;

import com.flowboard.common.ApiException;
import com.flowboard.project.Project;
import com.flowboard.project.ProjectMembershipRepository;
import com.flowboard.project.ProjectRepository;
import com.flowboard.workspace.Role;
import com.flowboard.workspace.WorkspaceMembership;
import com.flowboard.workspace.WorkspaceMembershipRepository;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central authorization rules. Every service call that touches workspace-scoped data goes through here.
 * <p>
 * Resources the caller may not know about answer 404 (never 403) so identifiers cannot be probed
 * across workspaces; a known resource with insufficient rights answers 403.
 * <ul>
 *   <li>ADMIN: everything in the workspace, including every project.</li>
 *   <li>MANAGER: create projects, manage members/settings of projects they belong to, coordinate tasks.</li>
 *   <li>MEMBER: see projects they belong to and manage tasks inside them.</li>
 * </ul>
 */
@Service
public class AccessService {

    /** Result of a project access check. */
    public record ProjectAccess(Project project, UUID userId, Role role, boolean projectMember) {
        public boolean isAdmin() { return role == Role.ADMIN; }
        public boolean canManageProject() { return isAdmin() || (role == Role.MANAGER && projectMember); }
        public boolean canManageMembers() { return canManageProject(); }
    }

    private final WorkspaceMembershipRepository memberships;
    private final ProjectRepository projects;
    private final ProjectMembershipRepository projectMemberships;

    public AccessService(WorkspaceMembershipRepository memberships, ProjectRepository projects,
                         ProjectMembershipRepository projectMemberships) {
        this.memberships = memberships;
        this.projects = projects;
        this.projectMemberships = projectMemberships;
    }

    @Transactional(readOnly = true)
    public WorkspaceMembership membership(UUID workspaceId) {
        return memberships.find(workspaceId, CurrentUser.id())
                .orElseThrow(() -> ApiException.notFound("Workspace"));
    }

    @Transactional(readOnly = true)
    public WorkspaceMembership requireRole(UUID workspaceId, Role... allowed) {
        WorkspaceMembership m = membership(workspaceId);
        if (Arrays.stream(allowed).noneMatch(r -> r == m.getRole())) {
            throw ApiException.forbidden("Your role (" + m.getRole() + ") cannot perform this action");
        }
        return m;
    }

    /** Loads a project the caller is allowed to see. */
    @Transactional(readOnly = true)
    public ProjectAccess project(UUID projectId) {
        UUID userId = CurrentUser.id();
        Project project = projects.findWithOwner(projectId).orElseThrow(() -> ApiException.notFound("Project"));
        WorkspaceMembership m = memberships.find(project.getWorkspaceId(), userId)
                .orElseThrow(() -> ApiException.notFound("Project"));
        boolean member = projectMemberships.isMember(projectId, userId);
        if (m.getRole() != Role.ADMIN && !member) {
            throw ApiException.notFound("Project");
        }
        return new ProjectAccess(project, userId, m.getRole(), member);
    }

    public ProjectAccess projectManage(UUID projectId) {
        ProjectAccess a = project(projectId);
        if (!a.canManageProject()) {
            throw ApiException.forbidden("Only workspace admins or managers of this project can do that");
        }
        return a;
    }

    public ProjectAccess projectWritable(UUID projectId) {
        ProjectAccess a = project(projectId);
        if (a.project().isArchived()) {
            throw ApiException.conflict("PROJECT_ARCHIVED", "This project is archived and read-only");
        }
        return a;
    }
}
