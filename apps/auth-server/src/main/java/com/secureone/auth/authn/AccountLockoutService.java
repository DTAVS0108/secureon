package com.secureone.auth.authn;

import com.secureone.auth.application.ApplicationEffectiveSettingsService;
import com.secureone.auth.application.PasswordPolicySanitizer;
import com.secureone.auth.notify.EmailNotificationService;
import com.secureone.auth.platform.AuthSettingsService;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountLockoutService {

    private static final Logger log = LoggerFactory.getLogger(AccountLockoutService.class);

    public static final String LOCKED_MESSAGE = "Account is temporarily locked. Try again later.";

    private final UserAccountRepository users;
    private final AuthSettingsService authSettings;
    /** Lazy: the effective settings service depends on beans that need this one. */
    private final ObjectProvider<ApplicationEffectiveSettingsService> effectiveSettings;
    /** Lazy for the same reason. */
    private final ObjectProvider<EmailNotificationService> mail;
    private final String publicBaseUrl;

    public AccountLockoutService(
            UserAccountRepository users,
            AuthSettingsService authSettings,
            ObjectProvider<ApplicationEffectiveSettingsService> effectiveSettings,
            ObjectProvider<EmailNotificationService> mail,
            @Value("${secureone.public-base-url:http://localhost:9000}") String publicBaseUrl) {
        this.effectiveSettings = effectiveSettings;
        this.mail = mail;
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
        this.users = users;
        this.authSettings = authSettings;
    }

    public void assertNotLocked(UserAccount account) {
        Instant lockedUntil = account.getLockedUntil();
        if (lockedUntil == null) {
            return;
        }
        if (lockedUntil.isAfter(Instant.now())) {
            throw new LockedException(LOCKED_MESSAGE);
        }
    }

    @Transactional
    public void recordFailedAttempt(UUID userId) {
        recordFailedAttempt(userId, null);
    }

    /** Counts a failed attempt against the application's lockout policy (platform policy when null). */
    @Transactional
    public void recordFailedAttempt(UUID userId, UUID applicationId) {
        UserAccount account = users.findByIdForUpdate(userId).orElse(null);
        if (account == null) {
            return;
        }
        Map<String, Object> policy = applicationId != null
                ? effectiveSettings.getObject().passwordPolicy(applicationId)
                : authSettings.getPasswordPolicy();
        int maxAttempts = PasswordPolicySanitizer.maxFailedAttempts(policy);
        int lockoutMinutes = PasswordPolicySanitizer.lockoutDurationMinutes(policy);

        int nextCount = account.getFailedLoginCount() + 1;
        account.setFailedLoginCount(nextCount);
        boolean newlyLocked = nextCount == maxAttempts;
        if (nextCount >= maxAttempts) {
            account.setLockedUntil(Instant.now().plusSeconds(lockoutMinutes * 60L));
        }
        users.save(account);
        if (newlyLocked) {
            // Often the first sign of someone guessing the password; tell the owner once per lockout.
            try {
                String reset = publicBaseUrl + "/account/forgot-password.html"
                        + (applicationId != null ? "?applicationId=" + applicationId : "");
                mail.getObject().sendAccountLocked(applicationId, account, lockoutMinutes, reset);
            } catch (RuntimeException ex) {
                log.warn("Could not send account-locked email to {}: {}", account.getEmail(), ex.getMessage());
            }
        }
    }

    @Transactional
    public void recordSuccess(UUID userId) {
        users.findByIdForUpdate(userId).ifPresent(account -> {
            account.setFailedLoginCount(0);
            account.setLockedUntil(null);
            users.save(account);
        });
    }
}
