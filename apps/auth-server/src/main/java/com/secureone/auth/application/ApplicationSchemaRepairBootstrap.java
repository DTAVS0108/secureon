package com.secureone.auth.application;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Applies incremental DDL to existing per-application schemas (new apps get full V1+V2 at provision). */
@Component
public class ApplicationSchemaRepairBootstrap {

    private static final Logger log = LoggerFactory.getLogger(ApplicationSchemaRepairBootstrap.class);

    private final JdbcTemplate jdbc;
    private final ApplicationSchemaRepository schemas;

    public ApplicationSchemaRepairBootstrap(JdbcTemplate jdbc, ApplicationSchemaRepository schemas) {
        this.jdbc = jdbc;
        this.schemas = schemas;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void repairSchemas() {
        List<String> migrations = List.of("db/migration/application-schema/V2__user_application_created_at.sql");
        for (ApplicationSchema schema : schemas.findAll()) {
            if (!"ACTIVE".equalsIgnoreCase(schema.getStatus())) {
                continue;
            }
            String schemaName = schema.getSchemaName();
            for (String resource : migrations) {
                applyIfNeeded(schemaName, resource);
            }
        }
    }

    private void applyIfNeeded(String schemaName, String resourcePath) {
        try {
            String ddl = new ClassPathResource(resourcePath).getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            jdbc.execute("SET LOCAL search_path TO " + quoteIdent(schemaName));
            for (String statement : ddl.replaceAll("(?m)^--.*$", "").split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    jdbc.execute(trimmed);
                }
            }
            jdbc.execute("SET LOCAL search_path TO " + quoteIdent(ApplicationSchemaNames.PLATFORM_SCHEMA));
            log.debug("Applied {} to schema {}", resourcePath, schemaName);
        } catch (Exception ex) {
            log.warn("Schema repair failed for {} on {}: {}", resourcePath, schemaName, ex.getMessage());
        }
    }

    private static String quoteIdent(String ident) {
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }
}
