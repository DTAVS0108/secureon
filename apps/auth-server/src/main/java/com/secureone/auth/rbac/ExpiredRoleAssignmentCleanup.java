package com.secureone.auth.rbac;

import com.secureone.auth.audit.AuditService;
import com.secureone.auth.tenant.RlsScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Removes role assignments whose time is up, in every application's schema, each with an audit entry
 * ({@code user.role_expired}). Expired assignments already give nothing (they are never read); this keeps the
 * lists accurate and records when access ended.
 */
@Component
public class ExpiredRoleAssignmentCleanup {

    private static final Logger log = LoggerFactory.getLogger(ExpiredRoleAssignmentCleanup.class);

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public ExpiredRoleAssignmentCleanup(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Scheduled(fixedDelayString = "${secureone.roles.expiry-interval:5m}", initialDelay = 60_000)
    public void removeExpired() {
        try {
            int removed = RlsScope.asPlatform(this::removeAll);
            if (removed > 0) {
                log.info("Role assignments expired: {}", removed);
            }
        } catch (RuntimeException e) {
            log.warn("Expired role assignments not removed: {}", e.getMessage());
        }
    }

    int removeAll() {
        List<String> schemas = new ArrayList<>(jdbc.queryForList(
                "SELECT DISTINCT schema_name FROM platform.application WHERE schema_name IS NOT NULL", String.class));
        schemas.add("platform");
        int total = 0;
        for (String schema : schemas) {
            if (!schema.matches("[a-z][a-z0-9_]{0,62}")) {
                continue;
            }
            List<Map<String, Object>> rows;
            try {
                rows = jdbc.queryForList("DELETE FROM \"" + schema + "\".user_role ur USING \"" + schema + "\".role r"
                        + " WHERE r.id = ur.role_id AND ur.expires_at IS NOT NULL AND ur.expires_at <= now()"
                        + " RETURNING ur.user_id, r.application_id, r.tenant_id, r.name,"
                        + " (SELECT email FROM platform.user_account u WHERE u.id = ur.user_id) AS email");
            } catch (RuntimeException e) {
                log.warn("Expired role assignments in {} not removed: {}", schema, e.getMessage());
                continue;
            }
            for (Map<String, Object> row : rows) {
                audit.recordIndependently((UUID) row.get("tenant_id"), (UUID) row.get("application_id"), "system",
                        "user.role_expired", "user_account", (UUID) row.get("user_id"),
                        row.get("email") + ": " + row.get("name"), true);
            }
            total += rows.size();
        }
        return total;
    }
}
