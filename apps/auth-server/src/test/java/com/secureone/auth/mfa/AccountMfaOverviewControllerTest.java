package com.secureone.auth.mfa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.secureone.auth.authn.SessionUserResolver;
import com.secureone.auth.user.UserAccount;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class AccountMfaOverviewControllerTest {

    @Mock
    private SessionUserResolver users;

    @Mock
    private MfaFactorRepository factors;

    private AccountMfaOverviewController controller;

    private UUID userId;

    @BeforeEach
    void setUp() {
        controller = new AccountMfaOverviewController(users, factors);
        userId = UUID.randomUUID();
    }

    @Test
    void listFactorsReturnsPublicViews() {
        UserAccount user = new UserAccount();
        user.setId(userId);
        when(users.requireTenantUser(org.mockito.ArgumentMatchers.any())).thenReturn(user);
        MfaFactor totp = new MfaFactor();
        totp.setId(UUID.randomUUID());
        totp.setUserId(userId);
        totp.setType("totp");
        totp.setLabel("Phone");
        totp.setVerified(true);
        when(factors.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(totp));

        List<Map<String, Object>> out = controller.listFactors(
                new UsernamePasswordAuthenticationToken("tenant-a:user@example.com", "n/a"));

        assertEquals(1, out.size());
        assertEquals("totp", out.get(0).get("type"));
        assertEquals(true, out.get(0).get("verified"));
    }
}
