package com.flowboard.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowboard.workspace.InvitationMailer;
import com.flowboard.workspace.Role;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class InvitationMailerTest {

    private static class FakeSmtp extends JavaMailSenderImpl {
        final List<MimeMessage> sent = new ArrayList<>();
        boolean fail;

        @Override
        public void send(MimeMessage... messages) {
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
    void sendsLinkToTheInvitee() throws Exception {
        FakeSmtp smtp = new FakeSmtp();

        assertThat(mailer(smtp, "no-reply@example.com").send("a@b.dev", "Acme", "Ada", Role.MANAGER, "tok123")).isTrue();

        MimeMessage msg = smtp.sent.get(0);
        assertThat(msg.getAllRecipients()[0].toString()).isEqualTo("a@b.dev");
        assertThat(msg.getFrom()[0].toString()).isEqualTo("no-reply@example.com");
        msg.saveChanges();
        java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream();
        msg.writeTo(raw);
        String body = raw.toString("UTF-8");
        assertThat(body).contains("text/plain").contains("text/html").contains("https://app.example.com/invite/tok123").contains("Acme");
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

    @Test
    void escapesHtmlInNames() throws Exception {
        FakeSmtp smtp = new FakeSmtp();
        mailer(smtp, "").send("a@b.dev", "<b>Acme</b>", "Ada", Role.MEMBER, "t");

        java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream();
        smtp.sent.get(0).writeTo(raw);
        assertThat(raw.toString("UTF-8")).contains("&lt;b&gt;Acme&lt;/b&gt;");
    }
}
