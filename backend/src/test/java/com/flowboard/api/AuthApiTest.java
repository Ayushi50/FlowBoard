package com.flowboard.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowboard.support.AbstractIntegrationTest;
import com.flowboard.support.Api.Result;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthApiTest extends AbstractIntegrationTest {

    private Map<String, Object> registration(String email, String password) {
        return Map.of("email", email, "password", password, "displayName", "Test User", "workspaceName", "Acme");
    }

    @Test
    void registrationCreatesUserAndWorkspaceWithAdminRole() {
        var s = api.register("Rita", "Rita's Workspace");
        Result workspaces = api.get(s, "/api/v1/workspaces");
        assertThat(workspaces.status()).isEqualTo(200);
        assertThat(workspaces.body()).hasSize(1);
        assertThat(workspaces.body().get(0).path("myRole").asText()).isEqualTo("ADMIN");
        assertThat(workspaces.body().get(0).path("name").asText()).isEqualTo("Rita's Workspace");
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() {
        String email = "dup" + UUID.randomUUID() + "@test.dev";
        assertThat(api.post(null, "/api/v1/auth/register", registration(email, "Password123!")).status()).isEqualTo(201);
        Result second = api.post(null, "/api/v1/auth/register", registration(email.toUpperCase(), "Password123!"));
        assertThat(second.status()).isEqualTo(409);
        assertThat(second.text("code")).isEqualTo("EMAIL_TAKEN");
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() {
        Result r = api.post(null, "/api/v1/auth/register", Map.of("email", "not-an-email", "password", "short", "displayName", ""));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.text("code")).isEqualTo("VALIDATION_FAILED");
        assertThat(r.body().path("fieldErrors").has("email")).isTrue();
        assertThat(r.body().path("fieldErrors").has("password")).isTrue();
        assertThat(r.body().path("fieldErrors").has("displayName")).isTrue();
    }

    @Test
    void loginRejectsWrongPasswordAndAcceptsRightOne() {
        String email = "login" + UUID.randomUUID() + "@test.dev";
        api.post(null, "/api/v1/auth/register", registration(email, "Password123!"));
        assertThat(api.post(null, "/api/v1/auth/login", Map.of("email", email, "password", "wrong-password")).status()).isEqualTo(401);
        Result ok = api.post(null, "/api/v1/auth/login", Map.of("email", email.toUpperCase(), "password", "Password123!"));
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.body().path("token").asText()).isNotBlank();
    }

    @Test
    void protectedRoutesRequireAValidToken() {
        assertThat(api.get(null, "/api/v1/workspaces").status()).isEqualTo(401);
        var bogus = new com.flowboard.support.Api.Session("not.a.jwt", UUID.randomUUID(), "x");
        assertThat(api.get(bogus, "/api/v1/users/me").status()).isEqualTo(401);
    }

    @Test
    void profileCanBeEdited() {
        var s = api.register("Old Name", "WS");
        Result r = api.patch(s, "/api/v1/users/me", Map.of("displayName", "New Name", "jobTitle", "CTO", "avatarColor", "#112233"));
        assertThat(r.status()).isEqualTo(200);
        assertThat(api.get(s, "/api/v1/users/me").text("displayName")).isEqualTo("New Name");
    }

    @Test
    void passwordsAreStoredHashed(@org.springframework.beans.factory.annotation.Autowired org.springframework.jdbc.core.JdbcTemplate jdbc) {
        var s = api.register("Hash Check", "WS");
        String stored = jdbc.queryForObject("select password_hash from users where id = ?", String.class, s.userId());
        assertThat(stored).startsWith("$2").doesNotContain("Password123!");
    }
}
