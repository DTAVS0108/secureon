package com.secureone.auth.platform;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VerificationPolicyDefaults {

    private VerificationPolicyDefaults() {}

    public static Map<String, Object> platformDefaults() {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("signup", signupDefaults());
        policy.put("login", loginDefaults());
        policy.put("recovery", recoveryDefaults());
        policy.put("defaultPhoneRegion", com.secureone.auth.account.PhoneNormalizer.PLATFORM_DEFAULT_REGION);
        return policy;
    }

    public static Map<String, Object> sanitizeForSave(Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>(platformDefaults());
        if (body == null) {
            return out;
        }
        mergeSection(out, "signup", body.get("signup"));
        mergeSection(out, "login", body.get("login"));
        mergeSection(out, "recovery", body.get("recovery"));
        // Country for phone numbers typed without +country code (sign-in, sign-up, recovery).
        Object region = body.get("defaultPhoneRegion");
        if (region != null && !region.toString().isBlank()) {
            String normalized = com.secureone.auth.account.PhoneNormalizer.normalizeRegion(region.toString());
            if (normalized == null) {
                throw new IllegalArgumentException(
                        "Unknown phone country code: " + region + " (use an ISO code such as IN or US).");
            }
            out.put("defaultPhoneRegion", normalized);
        }
        clampOtpFields(out, "signup");
        clampOtpFields(out, "login");
        clampOtpFields(out, "recovery");
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void clampOtpFields(Map<String, Object> policy, String sectionKey) {
        Object raw = policy.get(sectionKey);
        if (!(raw instanceof Map<?, ?> section)) {
            return;
        }
        Map<String, Object> clamped = new LinkedHashMap<>((Map<String, Object>) section);
        clamped.put("otpLength", clampInt(clamped.get("otpLength"), 6, 4, 8));
        clamped.put("otpTtlSeconds", clampInt(clamped.get("otpTtlSeconds"), 600, 60, 3600));
        clamped.put("maxAttempts", clampInt(clamped.get("maxAttempts"), 5, 3, 10));
        clamped.put("resendCooldownSeconds", clampInt(clamped.get("resendCooldownSeconds"), 60, 30, 600));
        policy.put(sectionKey, clamped);
    }

    private static int clampInt(Object raw, int defaultValue, int min, int max) {
        int value;
        if (raw instanceof Number n) {
            value = n.intValue();
        } else {
            value = defaultValue;
        }
        return Math.max(min, Math.min(max, value));
    }

    @SuppressWarnings("unchecked")
    private static void mergeSection(Map<String, Object> target, String key, Object incoming) {
        if (!(incoming instanceof Map<?, ?> map)) {
            return;
        }
        Map<String, Object> base = new LinkedHashMap<>((Map<String, Object>) target.get(key));
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                base.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        base.keySet().removeAll(RETIRED_KEYS);
        target.put(key, base);
    }

    /**
     * Settings that no longer exist. {@code mfaRequired}: whether a second step is asked is the Two Factor
     * Authentication policy's mode (since V72).
     */
    private static final java.util.Set<String> RETIRED_KEYS = java.util.Set.of("mfaRequired");

    private static Map<String, Object> signupDefaults() {
        Map<String, Object> signup = new LinkedHashMap<>();
        signup.put("requiredChannels", List.of("email"));
        signup.put("optionalChannels", List.of("phone"));
        signup.put("defaultMode", "link");
        signup.put("allowedModes", List.of("link", "otp"));
        signup.put("allowUserChannelChoice", false);
        signup.put("otpLength", 6);
        signup.put("otpTtlSeconds", 600);
        signup.put("maxAttempts", 5);
        signup.put("resendCooldownSeconds", 60);
        return signup;
    }

    private static Map<String, Object> loginDefaults() {
        Map<String, Object> login = new LinkedHashMap<>();
        login.put("identifierTypes", List.of("email", "phone"));
        login.put("passwordlessModes", List.of("email_otp", "phone_otp", "magic_link"));
        login.put("otpLength", 6);
        login.put("otpTtlSeconds", 600);
        login.put("maxAttempts", 5);
        login.put("resendCooldownSeconds", 60);
        return login;
    }

    private static Map<String, Object> recoveryDefaults() {
        Map<String, Object> recovery = new LinkedHashMap<>();
        recovery.put("passwordResetMode", "link");
        recovery.put("allowedModes", List.of("link", "otp"));
        recovery.put("allowedChannels", List.of("email", "phone"));
        recovery.put("allowUserChannelChoice", true);
        recovery.put("requireVerifiedChannel", true);
        recovery.put("fallbackToLink", true);
        recovery.put("otpLength", 6);
        recovery.put("otpTtlSeconds", 600);
        recovery.put("maxAttempts", 5);
        recovery.put("resendCooldownSeconds", 60);
        return recovery;
    }
}
