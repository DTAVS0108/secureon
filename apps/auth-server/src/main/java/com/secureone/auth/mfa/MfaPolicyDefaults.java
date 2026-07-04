package com.secureone.auth.mfa;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MfaPolicyDefaults {

    private MfaPolicyDefaults() {}

    public static Map<String, Object> platformDefaults() {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("stepUpAfterPassword", true);
        policy.put("recoveryCodesPerEnrollment", 10);
        policy.put("challengeTtlSeconds", 300);
        policy.put("maxChallengeAttempts", 5);
        policy.put("rememberDeviceDays", 0);
        policy.put("requirePasskeyWhenEnrolled", false);
        return policy;
    }

    public static Map<String, Object> merge(Map<String, Object> platform, Map<String, Object> override) {
        Map<String, Object> out = new LinkedHashMap<>(platformDefaults());
        if (platform != null) {
            out.putAll(platform);
        }
        if (override != null) {
            override.forEach((k, v) -> {
                if (v != null) {
                    out.put(k, v);
                }
            });
        }
        return out;
    }

    public static Map<String, Object> sanitizeForSave(Map<String, Object> body) {
        Map<String, Object> defaults = platformDefaults();
        Map<String, Object> out = new LinkedHashMap<>();
        copyBoolean(body, out, "stepUpAfterPassword", defaults);
        copyBoolean(body, out, "requirePasskeyWhenEnrolled", defaults);
        copyInt(body, out, "recoveryCodesPerEnrollment", defaults, 1, 20);
        copyInt(body, out, "challengeTtlSeconds", defaults, 60, 900);
        copyInt(body, out, "maxChallengeAttempts", defaults, 1, 10);
        copyInt(body, out, "rememberDeviceDays", defaults, 0, 90);
        return out;
    }

    private static void copyBoolean(
            Map<String, Object> body, Map<String, Object> out, String key, Map<String, Object> defaults) {
        if (body.containsKey(key)) {
            out.put(key, Boolean.TRUE.equals(body.get(key)));
        } else {
            out.put(key, defaults.get(key));
        }
    }

    private static void copyInt(
            Map<String, Object> body,
            Map<String, Object> out,
            String key,
            Map<String, Object> defaults,
            int min,
            int max) {
        Object raw = body.containsKey(key) ? body.get(key) : defaults.get(key);
        int value = raw instanceof Number n ? n.intValue() : min;
        out.put(key, Math.max(min, Math.min(max, value)));
    }
}
