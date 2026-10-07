package com.secureone.auth.serviceaccount;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.secureone.auth.ratelimit.RedisRateLimiter;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationTest {

    @Mock
    private ServiceAccountService serviceAccounts;

    @Mock
    private RedisRateLimiter rateLimiter;

    private ApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthenticationFilter(serviceAccounts, rateLimiter);
        SecurityContextHolder.clearContext();
    }

    @Test
    void setsM2mAuthenticationForBearerApiKey() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID serviceAccountId = UUID.randomUUID();
        UUID apiKeyId = UUID.randomUUID();
        ServiceAccountContext context = new ServiceAccountContext(tenantId, serviceAccountId, apiKeyId, "ci-bot");
        when(serviceAccounts.validateApiKey("sk_live_testkey")).thenReturn(Optional.of(context));
        when(rateLimiter.tryConsume(eq("m2m:key:" + apiKeyId), anyInt())).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/m2m/whoami");
        request.addHeader("Authorization", "Bearer sk_live_testkey");

        filter.doFilter(request, new org.springframework.mock.web.MockHttpServletResponse(), (req, res) -> {});

        assertTrue(SecurityContextHolder.getContext().getAuthentication().isAuthenticated());
        assertEquals(context, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        assertEquals(
                "ROLE_M2M",
                SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getAuthorities()
                        .iterator()
                        .next()
                        .getAuthority());
    }

    @Test
    void acceptsXApiKeyHeader() throws Exception {
        UUID apiKeyId = UUID.randomUUID();
        ServiceAccountContext context =
                new ServiceAccountContext(UUID.randomUUID(), UUID.randomUUID(), apiKeyId, "bot");
        when(serviceAccounts.validateApiKey("sk_live_header")).thenReturn(Optional.of(context));
        when(rateLimiter.tryConsume(any(), anyInt())).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/m2m/whoami");
        request.addHeader("X-Api-Key", "sk_live_header");

        filter.doFilter(request, new org.springframework.mock.web.MockHttpServletResponse(), (req, res) -> {});

        verify(serviceAccounts).validateApiKey("sk_live_header");
        assertEquals(context, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void ignoresNonM2mPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/account/profile");
        request.addHeader("Authorization", "Bearer sk_live_testkey");

        filter.doFilter(request, new org.springframework.mock.web.MockHttpServletResponse(), (req, res) -> {});

        verify(serviceAccounts, never()).validateApiKey(any());
        assertFalse(SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated());
    }

    @Test
    void validateApiKeyRejectsRevokedKeys() {
        ApiKeyRepository apiKeys = org.mockito.Mockito.mock(ApiKeyRepository.class);
        ServiceAccountRepository accounts = org.mockito.Mockito.mock(ServiceAccountRepository.class);
        ServiceAccountService service = new ServiceAccountService(accounts, apiKeys);

        String rawKey = "sk_live_revoked";
        ApiKey key = new ApiKey();
        key.setRevokedAt(Instant.now());
        when(apiKeys.findByKeyHash(ServiceAccountService.hashKey(rawKey))).thenReturn(Optional.of(key));

        assertTrue(service.validateApiKey(rawKey).isEmpty());
    }

    @Test
    void rejectsInvalidApiKeyOnM2mPath() throws Exception {
        when(serviceAccounts.validateApiKey("sk_live_bad")).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/m2m/whoami");
        request.addHeader("Authorization", "Bearer sk_live_bad");
        org.springframework.mock.web.MockHttpServletResponse response =
                new org.springframework.mock.web.MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertEquals(401, response.getStatus());
        verify(serviceAccounts).validateApiKey("sk_live_bad");
    }

    @Test
    void rejectsWhenRateLimitExceeded() throws Exception {
        UUID apiKeyId = UUID.randomUUID();
        ServiceAccountContext context =
                new ServiceAccountContext(UUID.randomUUID(), UUID.randomUUID(), apiKeyId, "bot");
        when(serviceAccounts.validateApiKey("sk_live_rl")).thenReturn(Optional.of(context));
        when(rateLimiter.tryConsume(eq("m2m:key:" + apiKeyId), anyInt())).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/m2m/whoami");
        request.addHeader("Authorization", "Bearer sk_live_rl");
        org.springframework.mock.web.MockHttpServletResponse response =
                new org.springframework.mock.web.MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertEquals(429, response.getStatus());
    }

    @Test
    void skipsWhenAlreadyAuthenticated() throws Exception {
        ServiceAccountContext context =
                new ServiceAccountContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "bot");
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        context,
                        null,
                        org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_M2M")));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/m2m/whoami");
        request.addHeader("Authorization", "Bearer sk_live_testkey");

        filter.doFilter(request, new org.springframework.mock.web.MockHttpServletResponse(), (req, res) -> {});

        verify(serviceAccounts, never()).validateApiKey(any());
    }
}
