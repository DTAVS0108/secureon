package com.secureone.auth.authn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.secureone.auth.application.ApplicationEffectiveSettingsService;
import com.secureone.auth.notify.EmailNotificationService;
import com.secureone.auth.platform.AuthSettingsService;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.LockedException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    private final UUID userId = UUID.randomUUID();
    private UserAccount account;

    @BeforeEach
    void setUp() {
        lockout = new AccountLockoutService(users, authSettings, effectiveSettings, mailProvider, "http://localhost:9000");
        account = new UserAccount();
        account.setId(userId);
        when(users.findByIdForUpdate(userId)).thenReturn(Optional.of(account));
        when(authSettings.getPasswordPolicy())
                .thenReturn(Map.of("maxFailedAttempts", 5, "lockoutDurationMinutes", 15));
        when(mailProvider.getObject()).thenReturn(mail);
    }

    private long minutesLocked() {
        return Duration.between(Instant.now(), account.getLockedUntil()).plusSeconds(5).toMinutes();
    }

    @Test
    void rejectsLockedAccount() {
        account.setLockedUntil(Instant.now().plusSeconds(600));

        assertThrows(LockedException.class, () -> lockout.assertNotLocked(account));
    }

    @Test
    void countsFailuresBelowTheLimitWithoutLocking() {
        account.setFailedLoginCount(2);

        lockout.recordFailedAttempt(userId);

        assertEquals(3, account.getFailedLoginCount());
        assertNull(account.getLockedUntil());
        verify(mailProvider, never()).getObject();
    }

    @Test
    void locksAtTheLimitStartsAFreshCountAndEmails() {
        account.setFailedLoginCount(4);

        lockout.recordFailedAttempt(userId);

        assertEquals(0, account.getFailedLoginCount());
        assertEquals(1, account.getLockoutLevel());
        assertEquals(15, minutesLocked());
        verify(mail).sendAccountLocked(any(), eq(account), eq(15), any());
    }

    @Test
    void afterALockExpiresOneWrongPasswordDoesNotLockAgain() {
        // The old behaviour: the count stayed at the limit, so the next failure locked again at once.
        account.setLockoutLevel(1);
        account.setLockedUntil(Instant.now().minusSeconds(5));
        account.setFailedLoginCount(0);

        lockout.recordFailedAttempt(userId);

        assertEquals(1, account.getFailedLoginCount());
        assertTrue(account.getLockedUntil().isBefore(Instant.now()));
        verify(mailProvider, never()).getObject();
    }

    @Test
    void repeatedLockoutsDoubleTheLockTimeAndEmailEachTime() {
        account.setLockoutLevel(1);
        account.setFailedLoginCount(4);

        lockout.recordFailedAttempt(userId);

        assertEquals(2, account.getLockoutLevel());
        assertEquals(30, minutesLocked());
        verify(mail).sendAccountLocked(any(), eq(account), eq(30), any());
    }

    @Test
    void failuresWhileLockedChangeNothing() {
        Instant until = Instant.now().plusSeconds(600);
        account.setLockedUntil(until);
        account.setFailedLoginCount(0);

        lockout.recordFailedAttempt(userId);

        assertEquals(until, account.getLockedUntil());
        assertEquals(0, account.getFailedLoginCount());
        verify(users, never()).save(any());
    }

    @Test
    void backOffIsCappedAtADay() {
        assertEquals(15, AccountLockoutService.lockMinutes(15, 1));
        assertEquals(60, AccountLockoutService.lockMinutes(15, 3));
        assertEquals(1440, AccountLockoutService.lockMinutes(15, 10));
        assertEquals(1440, AccountLockoutService.lockMinutes(1440, 4));
    }

    @Test
    void successOrClearEndsTheLockAndTheBackOff() {
        account.setFailedLoginCount(3);
        account.setLockoutLevel(4);
        account.setLockedUntil(Instant.now().plusSeconds(900));

        lockout.clear(userId);

        assertEquals(0, account.getFailedLoginCount());
        assertEquals(0, account.getLockoutLevel());
        assertNull(account.getLockedUntil());
        verify(users).save(account);
    }

    @Test
    void ignoresMissingUserOnFailure() {
        UUID unknown = UUID.randomUUID();
        when(users.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

        lockout.recordFailedAttempt(unknown);

        verify(users, never()).save(any());
    }
}
