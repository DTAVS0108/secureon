package com.secureone.auth.mfa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MfaPolicyDefaultsTest {

    @Test
    void platformDefaultsIncludeRememberDeviceAndPasskeyFlags() {
        Map<String, Object> defaults = MfaPolicyDefaults.platformDefaults();
        assertEquals(0, defaults.get("rememberDeviceDays"));
        assertFalse((Boolean) defaults.get("requirePasskeyWhenEnrolled"));
    }

    @Test
    void mergePrefersNonNullOverrides() {
        Map<String, Object> merged = MfaPolicyDefaults.merge(
                MfaPolicyDefaults.platformDefaults(),
                Map.of("rememberDeviceDays", 14, "requirePasskeyWhenEnrolled", true));
        assertEquals(14, merged.get("rememberDeviceDays"));
        assertTrue((Boolean) merged.get("requirePasskeyWhenEnrolled"));
        assertTrue((Boolean) merged.get("stepUpAfterPassword"));
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
}
