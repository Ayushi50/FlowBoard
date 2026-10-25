package com.flowboard.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowboard.workspace.InvitationMailer;
import com.flowboard.workspace.Role;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class InvitationMailerTest {

    private static class FakeSmtp extends JavaMailSenderImpl {
        final List<SimpleMailMessage> sent = new ArrayList<>();
        boolean fail;

        @Override
        public void send(SimpleMailMessage... messages) {
            if (fail) {
                throw new MailSendException("down");
            }
            sent.addAll(List.of(messages));
        }
    }

    private static InvitationMailer mailer(FakeSmtp smtp, String from) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        if (smtp != null) {
            beans.addBean("smtp", smtp);
        }
        return new InvitationMailer(beans.getBeanProvider(JavaMailSender.class), "https://app.example.com/", from);
    }

    @Test
    void sendsLinkToTheInvitee() {
        FakeSmtp smtp = new FakeSmtp();

        assertThat(mailer(smtp, "no-reply@example.com").send("a@b.dev", "Acme", "Ada", Role.MANAGER, "tok123")).isTrue();

        SimpleMailMessage msg = smtp.sent.get(0);
        assertThat(msg.getTo()).containsExactly("a@b.dev");
        assertThat(msg.getFrom()).isEqualTo("no-reply@example.com");
        assertThat(msg.getText()).contains("https://app.example.com/invite/tok123").contains("Acme");
    }

    @Test
    void returnsFalseWhenSmtpIsNotConfigured() {
        assertThat(mailer(null, "").send("a@b.dev", "W", "U", Role.MEMBER, "t")).isFalse();
    }

    @Test
    void returnsFalseWhenSmtpFails() {
        FakeSmtp smtp = new FakeSmtp();
        smtp.fail = true;
        assertThat(mailer(smtp, "").send("a@b.dev", "W", "U", Role.MEMBER, "t")).isFalse();
    }
}
