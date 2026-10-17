package com.secureone.auth.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Application features (main and advanced capabilities) may be overridden per application, only while the
 * platform has them on. Console controls are decided in platform settings only.
 */
class FeatureFlagScopeTest {

    private static Map<String, Object> row(String key, boolean enabled) {
        Map<String, Object> row = new HashMap<>();
        row.put("key", key);
        row.put("enabled", enabled);
        return row;
    }

    private static Map<String, Boolean> enabled(List<Map<String, Object>> flags) {
        return flags.stream().collect(Collectors.toMap(f -> (String) f.get("key"), f -> Boolean.TRUE.equals(f.get("enabled"))));
    }

    private static Map<String, Object> byKey(List<Map<String, Object>> flags, String key) {
        return flags.stream().filter(f -> key.equals(f.get("key"))).findFirst().orElseThrow();
    }

    @Test
    void catalogScopes() {
        assertEquals(FeatureFlagDefaults.SCOPE_APPLICATION, FeatureFlagDefaults.scopeOf("self_registration"));
        assertEquals(FeatureFlagDefaults.SCOPE_APPLICATION, FeatureFlagDefaults.scopeOf("ldap"));
        assertEquals(FeatureFlagDefaults.SCOPE_PLATFORM, FeatureFlagDefaults.scopeOf("audit_log"));
        assertEquals(FeatureFlagDefaults.SCOPE_PLATFORM, FeatureFlagDefaults.scopeOf("integration_checklist"));
        assertEquals(FeatureFlagDefaults.SCOPE_PLATFORM, FeatureFlagDefaults.scopeOf("notification_email_test_ui"));
    }

    @Test
    void applicationOverrideOfAConsoleControlIsIgnored() {
        List<Map<String, Object>> platform = List.of(row("audit_log", true), row("api_reference", false));
        List<Map<String, Object>> app = List.of(row("audit_log", false), row("api_reference", true));

        Map<String, Boolean> effective = enabled(FeatureFlagMerge.resolveEffective(platform, app));

        assertTrue(effective.get("audit_log"), "platform decides console controls");
        assertFalse(effective.get("api_reference"), "an application cannot turn a console control on");
    }

    @Test
    void platformSavedValueOfAConsoleControlApplies() {
        // Platform settings = built-in catalog + the platform's own saved values (same merge).
        List<Map<String, Object>> merged = FeatureFlagMerge.merge(
                FeatureFlagDefaults.platformCatalog(), List.of(row("audit_log", false), row("integration_checklist", true)));

        Map<String, Boolean> platform = enabled(merged);
        assertFalse(platform.get("audit_log"), "platform can turn a console control off");
        assertTrue(platform.get("integration_checklist"), "platform can turn a console control on");
        assertEquals("platform", byKey(merged, "audit_log").get("scope"));
    }

    @Test
    void applicationFeatureFollowsTheApplicationWhileThePlatformAllowsIt() {
        List<Map<String, Object>> platformOn = List.of(row("self_registration", true));
        List<Map<String, Object>> platformOff = List.of(row("self_registration", false));

        assertFalse(enabled(FeatureFlagMerge.resolveEffective(platformOn, List.of(row("self_registration", false))))
                .get("self_registration"));
        assertTrue(enabled(FeatureFlagMerge.resolveEffective(platformOn, List.of(row("self_registration", true))))
                .get("self_registration"));
        assertFalse(enabled(FeatureFlagMerge.resolveEffective(platformOff, List.of(row("self_registration", true))))
                .get("self_registration"), "platform off wins");
    }

    @Test
    void adminViewReportsScopeAndPlatformState() {
        List<Map<String, Object>> platform = List.of(row("self_registration", false), row("audit_log", true));

        List<Map<String, Object>> admin = FeatureFlagMerge.forApplicationAdmin(platform, null);

        assertEquals("application", byKey(admin, "self_registration").get("scope"));
        assertEquals(false, byKey(admin, "self_registration").get("platformEnabled"));
        assertEquals("platform", byKey(admin, "audit_log").get("scope"));
        assertEquals(true, byKey(admin, "audit_log").get("enabled"));
    }

    @Test
    void savingApplicationOverridesKeepsOnlyApplicationFeatures() {
        List<Map<String, Object>> kept = FeatureFlagMerge.applicationScopedOnly(
                List.of(row("self_registration", true), row("audit_log", false), row("email_templates", false)));

        assertEquals(List.of("self_registration"), kept.stream().map(f -> f.get("key")).toList());
    }
}
