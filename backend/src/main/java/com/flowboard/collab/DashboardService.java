package com.flowboard.collab;

import com.flowboard.project.ProjectDtos.ProjectResponse;
import com.flowboard.project.ProjectService;
import com.flowboard.project.ProjectStatus;
import com.flowboard.security.AccessService;
import com.flowboard.security.CurrentUser;
import com.flowboard.task.Task;
import com.flowboard.task.TaskAssembler;
import com.flowboard.task.TaskDtos.TaskResponse;
import com.flowboard.task.TaskFilter;
import com.flowboard.task.TaskRepository;
import com.flowboard.task.TaskService;
import com.flowboard.task.TaskSpecifications;
import com.flowboard.task.TaskStatus;
import com.flowboard.workspace.WorkspaceMembership;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read models for the Overview page and global search. */
@Service
public class DashboardService {

    public record Dashboard(long assignedToMe, long dueToday, long overdue, long completedThisWeek,
                            List<ProjectResponse> activeProjects, List<TaskResponse> upcomingDeadlines,
                            boolean hasProjects) {
    }

    public record SearchResults(List<ProjectResponse> projects, List<TaskResponse> tasks) {
    }

    private static final Pattern IDENTIFIER = Pattern.compile("^([A-Za-z][A-Za-z0-9]{1,9})-(\\d+)$");

    private final TaskRepository tasks;
    private final TaskService taskService;
    private final ProjectService projectService;
    private final TaskAssembler assembler;
    private final AccessService access;
    private final Clock clock;

    public DashboardService(TaskRepository tasks, TaskService taskService, ProjectService projectService,
                            TaskAssembler assembler, AccessService access, Clock clock) {
        this.tasks = tasks;
        this.taskService = taskService;
        this.projectService = projectService;
        this.assembler = assembler;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(UUID wsId) {
        WorkspaceMembership m = access.membership(wsId);
        UUID me = CurrentUser.id();
        List<UUID> visible = taskService.visibleProjectIds(wsId, m.getRole(), false);
        List<ProjectResponse> active = projectService.list(wsId, false, null).stream()
                .filter(p -> p.status() != ProjectStatus.COMPLETED).toList();
        if (visible.isEmpty()) {
            return new Dashboard(0, 0, 0, 0, active, List.of(), false);
        }
        LocalDate today = LocalDate.now(clock);
        TaskFilter upcoming = new TaskFilter(visible, null, Set.of(TaskStatus.BACKLOG, TaskStatus.TODO, TaskStatus.IN_PROGRESS),
                null, null, null, today, today.plusDays(14), false, false, null, "dueDate", false, today);
        List<Task> upcomingTasks = tasks.findAll(TaskSpecifications.from(upcoming), PageRequest.of(0, 8)).getContent();
        return new Dashboard(
                tasks.countOpenAssigned(visible, me, TaskStatus.DONE),
                tasks.countDueOn(visible, me, TaskStatus.DONE, today),
                tasks.countOverdue(visible, me, TaskStatus.DONE, today),
                tasks.countCompletedSince(visible, me, TaskStatus.DONE, Instant.now(clock).minus(Duration.ofDays(7))),
                active, assembler.toResponses(upcomingTasks), true);
    }

    @Transactional(readOnly = true)
    public SearchResults search(UUID wsId, String q) {
        WorkspaceMembership m = access.membership(wsId);
        String term = q == null ? "" : q.trim();
        if (term.length() < 2) {
            return new SearchResults(List.of(), List.of());
        }
        List<UUID> visible = taskService.visibleProjectIds(wsId, m.getRole(), true);
        List<ProjectResponse> foundProjects = projectService.list(wsId, true, term).stream().limit(5).toList();
        if (visible.isEmpty()) {
            return new SearchResults(foundProjects, List.of());
        }
        List<Task> found = new ArrayList<>();
        Matcher id = IDENTIFIER.matcher(term);
        if (id.matches()) {
            found.addAll(tasks.findByIdentifier(visible, id.group(1).toUpperCase(), Integer.parseInt(id.group(2))));
        }
        TaskFilter f = new TaskFilter(visible, null, null, null, null, term, null, null, false, true, null,
                "updatedAt", true, LocalDate.now(clock));
        for (Task t : tasks.findAll(TaskSpecifications.from(f), PageRequest.of(0, 8)).getContent()) {
            if (!found.contains(t)) {
                found.add(t);
            }
        }
        return new SearchResults(foundProjects, assembler.toResponses(found.stream().limit(8).toList()));
    }
}
