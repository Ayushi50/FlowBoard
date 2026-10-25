package com.flowboard.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowboard.support.AbstractIntegrationTest;
import com.flowboard.support.Api.Result;
import com.flowboard.support.Api.Session;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Workspace isolation and role-based access, asserted against the HTTP API (not the UI). */
class AuthorizationTest extends AbstractIntegrationTest {

    private String key() {
        return "K" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase().replaceAll("[^A-Z0-9]", "A");
    }

    @Test
    void anotherWorkspacesResourcesAreInvisibleByGuessingIds() {
        Session alice = api.register("Alice", "Alice Co");
        UUID aliceWs = api.firstWorkspace(alice);
        UUID project = api.createProject(alice, aliceWs, key()).id();
        UUID task = api.createTask(alice, project, "Secret roadmap", Map.of()).id();

        Session mallory = api.register("Mallory", "Mallory Co");

        assertThat(api.get(mallory, "/api/v1/workspaces/" + aliceWs).status()).isEqualTo(404);
        assertThat(api.get(mallory, "/api/v1/workspaces/" + aliceWs + "/projects").status()).isEqualTo(404);
        assertThat(api.get(mallory, "/api/v1/workspaces/" + aliceWs + "/members").status()).isEqualTo(404);
        assertThat(api.get(mallory, "/api/v1/projects/" + project).status()).isEqualTo(404);
        assertThat(api.get(mallory, "/api/v1/projects/" + project + "/tasks").status()).isEqualTo(404);
        assertThat(api.get(mallory, "/api/v1/tasks/" + task).status()).isEqualTo(404);
        assertThat(api.createTask(mallory, project, "injected", Map.of()).status()).isEqualTo(404);
        assertThat(api.delete(mallory, "/api/v1/tasks/" + task).status()).isEqualTo(404);
        assertThat(api.post(mallory, "/api/v1/tasks/" + task + "/comments", Map.of("body", "hi")).status()).isEqualTo(404);
        assertThat(api.get(mallory, "/api/v1/workspaces/" + aliceWs + "/search?q=Secret").status()).isEqualTo(404);
        assertThat(api.get(mallory, "/api/v1/workspaces/" + aliceWs + "/notifications").status()).isEqualTo(404);

        // The owner is unaffected.
        assertThat(api.get(alice, "/api/v1/tasks/" + task).status()).isEqualTo(200);
    }

    @Test
    void memberCannotCreateProjectsOrChangeRoles() {
        Session admin = api.register("Admin", "Team");
        UUID ws = api.firstWorkspace(admin);
        Session member = joinAs(admin, ws, "MEMBER");

        assertThat(api.createProject(member, ws, key()).status()).isEqualTo(403);
        assertThat(api.patch(member, "/api/v1/workspaces/" + ws + "/members/" + member.userId(), Map.of("role", "ADMIN")).status()).isEqualTo(403);
        assertThat(api.post(member, "/api/v1/workspaces/" + ws + "/invitations", Map.of("email", "x@y.dev", "role", "MEMBER")).status()).isEqualTo(403);
        assertThat(api.patch(member, "/api/v1/workspaces/" + ws, Map.of("name", "Renamed")).status()).isEqualTo(403);
    }

    @Test
    void managerCanCreateProjectsButMemberOutsideProjectCannotSeeIt() {
        Session admin = api.register("Admin", "Team");
        UUID ws = api.firstWorkspace(admin);
        Session manager = joinAs(admin, ws, "MANAGER");
        Session outsider = joinAs(admin, ws, "MEMBER");
        Session insider = joinAs(admin, ws, "MEMBER");

        Result created = api.createProject(manager, ws, key(), insider.userId());
        assertThat(created.status()).isEqualTo(201);
        UUID project = created.id();

        assertThat(api.get(insider, "/api/v1/projects/" + project).status()).isEqualTo(200);
        assertThat(api.get(outsider, "/api/v1/projects/" + project).status()).isEqualTo(404);
        assertThat(api.get(outsider, "/api/v1/workspaces/" + ws + "/projects").body()).isEmpty();
        assertThat(api.get(admin, "/api/v1/projects/" + project).status()).isEqualTo(200);
        // members may manage tasks but not the project itself
        assertThat(api.createTask(insider, project, "member task", Map.of()).status()).isEqualTo(201);
        assertThat(api.put(insider, "/api/v1/projects/" + project, Map.of("name", "Hijack", "status", "ACTIVE")).status()).isEqualTo(403);
        assertThat(api.delete(insider, "/api/v1/projects/" + project).status()).isEqualTo(403);
        assertThat(api.post(insider, "/api/v1/projects/" + project + "/members", Map.of("userId", outsider.userId())).status()).isEqualTo(403);
        // managers of the project can
        assertThat(api.post(manager, "/api/v1/projects/" + project + "/members", Map.of("userId", outsider.userId())).status()).isEqualTo(200);
        assertThat(api.get(outsider, "/api/v1/projects/" + project).status()).isEqualTo(200);
    }

    @Test
    void membersCannotDeleteTasksTheyDidNotReport() {
        Session admin = api.register("Admin", "Team");
        UUID ws = api.firstWorkspace(admin);
        Session member = joinAs(admin, ws, "MEMBER");
        UUID project = api.createProject(admin, ws, key(), member.userId()).id();
        UUID adminsTask = api.createTask(admin, project, "Admin's task", Map.of()).id();
        UUID membersTask = api.createTask(member, project, "Member's task", Map.of()).id();

        assertThat(api.delete(member, "/api/v1/tasks/" + adminsTask).status()).isEqualTo(403);
        assertThat(api.delete(member, "/api/v1/tasks/" + membersTask).status()).isEqualTo(204);
        assertThat(api.delete(admin, "/api/v1/tasks/" + adminsTask).status()).isEqualTo(204);
    }

    @Test
    void assigneeMustBeProjectMemberAndLabelsMustBelongToWorkspace() {
        Session admin = api.register("Admin", "Team");
        UUID ws = api.firstWorkspace(admin);
        Session other = joinAs(admin, ws, "MEMBER");
        UUID project = api.createProject(admin, ws, key()).id();
        assertThat(api.createTask(admin, project, "t", Map.of("assigneeId", other.userId())).status()).isEqualTo(400);

        Session foreign = api.register("Foreign", "Other Co");
        UUID foreignLabel = UUID.fromString(api.get(foreign, "/api/v1/workspaces/" + api.firstWorkspace(foreign) + "/labels")
                .body().get(0).path("id").asText());
        assertThat(api.createTask(admin, project, "t", Map.of("labelIds", java.util.List.of(foreignLabel))).status()).isEqualTo(400);
    }

    @Test
    void invitationsOnlyWorkForTheInvitedEmailAndCannotRemoveLastAdmin() {
        Session admin = api.register("Admin", "Team");
        UUID ws = api.firstWorkspace(admin);
        Session invitee = api.register("Invitee", null);
        Result inv = api.post(admin, "/api/v1/workspaces/" + ws + "/invitations", Map.of("email", invitee.email(), "role", "MANAGER"));
        assertThat(inv.status()).isEqualTo(201);
        String token = inv.text("token");

        Session stranger = api.register("Stranger", null);
        assertThat(api.post(stranger, "/api/v1/invitations/" + token + "/accept", Map.of()).status()).isEqualTo(403);
        assertThat(api.post(invitee, "/api/v1/invitations/" + token + "/accept", Map.of()).status()).isEqualTo(200);
        assertThat(api.get(invitee, "/api/v1/workspaces/" + ws).text("myRole")).isEqualTo("MANAGER");
        assertThat(api.post(invitee, "/api/v1/invitations/" + token + "/accept", Map.of()).status()).isEqualTo(409);

        assertThat(api.patch(admin, "/api/v1/workspaces/" + ws + "/members/" + admin.userId(), Map.of("role", "MEMBER")).status()).isEqualTo(409);
        assertThat(api.delete(admin, "/api/v1/workspaces/" + ws + "/members/" + admin.userId()).status()).isEqualTo(409);
    }

    @Test
    void archivedProjectsAreReadOnly() {
        Session admin = api.register("Admin", "Team");
        UUID project = api.createProject(admin, api.firstWorkspace(admin), key()).id();
        assertThat(api.post(admin, "/api/v1/projects/" + project + "/archive", Map.of()).status()).isEqualTo(200);
        Result r = api.createTask(admin, project, "late", Map.of());
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.text("code")).isEqualTo("PROJECT_ARCHIVED");
    }

    /** Invites a fresh user into the workspace with the given role and returns their session. */
    private Session joinAs(Session admin, UUID ws, String role) {
        Session s = api.register(role.toLowerCase(), null);
        String token = api.post(admin, "/api/v1/workspaces/" + ws + "/invitations", Map.of("email", s.email(), "role", role)).text("token");
        assertThat(api.post(s, "/api/v1/invitations/" + token + "/accept", Map.of()).status()).isEqualTo(200);
        return s;
    }
}
