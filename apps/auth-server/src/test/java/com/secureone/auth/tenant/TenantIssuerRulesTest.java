package com.secureone.auth.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.secureone.auth.authn.OAuthLoginRedirectSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class TenantIssuerRulesTest {

    @Test
    void tenantPrefixes() {
        assertTrue(TenantIssuers.hasTenantPrefix("/t/acme/oauth2/token"));
        assertTrue(TenantIssuers.hasTenantPrefix("/t/acme"));
        assertFalse(TenantIssuers.hasTenantPrefix("/oauth2/token"));
        assertFalse(TenantIssuers.hasTenantPrefix("/t/Acme/oauth2/token"), "slugs are lower case");
        assertFalse(TenantIssuers.hasTenantPrefix("/tx/acme/oauth2/token"));
    }

    @Test
    void continueUrlsMayNameATenantIssuersAuthorizeEndpoint() {
        assertTrue(OAuthLoginRedirectSupport.isAuthorizeUrl("/t/acme/oauth2/authorize?client_id=x"));
        assertTrue(OAuthLoginRedirectSupport.isAuthorizeUrl("/oauth2/authorize?client_id=x"));
        assertTrue(OAuthLoginRedirectSupport.isAuthorizeUrl("/saml2/idp/resume?ref=ab"));
        assertFalse(OAuthLoginRedirectSupport.isAuthorizeUrl("/t/acme"));
        assertFalse(OAuthLoginRedirectSupport.isAuthorizeUrl("/t/acme/admin"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertTrue(OAuthLoginRedirectSupport.isSafeContinue(request, "/t/acme/oauth2/authorize?client_id=x"));
        assertFalse(OAuthLoginRedirectSupport.isSafeContinue(request, "//evil.example/t/acme/oauth2/authorize"));
    }

    @Test
    void theContinueUrlKeepsTheIssuerPrefix() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        request.setQueryString("client_id=x");
        assertEquals("/oauth2/authorize?client_id=x", OAuthLoginRedirectSupport.buildAuthorizeUrl(request));
        request.setAttribute(TenantIssuers.ATTRIBUTE,
                new TenantIssuers.Target(java.util.UUID.randomUUID(), "acme", "https://a/t/acme", "/t/acme"));
        assertEquals("/t/acme/oauth2/authorize?client_id=x", OAuthLoginRedirectSupport.buildAuthorizeUrl(request));
    }

    @Test
    void domainNames() {
        assertEquals("login.example.com", TenantDomainService.normalize(" Login.Example.com. "));
        for (String bad : List.of("https://login.example.com", "login.example.com/x", "localhost", "10.0.0.1",
                "login.example.com:8443", "-a.example.com", "a..example.com", "")) {
            assertThrows(IllegalArgumentException.class, () -> TenantDomainService.normalize(bad), bad);
        }
    }
}
