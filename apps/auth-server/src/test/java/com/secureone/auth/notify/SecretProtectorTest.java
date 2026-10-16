package com.secureone.auth.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.secureone.auth.platform.PlatformSettingsService;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SecretProtectorTest {

    /** Platform settings held in memory, as the database would. */
    private static PlatformSettingsService settings() {
        Map<String, Map<String, Object>> store = new HashMap<>();
        PlatformSettingsService settings = mock(PlatformSettingsService.class);
        when(settings.get(any())).thenAnswer(inv -> new HashMap<>(store.getOrDefault(inv.getArgument(0), Map.of())));
        doAnswer(inv -> {
            store.put(inv.getArgument(0), new HashMap<>(inv.getArgument(1)));
            return null;
        }).when(settings).saveMap(any(), any());
        return settings;
    }

    private static String newKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return "base64:" + Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void environmentKeyReadsOldValuesAndProtectsNewOnes() {
        PlatformSettingsService db = settings();
        SecretProtector before = new SecretProtector(db, "");
        String old = before.encrypt("smtp-password");

        SecretProtector after = new SecretProtector(db, newKey());
        assertEquals("smtp-password", after.decrypt(old), "values written under the database key still decrypt");
        String fresh = after.encrypt("signing-key");
        assertEquals("signing-key", after.decrypt(fresh));
        // A database copy alone (database key only) cannot read what the environment key protects.
        assertThrows(IllegalStateException.class, () -> before.decrypt(fresh));
    }

    @Test
    void invalidEnvironmentKeyFailsFast() {
        PlatformSettingsService db = settings();
        assertThrows(IllegalStateException.class, () -> new SecretProtector(db, "base64:replace-with-32-byte-key"));
        assertThrows(IllegalStateException.class, () -> new SecretProtector(db, "base64:" + Base64.getEncoder().encodeToString(new byte[16])));
    }

    @Test
    void blankKeepsDevelopmentBehaviour() {
        SecretProtector protector = new SecretProtector(settings(), "  ");
        assertEquals("x", protector.decrypt(protector.encrypt("x")));
        assertEquals(false, protector.usesEnvironmentKey());
        assertEquals("", protector.encrypt(""));
    }
}
