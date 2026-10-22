package com.secureone.auth.platform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Platform-wide feature flag catalog (merged with stored overrides by key). */
public final class FeatureFlagDefaults {

    /** "Try another way" on the two-step verification screen (sign-in pages and admin console). */
    public static final String MFA_TRY_ANOTHER_WAY = "mfa_try_another_way";

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
                MFA_TRY_ANOTHER_WAY,
                "2FA: Try another way",
                "On the two-step verification screen, let users pick any of their enabled options when they can't use the one asked first",
                true,
                100,
                "identity"));
        flags.add(flag(
                "notification_email_test_ui",
                "Email test console",
                "Show the SMTP test panel under Application → Notifications",
                true,
                100,
                "notifications"));
        flags.add(flag(
                "email_templates",
                "Email templates",
                "Show the Email templates editor under Application → Notifications",
                true,
                100,
                "notifications"));
        flags.add(flag(
                "federation_add_provider",
                "Add sign-in provider",
                "Allow adding Google, GitHub, OIDC and SAML providers under Authentication → Identity federation",
                true,
                100,
                "identity"));
        flags.add(flag(
                "integration_checklist",
                "Integration checklist",
                "Show the go-live checklist and notes on the Integration tab",
                false,
                0,
                "integration"));
        flags.add(flag(
                "api_reference",
                "API reference",
                "Show the API reference (endpoints, request and response shapes) on the Integration tab",
                false,
                0,
                "integration"));
        flags.add(flag(
                "api_health_check",
                "API health check",
                "Show the API health check on the Integration tab",
                false,
                0,
                "integration"));
        flags.add(flag(
                "audit_log",
                "Audit log",
                "Show the Audit log page in this application's console",
                true,
                100,
                "console"));
        flags.add(flag(
                "sessions",
                "Sessions",
                "Show the Sessions page (sign-in history) in this application's console",
                true,
                100,
                "console"));
        return flags;
    }

    /**
     * Flags an application may override: product capabilities (main and advanced features). Every other
     * flag is a console control decided in Platform settings only; applications never see or change it.
     */
    private static final Set<String> APPLICATION_SCOPED =
            Set.of("self_service_recovery", "recovery_otp", "self_registration", "ldap", MFA_TRY_ANOTHER_WAY);

    public static final String SCOPE_APPLICATION = "application";
    public static final String SCOPE_PLATFORM = "platform";

    /** {@code application} (applications may override) or {@code platform} (platform settings only). */
    public static String scopeOf(String key) {
        return APPLICATION_SCOPED.contains(key) ? SCOPE_APPLICATION : SCOPE_PLATFORM;
    }

    public static boolean isApplicationScoped(String key) {
        return APPLICATION_SCOPED.contains(key);
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
        row.put("scope", scopeOf(key));
        return row;
    }
}
