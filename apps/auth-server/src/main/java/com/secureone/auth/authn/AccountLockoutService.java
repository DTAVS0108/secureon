package com.secureone.auth.authn;

import com.secureone.auth.application.PasswordPolicySanitizer;
import com.secureone.auth.platform.AuthSettingsService;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountLockoutService {

    public static final String LOCKED_MESSAGE = "Account is temporarily locked. Try again later.";

    private final UserAccountRepository users;
    private final AuthSettingsService authSettings;

    public AccountLockoutService(UserAccountRepository users, AuthSettingsService authSettings) {
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
        UserAccount account = users.findByIdForUpdate(userId).orElse(null);
        if (account == null) {
            return;
        }
        Map<String, Object> policy = authSettings.getPasswordPolicy();
        int maxAttempts = PasswordPolicySanitizer.maxFailedAttempts(policy);
        int lockoutMinutes = PasswordPolicySanitizer.lockoutDurationMinutes(policy);

        int nextCount = account.getFailedLoginCount() + 1;
        account.setFailedLoginCount(nextCount);
        if (nextCount >= maxAttempts) {
            account.setLockedUntil(Instant.now().plusSeconds(lockoutMinutes * 60L));
        }
        users.save(account);
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
