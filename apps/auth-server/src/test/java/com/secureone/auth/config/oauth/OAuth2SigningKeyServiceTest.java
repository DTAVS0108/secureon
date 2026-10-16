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
import com.secureone.auth.notify.SecretProtector;
import com.secureone.auth.platform.ClusterInitLock;
import com.secureone.auth.platform.PlatformSettingsService;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OAuth2SigningKeyServiceTest {

    /** One protector for all services in a test, keyed from the environment (as in production). */
    private static final SecretProtector SECRETS = new SecretProtector(
            mock(PlatformSettingsService.class),
            "base64:" + java.util.Base64.getEncoder().encodeToString(new byte[32]));

    /** Single replica: the lock simply reads, then creates. */
    @SuppressWarnings("unchecked")
    private static ClusterInitLock inlineLock() {
        ClusterInitLock lock = mock(ClusterInitLock.class);
        when(lock.readOrCreate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> {
                    Object existing = ((java.util.function.Supplier<Object>) inv.getArgument(1)).get();
                    return existing != null ? existing : ((java.util.function.Supplier<Object>) inv.getArgument(2)).get();
                });
        return lock;
    }

    @Test
    void storesThePrivateKeyOnlyEncrypted() {
        PlatformSettingsService settings = mock(PlatformSettingsService.class);
        when(settings.get("oauth2_signing_jwk")).thenReturn(new HashMap<>());
        new OAuth2SigningKeyService(settings, SECRETS, inlineLock(), devEnv(), "", "").loadOrCreateSigningKey();
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(settings).saveMap(eq("oauth2_signing_jwk"), captor.capture());
        assertEquals(false, captor.getValue().containsKey("privateKeyPem"));
        assertEquals(false, String.valueOf(captor.getValue().get("privateKeyEncrypted")).contains("PRIVATE KEY"));
    }

    @Test
    void legacyPlaintextKeyIsKeptAndEncrypted() throws Exception {
        PlatformSettingsService settings = mock(PlatformSettingsService.class);
        when(settings.get("oauth2_signing_jwk")).thenReturn(new HashMap<>());
        OAuth2SigningKeyService first = new OAuth2SigningKeyService(settings, SECRETS, inlineLock(), devEnv(), "", "");
        RSAKey original = first.loadOrCreateSigningKey();
        Map<String, Object> legacy = new HashMap<>();
        legacy.put("kid", original.getKeyID());
        legacy.put("privateKeyPem", "-----BEGIN PRIVATE KEY-----\n"
                + java.util.Base64.getMimeEncoder().encodeToString(original.toRSAPrivateKey().getEncoded())
                + "\n-----END PRIVATE KEY-----");
        PlatformSettingsService old = mock(PlatformSettingsService.class);
        when(old.get("oauth2_signing_jwk")).thenReturn(legacy);
        RSAKey reloaded = new OAuth2SigningKeyService(old, SECRETS, inlineLock(), devEnv(), "", "").loadOrCreateSigningKey();
        assertEquals(original.getKeyID(), reloaded.getKeyID());
        assertEquals(original.toRSAPublicKey(), reloaded.toRSAPublicKey());
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(old).saveMap(eq("oauth2_signing_jwk"), captor.capture());
        assertEquals(false, captor.getValue().containsKey("privateKeyPem"));
    }

    @Test
    void generatesAndCachesKeyInDevelopment() {
        PlatformSettingsService settings = mock(PlatformSettingsService.class);
        when(settings.get("oauth2_signing_jwk")).thenReturn(new HashMap<>());

        OAuth2SigningKeyService service = new OAuth2SigningKeyService(settings, SECRETS, inlineLock(), devEnv(), "", "");
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
        OAuth2SigningKeyService bootstrap = new OAuth2SigningKeyService(settings, SECRETS, inlineLock(), devEnv(), "", "");
        RSAKey created = bootstrap.loadOrCreateSigningKey();

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(settings).saveMap(eq("oauth2_signing_jwk"), captor.capture());
        when(settings.get("oauth2_signing_jwk")).thenReturn(captor.getValue());

        OAuth2SigningKeyService reloaded = new OAuth2SigningKeyService(settings, SECRETS, inlineLock(), devEnv(), "", "");
        assertEquals(created.getKeyID(), reloaded.loadOrCreateSigningKey().getKeyID());
    }

    @Test
    void rotationPublishesThePreviousKeyButSignsWithTheNewOne() throws Exception {
        PlatformSettingsService settings = mock(PlatformSettingsService.class);
        when(settings.get("oauth2_signing_jwk")).thenReturn(new HashMap<>());
        RSAKey oldKey = new OAuth2SigningKeyService(settings, SECRETS, inlineLock(), devEnv(), "", "").loadOrCreateSigningKey();
        RSAKey newKey = new OAuth2SigningKeyService(mock(PlatformSettingsService.class), SECRETS, inlineLock(), devEnv(), "", "")
                .loadOrCreateSigningKey();
        String oldPem = pem(oldKey);
        String newPem = pem(newKey);

        OAuth2SigningKeyService rotated = new OAuth2SigningKeyService(settings, SECRETS, inlineLock(), devEnv(), newPem, oldPem);
        java.util.List<RSAKey> published = rotated.verificationKeys();
        assertEquals(2, published.size());
        assertEquals(true, published.get(0).isPrivate(), "current key signs");
        assertEquals(false, published.get(1).isPrivate(), "previous key is published public-only");
        assertEquals(oldKey.toRSAPublicKey(), published.get(1).toRSAPublicKey());
        // The previous key keeps the kid it had when it was current (derived from its PEM).
        RSAKey asCurrent = new OAuth2SigningKeyService(settings, SECRETS, inlineLock(), devEnv(), oldPem, "").loadOrCreateSigningKey();
        assertEquals(asCurrent.getKeyID(), published.get(1).getKeyID());

        var selected = rotated.signingJwkSource().get(
                new com.nimbusds.jose.jwk.JWKSelector(new com.nimbusds.jose.jwk.JWKMatcher.Builder().build()), null);
        assertEquals(1, selected.size(), "the encoder sees only the current key");
    }

    private static String pem(RSAKey key) throws Exception {
        return "-----BEGIN PRIVATE KEY-----\n"
                + java.util.Base64.getMimeEncoder().encodeToString(key.toRSAPrivateKey().getEncoded())
                + "\n-----END PRIVATE KEY-----";
    }

    @Test
    void productionRequiresConfiguredKey() {
        PlatformSettingsService settings = mock(PlatformSettingsService.class);
        when(settings.get("oauth2_signing_jwk")).thenReturn(new HashMap<>());

        OAuth2SigningKeyService service = new OAuth2SigningKeyService(settings, SECRETS, inlineLock(), prodEnv(), "", "");
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
