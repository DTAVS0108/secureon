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

    /** Longest lock the back-off reaches (24 hours), unless the policy's own lock time is longer. */
    static final long MAX_LOCK_MINUTES = 24 * 60;

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

    /**
     * Counts a wrong password or a wrong second-step code against the application's lockout policy (platform
     * policy when null). Reaching the limit locks the account; every lockout in a row doubles the lock time, up
     * to 24 hours, and emails the owner. A new lock starts a fresh set of attempts once it expires.
     */
    @Transactional
    public void recordFailedAttempt(UUID userId, UUID applicationId) {
        UserAccount account = users.findByIdForUpdate(userId).orElse(null);
        if (account == null) {
            return;
        }
        Instant now = Instant.now();
        if (account.getLockedUntil() != null && account.getLockedUntil().isAfter(now)) {
            return; // already locked; nothing is checked while locked
        }
        Map<String, Object> policy = applicationId != null
                ? effectiveSettings.getObject().passwordPolicy(applicationId)
                : authSettings.getPasswordPolicy();
        int maxAttempts = PasswordPolicySanitizer.maxFailedAttempts(policy);
        int baseMinutes = PasswordPolicySanitizer.lockoutDurationMinutes(policy);

        int nextCount = account.getFailedLoginCount() + 1;
        if (nextCount < maxAttempts) {
            account.setFailedLoginCount(nextCount);
            users.save(account);
            return;
        }
        int level = account.getLockoutLevel() + 1;
        long lockMinutes = lockMinutes(baseMinutes, level);
        account.setLockoutLevel(level);
        account.setFailedLoginCount(0); // the attempts after this lock expires start from zero
        account.setLockedUntil(now.plusSeconds(lockMinutes * 60L));
        users.save(account);
        // Often the first sign of someone guessing the password or codes; tell the owner every time.
        try {
            String reset = publicBaseUrl + "/account/forgot-password.html"
                    + (applicationId != null ? "?applicationId=" + applicationId : "");
            mail.getObject().sendAccountLocked(applicationId, account, (int) lockMinutes, reset);
        } catch (RuntimeException ex) {
            log.warn("Could not send account-locked email to {}: {}", account.getEmail(), ex.getMessage());
        }
    }

    /** Lock time for the {@code level}-th lockout in a row: base, then doubled each time, at most 24 hours. */
    static long lockMinutes(int baseMinutes, int level) {
        long minutes = baseMinutes;
        for (int i = 1; i < level && minutes < MAX_LOCK_MINUTES; i++) {
            minutes *= 2;
        }
        return Math.min(minutes, Math.max(baseMinutes, MAX_LOCK_MINUTES));
    }

    @Transactional
    public void recordSuccess(UUID userId) {
        clear(userId);
    }

    /**
     * The same as {@link #clear} on an account the caller is about to save itself (so its own save does not put
     * the old lock back). Used by password reset and invitation flows.
     */
    public static void clearOn(UserAccount account) {
        account.setFailedLoginCount(0);
        account.setLockedUntil(null);
        account.setLockoutLevel(0);
    }

    /** Ends any lock and forgets past failures: successful sign-in, password reset, admin unlock. */
    @Transactional
    public void clear(UUID userId) {
        users.findByIdForUpdate(userId).ifPresent(account -> {
            account.setFailedLoginCount(0);
            account.setLockedUntil(null);
            account.setLockoutLevel(0);
            users.save(account);
        });
    }
}
