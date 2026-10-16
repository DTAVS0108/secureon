package com.secureone.auth.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Per-application setting overrides. An isolated application keeps them in its own schema, so every
 * query names that schema explicitly instead of relying on the request's search_path. Otherwise a
 * caller that passes an application id without having entered its schema (an older endpoint, a
 * background job) would read the platform schema, find no override and silently use platform
 * defaults. Joins the surrounding Spring transaction like any JDBC access.
 */
@Repository
public class ApplicationSettingRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ApplicationSettingRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Optional<ApplicationSetting> findByApplicationIdAndKey(UUID applicationId, String key) {
        List<ApplicationSetting> rows = jdbc.query(
                "SELECT value::text, updated_at FROM " + table(applicationId)
                        + " WHERE application_id = ? AND setting_key = ?",
                (rs, i) -> {
                    ApplicationSetting s = new ApplicationSetting();
                    s.setApplicationId(applicationId);
                    s.setKey(key);
                    s.setValue(parse(rs.getString(1)));
                    s.setUpdatedAt(rs.getTimestamp(2) != null ? rs.getTimestamp(2).toInstant() : null);
                    return s;
                },
                applicationId,
                key);
        return rows.stream().findFirst();
    }

    public ApplicationSetting save(ApplicationSetting row) {
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO " + table(row.getApplicationId())
                        + " (application_id, setting_key, value, updated_at) VALUES (?, ?, ?::jsonb, ?)"
                        + " ON CONFLICT (application_id, setting_key) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at",
                row.getApplicationId(),
                row.getKey(),
                write(row.getValue()),
                java.sql.Timestamp.from(now));
        row.setUpdatedAt(now);
        return row;
    }

    public void delete(ApplicationSetting row) {
        jdbc.update(
                "DELETE FROM " + table(row.getApplicationId()) + " WHERE application_id = ? AND setting_key = ?",
                row.getApplicationId(),
                row.getKey());
    }

    /** The application's own schema when it is isolated, else the shared platform schema. */
    private String table(UUID applicationId) {
        List<String> names = jdbc.queryForList(
                "SELECT schema_name FROM platform.application WHERE id = ?", String.class, applicationId);
        String schema = names.isEmpty() || names.get(0) == null || names.get(0).isBlank()
                ? ApplicationSchemaNames.PLATFORM_SCHEMA
                : names.get(0);
        return "\"" + schema.replace("\"", "\"\"") + "\".application_setting";
    }

    private Object parse(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return json.readValue(raw, Object.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unreadable application setting", ex);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Setting value is not serialisable", ex);
        }
    }
}
