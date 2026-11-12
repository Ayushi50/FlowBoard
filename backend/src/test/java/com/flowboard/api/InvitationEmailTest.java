package com.flowboard.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowboard.support.AbstractIntegrationTest;
import com.flowboard.support.Api.Result;
import com.flowboard.support.Api.Session;
import com.flowboard.workspace.InvitationMailer;
import com.flowboard.workspace.Role;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.awaitility.Awaitility.await;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Import(InvitationEmailTest.FakeMailConfig.class)
class InvitationEmailTest extends AbstractIntegrationTest {

    record Sent(String to, String workspace, String inviter, Role role, String token) {
    }

    static class RecordingMailer extends InvitationMailer {
        final List<Sent> sent = new CopyOnWriteArrayList<>();
        volatile boolean succeed = true;

        RecordingMailer() {
            super(new org.springframework.beans.factory.support.StaticListableBeanFactory()
                    .getBeanProvider(org.springframework.mail.javamail.JavaMailSender.class), "http://localhost:5173", "");
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public boolean send(String to, String workspaceName, String inviterName, Role role, String token) {
            sent.add(new Sent(to, workspaceName, inviterName, role, token));
            return succeed;
        }
    }

    @TestConfiguration
    static class FakeMailConfig {
        @Bean
        @Primary
        RecordingMailer recordingMailer() {
            return new RecordingMailer();
        }
    }

    @Autowired
    RecordingMailer mailer;

    @BeforeEach
    void reset() {
        mailer.sent.clear();
        mailer.succeed = true;
    }

    private String workspaceOf(Session admin) {
        return api.get(admin, "/api/v1/workspaces").body().get(0).path("id").asText();
    }

    private String statusOf(Session admin, String ws, String email) {
        for (var i : api.get(admin, "/api/v1/workspaces/" + ws + "/invitations").body()) {
            if (email.equals(i.path("email").asText())) {
                return i.path("emailStatus").asText();
            }
        }
        return "";
    }

    @Test
    void resendEmailsAgainAndRecoversFromFailure() {
        Session admin = api.register("Admin", "Acme");
        String ws = workspaceOf(admin);
        mailer.succeed = false;
        Result inv = api.post(admin, "/api/v1/workspaces/" + ws + "/invitations", Map.of("email", "again@person.dev", "role", "MEMBER"));
        await().untilAsserted(() -> assertThat(statusOf(admin, ws, "again@person.dev")).isEqualTo("FAILED"));

        mailer.succeed = true;
        Result re = api.post(admin, "/api/v1/workspaces/" + ws + "/invitations/" + inv.text("id") + "/resend", Map.of());

        assertThat(re.status()).isEqualTo(200);
        await().untilAsserted(() -> assertThat(statusOf(admin, ws, "again@person.dev")).isEqualTo("SENT"));
        assertThat(mailer.sent).hasSize(2);
    }

    @Test
    void cannotResendARevokedInvitation() {
        Session admin = api.register("Admin", "Acme");
        String ws = workspaceOf(admin);
        Result inv = api.post(admin, "/api/v1/workspaces/" + ws + "/invitations", Map.of("email", "gone@person.dev", "role", "MEMBER"));
        api.delete(admin, "/api/v1/workspaces/" + ws + "/invitations/" + inv.text("id"));

        Result re = api.post(admin, "/api/v1/workspaces/" + ws + "/invitations/" + inv.text("id") + "/resend", Map.of());

        assertThat(re.status()).isEqualTo(409);
    }

    @Test
    void inviteSendsEmailAndReportsIt() {
        Session admin = api.register("Admin", "Acme");
        String ws = workspaceOf(admin);

        Result inv = api.post(admin, "/api/v1/workspaces/" + ws + "/invitations", Map.of("email", "new@person.dev", "role", "MEMBER"));

        assertThat(inv.status()).isEqualTo(201);
        assertThat(inv.text("emailStatus")).isEqualTo("QUEUED");
        await().untilAsserted(() -> assertThat(statusOf(admin, ws, "new@person.dev")).isEqualTo("SENT"));
        assertThat(mailer.sent).containsExactly(new Sent("new@person.dev", "Acme", "Admin", Role.MEMBER, inv.text("token")));
    }

    @Test
    void invitationIsKeptEvenWhenEmailFails() {
        Session admin = api.register("Admin", "Acme");
        String ws = workspaceOf(admin);
        mailer.succeed = false;

        Result inv = api.post(admin, "/api/v1/workspaces/" + ws + "/invitations", Map.of("email", "fail@person.dev", "role", "MEMBER"));

        assertThat(inv.status()).isEqualTo(201);
        await().untilAsserted(() -> assertThat(statusOf(admin, ws, "fail@person.dev")).isEqualTo("FAILED"));
    }

    @Test
    void nonAdminCannotTriggerAnEmail() {
        Session admin = api.register("Admin", "Acme");
        String ws = workspaceOf(admin);
        Session other = api.register("Other", null);

        Result res = api.post(other, "/api/v1/workspaces/" + ws + "/invitations", Map.of("email", "x@y.dev", "role", "MEMBER"));

        assertThat(res.status()).isEqualTo(404);
        assertThat(mailer.sent).isEmpty();
    }
}
