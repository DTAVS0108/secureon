package com.secureone.auth.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformFeaturesTest {

    @Mock PlatformSettingsService settings;

    @Test
    void configAsCodeIsOffUntilTurnedOn() {
        when(settings.get(PlatformFeatures.SETTING_KEY)).thenReturn(new HashMap<>());

        PlatformFeatures features = new PlatformFeatures(settings);

        assertFalse(features.configAsCodeEnabled());
        assertTrue(features.connectAppEnabled());
        assertTrue(features.clientSetupCodeEnabled());
        assertEquals(
                Map.of(
                        PlatformFeatures.CONFIG_AS_CODE, false,
                        PlatformFeatures.CONNECT_APP, true,
                        PlatformFeatures.CLIENT_SETUP_CODE, true),
                features.get());
    }

    @Test
    void savesOnlyKnownSwitches() {
        when(settings.get(PlatformFeatures.SETTING_KEY)).thenReturn(new HashMap<>());

        Map<String, Object> saved = new PlatformFeatures(settings)
                .update(Map.of(PlatformFeatures.CONFIG_AS_CODE, true, "somethingElse", true));

        Map<String, Object> expected = Map.of(
                PlatformFeatures.CONFIG_AS_CODE, true,
                PlatformFeatures.CONNECT_APP, true,
                PlatformFeatures.CLIENT_SETUP_CODE, true);
        assertEquals(expected, saved);
        verify(settings).saveMap(eq(PlatformFeatures.SETTING_KEY), eq(expected));
    }

    @Test
    void connectAppCanBeTurnedOff() {
        when(settings.get(PlatformFeatures.SETTING_KEY))
                .thenReturn(new HashMap<>(Map.of(PlatformFeatures.CONNECT_APP, false)));

        assertFalse(new PlatformFeatures(settings).connectAppEnabled());
    }

    @Test
    void readsTheStoredValue() {
        when(settings.get(PlatformFeatures.SETTING_KEY))
                .thenReturn(new HashMap<>(Map.of(PlatformFeatures.CONFIG_AS_CODE, true)));

        assertTrue(new PlatformFeatures(settings).configAsCodeEnabled());
    }
}
