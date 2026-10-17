package com.secureone.auth.admin.platform;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.secureone.auth.tenant.RlsScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Storage for the platform-admin role held by named accounts in the reserved "platform" tenant. */
@Repository
public class PlatformOperators {

    /** Slug of the reserved tenant that holds platform operator accounts. */
    public static final String PLATFORM_TENANT_SLUG = "platform";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate platformLookup;

    public PlatformOperators(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.platformLookup = new TransactionTemplate(transactions);
        this.platformLookup.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.platformLookup.setReadOnly(true);
    }

    /**
     * Whether {@code email} is a platform operator account (in the "platform" tenant). Runs in its own
     * platform-scoped transaction: the caller's request may be scoped to another tenant (for example
     * {@code ?tenantId=} on a tenant page), where row-level security hides the platform tenant's users.
     */
    public boolean isOperatorEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        Boolean operator = RlsScope.asPlatform(() -> platformLookup.execute(status -> {
            Integer n = jdbc.queryForObject(
                    """
                    SELECT count(*) FROM platform.platform_operator p
                    JOIN platform.user_account u ON u.id = p.user_id
                    JOIN platform.tenant t ON t.id = u.tenant_id
                    WHERE t.slug = ? AND u.email = ?
                    """,
                    Integer.class,
                    PLATFORM_TENANT_SLUG,
                    email);
            return n != null && n > 0;
        }));
        return Boolean.TRUE.equals(operator);
    }

    public boolean isOperator(UUID userId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM platform.platform_operator WHERE user_id = ?", Integer.class, userId);
        return n != null && n > 0;
    }

    public void grant(UUID userId, String grantedBy) {
        jdbc.update(
                "INSERT INTO platform.platform_operator (user_id, granted_by) VALUES (?, ?) ON CONFLICT (user_id) DO NOTHING",
                userId, grantedBy);
    }

    public boolean revoke(UUID userId) {
        return jdbc.update("DELETE FROM platform.platform_operator WHERE user_id = ?", userId) > 0;
    }

    public record Row(UUID userId, String email, String displayName, String status, String grantedBy,
                      Instant grantedAt, Instant lastLoginAt) {}

    public List<Row> list() {
        return jdbc.query(
                """
                SELECT u.id, u.email, u.display_name, u.status, p.granted_by, p.granted_at, u.last_login_at
                FROM platform.platform_operator p JOIN platform.user_account u ON u.id = p.user_id
                ORDER BY u.email
                """,
                (rs, i) -> new Row(
                        rs.getObject(1, UUID.class),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5),
                        rs.getTimestamp(6).toInstant(),
                        rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toInstant()));
    }
}
