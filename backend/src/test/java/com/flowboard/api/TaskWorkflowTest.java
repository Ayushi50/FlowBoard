package com.flowboard.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowboard.support.AbstractIntegrationTest;
import com.flowboard.support.Api.Result;
import com.flowboard.support.Api.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskWorkflowTest extends AbstractIntegrationTest {

    private Session admin;
    private Session teammate;
    private UUID ws;
    private UUID project;

    @BeforeEach
    void setUp() {
        admin = api.register("Admin", "Flow Co");
        ws = api.firstWorkspace(admin);
        teammate = api.register("Teammate", null);
        String token = api.post(admin, "/api/v1/workspaces/" + ws + "/invitations", Map.of("email", teammate.email(), "role", "MEMBER")).text("token");
        api.post(teammate, "/api/v1/invitations/" + token + "/accept", Map.of());
        String key = "T" + UUID.randomUUID().toString().replaceAll("[^0-9a-f]", "").substring(0, 5).toUpperCase().replaceAll("[^A-Z0-9]", "Z");
        project = api.createProject(admin, ws, key, teammate.userId()).id();
    }

    private List<String> column(String status) {
        Result r = api.get(admin, "/api/v1/projects/" + project + "/tasks?status=" + status + "&size=100");
        List<String> titles = new ArrayList<>();
        r.body().path("items").forEach(t -> titles.add(t.path("title").asText()));
        return titles;
    }

    private Result move(UUID task, String status, UUID after, UUID before, long version) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("status", status);
        body.put("afterTaskId", after);
        body.put("beforeTaskId", before);
        body.put("version", version);
        return api.post(admin, "/api/v1/tasks/" + task + "/move", body);
    }

    @Test
    void tasksGetSequentialProjectScopedIdentifiers() {
        Result a = api.createTask(admin, project, "First", Map.of());
        Result b = api.createTask(admin, project, "Second", Map.of());
        assertThat(a.status()).isEqualTo(201);
        assertThat(a.text("identifier")).endsWith("-1");
        assertThat(b.text("identifier")).endsWith("-2");
        assertThat(a.text("status")).isEqualTo("TODO");
    }

    @Test
    void movingBetweenColumnsPersistsAndIsVisibleInEveryView() {
        UUID t = api.createTask(admin, project, "Ship it", Map.of("priority", "HIGH")).id();
        Result moved = move(t, "IN_PROGRESS", null, null, 0);
        assertThat(moved.status()).isEqualTo(200);
        assertThat(moved.text("status")).isEqualTo("IN_PROGRESS");

        assertThat(column("TODO")).isEmpty();
        assertThat(column("IN_PROGRESS")).containsExactly("Ship it");
        // detail view and cross-project "my tasks" style query see the same state
        assertThat(api.get(admin, "/api/v1/tasks/" + t).body().path("task").path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(api.get(admin, "/api/v1/workspaces/" + ws + "/tasks?status=IN_PROGRESS&projectId=" + project).body().path("totalItems").asInt()).isEqualTo(1);

        Result done = move(t, "DONE", null, null, moved.body().path("version").asLong());
        assertThat(done.body().path("completedAt").isNull()).isFalse();
        Result reopened = move(t, "TODO", null, null, done.body().path("version").asLong());
        assertThat(reopened.body().path("completedAt").isNull()).isTrue();
    }

    @Test
    void reorderingWithinAndAcrossColumnsRespectsNeighbours() {
        UUID a = api.createTask(admin, project, "A", Map.of()).id();
        UUID b = api.createTask(admin, project, "B", Map.of()).id();
        UUID c = api.createTask(admin, project, "C", Map.of()).id();
        assertThat(column("TODO")).containsExactly("A", "B", "C");

        // C to the top (before A)
        assertThat(move(c, "TODO", null, a, 0).status()).isEqualTo(200);
        assertThat(column("TODO")).containsExactly("C", "A", "B");
        // A after B
        assertThat(move(a, "TODO", b, null, 0).status()).isEqualTo(200);
        assertThat(column("TODO")).containsExactly("C", "B", "A");
        // B into another column between two tasks
        UUID x = api.createTask(admin, project, "X", Map.of("status", "DONE")).id();
        UUID y = api.createTask(admin, project, "Y", Map.of("status", "DONE")).id();
        assertThat(move(b, "DONE", x, y, 0).status()).isEqualTo(200);
        assertThat(column("DONE")).containsExactly("X", "B", "Y");
        assertThat(column("TODO")).containsExactly("C", "A");
    }

    @Test
    void repeatedInsertionBetweenTwoTasksTriggersRebalanceWithoutLosingOrder() {
        UUID first = api.createTask(admin, project, "first", Map.of()).id();
        UUID last = api.createTask(admin, project, "last", Map.of()).id();
        List<String> expected = new ArrayList<>(List.of("first", "last"));
        // Always insert directly after "first": halves the gap each time (1024 -> ~1e-6 after ~30 inserts).
        for (int i = 0; i < 40; i++) {
            String title = "n" + i;
            UUID t = api.createTask(admin, project, title, Map.of("status", "BACKLOG")).id();
            assertThat(move(t, "TODO", first, null, 0).status()).isEqualTo(200);
            expected.add(1, title);
        }
        assertThat(column("TODO")).isEqualTo(expected);
        assertThat(last).isNotNull();
    }

    @Test
    void movingNextToATaskFromAnotherColumnIsRejected() {
        UUID a = api.createTask(admin, project, "A", Map.of()).id();
        UUID inDone = api.createTask(admin, project, "D", Map.of("status", "DONE")).id();
        assertThat(move(a, "TODO", inDone, null, 0).status()).isEqualTo(400);
    }

    @Test
    void staleVersionsAreRejectedWithConflictAndNothingIsOverwritten() {
        UUID t = api.createTask(admin, project, "Original", Map.of()).id();
        Map<String, Object> edit = Map.of("title", "Edited by admin", "priority", "LOW", "type", "TASK", "version", 0);
        Result first = api.put(admin, "/api/v1/tasks/" + t, edit);
        assertThat(first.status()).isEqualTo(200);
        assertThat(first.body().path("version").asLong()).isEqualTo(1);

        Result stale = api.put(teammate, "/api/v1/tasks/" + t, Map.of("title", "Edited by teammate", "priority", "URGENT", "type", "BUG", "version", 0));
        assertThat(stale.status()).isEqualTo(409);
        assertThat(stale.text("code")).isEqualTo("CONCURRENT_MODIFICATION");
        assertThat(stale.text("message")).contains("changed by someone else");
        assertThat(move(t, "DONE", null, null, 0).status()).isEqualTo(409);

        Result current = api.get(admin, "/api/v1/tasks/" + t);
        assertThat(current.body().path("task").path("title").asText()).isEqualTo("Edited by admin");
        assertThat(current.body().path("task").path("status").asText()).isEqualTo("TODO");
    }

    @Test
    void filteringSearchSortingAndPaginationRunInTheDatabase() {
        for (int i = 0; i < 25; i++) {
            api.createTask(admin, project, "Bulk task " + i, Map.of("priority", i % 2 == 0 ? "HIGH" : "LOW"));
        }
        api.createTask(admin, project, "Needle in haystack", Map.of("description", "contains the word kubernetes"));
        api.createTask(admin, project, "Overdue thing", Map.of("dueDate", "2020-01-01", "assigneeId", teammate.userId()));

        Result page0 = api.get(admin, "/api/v1/projects/" + project + "/tasks?size=10&page=0&sort=key");
        assertThat(page0.body().path("items")).hasSize(10);
        assertThat(page0.body().path("totalItems").asInt()).isEqualTo(27);
        assertThat(page0.body().path("totalPages").asInt()).isEqualTo(3);
        assertThat(api.get(admin, "/api/v1/projects/" + project + "/tasks?size=10&page=2&sort=key").body().path("items")).hasSize(7);

        assertThat(api.get(admin, "/api/v1/projects/" + project + "/tasks?q=KUBERNETES").body().path("totalItems").asInt()).isEqualTo(1);
        assertThat(api.get(admin, "/api/v1/projects/" + project + "/tasks?q=100%25").body().path("totalItems").asInt()).isZero();
        assertThat(api.get(admin, "/api/v1/projects/" + project + "/tasks?priority=HIGH&size=100").body().path("totalItems").asInt()).isEqualTo(13);
        assertThat(api.get(admin, "/api/v1/projects/" + project + "/tasks?assigneeId=" + teammate.userId()).body().path("totalItems").asInt()).isEqualTo(1);
        Result overdue = api.get(admin, "/api/v1/projects/" + project + "/tasks?overdue=true");
        assertThat(overdue.body().path("totalItems").asInt()).isEqualTo(1);
        assertThat(overdue.body().path("items").get(0).path("overdue").asBoolean()).isTrue();

        JsonNode sorted = api.get(admin, "/api/v1/projects/" + project + "/tasks?sort=priority&dir=asc&size=3").body().path("items");
        assertThat(sorted.get(0).path("priority").asText()).isEqualTo("HIGH");
        JsonNode byTitle = api.get(admin, "/api/v1/projects/" + project + "/tasks?sort=title&dir=desc&size=1").body().path("items");
        assertThat(byTitle.get(0).path("title").asText()).isEqualTo("Overdue thing");
    }

    @Test
    void labelsFilterAndCommentsMentionsActivityAndNotifications() {
        UUID bug = UUID.fromString(api.get(admin, "/api/v1/workspaces/" + ws + "/labels").body().get(0).path("id").asText());
        UUID t = api.createTask(admin, project, "Labelled", Map.of("labelIds", List.of(bug), "assigneeId", teammate.userId())).id();
        api.createTask(admin, project, "Unlabelled", Map.of());
        assertThat(api.get(admin, "/api/v1/projects/" + project + "/tasks?labelId=" + bug).body().path("totalItems").asInt()).isEqualTo(1);

        // assignment notified the teammate
        Result inbox = api.get(teammate, "/api/v1/workspaces/" + ws + "/notifications");
        assertThat(inbox.body().path("items").get(0).path("type").asText()).isEqualTo("ASSIGNED");
        assertThat(api.get(teammate, "/api/v1/workspaces/" + ws + "/notifications/unread-count").body().path("count").asInt()).isEqualTo(1);

        Result comment = api.post(admin, "/api/v1/tasks/" + t + "/comments",
                Map.of("body", "hey @Teammate take a look", "mentionedUserIds", List.of(teammate.userId())));
        assertThat(comment.status()).isEqualTo(201);
        assertThat(api.get(teammate, "/api/v1/workspaces/" + ws + "/notifications/unread-count").body().path("count").asInt()).isEqualTo(2);
        assertThat(api.get(teammate, "/api/v1/workspaces/" + ws + "/notifications").body().path("items").get(0).path("type").asText()).isEqualTo("MENTIONED");
        assertThat(api.get(admin, "/api/v1/tasks/" + t + "/comments").body().path("totalItems").asInt()).isEqualTo(1);
        assertThat(api.get(admin, "/api/v1/projects/" + project + "/tasks?size=100").body().path("items").findValuesAsText("commentCount")).contains("1");

        // mark one read, then all (idempotent)
        UUID nid = UUID.fromString(api.get(teammate, "/api/v1/workspaces/" + ws + "/notifications").body().path("items").get(0).path("id").asText());
        assertThat(api.post(teammate, "/api/v1/notifications/" + nid + "/read", Map.of()).body().path("read").asBoolean()).isTrue();
        assertThat(api.post(teammate, "/api/v1/notifications/" + nid + "/read", Map.of()).status()).isEqualTo(200);
        assertThat(api.post(admin, "/api/v1/notifications/" + nid + "/read", Map.of()).status()).isEqualTo(404);
        api.post(teammate, "/api/v1/workspaces/" + ws + "/notifications/read-all", Map.of());
        assertThat(api.get(teammate, "/api/v1/workspaces/" + ws + "/notifications/unread-count").body().path("count").asInt()).isZero();

        // moving the task writes history
        move(t, "DONE", null, null, 0);
        List<String> actions = api.get(admin, "/api/v1/tasks/" + t + "/activity").body().path("items").findValuesAsText("action");
        assertThat(actions).contains("TASK_CREATED", "COMMENT_ADDED", "STATUS_CHANGED");
    }

    @Test
    void mentioningANonMemberIsRejected() {
        UUID t = api.createTask(admin, project, "t", Map.of()).id();
        Session outsider = api.register("Outsider", "Else");
        assertThat(api.post(admin, "/api/v1/tasks/" + t + "/comments",
                Map.of("body", "hi", "mentionedUserIds", List.of(outsider.userId()))).status()).isEqualTo(400);
    }

    @Test
    void subtasksChecklistAttachmentsAndValidation() {
        UUID t = api.createTask(admin, project, "Parent", Map.of()).id();
        Result sub = api.post(admin, "/api/v1/tasks/" + t + "/subtasks", Map.of("title", "Child"));
        assertThat(sub.status()).isEqualTo(201);
        assertThat(api.post(admin, "/api/v1/tasks/" + sub.id() + "/subtasks", Map.of("title", "Grandchild")).status()).isEqualTo(400);
        Result item = api.post(admin, "/api/v1/tasks/" + t + "/checklist", Map.of("text", "write docs"));
        api.patch(admin, "/api/v1/checklist/" + item.id(), Map.of("done", true));
        api.post(admin, "/api/v1/tasks/" + t + "/attachments", Map.of("fileName", "spec.pdf", "contentType", "application/pdf", "sizeBytes", 2048));

        JsonNode detail = api.get(admin, "/api/v1/tasks/" + t).body();
        assertThat(detail.path("subtasks")).hasSize(1);
        assertThat(detail.path("task").path("subtaskTotal").asInt()).isEqualTo(1);
        assertThat(detail.path("task").path("checklistDone").asInt()).isEqualTo(1);
        assertThat(detail.path("task").path("attachmentCount").asInt()).isEqualTo(1);
        // subtasks do not clutter the board by default
        assertThat(api.get(admin, "/api/v1/projects/" + project + "/tasks").body().path("totalItems").asInt()).isEqualTo(1);

        assertThat(api.createTask(admin, project, "", Map.of()).status()).isEqualTo(400);
        assertThat(api.createTask(admin, project, "dates", Map.of("startDate", "2030-02-01", "dueDate", "2030-01-01")).status()).isEqualTo(400);
        assertThat(api.get(admin, "/api/v1/tasks/not-a-uuid").status()).isEqualTo(400);
    }

    @Test
    void creatingATaskWithTheSameIdempotencyKeyReturnsTheOriginal() {
        String key = UUID.randomUUID().toString();
        Result first = api.postWithHeader(admin, "/api/v1/projects/" + project + "/tasks", Map.of("title", "Once"), "Idempotency-Key", key);
        Result retry = api.postWithHeader(admin, "/api/v1/projects/" + project + "/tasks", Map.of("title", "Once"), "Idempotency-Key", key);
        assertThat(first.status()).isEqualTo(201);
        assertThat(retry.id()).isEqualTo(first.id());
        assertThat(api.get(admin, "/api/v1/projects/" + project + "/tasks").body().path("totalItems").asInt()).isEqualTo(1);
    }

    @Test
    void dashboardCountsAndSearchReflectTaskState() {
        api.createTask(admin, project, "Mine overdue", Map.of("assigneeId", admin.userId(), "dueDate", "2020-01-01"));
        UUID done = api.createTask(admin, project, "Mine done", Map.of("assigneeId", admin.userId())).id();
        move(done, "DONE", null, null, 0);
        JsonNode d = api.get(admin, "/api/v1/workspaces/" + ws + "/dashboard").body();
        assertThat(d.path("assignedToMe").asInt()).isEqualTo(1);
        assertThat(d.path("overdue").asInt()).isEqualTo(1);
        assertThat(d.path("completedThisWeek").asInt()).isEqualTo(1);
        assertThat(api.get(admin, "/api/v1/workspaces/" + ws + "/search?q=overdue").body().path("tasks")).hasSize(1);
        // a fresh workspace has no projects: dashboard says so (drives the empty state)
        Session fresh = api.register("Fresh", "Empty Co");
        assertThat(api.get(fresh, "/api/v1/workspaces/" + api.firstWorkspace(fresh) + "/dashboard").body().path("hasProjects").asBoolean()).isFalse();
    }
}
