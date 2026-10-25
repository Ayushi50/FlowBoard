package com.flowboard.collab;

import com.flowboard.collab.CollabDtos.ActivityResponse;
import com.flowboard.common.PageResponse;
import com.flowboard.project.Project;
import com.flowboard.project.ProjectRepository;
import com.flowboard.security.AccessService;
import com.flowboard.security.CurrentUser;
import com.flowboard.workspace.Role;
import com.flowboard.workspace.WorkspaceMembership;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityQueryService {

    private final ActivityRepository repo;
    private final ProjectRepository projects;
    private final AccessService access;

    public ActivityQueryService(ActivityRepository repo, ProjectRepository projects, AccessService access) {
        this.repo = repo;
        this.projects = projects;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> forProject(UUID projectId, int page, int size) {
        access.project(projectId);
        return PageResponse.of(repo.findByProject(projectId, pageable(page, size)), ActivityResponse::of);
    }

    /** Team feed: only activity from projects the caller can see. */
    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> forWorkspace(UUID wsId, int page, int size) {
        WorkspaceMembership m = access.membership(wsId);
        List<UUID> visible = projects.findVisible(wsId, CurrentUser.id(), m.getRole() == Role.ADMIN, true, "")
                .stream().map(Project::getId).toList();
        if (visible.isEmpty()) {
            return new PageResponse<>(List.of(), page, size, 0, 0);
        }
        return PageResponse.of(repo.findByProjects(wsId, visible, pageable(page, size)), ActivityResponse::of);
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }
}
