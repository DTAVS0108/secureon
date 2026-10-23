package com.secureone.auth.application;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The application's "Two Factor Authentication" switch (Settings → Feature flags): on, the application has its
 * own two-factor settings tab and those settings apply at sign-in; off, it uses the platform and tenant policy.
 * See {@link com.secureone.auth.twostep.TwoStepPolicyResolver#hasOwnSettings}.
 */
public final class AuthMfaTabDefaults {

    public static final String SETTING_KEY = com.secureone.auth.twostep.TwoStepPolicyResolver.OWN_SETTINGS_KEY;

    private AuthMfaTabDefaults() {}

    public static Map<String, Object> platformDefaults() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("tabEnabled", false);
        return root;
    }

    public static boolean tabEnabled(Map<String, Object> row) {
        return row != null && Boolean.TRUE.equals(row.get("tabEnabled"));
    }
}
