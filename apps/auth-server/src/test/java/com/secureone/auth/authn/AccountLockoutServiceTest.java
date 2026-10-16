package com.secureone.auth.authn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.secureone.auth.application.ApplicationEffectiveSettingsService;
import com.secureone.auth.notify.EmailNotificationService;
import com.secureone.auth.platform.AuthSettingsService;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.LockedException;

@ExtendWith(MockitoExtension.class)
class AccountLockoutServiceTest {

    @Mock
    private UserAccountRepository users;

    @Mock
    private AuthSettingsService authSettings;

    @Mock
    private ObjectProvider<ApplicationEffectiveSettingsService> effectiveSettings;

    @Mock
    private ObjectProvider<EmailNotificationService> mailProvider;

    @Mock
    private EmailNotificationService mail;

    private AccountLockoutService lockout;

    @BeforeEach
    void setUp() {
        lockout = new AccountLockoutService(users, authSettings, effectiveSettings, mailProvider, "http://localhost:9000");
    }

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

        when(mailProvider.getObject()).thenReturn(mail);

        lockout.recordFailedAttempt(userId);

        assertEquals(5, account.getFailedLoginCount());
        verify(users).save(account);
        verify(mail).sendAccountLocked(any(), any(), anyInt(), any());
    }

    @Test
    void emailsOnlyWhenTheAccountFirstLocks() {
        UUID userId = UUID.randomUUID();
        UserAccount account = new UserAccount();
        account.setId(userId);
        account.setFailedLoginCount(5);

        when(users.findByIdForUpdate(userId)).thenReturn(Optional.of(account));
        when(authSettings.getPasswordPolicy())
                .thenReturn(Map.of("maxFailedAttempts", 5, "lockoutDurationMinutes", 15));

        lockout.recordFailedAttempt(userId);

        assertEquals(6, account.getFailedLoginCount());
        verify(mailProvider, never()).getObject();
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
