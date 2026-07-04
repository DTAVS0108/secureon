package com.secureone.auth.config.oauth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.secureone.auth.oauth.RefreshTokenReuseAuditService;
import com.secureone.auth.oauth.RefreshTokenReuseContext;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

@ExtendWith(MockitoExtension.class)
class ReuseDetectingOAuth2AuthorizationServiceTest {

    @Mock
    private ReuseDetectingOAuth2AuthorizationService.JdbcOAuth2AuthorizationServiceDelegate delegate;

    @Mock
    private RefreshTokenReuseAuditService audit;

    @AfterEach
    void clearContext() {
        RefreshTokenReuseContext.clear();
    }

    @Test
    void auditsWhenPresentedRefreshTokenDiffersFromStored() {
        OAuth2Authorization authorization = sampleAuthorization("stored-token", "client-1", "acme:user@test.com");
        ReuseDetectingOAuth2AuthorizationService service = new ReuseDetectingOAuth2AuthorizationService(delegate, audit);
        RefreshTokenReuseContext.setPresentedRefreshToken("reused-old-token");

        service.remove(authorization);

        verify(audit).recordReuse(authorization, "reused-old-token");
        verify(delegate).remove(authorization);
    }

    @Test
    void skipsAuditWhenTokensMatch() {
        OAuth2Authorization authorization = sampleAuthorization("same-token", "client-1", "acme:user@test.com");
        ReuseDetectingOAuth2AuthorizationService service = new ReuseDetectingOAuth2AuthorizationService(delegate, audit);
        RefreshTokenReuseContext.setPresentedRefreshToken("same-token");

        service.remove(authorization);

        verify(audit, never()).recordReuse(any(), any());
        verify(delegate).remove(authorization);
    }

    private static OAuth2Authorization sampleAuthorization(String refreshToken, String clientId, String principal) {
        RegisteredClient registeredClient = RegisteredClient.withId(clientId)
                .clientId(clientId)
                .clientSecret("{noop}secret")
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/cb")
                .build();
        OAuth2RefreshToken token = new OAuth2RefreshToken(refreshToken, Instant.now(), Instant.now().plusSeconds(3600));
        return OAuth2Authorization.withRegisteredClient(registeredClient)
                .id("auth-1")
                .principalName(principal)
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(token)
                .build();
    }
}
