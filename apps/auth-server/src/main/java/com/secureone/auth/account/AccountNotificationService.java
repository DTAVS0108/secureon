package com.secureone.auth.account;

import com.secureone.auth.application.ApplicationEffectiveSettingsService;
import com.secureone.auth.application.ApplicationSettingsService;
import com.secureone.auth.application.ApplicationTenantResolver;
import com.secureone.auth.application.UserApplication;
import com.secureone.auth.application.UserApplicationRepository;
import com.secureone.auth.notify.ApplicationEmailContextFactory;
import com.secureone.auth.notify.EmailNotificationService;
import com.secureone.auth.notify.EmailTokenType;
import com.secureone.auth.platform.AuthSettingsService;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import com.secureone.auth.user.UserInviteContext;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountNotificationService {

    private final UserAccountRepository users;
    private final TenantRepository tenants;
    private final EmailTokenService emailTokens;
    private final UserPasswordService passwords;
    private final EmailNotificationService mail;
    private final AuthSettingsService authSettings;
    private final ApplicationSettingsService applicationSettings;
    private final ApplicationEffectiveSettingsService effectiveSettings;
    private final ApplicationTenantResolver applicationTenants;
    private final ApplicationEmailContextFactory emailContext;
    private final UserApplicationRepository userApplications;
    private final String publicBaseUrl;

    public AccountNotificationService(
            UserAccountRepository users,
            TenantRepository tenants,
            EmailTokenService emailTokens,
            UserPasswordService passwords,
            EmailNotificationService mail,
            AuthSettingsService authSettings,
            ApplicationSettingsService applicationSettings,
            ApplicationEffectiveSettingsService effectiveSettings,
            ApplicationTenantResolver applicationTenants,
            ApplicationEmailContextFactory emailContext,
            UserApplicationRepository userApplications,
            @Value("${secureone.public-base-url:http://localhost:9000}") String publicBaseUrl) {
        this.users = users;
        this.tenants = tenants;
        this.emailTokens = emailTokens;
        this.passwords = passwords;
        this.mail = mail;
        this.authSettings = authSettings;
        this.applicationSettings = applicationSettings;
        this.effectiveSettings = effectiveSettings;
        this.applicationTenants = applicationTenants;
        this.emailContext = emailContext;
        this.userApplications = userApplications;
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    /** Always returns the same message to avoid email enumeration. */
    public Map<String, String> requestPasswordResetForApplication(UUID applicationId, String email) {
        if (!effectiveSettings.isFeatureEnabled(applicationId, "self_service_recovery")) {
            throw new IllegalStateException("Self-service recovery is disabled for this application.");
        }
        applicationTenants.findUserByEmail(applicationId, email).ifPresent(user -> sendPasswordResetEmail(applicationId, user, "self-service"));
        return Map.of("message", "Password reset link has been sent.");
    }

    /** @deprecated Use {@link #requestPasswordResetForApplication}. */
    @Deprecated
    public Map<String, String> requestPasswordReset(String tenantSlug, String email) {
        if (!authSettings.isFeatureEnabled("self_service_recovery")) {
            throw new IllegalStateException("Self-service recovery is disabled.");
        }
        resolveUserOptional(tenantSlug, email).ifPresent(user -> {
            UUID applicationId = emailContext.resolveApplicationId(null, user);
            sendPasswordResetEmail(applicationId, user, "self-service");
        });
        return Map.of("message", "Password reset link has been sent.");
    }

    public Map<String, String> requestMagicLinkForApplication(UUID applicationId, String email) {
        if (!effectiveSettings.isAuthMethodEnabled(applicationId, "m_magic")
                || !effectiveSettings.isAuthMethodImplemented(applicationId, "m_magic")) {
            throw new IllegalStateException("Magic link login is disabled for this application.");
        }
        applicationTenants.findUserByEmail(applicationId, email).ifPresent(user -> sendMagicLinkEmail(applicationId, user));
        return Map.of("message", "If an account exists for that email, a sign-in link has been sent.");
    }

    /** @deprecated Use {@link #requestMagicLinkForApplication}. */
    @Deprecated
    public Map<String, String> requestMagicLink(String tenantSlug, String email) {
        if (!authSettings.isAuthMethodEnabled("m_magic") || !authSettings.isAuthMethodImplemented("m_magic")) {
            throw new IllegalStateException("Magic link login is disabled.");
        }
        resolveUserOptional(tenantSlug, email).ifPresent(user -> sendMagicLinkEmail(null, user));
        return Map.of("message", "If an account exists for that email, a sign-in link has been sent.");
    }

    public Map<String, String> completeMagicLink(String rawToken) {
        var token = emailTokens.requireValid(rawToken, EmailTokenType.MAGIC_LINK);
        UserAccount user = users.findById(token.getUserId()).orElseThrow();
        emailTokens.consume(token);
        user.setLastLoginAt(java.time.Instant.now());
        user.setEmailVerified(true);
        if ("PENDING".equalsIgnoreCase(user.getStatus())) {
            user.setStatus("ACTIVE");
        }
        mail.sendAdminSecurityAlert(
                null, user, "Magic link sign-in", "User " + user.getEmail() + " signed in via magic link.");
        return Map.of(
                "message", "Signed in successfully.",
                "loginEmail", user.getEmail());
    }

    public Map<String, String> setPasswordFromInvite(String rawToken, String newPassword) {
        return setPasswordFromInvite(rawToken, newPassword, null);
    }

    public Map<String, String> setPasswordFromInvite(String rawToken, String newPassword, UUID applicationIdParam) {
        var token = emailTokens.requireValid(rawToken, EmailTokenType.SET_PASSWORD);
        UserAccount user = users.findById(token.getUserId()).orElseThrow();
        UUID applicationId = resolveInviteApplicationId(user, applicationIdParam);
        passwords.setPassword(user.getId(), newPassword, applicationId);
        emailTokens.consume(token);
        user.setEmailVerified(true);
        user.setStatus("ACTIVE");
        if (applicationId != null) {
            linkApplicationMembership(applicationId, user.getId());
            UserInviteContext.clearPendingApplicationInvite(user);
        }
        users.save(user);
        mail.sendPasswordChanged(applicationId, user);
        if (applicationId != null) {
            return Map.of("message", "Password set. You can sign in now.", "loginEmail", user.getEmail());
        }
        return Map.of("message", "Password set. You can sign in now.", "loginUsername", resolveLoginUsername(user));
    }

    public Map<String, String> resetPassword(String rawToken, String newPassword) {
        var token = emailTokens.requireValid(rawToken, EmailTokenType.RESET_PASSWORD);
        UserAccount user = users.findById(token.getUserId()).orElseThrow();
        UUID applicationId = emailContext.resolveApplicationId(null, user);
        passwords.setPassword(user.getId(), newPassword, applicationId);
        emailTokens.consume(token);
        user.setStatus("ACTIVE");
        users.save(user);
        mail.sendPasswordChanged(null, user);
        mail.sendAdminSecurityAlert(
                null,
                user,
                "Password reset completed",
                "User " + user.getEmail() + " reset their password via email link.");
        return Map.of("message", "Password updated. You can sign in with your new password.");
    }

    public Map<String, String> resendVerificationForApplication(UUID applicationId, String email) {
        applicationTenants.findUserByEmail(applicationId, email)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> sendVerificationEmail(applicationId, user, "self-service"));
        return Map.of("message", "If an unverified account exists, a verification email has been sent.");
    }

    /** @deprecated Use {@link #resendVerificationForApplication}. */
    @Deprecated
    public Map<String, String> resendVerification(String tenantSlug, String email) {
        resolveUserOptional(tenantSlug, email)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> sendVerificationEmail(null, user, "self-service"));
        return Map.of("message", "If an unverified account exists, a verification email has been sent.");
    }

    @Transactional
    public UserAccount verifyEmail(String rawToken) {
        var token = emailTokens.requireValid(rawToken, EmailTokenType.VERIFY_EMAIL);
        UserAccount user = users.findById(token.getUserId()).orElseThrow();
        user.setEmailVerified(true);
        if ("PENDING".equalsIgnoreCase(user.getStatus())) {
            user.setStatus("ACTIVE");
        }
        users.save(user);
        emailTokens.consume(token);
        mail.sendAdminSecurityAlert(
                null, user, "Email verified", "User " + user.getEmail() + " verified their email address.");
        return user;
    }

    public void sendVerificationEmail(UserAccount user, String initiatedBy) {
        sendVerificationEmail(null, user, initiatedBy);
    }

    public void sendVerificationEmail(UUID applicationId, UserAccount user, String initiatedBy) {
        if (user.isEmailVerified()) {
            return;
        }
        String raw = emailTokens.issue(user, EmailTokenType.VERIFY_EMAIL).rawToken();
        String link = publicBaseUrl + "/api/v1/account/email/verify?token=" + raw;
        mail.sendVerifyEmail(applicationId, user, link);
        if ("admin".equals(initiatedBy)) {
            mail.sendAdminSecurityAlert(
                    applicationId,
                    user,
                    "Verification email sent",
                    "Admin resent verification to " + user.getEmail() + ".");
        }
    }

    public void sendPasswordResetEmail(UserAccount user, String initiatedBy) {
        sendPasswordResetEmail(null, user, initiatedBy);
    }

    public void sendPasswordResetEmail(UUID applicationId, UserAccount user, String initiatedBy) {
        String raw = emailTokens.issue(user, EmailTokenType.RESET_PASSWORD).rawToken();
        String link = passwordResetLink(applicationId, raw);
        mail.sendPasswordReset(applicationId, user, link);
        mail.sendAdminSecurityAlert(
                applicationId,
                user,
                "Password reset requested",
                ("admin".equals(initiatedBy) ? "Admin" : "User")
                        + " triggered password reset for "
                        + user.getEmail()
                        + ".");
    }

    public void sendPasswordExpiringSoonEmail(UUID applicationId, UserAccount user, java.time.Instant expiresAt) {
        mail.sendPasswordExpiringSoon(applicationId, user, expiresAt);
    }

    /** Automated reset when a password has expired (no admin security alert). */
    public void sendExpiredPasswordResetEmail(UUID applicationId, UserAccount user) {
        String raw = emailTokens.issue(user, EmailTokenType.RESET_PASSWORD).rawToken();
        String link = passwordResetLink(applicationId, raw);
        mail.sendPasswordReset(applicationId, user, link);
    }

    public void onUserInvited(UserAccount user) {
        onUserInvited(null, user);
    }

    public void onUserInvited(UUID applicationId, UserAccount user) {
        if (!passwords.hasPassword(user.getId())) {
            sendSetPasswordEmail(applicationId, user);
        } else {
            sendVerificationEmail(applicationId, user, "invite");
        }
        mail.sendAdminNotification(
                applicationId, user, "User invited", "New user: " + user.getEmail() + " — onboarding email sent.");
    }

    public void sendMagicLinkEmail(UserAccount user) {
        sendMagicLinkEmail(null, user);
    }

    public void sendMagicLinkEmail(UUID applicationId, UserAccount user) {
        String raw = emailTokens.issue(user, EmailTokenType.MAGIC_LINK).rawToken();
        String link = magicLinkUrl(applicationId, raw);
        mail.sendMagicLink(applicationId, user, link);
    }

    private String magicLinkUrl(UUID applicationId, String rawToken) {
        String base = publicBaseUrl + "/account/magic-link.html";
        if (applicationId != null) {
            Object urls = applicationSettings.getClientIntegration(applicationId).get("clientUrls");
            if (urls instanceof Map<?, ?> map) {
                Object configured = map.get("magicLink");
                if (configured != null && !configured.toString().isBlank()) {
                    base = configured.toString().trim();
                    if (base.endsWith("/")) {
                        base = base.substring(0, base.length() - 1);
                    }
                }
            }
        }
        String sep = base.contains("?") ? "&" : "?";
        String link = base + sep + "token=" + rawToken;
        if (applicationId != null) {
            link += "&applicationId=" + applicationId;
        }
        return link;
    }

    public void sendSetPasswordEmail(UserAccount user) {
        sendSetPasswordEmail(null, user);
    }

    public void sendSetPasswordEmail(UUID applicationId, UserAccount user) {
        if (applicationId != null) {
            UserInviteContext.markPendingApplicationInvite(user, applicationId);
            users.save(user);
        }
        String raw = emailTokens.issue(user, EmailTokenType.SET_PASSWORD).rawToken();
        String link = publicBaseUrl + "/account/set-password.html?token=" + raw;
        if (applicationId != null) {
            link += "&applicationId=" + applicationId;
        }
        mail.sendSetPasswordInvite(applicationId, user, link);
        if (!user.isEmailVerified()) {
            sendVerificationEmail(applicationId, user, "invite");
        }
    }

    private void linkApplicationMembership(UUID applicationId, UUID userId) {
        if (!userApplications.existsByUserIdAndApplicationId(userId, applicationId)) {
            UserApplication link = new UserApplication();
            link.setUserId(userId);
            link.setApplicationId(applicationId);
            userApplications.save(link);
        }
    }

    private UUID resolveInviteApplicationId(UserAccount user, UUID applicationIdParam) {
        if (applicationIdParam != null) {
            return applicationIdParam;
        }
        return UserInviteContext.pendingApplicationId(user)
                .orElseGet(() -> emailContext.resolveApplicationId(null, user));
    }

    private String resolveLoginUsername(UserAccount user) {
        String slug = tenants.findById(user.getTenantId()).map(t -> t.getSlug()).orElse("tenant");
        return slug + ":" + user.getEmail();
    }

    public void adminMarkEmailVerified(UUID userId) {
        adminMarkEmailVerified(userId, null);
    }

    public void adminMarkEmailVerified(UUID userId, UUID applicationId) {
        UserAccount user = users.findById(userId).orElseThrow();
        user.setEmailVerified(true);
        if ("PENDING".equalsIgnoreCase(user.getStatus())) {
            user.setStatus("ACTIVE");
        }
        users.save(user);
        mail.sendAdminSecurityAlert(
                applicationId, user, "Email marked verified", "Admin verified email for " + user.getEmail() + ".");
    }

    public void onEmailVerified(UserAccount user, UUID applicationId) {
        mail.sendAdminSecurityAlert(
                applicationId, user, "Email verified", "User " + user.getEmail() + " verified their email address.");
    }

    public void sendAdminVerificationAlert(UUID applicationId, UserAccount user, String channel) {
        mail.sendAdminSecurityAlert(
                applicationId,
                user,
                "Verification OTP sent",
                "Admin resent " + channel + " verification OTP to " + user.getEmail() + ".");
    }

    private String passwordResetLink(UUID applicationId, String rawToken) {
        String base = publicBaseUrl + "/account/reset-password.html";
        if (applicationId != null) {
            Object urls = applicationSettings.getClientIntegration(applicationId).get("clientUrls");
            if (urls instanceof Map<?, ?> map) {
                Object configured = map.get("passwordReset");
                if (configured != null && !configured.toString().isBlank()) {
                    base = configured.toString().trim();
                    if (base.endsWith("/")) {
                        base = base.substring(0, base.length() - 1);
                    }
                }
            }
        }
        String sep = base.contains("?") ? "&" : "?";
        String link = base + sep + "token=" + rawToken;
        if (applicationId != null) {
            link += "&applicationId=" + applicationId;
        }
        return link;
    }

    private java.util.Optional<UserAccount> resolveUserOptional(String tenantSlug, String email) {
        if (tenantSlug == null || tenantSlug.isBlank() || email == null || email.isBlank()) {
            return java.util.Optional.empty();
        }
        return tenants.findBySlug(tenantSlug.trim().toLowerCase(Locale.ROOT)).flatMap(tenant -> users.findByTenantIdAndEmail(
                tenant.getId(), email.trim().toLowerCase(Locale.ROOT)));
    }
}
