package com.secureone.auth.admin.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ClientAuthMethodTest {

    @Test
    void confidentialClientsAuthenticateAndPublicClientsDoNot() {
        assertEquals("private_key_jwt", OAuthClientAdminService.authMethod("m2m", " Private_Key_JWT "));
        assertEquals("client_secret_post", OAuthClientAdminService.authMethod("web", "client_secret_post"));
        assertEquals("none", OAuthClientAdminService.authMethod("spa", "none"));
        assertThrows(IllegalArgumentException.class, () -> OAuthClientAdminService.authMethod("web", "none"));
        assertThrows(IllegalArgumentException.class, () -> OAuthClientAdminService.authMethod("native", "private_key_jwt"));
        assertThrows(IllegalArgumentException.class, () -> OAuthClientAdminService.authMethod("web", "client_secret_jwt"));
    }

    @Test
    void jwksUrlsAreHttps() {
        assertEquals("https://app.example.com/jwks.json", OAuthClientAdminService.jwksUri("https://app.example.com/jwks.json"));
        assertEquals("http://localhost:8080/jwks", OAuthClientAdminService.jwksUri("http://localhost:8080/jwks"));
        assertNull(OAuthClientAdminService.jwksUri(" "));
        assertThrows(IllegalArgumentException.class, () -> OAuthClientAdminService.jwksUri("http://app.example.com/jwks"));
        assertThrows(IllegalArgumentException.class, () -> OAuthClientAdminService.jwksUri("file:///etc/passwd"));
    }
}
