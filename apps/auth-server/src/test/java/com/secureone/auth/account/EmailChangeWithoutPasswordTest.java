package com.secureone.auth.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.secureone.auth.audit.AuditService;
import com.secureone.auth.notify.EmailNotificationService;
import com.secureone.auth.notify.EmailTokenType;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Accounts with no password (Google, SAML, passkey only) prove themselves with a recent sign-in instead. */
class EmailChangeWithoutPasswordTest {

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final UserPasswordService passwords = mock(UserPasswordService.class);
    private final EmailTokenService tokens = mock(EmailTokenService.class);
    private final EmailChangeService service = new EmailChangeService(
            users, passwords, tokens, mock(EmailNotificationService.class), mock(AuditService.class), "https://login.example.com");
    private final UserAccount user = new UserAccount();

    @BeforeEach
    void setUp() {
        user.setId(UUID.randomUUID());
        user.setTenantId(UUID.randomUUID());
        user.setEmail("old@example.com");
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.findByTenantIdAndEmail(any(), any())).thenReturn(Optional.empty());
        when(passwords.hasPassword(user.getId())).thenReturn(false);
        when(tokens.issue(any(), any())).thenReturn(new EmailTokenService.IssuedToken("raw-token", null));
    }

    @Test
    void anOldSignInMustSignInAgain() {
        Instant signedIn = Instant.now().minus(Duration.ofMinutes(30));

        assertThatThrownBy(() -> service.request(user.getId(), "new@example.com", null, null, signedIn))
                .hasMessageContaining("sign in again");
        assertThatThrownBy(() -> service.request(user.getId(), "new@example.com", null, null, null))
                .hasMessageContaining("sign in again");
        verify(tokens, never()).issue(any(), any());
    }

    @Test
    void aRecentSignInIsEnough() {
        Map<String, Object> out = service.request(
                user.getId(), "new@example.com", null, null, Instant.now().minus(Duration.ofMinutes(2)));

        assertThat(out).containsEntry("pendingEmail", "new@example.com");
        assertThat(user.getAttributes()).containsEntry(EmailChangeService.PENDING, "new@example.com");
        verify(tokens).issue(user, EmailTokenType.CHANGE_EMAIL);
    }
}
