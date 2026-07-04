package com.secureone.auth.authn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.secureone.auth.platform.AuthSettingsService;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.LockedException;

@ExtendWith(MockitoExtension.class)
class AccountLockoutServiceTest {

    @Mock
    private UserAccountRepository users;

    @Mock
    private AuthSettingsService authSettings;

    @InjectMocks
    private AccountLockoutService lockout;

    @Test
    void rejectsLockedAccount() {
        UserAccount account = new UserAccount();
        account.setLockedUntil(Instant.now().plusSeconds(600));

        assertThrows(LockedException.class, () -> lockout.assertNotLocked(account));
    }

    @Test
    void locksAccountAfterMaxFailedAttempts() {
        UUID userId = UUID.randomUUID();
        UserAccount account = new UserAccount();
        account.setId(userId);
        account.setFailedLoginCount(4);

        when(users.findByIdForUpdate(userId)).thenReturn(Optional.of(account));
        when(authSettings.getPasswordPolicy())
                .thenReturn(Map.of("maxFailedAttempts", 5, "lockoutDurationMinutes", 15));

        lockout.recordFailedAttempt(userId);

        assertEquals(5, account.getFailedLoginCount());
        verify(users).save(account);
    }

    @Test
    void clearsLockoutOnSuccess() {
        UUID userId = UUID.randomUUID();
        UserAccount account = new UserAccount();
        account.setId(userId);
        account.setFailedLoginCount(3);
        account.setLockedUntil(Instant.now().minusSeconds(30));

        when(users.findByIdForUpdate(userId)).thenReturn(Optional.of(account));

        lockout.recordSuccess(userId);

        assertEquals(0, account.getFailedLoginCount());
        assertEquals(null, account.getLockedUntil());
        verify(users).save(account);
    }

    @Test
    void ignoresMissingUserOnFailure() {
        UUID userId = UUID.randomUUID();
        when(users.findByIdForUpdate(userId)).thenReturn(Optional.empty());

        lockout.recordFailedAttempt(userId);

        verify(users, never()).save(any());
    }
}
