package com.secureone.auth.oauth.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientRegistrationPolicyTest {

    private final ClientRegistrationService service = new ClientRegistrationService(null, null, null, null, null);

    private static ClientRegistrationService.Policy policy(List<String> grants, List<String> hosts) {
        return new ClientRegistrationService.Policy(UUID.randomUUID(), UUID.randomUUID(), "token",
                List.of("openid", "profile", "orders:read"), grants, hosts, 10, 30);
    }

    private String error(Map<String, Object> metadata, ClientRegistrationService.Policy policy) {
        return assertThrows(ClientRegistrationService.RegistrationException.class, () -> service.validate(metadata, policy)).error();
    }

    @Test
    void aWebAppWithDefaults() {
        var r = service.validate(Map.of("redirect_uris", List.of("https://tool.example.com/cb"), "client_name", "InvoicePro"),
                policy(List.of("authorization_code", "refresh_token"), List.of()));
        assertEquals("web", r.type());
        assertEquals("client_secret_basic", r.authMethod());
        assertEquals(List.of("openid", "profile", "orders:read"), r.scopes(), "no scope asked: all allowed scopes");
        assertEquals("InvoicePro", r.echo().get("client_name"));
        assertEquals(List.of("code"), r.echo().get("response_types"));
    }

    @Test
    void publicAndMachineClients() {
        var spa = service.validate(Map.of("redirect_uris", List.of("https://app.example.com/cb"), "token_endpoint_auth_method", "none"),
                policy(List.of("authorization_code"), List.of()));
        assertEquals("spa", spa.type());
        var nativeApp = service.validate(Map.of("redirect_uris", List.of("com.example.tool:/cb"), "token_endpoint_auth_method", "none",
                "application_type", "native"), policy(List.of("authorization_code"), List.of()));
        assertEquals("native", nativeApp.type());
        var m2m = service.validate(Map.of("grant_types", List.of("client_credentials"), "scope", "orders:read"),
                policy(List.of("client_credentials"), List.of()));
        assertEquals("m2m", m2m.type());
        assertEquals(List.of(), m2m.redirectUris());
    }

    @Test
    void policiesAreEnforced() {
        var codeOnly = policy(List.of("authorization_code"), List.of("*.example.com", "tool.test"));
        assertEquals("invalid_client_metadata", error(Map.of("grant_types", List.of("client_credentials")), codeOnly));
        assertEquals("invalid_client_metadata", error(Map.of("redirect_uris", List.of("https://a.example.com/cb"), "scope", "admin"), codeOnly));
        assertEquals("invalid_redirect_uri", error(Map.of("redirect_uris", List.of("https://evil.example.net/cb")), codeOnly));
        assertEquals("invalid_redirect_uri", error(Map.of("grant_types", List.of("authorization_code")), codeOnly));
        assertEquals("invalid_redirect_uri", error(Map.of("redirect_uris", List.of("http://a.example.com/cb")), codeOnly), "plain http");
        assertEquals("invalid_client_metadata", error(Map.of("redirect_uris", List.of("https://a.example.com/cb"),
                "response_types", List.of("token")), codeOnly), "implicit flow");
        assertEquals("invalid_client_metadata", error(Map.of("redirect_uris", List.of("https://a.example.com/cb"),
                "token_endpoint_auth_method", "client_secret_jwt"), codeOnly));
        assertEquals("invalid_client_metadata", error(Map.of("redirect_uris", List.of("https://a.example.com/cb"),
                "token_endpoint_auth_method", "private_key_jwt"), codeOnly), "no keys");
        assertEquals("invalid_client_metadata", error(Map.of("redirect_uris", List.of("https://a.example.com/cb"),
                "logo_uri", "http://a.example.com/logo.png"), codeOnly));
        assertEquals("invalid_client_metadata", error(Map.of("redirect_uris", "https://a.example.com/cb"), codeOnly), "not an array");
    }

    @Test
    void trustedHosts() {
        assertTrue(ClientRegistrationService.trusted("https://a.b.example.com/cb", List.of("*.example.com")));
        assertTrue(ClientRegistrationService.trusted("https://tool.test/cb", List.of("tool.test")));
        assertFalse(ClientRegistrationService.trusted("https://example.com.evil.net/cb", List.of("*.example.com")));
        assertFalse(ClientRegistrationService.trusted("https://notexample.com/cb", List.of("*.example.com")));
        assertFalse(ClientRegistrationService.trusted("com.example.app:/cb", List.of("example.com")), "app schemes have no host");
    }
}
