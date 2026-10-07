package com.secureone.auth.notify;

import com.secureone.auth.notify.ApplicationEmailContextFactory.ApplicationEmailContext;
import com.secureone.auth.notify.EmailTemplateService.RenderedEmail;
import com.secureone.auth.platform.PlatformNotificationDefaults;
import com.secureone.auth.platform.PlatformSettingsService;
import com.secureone.auth.user.UserAccount;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final PlatformSettingsService settings;
    private final PlatformSmtpSettingsService platformSmtp;
    private final ApplicationEmailContextFactory emailContext;
    private final EmailTemplateService templates;
    private final EmailDeliveryService delivery;
    private final boolean logLinksWhenUnavailable;

    public EmailNotificationService(
            PlatformSettingsService settings,
            PlatformSmtpSettingsService platformSmtp,
            ApplicationEmailContextFactory emailContext,
            EmailTemplateService templates,
            EmailDeliveryService delivery,
            @Value("${secureone.mail.log-links-when-smtp-unavailable:true}") boolean logLinksWhenUnavailable) {
        this.settings = settings;
        this.platformSmtp = platformSmtp;
        this.emailContext = emailContext;
        this.templates = templates;
        this.delivery = delivery;
        this.logLinksWhenUnavailable = logLinksWhenUnavailable;
    }

    public boolean isMailConfigured(UUID applicationId) {
        return applicationId != null && delivery.isConfigured(applicationId);
    }

    public boolean isUserTransactionalEmailEnabled(UUID applicationId) {
        ApplicationEmailContext ctx = emailContext.context(applicationId);
        Object flag = ctx.notifications().get("userEmailEnabled");
        boolean settingOn = flag == null || Boolean.TRUE.equals(flag);
        return settingOn && (ctx.smtpConfigured() || logLinksWhenUnavailable);
    }

    public Map<String, Object> getNotificationSettings() {
        return PlatformNotificationDefaults.mergeNotifications(settings.get("notifications"));
    }

    public Map<String, Object> getEmailSettings() {
        return PlatformNotificationDefaults.mergeEmail(settings.get("email"));
    }

    public Map<String, Object> saveNotificationSettings(Map<String, Object> value) {
        Map<String, Object> sanitized = PlatformNotificationDefaults.sanitizeNotifications(value);
        settings.save("notifications", sanitized);
        return sanitized;
    }

    public Map<String, Object> saveEmailSettings(Map<String, Object> value) {
        Map<String, Object> sanitized = PlatformNotificationDefaults.sanitizeEmail(value);
        settings.save("email", sanitized);
        return sanitized;
    }

    public void sendTestEmail(
            UUID applicationId,
            String to,
            List<String> cc,
            List<String> bcc,
            String templateKey,
            Map<String, String> customData) {
        ApplicationEmailContext ctx = emailContext.context(applicationId);
        requireMail(ctx);
        String key = templateKey != null && !templateKey.isBlank() ? templateKey : "test";
        Map<String, String> vars = baseVariables(ctx);
        if (customData != null) {
            vars.putAll(customData);
        }
        vars.putIfAbsent("userName", "SecureOne Admin");
        vars.putIfAbsent("customMessage", "Test message from SecureOne admin console.");
        RenderedEmail rendered = templates
                .render(applicationId, key, vars)
                .withExtraRecipients(
                        EmailRecipientResolver.resolve(ctx.notifications(), cc),
                        EmailRecipientResolver.resolve(ctx.notifications(), bcc));
        sendOrLog(ctx, to, rendered.cc(), rendered.bcc(), rendered.subject(), rendered.bodyText(), rendered.bodyHtml());
    }

    public void sendAdminNotification(UUID applicationId, String subject, String body) {
        sendAdminNotification(applicationId, null, subject, body);
    }

    public void sendAdminNotification(UUID applicationId, UserAccount user, String subject, String body) {
        if (applicationId == null && user == null) {
            sendPlatformAdminNotification(subject, body);
            return;
        }
        UUID appId = emailContext.resolveApplicationId(applicationId, user);
        if (appId == null) {
            log.warn("Skipping admin notification (no application SMTP context): {}", subject);
            return;
        }
        ApplicationEmailContext ctx = emailContext.context(appId);
        if (!Boolean.TRUE.equals(ctx.notifications().get("emailEnabled"))) {
            return;
        }
        if (!Boolean.TRUE.equals(ctx.notifications().get("auditAlertsEnabled"))) {
            return;
        }
        Map<String, String> vars = baseVariables(ctx);
        vars.put("subject", subject);
        vars.put("body", body);
        sendTemplatedToRecipients(
                ctx, "admin_notification", vars, adminRecipientEmails(ctx.notifications()), List.of(), List.of());
    }

    public void sendAdminSecurityAlert(UUID applicationId, String subject, String body) {
        sendAdminSecurityAlert(applicationId, null, subject, body);
    }

    public void sendAdminSecurityAlert(UUID applicationId, UserAccount user, String subject, String body) {
        if (applicationId == null && user == null) {
            sendPlatformSecurityAlert(subject, body);
            return;
        }
        UUID appId = emailContext.resolveApplicationId(applicationId, user);
        if (appId == null) {
            sendPlatformSecurityAlert(subject, body);
            return;
        }
        ApplicationEmailContext ctx = emailContext.context(appId);
        if (!Boolean.TRUE.equals(ctx.notifications().get("emailEnabled"))) {
            return;
        }
        if (!Boolean.TRUE.equals(ctx.notifications().get("securityAlertsEnabled"))) {
            return;
        }
        Map<String, String> vars = baseVariables(ctx);
        vars.put("subject", subject);
        vars.put("body", body);
        sendTemplatedToRecipients(
                ctx, "admin_security_alert", vars, adminRecipientEmails(ctx.notifications()), List.of(), List.of());
    }

    public void sendAccountSuspended(UUID applicationId, UserAccount user) {
        sendAccountStatusEmail(applicationId, user, "account_suspended");
    }

    public void sendAccountReactivated(UUID applicationId, UserAccount user) {
        sendAccountStatusEmail(applicationId, user, "account_reactivated");
    }

    public void sendVerifyEmail(UUID applicationId, UserAccount user, String verifyLink) {
        UUID appId = emailContext.resolveApplicationId(applicationId, user);
        ensureUserTransactionalEmailEnabled(appId);
        sendUserTemplate(appId, user, "verify_email", verifyLink);
    }

    public void sendVerifyEmailOtp(UUID applicationId, UserAccount user, String code, int ttlSeconds) {
        UUID appId = emailContext.resolveApplicationId(applicationId, user);
        ensureUserTransactionalEmailEnabled(appId);
        ApplicationEmailContext ctx = emailContext.context(appId);
        Map<String, String> vars = userVariables(ctx, user, null);
        vars.put("otpCode", code);
        vars.put("otpTtlMinutes", String.valueOf(Math.max(1, ttlSeconds / 60)));
        sendTemplated(ctx, user.getEmail(), "verify_email_otp", vars, List.of(), List.of());
    }

    public void sendLoginOtp(UUID applicationId, UserAccount user, String code, int ttlSeconds) {
        UUID appId = emailContext.resolveApplicationId(applicationId, user);
        ensureUserTransactionalEmailEnabled(appId);
        ApplicationEmailContext ctx = emailContext.context(appId);
        Map<String, String> vars = userVariables(ctx, user, null);
        vars.put("otpCode", code);
        vars.put("otpTtlMinutes", String.valueOf(Math.max(1, ttlSeconds / 60)));
        sendTemplated(ctx, user.getEmail(), "login_otp_email", vars, List.of(), List.of());
    }

    public void sendPasswordResetOtp(UUID applicationId, UserAccount user, String code, int ttlSeconds) {
        UUID appId = emailContext.resolveApplicationId(applicationId, user);
        ensureUserTransactionalEmailEnabled(appId);
        ApplicationEmailContext ctx = emailContext.context(appId);
        Map<String, String> vars = userVariables(ctx, user, null);
        vars.put("otpCode", code);
        vars.put("otpTtlMinutes", String.valueOf(Math.max(1, ttlSeconds / 60)));
        sendTemplated(ctx, user.getEmail(), "password_reset_otp", vars, List.of(), List.of());
    }

    public void sendPasswordReset(UUID applicationId, UserAccount user, String resetLink) {
        UUID appId = emailContext.resolveApplicationId(applicationId, user);
        ensureUserTransactionalEmailEnabled(appId);
        sendUserTemplate(appId, user, "password_reset", resetLink);
    }

    public void sendPasswordExpiringSoon(UUID applicationId, UserAccount user, java.time.Instant expiresAt) {
        UUID appId = emailContext.resolveApplicationId(applicationId, user);
        ensureUserTransactionalEmailEnabled(appId);
        ApplicationEmailContext ctx = emailContext.context(appId);
        Map<String, String> vars = userVariables(ctx, user, null);
        vars.put("expiryDate", formatExpiryDate(expiresAt));
        sendTemplated(ctx, user.getEmail(), "password_expiring_soon", vars, List.of(), List.of());
    }

    public void sendMagicLink(UUID applicationId, UserAccount user, String magicLink) {
        UUID appId = emailContext.resolveApplicationId(applicationId, user);
        ensureUserTransactionalEmailEnabled(appId);
        sendUserTemplate(appId, user, "magic_link", magicLink);
    }

    public void sendSetPasswordInvite(UUID applicationId, UserAccount user, String setPasswordLink) {
        UUID appId = emailContext.resolveApplicationId(applicationId, user);
        if (appId == null) {
            // Accounts outside any application (platform operators) use the platform mail settings.
            sendPlatformUserTemplate(user, "set_password_invite", setPasswordLink);
            return;
        }
        ensureUserTransactionalEmailEnabled(appId);
        sendUserTemplate(appId, user, "set_password_invite", setPasswordLink);
    }

    public void sendPasswordChanged(UUID applicationId, UserAccount user) {
        UUID appId = emailContext.resolveApplicationId(applicationId, user);
        ensureUserTransactionalEmailEnabled(appId);
        ApplicationEmailContext ctx = emailContext.context(appId);
        Map<String, String> vars = userVariables(ctx, user, null);
        sendTemplated(ctx, user.getEmail(), "password_changed", vars, List.of(), List.of());
    }

    private void sendUserTemplate(UUID applicationId, UserAccount user, String templateKey, String actionLink) {
        ApplicationEmailContext ctx = emailContext.context(applicationId);
        Map<String, String> vars = userVariables(ctx, user, actionLink);
        sendTemplated(ctx, user.getEmail(), templateKey, vars, List.of(), List.of());
    }

    private void sendTemplated(
            ApplicationEmailContext ctx,
            String to,
            String templateKey,
            Map<String, String> vars,
            List<String> cc,
            List<String> bcc) {
        RenderedEmail rendered = templates
                .render(ctx.applicationId(), templateKey, vars)
                .withExtraRecipients(cc, bcc);
        sendOrLog(ctx, to, rendered.cc(), rendered.bcc(), rendered.subject(), rendered.bodyText(), rendered.bodyHtml());
    }

    private void sendAccountStatusEmail(UUID applicationId, UserAccount user, String templateKey) {
        UUID appId = emailContext.resolveApplicationId(applicationId, user);
        if (appId == null) {
            log.warn("Cannot send {} to {} — no application SMTP context", templateKey, user.getEmail());
            return;
        }
        ApplicationEmailContext ctx = emailContext.context(appId);
        if (!ctx.smtpConfigured() && !logLinksWhenUnavailable) {
            log.warn("Cannot send {} to {} — SMTP not configured for application {}", templateKey, user.getEmail(), appId);
            return;
        }
        Map<String, String> vars = userVariables(ctx, user, null);
        sendTemplated(ctx, user.getEmail(), templateKey, vars, List.of(), List.of());
    }

    private void sendTemplatedToRecipients(
            ApplicationEmailContext ctx,
            String templateKey,
            Map<String, String> vars,
            List<String> recipients,
            List<String> cc,
            List<String> bcc) {
        if (recipients.isEmpty()) {
            log.warn(
                    "No admin recipients configured for application {} — enable Notifications and add admin recipients",
                    ctx.applicationId());
            return;
        }
        if (!ctx.smtpConfigured() && !logLinksWhenUnavailable) {
            return;
        }
        RenderedEmail rendered = templates
                .render(ctx.applicationId(), templateKey, vars)
                .withExtraRecipients(cc, bcc);
        for (String recipient : recipients) {
            sendOrLog(
                    ctx,
                    recipient,
                    rendered.cc(),
                    rendered.bcc(),
                    rendered.subject(),
                    rendered.bodyText(),
                    rendered.bodyHtml());
        }
    }

    private void sendOrLog(
            ApplicationEmailContext ctx,
            String to,
            List<String> cc,
            List<String> bcc,
            String subject,
            String text,
            String html) {
        if (!ctx.smtpConfigured()) {
            if (logLinksWhenUnavailable) {
                log.warn(
                        """
                        SMTP unavailable — email logged to console instead of sent.
                        Application: {}
                        To: {}
                        Cc: {}
                        Bcc: {}
                        Subject: {}
                        Body:
                        {}
                        """,
                        ctx.applicationId(),
                        to,
                        cc,
                        bcc,
                        subject,
                        text);
            }
            return;
        }
        if (!cc.isEmpty() || !bcc.isEmpty()) {
            log.info("Email recipients application={} to={} cc={} bcc={}", ctx.applicationId(), to, cc, bcc);
        }
        try {
            delivery.send(ctx.applicationId(), ctx.email(), to, cc, bcc, subject, text, html);
        } catch (Exception ex) {
            if (logLinksWhenUnavailable) {
                log.warn(
                        "SMTP send failed ({}). Email logged to console instead.\nTo: {}\nSubject: {}\nBody:\n{}",
                        ex.getMessage(),
                        to,
                        subject,
                        text);
                return;
            }
            throw ex;
        }
    }

    private List<String> adminRecipientEmails(Map<String, Object> notifications) {
        Set<String> emails =
                new LinkedHashSet<>(SmtpSettingsService.parseEmailList(notifications.get("adminRecipients")));
        expandRecipientGroups(notifications, emails);
        return new ArrayList<>(emails);
    }

    @SuppressWarnings("unchecked")
    private void expandRecipientGroups(Map<String, Object> notifications, Set<String> emails) {
        Object groups = notifications.get("recipientGroups");
        if (!(groups instanceof Map<?, ?> groupMap)) {
            return;
        }
        for (Object members : groupMap.values()) {
            emails.addAll(SmtpSettingsService.parseEmailList(members));
        }
    }

    private Map<String, String> userVariables(
            ApplicationEmailContext ctx, UserAccount user, String actionLink) {
        Map<String, String> vars = baseVariables(ctx);
        vars.put("userName", displayName(user));
        vars.put("userEmail", user.getEmail());
        if (actionLink != null) {
            vars.put("actionLink", actionLink);
        }
        return vars;
    }

    private Map<String, String> baseVariables(ApplicationEmailContext ctx) {
        Map<String, String> vars = new HashMap<>();
        vars.put("appName", string(ctx.email(), "fromName", "SecureOne"));
        vars.put("tenantName", "SecureOne");
        return vars;
    }

    private void ensureUserTransactionalEmailEnabled(UUID applicationId) {
        if (applicationId == null) {
            throw new IllegalStateException(
                    "No application context for email. Configure SMTP under Application settings → Notifications.");
        }
        ApplicationEmailContext ctx = emailContext.context(applicationId);
        if (Boolean.FALSE.equals(ctx.notifications().get("userEmailEnabled"))) {
            throw new IllegalStateException(
                    "User transactional email is disabled in Application settings → Notifications.");
        }
        if (!ctx.smtpConfigured() && !logLinksWhenUnavailable) {
            requireMail(ctx);
        }
    }

    private void sendPlatformAdminNotification(String subject, String body) {
        Map<String, Object> notifications = getNotificationSettings();
        if (!Boolean.TRUE.equals(notifications.get("emailEnabled"))) {
            return;
        }
        if (!Boolean.TRUE.equals(notifications.get("auditAlertsEnabled"))) {
            return;
        }
        sendPlatformTemplated("admin_notification", notifications, subject, body);
    }

    private void sendPlatformSecurityAlert(String subject, String body) {
        Map<String, Object> notifications = getNotificationSettings();
        if (!Boolean.TRUE.equals(notifications.get("emailEnabled"))) {
            return;
        }
        if (!Boolean.TRUE.equals(notifications.get("securityAlertsEnabled"))) {
            return;
        }
        sendPlatformTemplated("admin_security_alert", notifications, subject, body);
    }

    private void sendPlatformUserTemplate(UserAccount user, String templateKey, String actionLink) {
        Map<String, Object> email = getEmailSettings();
        Map<String, String> vars = new HashMap<>();
        vars.put("appName", string(email, "fromName", "SecureOne"));
        vars.put("tenantName", "SecureOne");
        vars.put("userName", displayName(user));
        vars.put("userEmail", user.getEmail());
        vars.put("actionLink", actionLink);
        RenderedEmail rendered = templates.renderPlatform(templateKey, vars);
        sendPlatformOrLog(email, user.getEmail(), List.of(), List.of(), rendered.subject(), rendered.bodyText(), rendered.bodyHtml());
    }

    private void sendPlatformTemplated(
            String templateKey, Map<String, Object> notifications, String subject, String body) {
        List<String> recipients = adminRecipientEmails(notifications);
        if (recipients.isEmpty()) {
            log.warn("No platform admin recipients configured — add recipients under Platform settings → Notifications");
            return;
        }
        if (!platformSmtp.isConfigured() && !logLinksWhenUnavailable) {
            log.warn("Platform SMTP not configured — skipping platform alert: {}", subject);
            return;
        }
        Map<String, Object> email = getEmailSettings();
        Map<String, String> vars = new HashMap<>();
        vars.put("appName", string(email, "fromName", "SecureOne"));
        vars.put("tenantName", "SecureOne");
        vars.put("subject", subject);
        vars.put("body", body);
        RenderedEmail rendered = templates.renderPlatform(templateKey, vars);
        for (String recipient : recipients) {
            sendPlatformOrLog(email, recipient, rendered.cc(), rendered.bcc(), rendered.subject(), rendered.bodyText(), rendered.bodyHtml());
        }
    }

    private void sendPlatformOrLog(
            Map<String, Object> email,
            String to,
            List<String> cc,
            List<String> bcc,
            String subject,
            String text,
            String html) {
        if (!platformSmtp.isConfigured()) {
            if (logLinksWhenUnavailable) {
                log.warn(
                        """
                        Platform SMTP unavailable — email logged to console instead of sent.
                        To: {}
                        Subject: {}
                        Body:
                        {}
                        """,
                        to,
                        subject,
                        text);
            }
            return;
        }
        try {
            delivery.sendPlatform(email, to, cc, bcc, subject, text, html);
        } catch (Exception ex) {
            if (logLinksWhenUnavailable) {
                log.warn(
                        "Platform SMTP send failed ({}). Email logged to console instead.\nTo: {}\nSubject: {}\nBody:\n{}",
                        ex.getMessage(),
                        to,
                        subject,
                        text);
                return;
            }
            throw ex;
        }
    }

    private void requireMail(ApplicationEmailContext ctx) {
        if (!ctx.smtpConfigured()) {
            throw new IllegalStateException(
                    "SMTP is not configured. Open Application settings → Notifications, save SMTP host/port/credentials, "
                            + "or set SECUREONE_MAIL_LOG_WHEN_UNAVAILABLE=true to print links in the server log.");
        }
    }

    private static String formatExpiryDate(Instant expiresAt) {
        if (expiresAt == null) {
            return "";
        }
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(Locale.ENGLISH)
                .withZone(ZoneOffset.UTC)
                .format(expiresAt);
    }

    private static String displayName(UserAccount user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName().trim();
        }
        return user.getEmail();
    }

    private static String string(Map<String, Object> map, String key, String defaultValue) {
        Object v = map.get(key);
        return v != null ? v.toString() : defaultValue;
    }
}
