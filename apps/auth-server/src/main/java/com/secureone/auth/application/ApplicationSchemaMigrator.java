package com.secureone.auth.application;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Copies legacy per-application rows from {@code platform.*} into a dedicated app schema. */
@Service
public class ApplicationSchemaMigrator {

    private final JdbcTemplate jdbc;

    public ApplicationSchemaMigrator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void migrateLegacyDataToSchema(UUID applicationId, String schemaName) {
        String platform = quoteIdent(ApplicationSchemaNames.PLATFORM_SCHEMA);
        String app = quoteIdent(schemaName);

        copyByApplicationId("permission", platform, app, applicationId);
        copyByApplicationId("role", platform, app, applicationId);
        copyByRole("role_permission", platform, app, applicationId);
        copyByRole("role_composite", platform, app, applicationId, "parent_role_id");
        copyByRole("user_role", platform, app, applicationId);
        copyByApplicationId("user_application", platform, app, applicationId);
        copyByApplicationId("application_setting", platform, app, applicationId);
        copyByApplicationId("rbac_group", platform, app, applicationId);
        copyByGroup("rbac_group_role", platform, app, applicationId);
        copyByGroup("rbac_group_member", platform, app, applicationId);
        // application_log stays in the platform table (filtered by application_id); see V66.

        deleteLegacyData(applicationId);
    }

    private void copyByApplicationId(String table, String platform, String app, UUID applicationId) {
        jdbc.update(
                "INSERT INTO " + app + "." + table + " SELECT * FROM " + platform + "." + table
                        + " WHERE application_id = ?",
                applicationId);
    }

    private void copyByRole(String table, String platform, String app, UUID applicationId) {
        copyByRole(table, platform, app, applicationId, "role_id");
    }

    private void copyByRole(
            String table, String platform, String app, UUID applicationId, String roleColumn) {
        jdbc.update(
                "INSERT INTO " + app + "." + table + " SELECT t.* FROM " + platform + "." + table + " t"
                        + " JOIN " + platform + ".role r ON r.id = t." + roleColumn
                        + " WHERE r.application_id = ?",
                applicationId);
    }

    private void copyByGroup(String table, String platform, String app, UUID applicationId) {
        jdbc.update(
                "INSERT INTO " + app + "." + table + " SELECT t.* FROM " + platform + "." + table + " t"
                        + " JOIN " + platform + ".rbac_group g ON g.id = t.group_id"
                        + " WHERE g.application_id = ?",
                applicationId);
    }

    private void deleteLegacyData(UUID applicationId) {
        jdbc.update(
                "DELETE FROM " + ApplicationSchemaNames.PLATFORM_SCHEMA + ".rbac_group_member"
                        + " WHERE group_id IN (SELECT id FROM " + ApplicationSchemaNames.PLATFORM_SCHEMA
                        + ".rbac_group WHERE application_id = ?)",
                applicationId);
        jdbc.update(
                "DELETE FROM " + ApplicationSchemaNames.PLATFORM_SCHEMA + ".rbac_group_role"
                        + " WHERE group_id IN (SELECT id FROM " + ApplicationSchemaNames.PLATFORM_SCHEMA
                        + ".rbac_group WHERE application_id = ?)",
                applicationId);
        jdbc.update(
                "DELETE FROM " + ApplicationSchemaNames.PLATFORM_SCHEMA + ".user_role"
                        + " WHERE role_id IN (SELECT id FROM " + ApplicationSchemaNames.PLATFORM_SCHEMA
                        + ".role WHERE application_id = ?)",
                applicationId);
        jdbc.update(
                "DELETE FROM " + ApplicationSchemaNames.PLATFORM_SCHEMA + ".role_composite"
                        + " WHERE parent_role_id IN (SELECT id FROM " + ApplicationSchemaNames.PLATFORM_SCHEMA
                        + ".role WHERE application_id = ?)",
                applicationId);
        jdbc.update(
                "DELETE FROM " + ApplicationSchemaNames.PLATFORM_SCHEMA + ".role_permission"
                        + " WHERE role_id IN (SELECT id FROM " + ApplicationSchemaNames.PLATFORM_SCHEMA
                        + ".role WHERE application_id = ?)",
                applicationId);
        jdbc.update(
                "DELETE FROM " + ApplicationSchemaNames.PLATFORM_SCHEMA + ".rbac_group WHERE application_id = ?",
                applicationId);
        jdbc.update(
                "DELETE FROM " + ApplicationSchemaNames.PLATFORM_SCHEMA + ".user_application WHERE application_id = ?",
                applicationId);
        jdbc.update(
                "DELETE FROM " + ApplicationSchemaNames.PLATFORM_SCHEMA + ".application_setting WHERE application_id = ?",
                applicationId);
        jdbc.update(
                "DELETE FROM " + ApplicationSchemaNames.PLATFORM_SCHEMA + ".role WHERE application_id = ?",
                applicationId);
        jdbc.update(
                "DELETE FROM " + ApplicationSchemaNames.PLATFORM_SCHEMA + ".permission WHERE application_id = ?",
                applicationId);
    }

    private static String quoteIdent(String ident) {
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }
}
