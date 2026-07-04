package com.secureone.auth.application;

import java.util.LinkedHashMap;
import java.util.Map;

/** OAuth2 / OIDC token lifetimes and refresh behavior (seconds unless noted). */
public final class TokenPolicyDefaults {

    private TokenPolicyDefaults() {}

    public static Map<String, Object> platformDefaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("accessTokenTtlSeconds", 3600);
        defaults.put("refreshTokenTtlSeconds", 604_800);
        defaults.put("authorizationCodeTtlSeconds", 300);
        defaults.put("idTokenTtlSeconds", 3600);
        defaults.put("clientCredentialsTtlSeconds", 3600);
        defaults.put("deviceCodeTtlSeconds", 600);
        defaults.put("refreshTokensEnabled", true);
        defaults.put("reuseRefreshTokens", false);
        defaults.put("rotateRefreshTokens", true);
        defaults.put("refreshTokenReuseDetection", true);
        return defaults;
    }

    /** Shorter-lived access tokens for production deployments. */
    public static Map<String, Object> productionDefaults() {
        Map<String, Object> defaults = platformDefaults();
        defaults.put("accessTokenTtlSeconds", 900);
        defaults.put("idTokenTtlSeconds", 900);
        defaults.put("clientCredentialsTtlSeconds", 900);
        return defaults;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> merge(Map<String, Object> platform, Map<String, Object> appOverride) {
        Map<String, Object> merged = new LinkedHashMap<>(platform != null ? platform : platformDefaults());
        if (appOverride != null) {
            appOverride.forEach((k, v) -> {
                if (v != null) {
                    merged.put(k, v);
                }
            });
        }
        return merged;
    }

    public static Map<String, Object> sanitizeForSave(Map<String, Object> body) {
        Map<String, Object> defaults = platformDefaults();
        Map<String, Object> out = new LinkedHashMap<>();
        copyInt(body, out, "accessTokenTtlSeconds", defaults, 60, 86_400);
        copyInt(body, out, "refreshTokenTtlSeconds", defaults, 0, 31_536_000);
        copyInt(body, out, "authorizationCodeTtlSeconds", defaults, 60, 3600);
        copyInt(body, out, "idTokenTtlSeconds", defaults, 60, 86_400);
        copyInt(body, out, "clientCredentialsTtlSeconds", defaults, 60, 86_400);
        copyInt(body, out, "deviceCodeTtlSeconds", defaults, 60, 3600);
        out.put("refreshTokensEnabled", bool(body, "refreshTokensEnabled", defaults));
        boolean reuse = bool(body, "reuseRefreshTokens", defaults);
        out.put("reuseRefreshTokens", reuse);
        out.put("rotateRefreshTokens", body.containsKey("rotateRefreshTokens") ? bool(body, "rotateRefreshTokens", defaults) : !reuse);
        out.put("refreshTokenReuseDetection", bool(body, "refreshTokenReuseDetection", defaults));
        if (body.containsKey("tabEnabled")) {
            out.put("tabEnabled", Boolean.TRUE.equals(body.get("tabEnabled")));
        }
        return out;
    }

    public static boolean tabEnabled(Map<String, Object> policy) {
        return Boolean.TRUE.equals(policy.get("tabEnabled"));
    }

    private static void copyInt(
            Map<String, Object> from,
            Map<String, Object> to,
            String key,
            Map<String, Object> defaults,
            int min,
            int max) {
        Object raw = from.get(key);
        int value;
        if (raw instanceof Number n) {
            value = n.intValue();
        } else {
            value = ((Number) defaults.get(key)).intValue();
        }
        to.put(key, Math.max(min, Math.min(max, value)));
    }

    private static boolean bool(Map<String, Object> from, String key, Map<String, Object> defaults) {
        Object raw = from.get(key);
        if (raw instanceof Boolean b) {
            return b;
        }
        return Boolean.TRUE.equals(defaults.get(key));
    }
}
