package com.secureone.auth.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VerificationPolicyDefaultsTest {

    @Test
    void clampsOtpNumericFieldsOnSave() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(
                "signup",
                Map.of(
                        "otpLength", 999,
                        "otpTtlSeconds", 10,
                        "maxAttempts", 1,
                        "resendCooldownSeconds", 5));
        body.put(
                "login",
                Map.of(
                        "otpLength", 2,
                        "otpTtlSeconds", 9999,
                        "maxAttempts", 99,
                        "resendCooldownSeconds", 999));
        body.put(
                "recovery",
                Map.of(
                        "otpLength", 4,
                        "otpTtlSeconds", 3600,
                        "maxAttempts", 10,
                        "resendCooldownSeconds", 30));

        Map<String, Object> saved = VerificationPolicyDefaults.sanitizeForSave(body);

        @SuppressWarnings("unchecked")
        Map<String, Object> signup = (Map<String, Object>) saved.get("signup");
        assertEquals(8, signup.get("otpLength"));
        assertEquals(60, signup.get("otpTtlSeconds"));
        assertEquals(3, signup.get("maxAttempts"));
        assertEquals(30, signup.get("resendCooldownSeconds"));

        @SuppressWarnings("unchecked")
        Map<String, Object> login = (Map<String, Object>) saved.get("login");
        assertEquals(4, login.get("otpLength"));
        assertEquals(3600, login.get("otpTtlSeconds"));
        assertEquals(10, login.get("maxAttempts"));
        assertEquals(600, login.get("resendCooldownSeconds"));

        @SuppressWarnings("unchecked")
        Map<String, Object> recovery = (Map<String, Object>) saved.get("recovery");
        assertEquals(4, recovery.get("otpLength"));
        assertEquals(3600, recovery.get("otpTtlSeconds"));
        assertEquals(10, recovery.get("maxAttempts"));
        assertEquals(30, recovery.get("resendCooldownSeconds"));
    }

    @Test
    void clampsStoredPolicyOnReadPath() {
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("signup", Map.of("otpLength", 100));
        stored.put("login", loginSection());
        stored.put("recovery", recoverySection());

        Map<String, Object> sanitized = VerificationPolicyDefaults.sanitizeForSave(stored);

        @SuppressWarnings("unchecked")
        Map<String, Object> signup = (Map<String, Object>) sanitized.get("signup");
        assertEquals(8, signup.get("otpLength"));
    }

    private static Map<String, Object> loginSection() {
        return Map.of(
                "identifierTypes", java.util.List.of("email"),
                "passwordlessModes", java.util.List.of("email_otp"),
                "mfaRequired", "optional",
                "otpLength", 6,
                "otpTtlSeconds", 600,
                "maxAttempts", 5,
                "resendCooldownSeconds", 60);
    }

    private static Map<String, Object> recoverySection() {
        return Map.of(
                "passwordResetMode", "link",
                "allowedModes", java.util.List.of("link"),
                "allowedChannels", java.util.List.of("email"),
                "allowUserChannelChoice", true,
                "requireVerifiedChannel", true,
                "fallbackToLink", true,
                "otpLength", 6,
                "otpTtlSeconds", 600,
                "maxAttempts", 5,
                "resendCooldownSeconds", 60);
    }
}
