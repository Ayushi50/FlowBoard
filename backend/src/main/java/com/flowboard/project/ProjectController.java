package com.flowboard.project;

import com.flowboard.collab.CollabDtos.ActivityResponse;
import com.flowboard.common.PageResponse;
import com.flowboard.project.ProjectDtos.*;
import com.flowboard.collab.ActivityQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Projects")
public class ProjectController {

    private final ProjectService projects;
    private final ActivityQueryService activity;

    public ProjectController(ProjectService projects, ActivityQueryService activity) {
        this.projects = projects;
        this.activity = activity;
    }

    @GetMapping("/workspaces/{wsId}/projects")
    public List<ProjectResponse> list(@PathVariable UUID wsId, @RequestParam(defaultValue = "false") boolean includeArchived,
                                      @RequestParam(required = false) String q) {
        return projects.list(wsId, includeArchived, q);
    }

    @GetMapping("/workspaces/{wsId}/projects/recent")
    public List<ProjectResponse> recent(@PathVariable UUID wsId, @RequestParam(defaultValue = "5") int limit) {
        return projects.recent(wsId, limit);
    }

    @PostMapping("/workspaces/{wsId}/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@PathVariable UUID wsId, @Valid @RequestBody CreateProjectRequest req) {
        return projects.create(wsId, req);
    }

    @GetMapping("/projects/{id}")
    public ProjectResponse get(@PathVariable UUID id) {
        return projects.get(id);
    }

    @GetMapping("/projects/{id}/overview")
    public ProjectOverview overview(@PathVariable UUID id) {
        return projects.overview(id);
    }

    @PutMapping("/projects/{id}")
    public ProjectResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateProjectRequest req) {
        return projects.update(id, req);
    }

    @PostMapping("/projects/{id}/archive")
    public ProjectResponse archive(@PathVariable UUID id) {
        return projects.setArchived(id, true);
    }

    @PostMapping("/projects/{id}/unarchive")
    public ProjectResponse unarchive(@PathVariable UUID id) {
        return projects.setArchived(id, false);
    }

    @DeleteMapping("/projects/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        projects.delete(id);
    }

    @PostMapping("/projects/{id}/members")
    public ProjectResponse addMember(@PathVariable UUID id, @Valid @RequestBody AddMemberRequest req) {
        return projects.addMember(id, req.userId());
    }

    @DeleteMapping("/projects/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable UUID id, @PathVariable UUID userId) {
        projects.removeMember(id, userId);
    }

    @PutMapping("/projects/{id}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void favorite(@PathVariable UUID id) {
        projects.setFavorite(id, true);
    }

    @DeleteMapping("/projects/{id}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfavorite(@PathVariable UUID id) {
        projects.setFavorite(id, false);
    }

    @PostMapping("/projects/{id}/visit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void visit(@PathVariable UUID id) {
        projects.visit(id);
    }

    @GetMapping("/projects/{id}/milestones")
    public List<MilestoneResponse> milestones(@PathVariable UUID id) {
        return projects.milestones(id);
    }

    @PostMapping("/projects/{id}/milestones")
    @ResponseStatus(HttpStatus.CREATED)
    public MilestoneResponse addMilestone(@PathVariable UUID id, @Valid @RequestBody MilestoneRequest req) {
        return projects.addMilestone(id, req);
    }

    @DeleteMapping("/milestones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMilestone(@PathVariable UUID id) {
        projects.deleteMilestone(id);
    }

    @GetMapping("/projects/{id}/activity")
    public PageResponse<ActivityResponse> activity(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return activity.forProject(id, page, size);
    }
}
