package com.flowboard.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Sends invitation emails over SMTP. Does nothing (returns false) when no SMTP host is configured. */
@Component
public class InvitationMailer {

    private static final Logger log = LoggerFactory.getLogger(InvitationMailer.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final String baseUrl;
    private final String from;

    public InvitationMailer(ObjectProvider<JavaMailSender> sender,
                            @Value("${flowboard.app-base-url}") String baseUrl,
                            @Value("${flowboard.mail-from:}") String from) {
        this.sender = sender;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.from = from;
    }

    public String linkFor(String token) {
        return baseUrl + "/invite/" + token;
    }

    /** @return true if the message was handed to the SMTP server. */
    public boolean send(String to, String workspaceName, String inviterName, Role role, String token) {
        JavaMailSender mail = sender.getIfAvailable();
        if (mail == null) {
            log.info("SMTP not configured; invitation email to {} not sent", to);
            return false;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            if (!from.isBlank()) {
                msg.setFrom(from);
            }
            msg.setTo(to);
            msg.setSubject(inviterName + " invited you to " + workspaceName + " on Flowboard");
            msg.setText("""
                    Hi,

                    %s invited you to join "%s" on Flowboard as %s.

                    Accept the invitation (link valid for 7 days):
                    %s

                    Sign up or log in with this email address (%s) to join.
                    """.formatted(inviterName, workspaceName, role.name().toLowerCase(), linkFor(token), to));
            mail.send(msg);
            return true;
        } catch (RuntimeException e) {
            log.warn("Could not send invitation email to {}: {}", to, e.getMessage());
            return false;
        }
    }
}
