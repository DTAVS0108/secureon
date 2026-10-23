package com.secureone.auth.mfa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MfaPolicyDefaultsTest {

    private final Map<String, Object> platform = MfaPolicyDefaults.sanitizeForSave(Map.of());

    @Test
    void defaultsAreOptionalStrongWithPasskeysFirst() {
        assertEquals("optional", platform.get("mode"));
        assertEquals("strong", platform.get("minimumStrength"));
        assertEquals(List.of("passkey", "security_key", "totp"), platform.get("methods"));
        assertEquals("passkey", platform.get("defaultMethod"));
        assertEquals(14, platform.get("graceDays"));
    }

    @Test
    void invalidValuesAreReplacedAndClamped() {
        Map<String, Object> p = MfaPolicyDefaults.sanitizeForSave(Map.of(
                "mode", "sometimes", "methods", List.of("email", "fax", "totp"), "graceDays", 999,
                "defaultMethod", "passkey", "enforceFrom", "2026-11-01T10:00:00Z"));
        assertEquals("optional", p.get("mode"));
        assertEquals(List.of("totp", "email"), p.get("methods")); // catalog order, unknown dropped
        assertEquals("totp", p.get("defaultMethod")); // passkey not offered
        assertEquals(180, p.get("graceDays"));
        assertEquals("2026-11-01", p.get("enforceFrom"));
    }

    @Test
    void openSettingsTakeTheLowerLevelValue() {
        Map<String, Object> out = MfaPolicyDefaults.applyLayer(platform, Map.of(), Map.of("mode", "off"));
        assertEquals("off", out.get("mode"));
    }

    @Test
    void lockedSettingsIgnoreTheLowerLevel() {
        Map<String, Object> out = MfaPolicyDefaults.applyLayer(
                platform, Map.of("mode", "locked"), Map.of("mode", "required"));
        assertEquals("optional", out.get("mode"));
    }

    @Test
    void stricterOnlySettingsAcceptStricterAndRefuseWeaker() {
        Map<String, String> locks = Map.of("mode", "stricter", "rememberDeviceDays", "stricter", "methods", "stricter",
                "trustIdpMfa", "stricter");
        Map<String, Object> base = MfaPolicyDefaults.sanitizeForSave(Map.of("rememberDeviceDays", 30));
        Map<String, Object> out = MfaPolicyDefaults.applyLayer(base, locks, Map.of(
                "mode", "required", "rememberDeviceDays", 60, "methods", List.of("passkey"), "trustIdpMfa", true));
        assertEquals("required", out.get("mode"));          // stricter: accepted
        assertEquals(30, out.get("rememberDeviceDays"));    // longer is weaker: refused
        assertEquals(List.of("passkey"), out.get("methods")); // fewer methods: accepted
        assertEquals(false, out.get("trustIdpMfa"));          // trusting more is weaker: refused
    }

    @Test
    void violationsExplainWhatIsRefused() {
        List<String> v = MfaPolicyDefaults.violations(platform, Map.of("mode", "stricter", "graceDays", "locked"),
                Map.of("mode", "off", "graceDays", 30, "promptIntervalDays", 3));
        assertEquals(2, v.size());
        assertTrue(v.stream().anyMatch(s -> s.contains("mode can only be made stricter")));
        assertTrue(v.stream().anyMatch(s -> s.contains("graceDays is set by the level above")));
    }

    @Test
    void combinedLocksKeepTheTighterMode() {
        Map<String, String> combined = MfaPolicyDefaults.combineLocks(
                Map.of("mode", "stricter", "methods", "locked"), Map.of("mode", "locked", "graceDays", "stricter"));
        assertEquals("locked", combined.get("mode"));
        assertEquals("locked", combined.get("methods"));
        assertEquals("stricter", combined.get("graceDays"));
    }

    @Test
    void platformDefaultsIncludeRememberDeviceAndPasskeyFlags() {
        Map<String, Object> defaults = MfaPolicyDefaults.platformDefaults();
        assertEquals(0, defaults.get("rememberDeviceDays"));
        assertEquals(false, defaults.get("requirePasskeyWhenEnrolled"));
    }

    @Test
    void mergePrefersNonNullOverrides() {
        Map<String, Object> merged = MfaPolicyDefaults.merge(
                MfaPolicyDefaults.platformDefaults(),
                Map.of("rememberDeviceDays", 14, "requirePasskeyWhenEnrolled", true));
        assertEquals(14, merged.get("rememberDeviceDays"));
        assertEquals(true, merged.get("requirePasskeyWhenEnrolled"));
        assertEquals(true, merged.get("stepUpAfterPassword"));
    }

    @Test
    void sanitizeClampsNumericBounds() {
        Map<String, Object> sanitized = MfaPolicyDefaults.sanitizeForSave(Map.of(
                "rememberDeviceDays", 999,
                "maxChallengeAttempts", 0,
                "recoveryCodesPerEnrollment", 100));
        assertEquals(90, sanitized.get("rememberDeviceDays"));
        assertEquals(1, sanitized.get("maxChallengeAttempts"));
        assertEquals(20, sanitized.get("recoveryCodesPerEnrollment"));
    }

    @Test
    void overrideKeepsOnlyKeysSent() {
        Map<String, Object> o = MfaPolicyDefaults.sanitizeOverride(Map.of("mode", "required", "graceDays", 7));
        assertEquals(Map.of("mode", "required", "graceDays", 7), o);
    }

    // ---- What waits for a second admin ---------------------------------------------------------------------

    @Test
    void tighteningIsNotAWeakening() {
        Map<String, Object> stricter = new java.util.HashMap<>(platform);
        stricter.put("mode", "required");
        stricter.put("graceDays", 3);
        stricter.put("methods", List.of("passkey", "totp"));

        assertTrue(MfaPolicyDefaults.weakenings(platform, stricter).isEmpty());
        assertTrue(MfaPolicyDefaults.weakenings(platform, platform).isEmpty(), "nothing changed");
    }

    @Test
    void weakerValuesAreListed() {
        Map<String, Object> weaker = new java.util.HashMap<>(platform);
        weaker.put("mode", "off");
        weaker.put("minimumStrength", "any");
        weaker.put("methods", List.of("passkey", "security_key", "totp", "sms"));
        weaker.put("rememberDeviceDays", 30);
        weaker.put("trustIdpMfa", true);

        List<String> found = MfaPolicyDefaults.weakenings(platform, weaker);
        assertEquals(5, found.size(), String.valueOf(found));
        assertTrue(found.contains("mode: optional → off"));
    }

    @Test
    void removingOrLooseningALockIsAWeakening() {
        Map<String, Object> locked = new java.util.HashMap<>(platform);
        locked.put(MfaPolicyDefaults.LOCKS, Map.of("mode", "locked", "methods", "stricter"));
        Map<String, Object> loosened = new java.util.HashMap<>(platform);
        loosened.put(MfaPolicyDefaults.LOCKS, Map.of("mode", "stricter"));

        List<String> found = MfaPolicyDefaults.weakenings(locked, loosened);
        assertEquals(List.of("lock on mode: locked → stricter", "lock on methods: stricter → open"),
                found.stream().sorted(java.util.Comparator.comparing((String s) -> !s.contains("mode"))).toList());
    }

    @Test
    void onlyTheKeysBeingChangedAreCompared() {
        assertTrue(MfaPolicyDefaults.weakenings(platform, Map.of("graceDays", 7)).isEmpty());
        assertEquals(1, MfaPolicyDefaults.weakenings(platform, Map.of("graceDays", 30)).size());
    }
}
