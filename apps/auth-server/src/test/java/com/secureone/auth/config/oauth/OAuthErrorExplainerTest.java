package com.secureone.auth.config.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

class OAuthErrorExplainerTest {

    private static final RegisteredClient SPA = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("shop-spa")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost:5173/")
            .scope("openid")
            .scope("profile")
            .build();

    private static MockHttpServletRequest authorize(String query) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            request.addParameter(kv[0], kv.length > 1 ? kv[1] : "");
        }
        return request;
    }

    private static OAuth2Error param(String code, String parameter) {
        return new OAuth2Error(code, "OAuth 2.0 Parameter: " + parameter, null);
    }

    @Test
    void unregisteredRedirectNamesTheValueAndOutsideProductionTheRegisteredOnes() {
        var request = authorize("client_id=shop-spa&redirect_uri=http://localhost:5174/");
        String dev = OAuthErrorExplainer.explainAuthorization(param("invalid_request", "redirect_uri"), request, SPA, false);
        assertTrue(dev.contains("'http://localhost:5174/' is not registered for client 'shop-spa'"), dev);
        assertTrue(dev.contains("Registered: http://localhost:5173/"), dev);
        String prod = OAuthErrorExplainer.explainAuthorization(param("invalid_request", "redirect_uri"), request, SPA, true);
        assertFalse(prod.contains("Registered:"), "production does not list registered URIs: " + prod);
    }

    @Test
    void unknownClientAndScopeAndPkce() {
        String unknown = OAuthErrorExplainer.explainAuthorization(
                param("invalid_request", "client_id"), authorize("client_id=shopp-spa"), null, false);
        assertTrue(unknown.contains("No active OAuth client has client_id 'shopp-spa'"), unknown);

        String scope = OAuthErrorExplainer.explainAuthorization(
                param("invalid_scope", "scope"), authorize("client_id=shop-spa&scope=openid orders.write"), SPA, false);
        assertTrue(scope.contains("'orders.write' is not allowed") && scope.contains("Allowed: openid profile"), scope);

        String pkce = OAuthErrorExplainer.explainAuthorization(
                param("invalid_request", "code_challenge"), authorize("client_id=shop-spa"), SPA, false);
        assertTrue(pkce.contains("requires PKCE") && pkce.contains("S256"), pkce);
    }

    @Test
    void specificDescriptionsAreKept() {
        OAuth2Error denied = new OAuth2Error("access_denied", "This app belongs to another organization.", null);
        assertEquals("This app belongs to another organization.",
                OAuthErrorExplainer.explainAuthorization(denied, authorize("client_id=shop-spa"), SPA, false));
    }

    @Test
    void tokenErrorsExplainTheCommonCauses() {
        MockHttpServletRequest code = new MockHttpServletRequest("POST", "/oauth2/token");
        code.addParameter("grant_type", "authorization_code");
        String grant = OAuthErrorExplainer.explainToken(new OAuth2Error("invalid_grant"), code);
        assertTrue(grant.contains("already used") && grant.contains("code_verifier"), grant);

        // Spring's public-client PKCE check reports a bad code as "Client authentication failed: code".
        String pkce = OAuthErrorExplainer.explainToken(new OAuth2Error("invalid_grant", "Client authentication failed: code", null), code);
        assertTrue(pkce.contains("already used"), pkce);

        MockHttpServletRequest refresh = new MockHttpServletRequest("POST", "/oauth2/token");
        refresh.addParameter("grant_type", "refresh_token");
        assertTrue(OAuthErrorExplainer.explainToken(new OAuth2Error("invalid_grant"), refresh).contains("rotate"));

        MockHttpServletRequest odd = new MockHttpServletRequest("POST", "/oauth2/token");
        odd.addParameter("grant_type", "password");
        assertTrue(OAuthErrorExplainer.explainToken(new OAuth2Error("unsupported_grant_type"), odd).contains("'password' is not supported"));
    }
}
