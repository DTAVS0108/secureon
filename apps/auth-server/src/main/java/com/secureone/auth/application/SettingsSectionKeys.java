package com.secureone.auth.application;

import java.util.Map;

/** Maps application_setting keys to platform exposure section keys. */
public final class SettingsSectionKeys {

    private static final Map<String, String> SETTING_TO_EXPOSURE = Map.ofEntries(
            Map.entry("notifications", "notifications"),
            Map.entry("email", "email"),
            Map.entry("auth_methods", "auth-methods"),
            Map.entry("mfa_policy", "two-factor"),
            Map.entry("password_policy", "password-policy"),
            Map.entry("feature_flags", "feature-flags"),
            Map.entry("appearance", "appearance"),
            Map.entry("user_directory", "user-directory"),
            Map.entry("public_manifest", "public-manifest"),
            Map.entry("token_policy", "token-policy"),
            Map.entry("verification_policy", "verification-policy"),
            Map.entry("sms", "sms"));

    private SettingsSectionKeys() {}

    public static String exposureForSettingKey(String settingKey) {
        return SETTING_TO_EXPOSURE.get(settingKey);
    }

    public static boolean isKnownSettingKey(String settingKey) {
        return SETTING_TO_EXPOSURE.containsKey(settingKey);
    }

    public static String settingKeyForExposure(String exposureKey) {
        if (exposureKey == null) {
            return null;
        }
        for (var entry : SETTING_TO_EXPOSURE.entrySet()) {
            if (entry.getValue().equals(exposureKey)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
