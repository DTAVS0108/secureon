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
        assertTrue(TenantIssuers.hasTenantPrefix("/t/tenant-a/oauth2/token"));
        assertTrue(TenantIssuers.hasTenantPrefix("/t/tenant-a"));
        assertFalse(TenantIssuers.hasTenantPrefix("/oauth2/token"));
        assertFalse(TenantIssuers.hasTenantPrefix("/t/Tenant-A/oauth2/token"), "slugs are lower case");
        assertFalse(TenantIssuers.hasTenantPrefix("/tx/tenant-a/oauth2/token"));
    }

    @Test
    void continueUrlsMayNameATenantIssuersAuthorizeEndpoint() {
        assertTrue(OAuthLoginRedirectSupport.isAuthorizeUrl("/t/tenant-a/oauth2/authorize?client_id=x"));
        assertTrue(OAuthLoginRedirectSupport.isAuthorizeUrl("/oauth2/authorize?client_id=x"));
        assertTrue(OAuthLoginRedirectSupport.isAuthorizeUrl("/saml2/idp/resume?ref=ab"));
        assertFalse(OAuthLoginRedirectSupport.isAuthorizeUrl("/t/tenant-a"));
        assertFalse(OAuthLoginRedirectSupport.isAuthorizeUrl("/t/tenant-a/admin"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertTrue(OAuthLoginRedirectSupport.isSafeContinue(request, "/t/tenant-a/oauth2/authorize?client_id=x"));
        assertFalse(OAuthLoginRedirectSupport.isSafeContinue(request, "//evil.example/t/tenant-a/oauth2/authorize"));
    }

    @Test
    void theContinueUrlKeepsTheIssuerPrefix() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        request.setQueryString("client_id=x");
        assertEquals("/oauth2/authorize?client_id=x", OAuthLoginRedirectSupport.buildAuthorizeUrl(request));
        request.setAttribute(TenantIssuers.ATTRIBUTE,
                new TenantIssuers.Target(java.util.UUID.randomUUID(), "tenant-a", "https://a/t/tenant-a", "/t/tenant-a"));
        assertEquals("/t/tenant-a/oauth2/authorize?client_id=x", OAuthLoginRedirectSupport.buildAuthorizeUrl(request));
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
