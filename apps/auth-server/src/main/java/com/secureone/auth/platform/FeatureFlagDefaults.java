package com.secureone.auth.platform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Platform-wide feature flag catalog (merged with stored overrides by key). */
public final class FeatureFlagDefaults {

    private FeatureFlagDefaults() {}

    public static Set<String> catalogKeys() {
        return platformCatalog().stream()
                .map(row -> String.valueOf(row.get("key")))
                .collect(Collectors.toUnmodifiableSet());
    }

    public static List<Map<String, Object>> platformCatalog() {
        List<Map<String, Object>> flags = new ArrayList<>();
        flags.add(flag(
                "self_service_recovery",
                "Self-service Recovery",
                "Application-scoped forgot-password (link or OTP per verification policy)",
                true,
                100,
                "identity"));
        flags.add(flag(
                "recovery_otp",
                "Recovery OTP",
                "Allow password reset via email/SMS OTP (requires verification policy + SMS for phone)",
                true,
                100,
                "identity"));
        flags.add(flag(
                "self_registration",
                "Self Registration",
                "Public user signup for this application",
                false,
                0,
                "identity"));
        flags.add(flag(
                "ldap",
                "LDAP / AD",
                "LDAP user import and directory source configuration",
                false,
                0,
                "identity"));
        flags.add(flag(
                "notification_email_test_ui",
                "Email test console",
                "Show the SMTP test panel under Application → Notifications",
                true,
                100,
                "notifications"));
        return flags;
    }

    private static Map<String, Object> flag(
            String key, String name, String description, boolean enabled, int rollout, String category) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        row.put("name", name);
        row.put("description", description);
        row.put("enabled", enabled);
        row.put("rollout", rollout);
        row.put("category", category);
        return row;
    }
}
