package com.secureone.auth.config;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL skips row-level security for superusers and BYPASSRLS roles. With enforcement on,
 * refuse to start on such a connection instead of silently serving unprotected queries.
 */
@Component
public class RlsConnectionCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RlsConnectionCheck.class);

    private final JdbcTemplate jdbc;
    private final boolean enforce;

    public RlsConnectionCheck(JdbcTemplate jdbc, @Value("${secureone.rls.enforce:off}") String enforce) {
        this.jdbc = jdbc;
        this.enforce = "on".equalsIgnoreCase(enforce) || "true".equalsIgnoreCase(enforce);
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<String, Object> role = jdbc.queryForMap(
                "SELECT current_user AS name, rolsuper OR rolbypassrls AS bypasses FROM pg_roles WHERE rolname = current_user");
        boolean bypasses = Boolean.TRUE.equals(role.get("bypasses"));
        if (bypasses && enforce) {
            throw new IllegalStateException("SECUREONE_RLS_ENFORCE=on, but database role '" + role.get("name")
                    + "' is a superuser or has BYPASSRLS, so PostgreSQL would not apply row-level security. "
                    + "Apply migration V63 (role secureone_app) or connect as a role without those attributes.");
        }
        if (bypasses) {
            log.warn("Database role '{}' bypasses row-level security; tenant isolation relies on application checks only.",
                    role.get("name"));
        } else {
            log.info("Row-level security: database role '{}', enforcement {}.", role.get("name"), enforce ? "on" : "off");
        }
    }
}
