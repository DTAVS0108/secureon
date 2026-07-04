package com.secureone.auth.scim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.secureone.auth.notify.SecretProtector;
import com.secureone.auth.platform.PlatformSetting;
import com.secureone.auth.platform.PlatformSettingRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScimSettingsServiceTest {

    @Mock
    private PlatformSettingRepository settings;

    @Mock
    private SecretProtector secrets;

    private ScimSettingsService service;

    @BeforeEach
    void setUp() {
        service = new ScimSettingsService(settings, secrets);
    }

    @Test
    void saveEncryptsBearerTokenAndStripsFromResponse() {
        PlatformSetting row = new PlatformSetting();
        row.setKey("scim");
        row.setValue(new HashMap<>(Map.of("enabled", false, "bearerTokenEncrypted", "")));
        when(settings.findById("scim")).thenReturn(Optional.of(row));
        when(secrets.encrypt("secret-token")).thenReturn("enc-token");
        when(secrets.decrypt("enc-token")).thenReturn("secret-token");

        Map<String, Object> saved = service.saveConfig(Map.of("enabled", true, "bearerToken", "secret-token"));

        assertTrue(Boolean.TRUE.equals(saved.get("enabled")));
        assertTrue(Boolean.TRUE.equals(saved.get("bearerTokenConfigured")));
        assertFalse(saved.containsKey("bearerTokenEncrypted"));

        ArgumentCaptor<PlatformSetting> captor = ArgumentCaptor.forClass(PlatformSetting.class);
        verify(settings).save(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> persisted = (Map<String, Object>) captor.getValue().getValue();
        assertEquals("enc-token", persisted.get("bearerTokenEncrypted"));
        assertEquals("secret-token", service.bearerToken());
    }

    @Test
    void enabledReflectsStoredFlag() {
        PlatformSetting row = new PlatformSetting();
        row.setKey("scim");
        row.setValue(Map.of("enabled", true, "bearerTokenEncrypted", ""));
        when(settings.findById("scim")).thenReturn(Optional.of(row));

        assertTrue(service.enabled());
    }
}
