package com.secureone.auth.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.secureone.auth.config.oauth.ApplicationRegisteredClientRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

@ExtendWith(MockitoExtension.class)
class OAuthTokenExchangeServiceTest {

    @Mock
    private ApplicationRegisteredClientRepository clients;

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private JWKSource<SecurityContext> jwkSource;

    private OAuthTokenExchangeService service;

    @Mock
    private com.secureone.auth.config.oauth.OAuth2SigningKeyService signingKeys;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(signingKeys.signingJwkSource()).thenReturn(jwkSource);
        service = new OAuthTokenExchangeService(
                clients, NoOpPasswordEncoder.getInstance(), jwtDecoder, signingKeys);
    }

    @Test
    void exchangeRejectsMismatchedClientParty() {
        when(clients.findByClientId("test-client")).thenReturn(confidentialClient(true));
        when(jwtDecoder.decode("subject.jwt")).thenReturn(subject("other-client", List.of("openid")));

        OAuth2AuthenticationException ex =
                assertThrows(OAuth2AuthenticationException.class, () -> service.exchange(request("test-client", "secret")));
        assertEquals("invalid_grant", ex.getError().getErrorCode());
    }

    @Test
    void exchangeRejectsUnknownClient() {
        when(clients.findByClientId("missing")).thenReturn(null);

        OAuth2AuthenticationException ex =
                assertThrows(OAuth2AuthenticationException.class, () -> service.exchange(request("missing", "secret")));
        assertEquals("invalid_client", ex.getError().getErrorCode());
    }

    @Test
    void exchangeRejectsClientWithoutTheGrant() {
        when(clients.findByClientId("test-client")).thenReturn(confidentialClient(false));

        OAuth2AuthenticationException ex =
                assertThrows(OAuth2AuthenticationException.class, () -> service.exchange(request("test-client", "secret")));
        assertEquals("unauthorized_client", ex.getError().getErrorCode());
    }

    @Test
    void exchangeRejectsPublicClients() {
        RegisteredClient publicClient = RegisteredClient.withId("pub")
                .clientId("spa")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(OAuthTokenExchangeService.TOKEN_EXCHANGE_GRANT)
                .clientSettings(ClientSettings.builder().build())
                .tokenSettings(TokenSettings.builder().build())
                .build();
        when(clients.findByClientId("spa")).thenReturn(publicClient);

        OAuth2AuthenticationException ex =
                assertThrows(OAuth2AuthenticationException.class, () -> service.exchange(request("spa", null)));
        assertEquals("invalid_client", ex.getError().getErrorCode());
    }

    @Test
    void scopeOutsideAnArrayScopeClaimIsRejected() {
        when(clients.findByClientId("test-client")).thenReturn(confidentialClient(true));
        when(jwtDecoder.decode("subject.jwt")).thenReturn(subject("test-client", List.of("openid", "profile")));
        MockHttpServletRequest request = request("test-client", "secret");
        request.addParameter(OAuth2ParameterNames.SCOPE, "admin");

        OAuth2AuthenticationException ex =
                assertThrows(OAuth2AuthenticationException.class, () -> service.exchange(request));
        assertEquals("invalid_grant", ex.getError().getErrorCode());
    }

    @Test
    void malformedBasicHeaderIsInvalidClient() {
        MockHttpServletRequest request = request(null, null);
        request.addHeader("Authorization", "Basic !!!not-base64!!!");

        OAuth2AuthenticationException ex =
                assertThrows(OAuth2AuthenticationException.class, () -> service.exchange(request));
        assertEquals("invalid_client", ex.getError().getErrorCode());
    }

    @Test
    void basicCredentialsAreUrlDecoded() {
        when(clients.findByClientId("client:id")).thenReturn(null);
        MockHttpServletRequest request = request(null, null);
        String encoded = Base64.getEncoder().encodeToString("client%3Aid:s%40cret".getBytes(StandardCharsets.UTF_8));
        request.addHeader("Authorization", "Basic " + encoded);

        OAuth2AuthenticationException ex =
                assertThrows(OAuth2AuthenticationException.class, () -> service.exchange(request));
        assertEquals("Unknown client_id.", ex.getError().getDescription());
    }

    private static RegisteredClient confidentialClient(boolean withTokenExchange) {
        RegisteredClient.Builder builder = RegisteredClient.withId("id")
                .clientId("test-client")
                .clientSecret("secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientSettings(ClientSettings.builder().build())
                .tokenSettings(TokenSettings.builder().build())
                .redirectUri("http://localhost/cb");
        if (withTokenExchange) {
            builder.authorizationGrantType(OAuthTokenExchangeService.TOKEN_EXCHANGE_GRANT);
        }
        return builder.build();
    }

    private static Jwt subject(String azp, List<String> scopes) {
        return Jwt.withTokenValue("subject")
                .header("alg", "none")
                .subject("user-1")
                .issuer("http://localhost:9000")
                .claim("scope", scopes)
                .claim("azp", azp)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private static MockHttpServletRequest request(String clientId, String secret) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
        request.addParameter(OAuth2ParameterNames.GRANT_TYPE, OAuthTokenExchangeService.TOKEN_EXCHANGE_GRANT.getValue());
        if (clientId != null) {
            request.addParameter(OAuth2ParameterNames.CLIENT_ID, clientId);
        }
        if (secret != null) {
            request.addParameter(OAuth2ParameterNames.CLIENT_SECRET, secret);
        }
        request.addParameter(OAuth2ParameterNames.SUBJECT_TOKEN, "subject.jwt");
        return request;
    }
}
