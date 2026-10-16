package com.secureone.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.secureone.auth.oauth.OAuthClient;
import com.secureone.auth.oauth.OAuthClientRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegisteredClientOriginsTest {

    @Test
    void originIsSchemeHostAndNonDefaultPort() {
        assertEquals("https://shop.example.com", RegisteredClientOrigins.originOf("https://Shop.example.com/callback?x=1"));
        assertEquals("https://shop.example.com", RegisteredClientOrigins.originOf("https://shop.example.com:443/cb"));
        assertEquals("http://localhost:5173", RegisteredClientOrigins.originOf("http://localhost:5173/"));
        assertNull(RegisteredClientOrigins.originOf("com.example.app:/oauthredirect"), "app schemes are not web origins");
        assertNull(RegisteredClientOrigins.originOf("not a uri"));
    }

    @Test
    void onlyActiveUserFacingClientsCount() {
        OAuthClient spa = client("spa", "ACTIVE", "https://shop.example.com/callback");
        OAuthClient disabled = client("spa", "DISABLED", "https://old.example.com/callback");
        OAuthClient service = client("m2m", "ACTIVE", "https://service.example.com/cb");
        OAuthClientRepository repository = mock(OAuthClientRepository.class);
        when(repository.findAll()).thenReturn(List.of(spa, disabled, service));
        RegisteredClientOrigins origins = new RegisteredClientOrigins(repository);

        assertTrue(origins.isRegistered("https://shop.example.com"));
        assertFalse(origins.isRegistered("https://old.example.com"));
        assertFalse(origins.isRegistered("https://service.example.com"));
        assertFalse(origins.isRegistered("https://evil.example.com"));
        assertFalse(origins.isRegistered(null));
    }

    private static OAuthClient client(String type, String status, String redirect) {
        OAuthClient c = new OAuthClient();
        c.setType(type);
        c.setStatus(status);
        c.setRedirectUris(List.of(redirect));
        return c;
    }
}
