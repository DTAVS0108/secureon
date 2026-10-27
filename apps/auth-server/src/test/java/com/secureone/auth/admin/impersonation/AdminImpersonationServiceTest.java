package com.secureone.auth.admin.impersonation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureone.auth.application.ApplicationMembershipService;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.authn.TenantUserDetailsService;
import com.secureone.auth.tenant.Tenant;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AdminImpersonationServiceTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private UserAccountRepository users;

    @Mock
    private TenantRepository tenants;

    @Mock
    private TenantUserDetailsService userDetailsService;

    @Mock
    private SecurityContextRepository securityContextRepository;

    @Mock
    private ApplicationMembershipService memberships;

    @Mock
    private AuditService audit;

    private AdminImpersonationService service;

    @BeforeEach
    void setUp() {
        service = new AdminImpersonationService(
                redis,
                new ObjectMapper(),
                users,
                tenants,
                userDetailsService,
                securityContextRepository,
                memberships,
                audit,
                "admin");
    }

    private void stubRedis() {
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void createTokenStoresPayloadInRedis() {
        stubRedis();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserAccount user = new UserAccount();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setEmail("user@example.com");
        user.setStatus("ACTIVE");
        when(users.findById(userId)).thenReturn(Optional.of(user));

        AdminImpersonationService.ImpersonationTokenResponse response =
                service.createToken(userId, "operator@example.com", "support ticket", null);

        assertEquals(120, response.expiresInSeconds());
        verify(valueOps).set(any(String.class), any(String.class), eq(Duration.ofMinutes(2)));
        verify(audit)
                .record(
                        eq(tenantId),
                        eq(null),
                        eq("operator@example.com"),
                        eq("user.impersonate"),
                        eq("user"),
                        eq(userId),
                        eq("user@example.com — reason: support ticket"),
                        eq(true));
    }

    @Test
    void createTokenRejectsDisabledUser() {
        UUID userId = UUID.randomUUID();
        UserAccount user = new UserAccount();
        user.setId(userId);
        user.setEmail("disabled@example.com");
        user.setStatus("DISABLED");
        when(users.findById(userId)).thenReturn(Optional.of(user));

        assertThrows(
                ResponseStatusException.class,
                () -> service.createToken(userId, "operator@example.com", "debug", null));
    }

    @Test
    void createTokenRejectsPlatformSuperAdminEmail() {
        UUID userId = UUID.randomUUID();
        UserAccount user = new UserAccount();
        user.setId(userId);
        user.setEmail("admin");
        user.setStatus("ACTIVE");
        when(users.findById(userId)).thenReturn(Optional.of(user));

        assertThrows(
                ResponseStatusException.class,
                () -> service.createToken(userId, "operator@example.com", "debug", null));
    }
}
