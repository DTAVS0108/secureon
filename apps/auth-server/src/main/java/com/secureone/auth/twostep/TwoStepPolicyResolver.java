package com.secureone.auth.twostep;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureone.auth.mfa.MfaPolicyDefaults;
import com.secureone.auth.platform.PlatformSettingsService;
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
 * locked / stricter only / open). Reads run in their own platform-scoped transaction because sign-in has no
 * tenant scope yet.
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

    public TwoStepPolicyResolver(
            PlatformSettingsService platformSettings,
            TenantSettings tenantSettings,
            JdbcTemplate jdbc,
            ObjectMapper json,
            PlatformTransactionManager transactions) {
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
        Map<String, Object> effective = MfaPolicyDefaults.applyLayer(afterTenant, locks, appOverrides(applicationId));
        effective.put(MfaPolicyDefaults.LOCKS, locks);
        return new Resolved(effective, tenantId, locks);
    }

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
        requireAllowed(MfaPolicyDefaults.violations(afterTenant, locks, overrides));
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
     * The application's own overrides. Application settings live in the application's schema (the settings
     * service writes them there through the schema-routed data source), so read that schema explicitly: this
     * runs at sign-in too, where the current schema may be another one or none.
     */
    private Map<String, Object> appOverrides(UUID applicationId) {
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
                        KEY)
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
