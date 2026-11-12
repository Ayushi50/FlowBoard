package com.flowboard.workspace;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/** Sends invitation emails (HTML with a plain-text alternative) over SMTP. Does nothing when no SMTP host is configured. */
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

    public boolean isConfigured() {
        return sender.getIfAvailable() != null;
    }

    /** @return true if the message was handed to the SMTP server. */
    public boolean send(String to, String workspaceName, String inviterName, Role role, String token) {
        JavaMailSender mail = sender.getIfAvailable();
        if (mail == null) {
            log.info("SMTP not configured; invitation email to {} not sent", to);
            return false;
        }
        try {
            String link = linkFor(token);
            String roleName = role.name().toLowerCase();
            MimeMessage msg = mail.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            if (!from.isBlank()) {
                helper.setFrom(from);
            }
            helper.setTo(to);
            helper.setSubject(inviterName + " invited you to " + workspaceName + " on Flowboard");
            helper.setText(plainText(inviterName, workspaceName, roleName, link, to),
                    html(inviterName, workspaceName, roleName, link, to));
            mail.send(msg);
            return true;
        } catch (Exception e) {
            log.warn("Could not send invitation email to {}: {}", to, e.getMessage());
            return false;
        }
    }

    private static String plainText(String inviter, String workspace, String role, String link, String to) {
        return """
                Hi,

                %s invited you to join "%s" on Flowboard as %s.

                Accept the invitation (link valid for 7 days):
                %s

                Sign up or log in with this email address (%s) to join.
                """.formatted(inviter, workspace, role, link, to);
    }

    private static String html(String inviter, String workspace, String role, String link, String to) {
        String i = HtmlUtils.htmlEscape(inviter);
        String w = HtmlUtils.htmlEscape(workspace);
        String r = HtmlUtils.htmlEscape(role);
        String l = HtmlUtils.htmlEscape(link);
        String t = HtmlUtils.htmlEscape(to);
        return """
                <div style="background:#f4f5f7;padding:32px 16px;font-family:-apple-system,Segoe UI,Helvetica,Arial,sans-serif">
                  <div style="max-width:480px;margin:0 auto;background:#ffffff;border-radius:12px;padding:32px;color:#1f2430">
                    <div style="font-size:18px;font-weight:700;margin-bottom:20px">Flowboard</div>
                    <p style="font-size:15px;line-height:1.5;margin:0 0 8px"><strong>%s</strong> invited you to join <strong>%s</strong> as %s.</p>
                    <p style="margin:24px 0">
                      <a href="%s" style="background:#4f46e5;color:#ffffff;text-decoration:none;padding:12px 22px;border-radius:8px;font-size:14px;font-weight:600;display:inline-block">Accept invitation</a>
                    </p>
                    <p style="font-size:13px;color:#5b6270;line-height:1.5;margin:0">Sign up or log in with <strong>%s</strong> to join. This link is valid for 7 days.</p>
                    <p style="font-size:12px;color:#8a909c;line-height:1.5;margin:20px 0 0">Button not working? Paste this link into your browser:<br><a href="%s" style="color:#4f46e5;word-break:break-all">%s</a></p>
                  </div>
                </div>
                """.formatted(i, w, r, l, t, l, l);
    }
}
