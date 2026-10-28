package com.secureone.auth.config.oauth;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.secureone.auth.oauth.RefreshTokenReuseAuditService;
import com.secureone.auth.oauth.RefreshTokenReuseContext;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReuseDetectingOAuth2AuthorizationServiceTest {

    @Mock
    private ReuseDetectingOAuth2AuthorizationService.JdbcOAuth2AuthorizationServiceDelegate delegate;

    @Mock
    private RefreshTokenReuseAuditService audit;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> values;

    private ReuseDetectingOAuth2AuthorizationService service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        service = new ReuseDetectingOAuth2AuthorizationService(delegate, audit, redis);
    }

    @AfterEach
    void clearContext() {
        RefreshTokenReuseContext.clear();
    }

    @Test
    void auditsWhenPresentedRefreshTokenDiffersFromStored() {
        OAuth2Authorization authorization = sampleAuthorization("stored-token");
        RefreshTokenReuseContext.setPresentedRefreshToken("reused-old-token");

        service.remove(authorization);

        verify(audit).recordReuse(authorization, "reused-old-token");
        verify(delegate).remove(authorization);
    }

    @Test
    void skipsAuditWhenTokensMatch() {
        OAuth2Authorization authorization = sampleAuthorization("same-token");
        RefreshTokenReuseContext.setPresentedRefreshToken("same-token");

        service.remove(authorization);

        verify(audit, never()).recordReuse(any(), any());
        verify(delegate).remove(authorization);
    }

    @Test
    void rotationRemembersTheRetiredToken() {
        when(delegate.findById("auth-1")).thenReturn(sampleAuthorization("old-token"));

        service.save(sampleAuthorization("new-token"));

        verify(values).set(eq(ReuseDetectingOAuth2AuthorizationService.retiredKey("old-token")), eq("auth-1"), any(Duration.class));
        verify(delegate).save(any());
    }

    @Test
    void saveWithoutRotationRemembersNothing() {
        when(delegate.findById("auth-1")).thenReturn(sampleAuthorization("same-token"));

        service.save(sampleAuthorization("same-token"));

        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void replayOfRetiredTokenRevokesTheAuthorization() {
        OAuth2Authorization family = sampleAuthorization("current-token");
        when(delegate.findByToken("old-token", OAuth2TokenType.REFRESH_TOKEN)).thenReturn(null);
        when(values.get(ReuseDetectingOAuth2AuthorizationService.retiredKey("old-token"))).thenReturn("auth-1");
        when(delegate.findById("auth-1")).thenReturn(family);

        assertNull(service.findByToken("old-token", OAuth2TokenType.REFRESH_TOKEN));

        verify(audit).recordReuse(family, "old-token");
        verify(delegate).remove(family);
    }

    @Test
    void unknownTokenRevokesNothing() {
        when(delegate.findByToken("garbage", null)).thenReturn(null);
        when(values.get(anyString())).thenReturn(null);

        assertNull(service.findByToken("garbage", null));

        verify(delegate, never()).remove(any());
    }

    private static OAuth2Authorization sampleAuthorization(String refreshToken) {
        RegisteredClient registeredClient = RegisteredClient.withId("client-1")
                .clientId("client-1")
                .clientSecret("{noop}secret")
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/cb")
                .build();
        OAuth2RefreshToken token = new OAuth2RefreshToken(refreshToken, Instant.now(), Instant.now().plusSeconds(3600));
        return OAuth2Authorization.withRegisteredClient(registeredClient)
                .id("auth-1")
                .principalName("tenant-a:user@test.com")
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(token)
                .build();
    }
}
