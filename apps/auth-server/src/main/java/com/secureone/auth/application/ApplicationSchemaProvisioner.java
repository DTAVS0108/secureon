package com.secureone.auth.application;

import com.secureone.auth.admin.ConflictException;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationSchemaProvisioner {

    private final JdbcTemplate jdbc;
    private final ApplicationSchemaRepository schemaRegistry;

    public ApplicationSchemaProvisioner(JdbcTemplate jdbc, ApplicationSchemaRepository schemaRegistry) {
        this.jdbc = jdbc;
        this.schemaRegistry = schemaRegistry;
    }

    @Transactional
    public String provision(UUID applicationId, String slug) {
        String schemaName = resolveUniqueSchemaName(slug);
        if (schemaExists(schemaName)) {
            throw new ConflictException("Database schema already exists: " + schemaName);
        }
        // DDL needs the schema owner. Request connections run as secureone_app (see V63); this
        // transaction alone switches back to the login role, and the default privileges from V63
        // give secureone_app access to what is created here.
        jdbc.execute("SET LOCAL ROLE NONE");
        jdbc.execute("CREATE SCHEMA " + quoteIdent(schemaName));
        jdbc.execute("SET LOCAL search_path TO " + quoteIdent(schemaName));
        executeAppDdl();
        jdbc.execute("SET LOCAL search_path TO " + quoteIdent(ApplicationSchemaNames.PLATFORM_SCHEMA));

        ApplicationSchema record = new ApplicationSchema();
        record.setApplicationId(applicationId);
        record.setSchemaName(schemaName);
        record.setStatus("ACTIVE");
        record.setFlywayVersion(ApplicationSchemaMigrations.latest().name());
        record.setProvisionedAt(Instant.now());
        schemaRegistry.save(record);
        return schemaName;
    }

    /**
     * Drops an application's dedicated schema when the application is deleted, so its users' roles,
     * groups and settings do not linger. Never touches the shared platform or public schemas.
     */
    public void drop(String schemaName) {
        if (schemaName == null
                || !schemaName.matches("[a-z][a-z0-9_]{0,62}")
                || ApplicationSchemaNames.PLATFORM_SCHEMA.equals(schemaName)
                || "public".equals(schemaName)) {
            return;
        }
        jdbc.execute("SET LOCAL ROLE NONE"); // DDL needs the schema owner (see V63)
        jdbc.execute("DROP SCHEMA IF EXISTS " + quoteIdent(schemaName) + " CASCADE");
    }

    private String resolveUniqueSchemaName(String slug) {
        String base = ApplicationSchemaNames.fromSlug(slug);
        String candidate = base;
        int suffix = 2;
        while (schemaExists(candidate) || schemaRegistry.existsBySchemaName(candidate)) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    private boolean schemaExists(String schemaName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class,
                schemaName);
        return count != null && count > 0;
    }

    private void executeAppDdl() {
        for (ApplicationSchemaMigrations.Migration migration : ApplicationSchemaMigrations.ALL) {
            for (String statement : ApplicationSchemaMigrations.statements(migration)) {
                jdbc.execute(statement);
            }
        }
    }

    private static String quoteIdent(String ident) {
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }
}
