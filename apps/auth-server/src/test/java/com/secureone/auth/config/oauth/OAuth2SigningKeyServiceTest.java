package com.secureone.auth.config.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.RSAKey;
import com.secureone.auth.config.SecureOneEnvironmentProperties;
import com.secureone.auth.platform.PlatformSettingsService;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OAuth2SigningKeyServiceTest {

    @Test
    void generatesAndCachesKeyInDevelopment() {
        PlatformSettingsService settings = mock(PlatformSettingsService.class);
        when(settings.get("oauth2_signing_jwk")).thenReturn(new HashMap<>());

        OAuth2SigningKeyService service = new OAuth2SigningKeyService(settings, devEnv(), "");
        RSAKey first = service.loadOrCreateSigningKey();
        RSAKey second = service.loadOrCreateSigningKey();

        assertNotNull(first.getKeyID());
        assertEquals(first.getKeyID(), second.getKeyID());
        verify(settings).saveMap(eq("oauth2_signing_jwk"), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void reloadsPersistedKey() {
        PlatformSettingsService settings = mock(PlatformSettingsService.class);
        when(settings.get("oauth2_signing_jwk")).thenReturn(new HashMap<>());
        OAuth2SigningKeyService bootstrap = new OAuth2SigningKeyService(settings, devEnv(), "");
        RSAKey created = bootstrap.loadOrCreateSigningKey();

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(settings).saveMap(eq("oauth2_signing_jwk"), captor.capture());
        when(settings.get("oauth2_signing_jwk")).thenReturn(captor.getValue());

        OAuth2SigningKeyService reloaded = new OAuth2SigningKeyService(settings, devEnv(), "");
        assertEquals(created.getKeyID(), reloaded.loadOrCreateSigningKey().getKeyID());
    }

    @Test
    void productionRequiresConfiguredKey() {
        PlatformSettingsService settings = mock(PlatformSettingsService.class);
        when(settings.get("oauth2_signing_jwk")).thenReturn(new HashMap<>());

        OAuth2SigningKeyService service = new OAuth2SigningKeyService(settings, prodEnv(), "");
        assertThrows(IllegalStateException.class, service::loadOrCreateSigningKey);
    }

    private static SecureOneEnvironmentProperties devEnv() {
        SecureOneEnvironmentProperties env = new SecureOneEnvironmentProperties();
        env.setEnvironment("development");
        return env;
    }

    private static SecureOneEnvironmentProperties prodEnv() {
        SecureOneEnvironmentProperties env = new SecureOneEnvironmentProperties();
        env.setEnvironment("production");
        return env;
    }
}
