package com.secureone.auth.federation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FederationPresetsTest {

    @Test
    void googleDefaultsIncludeOidcEndpoints() {
        Map<String, Object> defaults = FederationPresets.defaultsFor(FederationProviderType.GOOGLE);
        assertTrue(defaults.get("authorizationUri").toString().contains("google.com"));
        assertTrue(defaults.get("tokenUri").toString().contains("googleapis.com"));
        assertTrue(defaults.get("scopes").toString().contains("openid"));
        assertEquals("m_google", FederationPresets.authMethodId(FederationProviderType.GOOGLE));
    }

    @Test
    void githubDefaultsIncludeEmailEndpoint() {
        Map<String, Object> defaults = FederationPresets.defaultsFor(FederationProviderType.GITHUB);
        assertTrue(defaults.get("authorizationUri").toString().contains("github.com"));
        assertTrue(defaults.get("emailUri").toString().contains("user/emails"));
        assertEquals("m_github", FederationPresets.authMethodId(FederationProviderType.GITHUB));
    }

    @Test
    void oidcDefaultsStartEmptyUntilConfigured() {
        Map<String, Object> defaults = FederationPresets.defaultsFor(FederationProviderType.OIDC);
        assertEquals("", defaults.get("authorizationUri"));
        assertEquals("m_oidc", FederationPresets.authMethodId(FederationProviderType.OIDC));
        assertEquals("m_saml", FederationPresets.authMethodId(FederationProviderType.SAML));
    }
}
