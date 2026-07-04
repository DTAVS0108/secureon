package com.secureone.auth.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.secureone.auth.audit.AuditLog;
import com.secureone.auth.audit.AuditLogRepository;
import com.secureone.auth.authn.TenantUserDetailsService;
import com.secureone.auth.federation.UserIdentityRepository;
import com.secureone.auth.mfa.MfaFactor;
import com.secureone.auth.mfa.MfaFactorRepository;
import com.secureone.auth.tenant.Tenant;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AccountPrivacyServiceTest {

    @Mock
    private UserAccountRepository users;

    @Mock
    private TenantRepository tenants;

    @Mock
    private TenantUserDetailsService userDetails;

    @Mock
    private MfaFactorRepository mfaFactors;

    @Mock
    private UserIdentityRepository identities;

    @Mock
    private AuditLogRepository auditLogs;

    private AccountPrivacyService service;

    private UUID userId;
    private UUID tenantId;
    private UserAccount user;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        service = new AccountPrivacyService(users, tenants, userDetails, mfaFactors, identities, auditLogs);
        userId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        user = new UserAccount();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setEmail("user@example.com");
        user.setDisplayName("Jane");
        user.setStatus("ACTIVE");
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .build();
    }

    @Test
    void exportIncludesMfaAndIdentitySummary() {
        when(users.findById(userId)).thenReturn(Optional.of(user));
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setSlug("acme");
        tenant.setName("Acme");
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
        MfaFactor totp = new MfaFactor();
        totp.setId(UUID.randomUUID());
        totp.setType("totp");
        totp.setVerified(true);
        when(mfaFactors.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(totp));
        when(identities.findByUserId(userId)).thenReturn(List.of());

        Map<String, Object> out = service.export(jwt);

        assertEquals("user@example.com", out.get("email"));
        assertEquals("acme", out.get("tenantSlug"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> factors = (List<Map<String, Object>>) out.get("mfaFactors");
        assertEquals(1, factors.size());
        assertEquals("totp", factors.get(0).get("type"));
        assertEquals(0, out.get("linkedIdentityCount"));
    }

    @Test
    void deleteRequestDisablesAccountAndAudits() {
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(users.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> out = service.requestDeletion(jwt);

        assertFalse(out.get("message").isBlank());
        assertEquals("DISABLED", user.getStatus());
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogs).save(captor.capture());
        assertEquals("USER", captor.getValue().getActorType());
        assertEquals("account.deletion_requested", captor.getValue().getAction());
    }

    @Test
    void deleteRequestWhenAlreadyDisabledIsIdempotent() {
        user.setStatus("DISABLED");
        when(users.findById(userId)).thenReturn(Optional.of(user));

        Map<String, String> out = service.requestDeletion(jwt);

        assertTrue(out.get("message").contains("already disabled"));
    }
}
