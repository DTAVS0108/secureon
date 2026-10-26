package com.secureone.auth.account;

import com.secureone.auth.admin.ConflictException;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.notify.EmailNotificationService;
import com.secureone.auth.notify.EmailToken;
import com.secureone.auth.notify.EmailTokenType;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A user changes their own sign-in email. The new address must be confirmed from a link sent to it (one hour);
 * until then nothing changes. The old address is told when the change is asked for and when it is done, so a
 * stolen session cannot quietly move the account. Admins change emails from the console instead.
 */
@Service
public class EmailChangeService {

    static final String PENDING = "pendingEmail";
    /** Accounts without a password must have signed in this recently to change their email. */
    static final java.time.Duration RECENT_SIGN_IN = java.time.Duration.ofMinutes(10);
    static final String PENDING_APP = "pendingEmailApplicationId";
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserAccountRepository users;
    private final UserPasswordService passwords;
    private final EmailTokenService tokens;
    private final EmailNotificationService mail;
    private final AuditService audit;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.secureone.auth.oauth.BackchannelLogoutService> backchannelLogout;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.secureone.auth.tenant.TenantRepository> tenants;
    private final String publicBaseUrl;

    public EmailChangeService(
            UserAccountRepository users,
            UserPasswordService passwords,
            EmailTokenService tokens,
            EmailNotificationService mail,
            AuditService audit,
            @Value("${secureone.public-base-url:http://localhost:9000}") String publicBaseUrl) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.mail = mail;
        this.audit = audit;
        this.publicBaseUrl = publicBaseUrl;
    }

    /**
     * Starts the change: checks the password (when the account has one) and sends the confirmation link. Call inside
     * {@code RlsScope.asPlatform}; the user is the signed-in one (already checked by the caller).
     */
    @Transactional
    public Map<String, Object> request(
            UUID userId, String newEmailRaw, String currentPassword, UUID applicationId, java.time.Instant signedInAt) {
        UserAccount user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found."));
        String newEmail = newEmailRaw == null ? "" : newEmailRaw.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(newEmail).matches() || newEmail.length() > 254) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            throw new IllegalArgumentException("That is already your email address.");
        }
        if (passwords.hasPassword(user.getId())) {
            if (!passwords.isCurrentPassword(user.getId(), currentPassword)) {
                throw new BadCredentialsException("Your current password is not correct.");
            }
        } else if (signedInAt == null || signedInAt.isBefore(java.time.Instant.now().minus(RECENT_SIGN_IN))) {
            // No password to confirm (Google, SAML, passkey accounts): a recent sign-in instead, so a stolen
            // session cannot move the account to another address.
            throw new IllegalArgumentException(
                    "For your security, sign in again and change your email within 10 minutes.");
        }
        if (users.findByTenantIdAndEmail(user.getTenantId(), newEmail).isPresent()) {
            throw new ConflictException("That email address is already used by another account.");
        }
        Map<String, Object> attributes = new HashMap<>(user.getAttributes() != null ? user.getAttributes() : Map.of());
        attributes.put(PENDING, newEmail);
        if (applicationId != null) {
            attributes.put(PENDING_APP, applicationId.toString());
        } else {
            attributes.remove(PENDING_APP);
        }
        user.setAttributes(attributes);
        // Flushed now: issuing the token runs a bulk delete that clears the persistence context.
        users.saveAndFlush(user);

        EmailTokenService.IssuedToken token = tokens.issue(user, EmailTokenType.CHANGE_EMAIL);
        String link = publicBaseUrl + "/account/confirm-email.html?token=" + URLEncoder.encode(token.rawToken(), StandardCharsets.UTF_8)
                + (applicationId != null ? "&applicationId=" + applicationId : "");
        mail.sendEmailChangeVerification(applicationId, user, newEmail, link);
        try {
            mail.sendSecurityNotice(applicationId, user,
                    "Someone asked to change your sign-in email to " + newEmail
                            + ". Nothing changes until the new address is confirmed. If this wasn't you, change your password now.",
                    accountLink(applicationId));
        } catch (RuntimeException e) {
            // The confirmation link (above) is what the change needs; a notice that cannot go out is logged only.
            org.slf4j.LoggerFactory.getLogger(EmailChangeService.class)
                    .warn("Email change notice to {} not sent: {}", user.getEmail(), e.getMessage());
        }
        audit.record(user.getTenantId(), applicationId, user.getEmail(), "user.email_change_requested", "user_account",
                user.getId(), newEmail, true);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "We sent a confirmation link to " + newEmail + ". Your email changes when you open it.");
        out.put("pendingEmail", newEmail);
        return out;
    }

    /**
     * Opens the link: the new address becomes the sign-in email (verified), and the old address is told. Call inside
     * {@code RlsScope.asPlatform} (the link is opened signed out, so no tenant is known yet).
     */
    @Transactional
    public Map<String, Object> confirm(String rawToken) {
        EmailToken token = tokens.requireValid(rawToken, EmailTokenType.CHANGE_EMAIL);
        UserAccount user = users.findById(token.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired link."));
        Map<String, Object> attributes = new HashMap<>(user.getAttributes() != null ? user.getAttributes() : Map.of());
        Object pending = attributes.get(PENDING);
        if (!(pending instanceof String newEmail) || newEmail.isBlank()) {
            throw new IllegalArgumentException("Invalid or expired link.");
        }
        if (users.findByTenantIdAndEmail(user.getTenantId(), newEmail).isPresent()) {
            throw new ConflictException("That email address is already used by another account.");
        }
        UUID applicationId = attributes.get(PENDING_APP) instanceof String s ? UUID.fromString(s) : null;
        String oldEmail = user.getEmail();
        attributes.remove(PENDING);
        attributes.remove(PENDING_APP);
        user.setAttributes(attributes);
        user.setEmail(newEmail);
        user.setEmailVerified(true);
        users.save(user);
        tokens.consume(token);
        // Apps signed in under the old address end those sessions (their tokens name the old address).
        if (backchannelLogout != null && tenants != null) {
            String old = oldEmail;
            tenants.ifAvailable(t -> t.findById(user.getTenantId()).ifPresent(tenant ->
                    backchannelLogout.ifAvailable(b -> b.signedOut(tenant.getSlug(), old, user.getId()))));
        }
        audit.record(user.getTenantId(), applicationId, newEmail, "user.email_changed", "user_account", user.getId(),
                oldEmail + " → " + newEmail, true);
        try {
            mail.sendSecurityNoticeTo(applicationId, user, oldEmail,
                    "Your sign-in email was changed to " + newEmail
                            + ". If this wasn't you, contact the application's support right away.",
                    accountLink(applicationId));
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(EmailChangeService.class)
                    .warn("Email changed notice to {} not sent: {}", oldEmail, e.getMessage());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "Your email is now " + newEmail + ". Sign in again with the new address.");
        out.put("email", newEmail);
        return out;
    }

    private String accountLink(UUID applicationId) {
        return publicBaseUrl + "/account/security.html" + (applicationId != null ? "?applicationId=" + applicationId : "");
    }
}
