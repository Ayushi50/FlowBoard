package com.flowboard.seed;

import com.flowboard.collab.ActivityAction;
import com.flowboard.collab.ActivityLog;
import com.flowboard.collab.ActivityRepository;
import com.flowboard.collab.Notification;
import com.flowboard.collab.NotificationRepository;
import com.flowboard.collab.NotificationType;
import com.flowboard.project.Milestone;
import com.flowboard.project.MilestoneRepository;
import com.flowboard.project.Project;
import com.flowboard.project.ProjectMembership;
import com.flowboard.project.ProjectMembershipRepository;
import com.flowboard.project.ProjectRepository;
import com.flowboard.project.ProjectStatus;
import com.flowboard.task.*;
import com.flowboard.user.User;
import com.flowboard.user.UserRepository;
import com.flowboard.workspace.Role;
import com.flowboard.workspace.Workspace;
import com.flowboard.workspace.WorkspaceMembership;
import com.flowboard.workspace.WorkspaceMembershipRepository;
import com.flowboard.workspace.WorkspaceService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates a believable demo workspace (enabled with SEED_DEMO=true). Idempotent: does nothing if the demo
 * admin already exists. Log in as demo@flowboard.dev / Password123! (demo data only - never reuse this password).
 */
@Component
@ConditionalOnProperty(name = "flowboard.seed.demo", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    public static final String DEMO_EMAIL = "demo@flowboard.dev";
    public static final String DEMO_PASSWORD = "Password123!";

    private final UserRepository users;
    private final WorkspaceService workspaces;
    private final WorkspaceMembershipRepository wsMemberships;
    private final ProjectRepository projects;
    private final ProjectMembershipRepository projectMemberships;
    private final MilestoneRepository milestones;
    private final TaskRepository tasks;
    private final LabelRepository labels;
    private final CommentRepository comments;
    private final ChecklistRepository checklists;
    private final ActivityRepository activity;
    private final NotificationRepository notifications;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final TaskNumberGenerator numbers;

    public DemoDataSeeder(UserRepository users, WorkspaceService workspaces, WorkspaceMembershipRepository wsMemberships,
                          ProjectRepository projects, ProjectMembershipRepository projectMemberships,
                          MilestoneRepository milestones, TaskRepository tasks, LabelRepository labels,
                          CommentRepository comments, ChecklistRepository checklists, ActivityRepository activity,
                          NotificationRepository notifications, PasswordEncoder encoder, TransactionTemplate tx,
                          Clock clock, TaskNumberGenerator numbers) {
        this.users = users;
        this.workspaces = workspaces;
        this.wsMemberships = wsMemberships;
        this.projects = projects;
        this.projectMemberships = projectMemberships;
        this.milestones = milestones;
        this.tasks = tasks;
        this.labels = labels;
        this.comments = comments;
        this.checklists = checklists;
        this.activity = activity;
        this.notifications = notifications;
        this.encoder = encoder;
        this.tx = tx;
        this.clock = clock;
        this.numbers = numbers;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByEmail(DEMO_EMAIL)) {
            log.info("Demo data already present, skipping");
            return;
        }
        tx.executeWithoutResult(s -> seed());
        log.info("Demo data created. Log in with {} / {}", DEMO_EMAIL, DEMO_PASSWORD);
    }

    private User user(String email, String name, String title, String color) {
        User u = new User(email, name, encoder.encode(DEMO_PASSWORD), color);
        u.updateProfile(name, title, color);
        return users.save(u);
    }

    private void seed() {
        LocalDate today = LocalDate.now(clock);
        Random rnd = new Random(42);

        User ava = user(DEMO_EMAIL, "Ava Chen", "Engineering Director", "#6366f1");
        User marcus = user("marcus@flowboard.dev", "Marcus Rivera", "Engineering Manager", "#0ea5e9");
        User priya = user("priya@flowboard.dev", "Priya Nair", "Senior Backend Engineer", "#ec4899");
        User sam = user("sam@flowboard.dev", "Sam Okafor", "Frontend Engineer", "#10b981");
        User lena = user("lena@flowboard.dev", "Lena Fischer", "Product Designer", "#f59e0b");
        User jonas = user("jonas@flowboard.dev", "Jonas Berg", "QA Engineer", "#8b5cf6");

        Workspace ws = workspaces.create("Acme Engineering", ava);
        wsMemberships.save(new WorkspaceMembership(ws, marcus, Role.MANAGER));
        for (User u : List.of(priya, sam, lena, jonas)) {
            wsMemberships.save(new WorkspaceMembership(ws, u, Role.MEMBER));
        }
        Map<String, Label> label = labels.findByWorkspaceIdOrderByName(ws.getId()).stream()
                .collect(Collectors.toMap(Label::getName, l -> l));

        buildProject(ws, "WEB", "Web App Redesign", "Rebuild the customer-facing web app with the new design system.",
                marcus, List.of(ava, marcus, priya, sam, lena, jonas), today.minusDays(40), today.plus(30, ChronoUnit.DAYS),
                List.of(
                    t("Audit existing navigation patterns", TaskStatus.DONE, TaskPriority.MEDIUM, TaskType.TASK, "lena", -30, -24, "design"),
                    t("Define design tokens for color and spacing", TaskStatus.DONE, TaskPriority.HIGH, TaskType.TASK, "lena", -28, -20, "design"),
                    t("Set up Storybook with component docs", TaskStatus.DONE, TaskPriority.LOW, TaskType.TASK, "sam", -25, -18, "frontend"),
                    t("Implement responsive app shell and sidebar", TaskStatus.DONE, TaskPriority.HIGH, TaskType.FEATURE, "sam", -20, -9, "frontend"),
                    t("Migrate settings pages to new components", TaskStatus.IN_PROGRESS, TaskPriority.MEDIUM, TaskType.IMPROVEMENT, "sam", -6, 4, "frontend"),
                    t("Fix focus trap in modal dialogs", TaskStatus.IN_PROGRESS, TaskPriority.URGENT, TaskType.BUG, "sam", -3, -1, "bug", "frontend"),
                    t("Dark mode contrast review", TaskStatus.IN_PROGRESS, TaskPriority.MEDIUM, TaskType.TASK, "ava", -2, 0, "design"),
                    t("Checkout page redesign", TaskStatus.TODO, TaskPriority.HIGH, TaskType.FEATURE, "lena", 0, 12, "design", "feature"),
                    t("Add skeleton loading states", TaskStatus.TODO, TaskPriority.LOW, TaskType.IMPROVEMENT, "sam", 1, 10, "frontend"),
                    t("Cross-browser regression pass", TaskStatus.TODO, TaskPriority.MEDIUM, TaskType.TASK, "jonas", 3, 14, "bug"),
                    t("Accessibility audit with axe", TaskStatus.TODO, TaskPriority.HIGH, TaskType.TASK, "ava", -4, -1, "frontend"),
                    t("Update onboarding illustrations", TaskStatus.BACKLOG, TaskPriority.LOW, TaskType.TASK, "lena", null, null, "design"),
                    t("Evaluate Lighthouse performance budget", TaskStatus.BACKLOG, TaskPriority.MEDIUM, TaskType.IMPROVEMENT, null, null, null, "frontend"),
                    t("Localisation-ready string extraction", TaskStatus.BACKLOG, TaskPriority.NONE, TaskType.FEATURE, null, null, null, "frontend", "docs")),
                label, rnd, today);

        buildProject(ws, "API", "Platform API v2", "Versioned REST API, rate limiting and webhooks for partners.",
                marcus, List.of(ava, marcus, priya, jonas), today.minusDays(55), today.plusDays(21),
                List.of(
                    t("Design v2 resource model and error format", TaskStatus.DONE, TaskPriority.HIGH, TaskType.TASK, "priya", -50, -40, "backend", "docs"),
                    t("Implement cursor pagination", TaskStatus.DONE, TaskPriority.MEDIUM, TaskType.FEATURE, "priya", -38, -30, "backend"),
                    t("Token-bucket rate limiter", TaskStatus.DONE, TaskPriority.HIGH, TaskType.FEATURE, "priya", -30, -20, "backend"),
                    t("Webhook delivery with retries", TaskStatus.IN_PROGRESS, TaskPriority.HIGH, TaskType.FEATURE, "priya", -9, 3, "backend", "feature"),
                    t("N+1 query on /projects listing", TaskStatus.IN_PROGRESS, TaskPriority.URGENT, TaskType.BUG, "priya", -4, 0, "bug", "backend"),
                    t("Publish OpenAPI spec and Postman collection", TaskStatus.TODO, TaskPriority.MEDIUM, TaskType.TASK, "ava", 0, 5, "docs"),
                    t("Contract tests for partner integrations", TaskStatus.TODO, TaskPriority.MEDIUM, TaskType.TASK, "jonas", 2, 13, "backend"),
                    t("Idempotency keys for POST endpoints", TaskStatus.TODO, TaskPriority.HIGH, TaskType.FEATURE, "priya", 1, 8, "backend"),
                    t("Deprecation headers for v1 routes", TaskStatus.BACKLOG, TaskPriority.LOW, TaskType.IMPROVEMENT, null, null, null, "backend"),
                    t("Audit log export endpoint", TaskStatus.BACKLOG, TaskPriority.MEDIUM, TaskType.FEATURE, null, null, null, "backend", "feature")),
                label, rnd, today);

        buildProject(ws, "MOB", "Mobile Launch", "Ship the iOS and Android companion apps.",
                ava, List.of(ava, marcus, sam, lena), today.minusDays(10), today.plusDays(75),
                List.of(
                    t("Pick cross-platform framework", TaskStatus.DONE, TaskPriority.HIGH, TaskType.TASK, "ava", -9, -5, "docs"),
                    t("Mobile navigation prototype", TaskStatus.IN_PROGRESS, TaskPriority.MEDIUM, TaskType.TASK, "lena", -4, 6, "design"),
                    t("Push notification service spike", TaskStatus.TODO, TaskPriority.MEDIUM, TaskType.TASK, "sam", 1, 15, "backend"),
                    t("App store listing assets", TaskStatus.BACKLOG, TaskPriority.LOW, TaskType.TASK, "lena", null, null, "design"),
                    t("Offline sync strategy", TaskStatus.BACKLOG, TaskPriority.HIGH, TaskType.FEATURE, "ava", null, null, "feature")),
                label, rnd, today);
    }

    private record TaskSeed(String title, TaskStatus status, TaskPriority priority, TaskType type, String assignee,
                            Integer startOffset, Integer dueOffset, String[] labels) {
    }

    private static TaskSeed t(String title, TaskStatus status, TaskPriority priority, TaskType type, String assignee,
                              Integer startOffset, Integer dueOffset, String... labels) {
        return new TaskSeed(title, status, priority, type, assignee, startOffset, dueOffset, labels);
    }

    private void buildProject(Workspace ws, String key, String name, String description, User owner, List<User> members,
                              LocalDate start, LocalDate target, List<TaskSeed> seeds, Map<String, Label> labelsByName,
                              Random rnd, LocalDate today) {
        Project p = new Project(ws.getId(), key, name, owner);
        p.update(name, description, ProjectStatus.ACTIVE, start, target);
        projects.saveAndFlush(p);
        for (User m : members) {
            projectMemberships.save(new ProjectMembership(p, m));
        }
        milestones.save(new Milestone(p.getId(), "Design freeze", start.plusDays(30)));
        milestones.save(new Milestone(p.getId(), "Beta release", target.minusDays(10)));
        milestones.save(new Milestone(p.getId(), "General availability", target));
        activity.save(new ActivityLog(ws.getId(), p.getId(), null, owner, ActivityAction.PROJECT_CREATED,
                "created project " + name + " (" + key + ")", null, null));

        Map<String, User> byFirstName = members.stream()
                .collect(Collectors.toMap(u -> u.getDisplayName().split(" ")[0].toLowerCase(), u -> u));
        Map<TaskStatus, Double> positions = new java.util.EnumMap<>(TaskStatus.class);
        List<Task> created = new ArrayList<>();
        for (TaskSeed s : seeds) {
            Task task = new Task(p, numbers.next(p.getId()), s.title(), owner);
            task.setStatus(s.status());
            task.setPriority(s.priority());
            task.setType(s.type());
            task.setAssignee(s.assignee() == null ? null : byFirstName.get(s.assignee()));
            if (s.startOffset() != null) {
                task.setStartDate(today.plusDays(s.startOffset()));
            }
            if (s.dueOffset() != null) {
                task.setDueDate(today.plusDays(s.dueOffset()));
            }
            task.setDescription("Context and acceptance criteria for \"" + s.title()
                    + "\".\n\n- Agree scope with the team\n- Link related designs and tickets\n- Capture follow-ups as subtasks");
            double pos = positions.merge(s.status(), 1024.0, (a, b) -> a + 1024.0);
            task.setPosition(pos);
            Set<Label> ls = new HashSet<>();
            for (String l : s.labels()) {
                ls.add(labelsByName.get(l));
            }
            task.getLabels().addAll(ls);
            tasks.saveAndFlush(task);
            created.add(task);
            activity.save(new ActivityLog(ws.getId(), p.getId(), task.getId(), owner, ActivityAction.TASK_CREATED,
                    "created " + task.identifier() + " \"" + task.getTitle() + "\"", null, null));
            if (task.getAssignee() != null && !task.getAssignee().equals(owner) && s.status() != TaskStatus.DONE) {
                notifications.save(new Notification(ws.getId(), task.getAssignee().getId(), NotificationType.ASSIGNED,
                        owner, task.getId(), p.getId(), owner.getDisplayName() + " assigned you " + task.identifier()
                        + ": " + task.getTitle()));
            }
        }

        // comments, checklist items and a subtask on in-flight work
        String[] chatter = {"Looks good to me - ready for review.", "Blocked on design feedback, will sync tomorrow.",
                "I pushed a first pass, would love a second pair of eyes.", "Can we de-scope the edge case for now?",
                "Added notes to the description."};
        for (Task task : created) {
            if (task.getStatus() == TaskStatus.BACKLOG) {
                continue;
            }
            User author = members.get(rnd.nextInt(members.size()));
            for (int i = 0; i < 1 + rnd.nextInt(3); i++) {
                comments.save(new Comment(task, members.get(rnd.nextInt(members.size())), chatter[rnd.nextInt(chatter.length)]));
            }
            if (task.getStatus() == TaskStatus.IN_PROGRESS) {
                checklists.save(new ChecklistItem(task.getId(), "Write implementation", 1));
                ChecklistItem done = new ChecklistItem(task.getId(), "Review with design", 2);
                done.setDone(true);
                checklists.save(done);
                checklists.save(new ChecklistItem(task.getId(), "Add tests", 3));
                Task sub = new Task(p, numbers.next(p.getId()), "Follow-up: " + task.getTitle().toLowerCase(), author);
                sub.setParent(task);
                sub.setStatus(TaskStatus.TODO);
                sub.setPosition(1024);
                tasks.saveAndFlush(sub);
            }
            activity.save(new ActivityLog(ws.getId(), p.getId(), task.getId(), author, ActivityAction.COMMENT_ADDED,
                    "commented on " + task.identifier(), null, null));
        }
        // a mention for the demo admin, so the inbox is not empty
        Task first = created.stream().filter(x -> x.getStatus() == TaskStatus.IN_PROGRESS).findFirst().orElse(created.get(0));
        notifications.save(new Notification(ws.getId(), members.get(0).getId(), NotificationType.MENTIONED,
                members.get(members.size() - 1), first.getId(), p.getId(),
                members.get(members.size() - 1).getDisplayName() + " mentioned you on " + first.identifier()));
        log.info("Seeded project {} with {} tasks", key, created.size());
    }
}
