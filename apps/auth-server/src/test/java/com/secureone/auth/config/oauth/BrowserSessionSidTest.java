package com.secureone.auth.config.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureone.auth.federation.IdentityProviderService;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class BrowserSessionSidTest {

    private static final RegisteredClient CLIENT = RegisteredClient.withId("c1")
            .clientId("shop-spa")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://shop.example.com/callback")
            .build();

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static OAuth2Authorization.Builder withCode() {
        return OAuth2Authorization.withRegisteredClient(CLIENT)
                .id("a1")
                .principalName("alice")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(new OAuth2AuthorizationCode("code", Instant.now(), Instant.now().plusSeconds(60)));
    }

    private static MockHttpServletRequest browser() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    @Test
    void hashesLikeSpringAuthorizationServer() {
        // SHA-256, base64url without padding
        assertThat(BrowserSessionSid.hash("abc")).isEqualTo("ungWv48Bz-pBQUDeXa4iI7ADYaOWF3qctBD_YfIAFa0");
    }

    @Test
    void recordsTheAuthorizeRequestsBrowserSession() {
        MockHttpServletRequest request = browser();
        String sessionId = request.getSession(true).getId();

        OAuth2Authorization saved = BrowserSessionSid.withBrowserSession(withCode().build());

        assertThat((String) saved.getAttribute(BrowserSessionSid.ATTRIBUTE)).isEqualTo(BrowserSessionSid.hash(sessionId));
    }

    @Test
    void keepsTheSessionRecordedFirst() {
        browser().getSession(true);
        OAuth2Authorization first = withCode().attribute(BrowserSessionSid.ATTRIBUTE, "earlier").build();

        assertThat((String) BrowserSessionSid.withBrowserSession(first).getAttribute(BrowserSessionSid.ATTRIBUTE))
                .isEqualTo("earlier");
    }

    @Test
    void neverRecordsAtTheTokenEndpointOrWithoutASession() {
        MockHttpServletRequest token = new MockHttpServletRequest("POST", "/oauth2/token");
        token.setParameter("grant_type", "authorization_code");
        token.getSession(true);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(token));
        assertThat((Object) BrowserSessionSid.withBrowserSession(withCode().build()).getAttribute(BrowserSessionSid.ATTRIBUTE))
                .isNull();

        browser(); // no session
        assertThat((Object) BrowserSessionSid.withBrowserSession(withCode().build()).getAttribute(BrowserSessionSid.ATTRIBUTE))
                .isNull();
    }

    @Test
    void ignoresAuthorizationsThatAlreadyHaveTokens() {
        browser().getSession(true);
        OAuth2Authorization issued = withCode()
                .accessToken(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "at", Instant.now(), Instant.now().plusSeconds(60)))
                .build();

        assertThat((Object) BrowserSessionSid.withBrowserSession(issued).getAttribute(BrowserSessionSid.ATTRIBUTE)).isNull();
    }

    @Test
    void pkceChallengeFollowsRfc7636() {
        // RFC 7636 appendix B
        assertThat(IdentityProviderService.codeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
        String verifier = IdentityProviderService.newCodeVerifier();
        assertThat(verifier).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(IdentityProviderService.newCodeVerifier()).isNotEqualTo(verifier);
    }
}
