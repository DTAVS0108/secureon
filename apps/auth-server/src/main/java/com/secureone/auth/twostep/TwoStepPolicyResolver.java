package com.secureone.auth.twostep;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureone.auth.mfa.MfaPolicyDefaults;
import com.secureone.auth.platform.PlatformSettingsService;
import com.secureone.auth.platform.SettingsExposureService;
import com.secureone.auth.tenant.RlsScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The effective two-step policy for an application: the platform policy, then the tenant's defaults, then the
 * application's own settings. Each level may only change what the levels above allow (lock modes:
 * locked / stricter only / open). While the platform opens Two Factor Authentication to applications, each
 * application's "Two Factor Authentication" switch (Feature flags) decides: on, its own settings apply; off, two-factor
 * is off for it (its saved values wait unused), unless the platform or tenant locks the requirement. While the
 * platform does not open it, the platform and tenant policy applies.
 * Reads run in their own platform-scoped transaction because sign-in has no tenant scope yet.
 */
@Service
public class TwoStepPolicyResolver {

    public static final String KEY = "mfa_policy";

    /** The policy and where it came from. {@code locks} are the locks the application is under. */
    public record Resolved(Map<String, Object> policy, UUID tenantId, Map<String, String> locks) {}

    private final PlatformSettingsService platformSettings;
    private final TenantSettings tenantSettings;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate platformRead;
    private final SettingsExposureService exposure;

    public TwoStepPolicyResolver(
            PlatformSettingsService platformSettings,
            TenantSettings tenantSettings,
            JdbcTemplate jdbc,
            ObjectMapper json,
            PlatformTransactionManager transactions,
            SettingsExposureService exposure) {
        this.exposure = exposure;
        this.platformSettings = platformSettings;
        this.tenantSettings = tenantSettings;
        this.jdbc = jdbc;
        this.json = json;
        this.platformRead = new TransactionTemplate(transactions);
        this.platformRead.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.platformRead.setReadOnly(true);
    }

    public Map<String, Object> platform() {
        return MfaPolicyDefaults.sanitizeForSave(platformSettings.get(KEY));
    }

    /** Effective policy for an application ({@code null}: platform policy). */
    public Resolved resolve(UUID applicationId) {
        Map<String, Object> platform = platform();
        Map<String, String> platformLocks = MfaPolicyDefaults.locks(platform.get(MfaPolicyDefaults.LOCKS));
        if (applicationId == null) {
            return new Resolved(platform, null, Map.of());
        }
        UUID tenantId = tenantOf(applicationId);
        Map<String, Object> tenantRaw = tenantSettings.get(tenantId, KEY);
        Map<String, Object> afterTenant = MfaPolicyDefaults.applyLayer(platform, platformLocks, tenantRaw);
        Map<String, String> locks = MfaPolicyDefaults.combineLocks(
                platformLocks, MfaPolicyDefaults.locks(tenantRaw.get(MfaPolicyDefaults.LOCKS)));
        Map<String, Object> effective;
        if (!exposure.isExposed(OWN_SETTINGS_EXPOSURE)) {
            // Applications cannot manage two-factor: the platform and tenant settings apply.
            effective = new LinkedHashMap<>(afterTenant);
        } else if (!switchOn(applicationId)) {
            // The application turned Two Factor Authentication off: no second step, unless the platform or tenant
            // locks whether it is required (locked or stricter only), which then still applies.
            effective = new LinkedHashMap<>(afterTenant);
            if (!locks.containsKey("mode")) {
                effective.put("mode", "off");
            }
        } else {
            Map<String, Object> own = new LinkedHashMap<>(appSetting(applicationId, KEY));
            own.keySet().removeAll(MfaPolicyDefaults.NOT_PER_APPLICATION);
            effective = MfaPolicyDefaults.applyLayer(afterTenant, locks, own);
        }
        effective.put(MfaPolicyDefaults.LOCKS, locks);
        return new Resolved(effective, tenantId, locks);
    }

    private boolean switchOn(UUID applicationId) {
        return Boolean.TRUE.equals(appSetting(applicationId, OWN_SETTINGS_KEY).get("tabEnabled"));
    }

    /**
     * Whether the application turned Two Factor Authentication off (its Feature flags switch, while applications may
     * manage it): then sign-in has no second step unless the platform or tenant locks the requirement.
     */
    public boolean switchedOff(UUID applicationId) {
        return applicationId != null && exposure.isExposed(OWN_SETTINGS_EXPOSURE) && !switchOn(applicationId);
    }

    /**
     * Whether the application uses its own two-factor settings: Two Factor Authentication is open to
     * applications (Platform settings → For applications) and its "Two Factor Authentication" switch is on.
     */
    public boolean hasOwnSettings(UUID applicationId) {
        return applicationId != null && exposure.isExposed(OWN_SETTINGS_EXPOSURE) && switchOn(applicationId);
    }

    /** Exposure section that opens two-factor settings to applications (Platform settings → For applications). */
    public static final String OWN_SETTINGS_EXPOSURE = "two-factor";
    /** Application setting holding the "Two Factor Authentication" switch ({@code tabEnabled}). */
    public static final String OWN_SETTINGS_KEY = "auth_mfa_tab";

    /** Effective policy at tenant level (what a tenant's applications start from) and the platform's locks. */
    public Resolved resolveTenant(UUID tenantId) {
        Map<String, Object> platform = platform();
        Map<String, String> platformLocks = MfaPolicyDefaults.locks(platform.get(MfaPolicyDefaults.LOCKS));
        Map<String, Object> tenantRaw = tenantSettings.get(tenantId, KEY);
        Map<String, Object> effective = MfaPolicyDefaults.applyLayer(platform, platformLocks, tenantRaw);
        effective.put(MfaPolicyDefaults.LOCKS, MfaPolicyDefaults.locks(tenantRaw.get(MfaPolicyDefaults.LOCKS)));
        return new Resolved(effective, tenantId, platformLocks);
    }

    public Map<String, Object> tenantOverrides(UUID tenantId) {
        return tenantSettings.get(tenantId, KEY);
    }

    /** Saves a tenant's defaults; refuses values the platform's locks do not allow. */
    public Resolved saveTenant(UUID tenantId, Map<String, Object> body) {
        Map<String, Object> platform = platform();
        Map<String, String> platformLocks = MfaPolicyDefaults.locks(platform.get(MfaPolicyDefaults.LOCKS));
        Map<String, Object> overrides = MfaPolicyDefaults.sanitizeOverride(body);
        overrides.put(MfaPolicyDefaults.LOCKS, MfaPolicyDefaults.locks(body != null ? body.get(MfaPolicyDefaults.LOCKS) : null));
        requireAllowed(MfaPolicyDefaults.violations(platform, platformLocks, overrides));
        tenantSettings.save(tenantId, KEY, overrides);
        return resolveTenant(tenantId);
    }

    public void resetTenant(UUID tenantId) {
        tenantSettings.delete(tenantId, KEY);
    }

    /** Refuses application overrides the platform's or tenant's locks do not allow. */
    public void requireAllowedForApplication(UUID applicationId, Map<String, Object> overrides) {
        Map<String, Object> platform = platform();
        Map<String, String> platformLocks = MfaPolicyDefaults.locks(platform.get(MfaPolicyDefaults.LOCKS));
        UUID tenantId = tenantOf(applicationId);
        Map<String, Object> tenantRaw = tenantSettings.get(tenantId, KEY);
        Map<String, Object> afterTenant = MfaPolicyDefaults.applyLayer(platform, platformLocks, tenantRaw);
        Map<String, String> locks = MfaPolicyDefaults.combineLocks(
                platformLocks, MfaPolicyDefaults.locks(tenantRaw.get(MfaPolicyDefaults.LOCKS)));
        List<String> violations = new java.util.ArrayList<>(MfaPolicyDefaults.violations(afterTenant, locks, overrides));
        for (String key : MfaPolicyDefaults.NOT_PER_APPLICATION) {
            Object value = overrides.get(key);
            if (value != null && !value.equals(afterTenant.get(key))) {
                violations.add(key + " is set by the platform or tenant for every application (backup codes are shared)");
            }
        }
        requireAllowed(violations);
    }

    private static void requireAllowed(List<String> violations) {
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("Not allowed by the level above: " + String.join("; ", violations) + ".");
        }
    }

    public UUID tenantOf(UUID applicationId) {
        return RlsScope.asPlatform(() -> platformRead.execute(status -> jdbc
                .query("SELECT tenant_id FROM platform.application WHERE id = ?",
                        (rs, i) -> rs.getObject(1, UUID.class), applicationId)
                .stream().findFirst().orElse(null)));
    }

    /**
     * One of the application's own settings. Application settings live in the application's schema (the
     * settings service writes them there through the schema-routed data source), so read that schema
     * explicitly: this runs at sign-in too, where the current schema may be another one or none.
     */
    private Map<String, Object> appSetting(UUID applicationId, String key) {
        String schema = RlsScope.asPlatform(() -> platformRead.execute(status -> jdbc
                .query("SELECT schema_name FROM platform.application WHERE id = ?",
                        (rs, i) -> rs.getString(1), applicationId)
                .stream().findFirst().orElse(null)));
        String table = schema == null || schema.isBlank()
                ? "platform.application_setting"
                : "\"" + schema.replace("\"", "\"\"") + "\".application_setting";
        return jdbc.query(
                        "SELECT value::text FROM " + table + " WHERE application_id = ? AND setting_key = ?",
                        (rs, i) -> read(rs.getString(1)),
                        applicationId,
                        key)
                .stream()
                .findFirst()
                .orElse(Map.of());
    }

    private Map<String, Object> read(String raw) {
        try {
            return raw == null ? Map.of() : json.readValue(raw, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
