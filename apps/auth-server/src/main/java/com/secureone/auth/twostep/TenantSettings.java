package com.secureone.auth.twostep;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Tenant-level settings ({@code platform.tenant_setting}): defaults a tenant sets for all its applications. */
@Repository
public class TenantSettings {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public TenantSettings(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Map<String, Object> get(UUID tenantId, String key) {
        if (tenantId == null) {
            return Map.of();
        }
        return jdbc.query(
                        "SELECT value::text FROM platform.tenant_setting WHERE tenant_id = ? AND setting_key = ?",
                        (rs, i) -> read(rs.getString(1)),
                        tenantId,
                        key)
                .stream()
                .findFirst()
                .orElse(Map.of());
    }

    public void save(UUID tenantId, String key, Map<String, Object> value) {
        jdbc.update(
                """
                INSERT INTO platform.tenant_setting (tenant_id, setting_key, value, updated_at)
                VALUES (?, ?, ?::jsonb, now())
                ON CONFLICT (tenant_id, setting_key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()
                """,
                tenantId,
                key,
                write(value));
    }

    public void delete(UUID tenantId, String key) {
        jdbc.update("DELETE FROM platform.tenant_setting WHERE tenant_id = ? AND setting_key = ?", tenantId, key);
    }

    private Map<String, Object> read(String raw) {
        try {
            return raw == null ? Map.of() : json.readValue(raw, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String write(Map<String, Object> value) {
        try {
            return json.writeValueAsString(value != null ? value : Map.of());
        } catch (Exception ex) {
            throw new IllegalStateException("Could not store tenant setting", ex);
        }
    }
}
