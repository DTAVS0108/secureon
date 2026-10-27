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

    /** Full access: changes anything on the platform. */
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    /** Sees everything a super admin sees (audit, settings, tenants) but changes nothing: auditors, support. */
    public static final String ROLE_READ_ONLY = "READ_ONLY";

    /** Whether this platform operator account may only look (same lookup rules as {@link #isOperatorEmail}). */
    public boolean isReadOnlyEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        Boolean readOnly = RlsScope.asPlatform(() -> platformLookup.execute(status -> {
            Integer n = jdbc.queryForObject(
                    """
                    SELECT count(*) FROM platform.platform_operator p
                    JOIN platform.user_account u ON u.id = p.user_id
                    JOIN platform.tenant t ON t.id = u.tenant_id
                    WHERE t.slug = ? AND u.email = ? AND p.role = ?
                    """,
                    Integer.class, PLATFORM_TENANT_SLUG, email, ROLE_READ_ONLY);
            return n != null && n > 0;
        }));
        return Boolean.TRUE.equals(readOnly);
    }

    public void grant(UUID userId, String grantedBy) {
        grant(userId, grantedBy, ROLE_SUPER_ADMIN);
    }

    public void grant(UUID userId, String grantedBy, String role) {
        jdbc.update(
                "INSERT INTO platform.platform_operator (user_id, granted_by, role) VALUES (?, ?, ?) ON CONFLICT (user_id) DO NOTHING",
                userId, grantedBy, role);
    }

    public boolean setRole(UUID userId, String role) {
        return jdbc.update("UPDATE platform.platform_operator SET role = ? WHERE user_id = ?", role, userId) > 0;
    }

    /** Operators with full access (at least one must remain). */
    public int countSuperAdmins() {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM platform.platform_operator WHERE role = ?", Integer.class, ROLE_SUPER_ADMIN);
        return n == null ? 0 : n;
    }

    public String roleOf(UUID userId) {
        List<String> roles = jdbc.queryForList(
                "SELECT role FROM platform.platform_operator WHERE user_id = ?", String.class, userId);
        return roles.isEmpty() ? null : roles.get(0);
    }

    public boolean revoke(UUID userId) {
        return jdbc.update("DELETE FROM platform.platform_operator WHERE user_id = ?", userId) > 0;
    }

    public record Row(UUID userId, String email, String displayName, String status, String grantedBy,
                      Instant grantedAt, Instant lastLoginAt, String role) {}

    public List<Row> list() {
        return jdbc.query(
                """
                SELECT u.id, u.email, u.display_name, u.status, p.granted_by, p.granted_at, u.last_login_at, p.role
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
                        rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toInstant(),
                        rs.getString(8)));
    }
}
