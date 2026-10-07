package com.secureone.auth.admin.review;

import com.secureone.auth.tenant.RlsScope;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Privileged access review: everyone who holds a console role or the platform operator role, with
 * the facts a reviewer needs (scope, who granted it and when, last sign-in, MFA) and flags for
 * entries that usually need action.
 */
@Service
public class AccessReviewService {

    /** Accounts unused for longer than this are flagged as stale. */
    static final Duration STALE_AFTER = Duration.ofDays(90);

    private final JdbcTemplate jdbc;

    public AccessReviewService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Entry(
            UUID userId,
            String email,
            String displayName,
            String accountStatus,
            UUID tenantId,
            String tenantSlug,
            String tenantName,
            String role,
            UUID assignmentId,
            UUID applicationId,
            String applicationName,
            Instant grantedAt,
            String grantedBy,
            Instant expiresAt,
            Instant lastLoginAt,
            boolean mfaEnrolled,
            List<String> flags) {}

    /** All entries, or only those of one tenant when {@code tenantId} is set. */
    public List<Entry> review(UUID tenantId) {
        return RlsScope.asPlatform(() -> {
            List<Entry> entries = new ArrayList<>();
            if (tenantId == null) {
                entries.addAll(jdbc.query(PLATFORM_SQL, this::platformRow));
            }
            String sql = CONSOLE_SQL + (tenantId != null ? " AND a.tenant_id = ?" : "") + " ORDER BY t.slug, u.email, a.role_type";
            entries.addAll(tenantId != null ? jdbc.query(sql, this::consoleRow, tenantId) : jdbc.query(sql, this::consoleRow));
            return entries;
        });
    }

    private static final String MFA_EXISTS =
            "EXISTS (SELECT 1 FROM platform.mfa_factor f WHERE f.user_id = u.id AND f.verified AND f.type IN ('totp', 'passkey'))";

    private static final String PLATFORM_SQL = """
            SELECT u.id, u.email, u.display_name, u.status, t.id, t.slug, t.name,
                   p.granted_at, p.granted_by, u.last_login_at, %s AS mfa
            FROM platform.platform_operator p
            JOIN platform.user_account u ON u.id = p.user_id
            JOIN platform.tenant t ON t.id = u.tenant_id
            ORDER BY u.email
            """.formatted(MFA_EXISTS);

    private static final String CONSOLE_SQL = """
            SELECT u.id, u.email, u.display_name, u.status, t.id, t.slug, t.name,
                   a.id, a.role_type, a.application_id, app.name, a.granted_at, g.email,
                   a.expires_at, u.last_login_at, %s AS mfa
            FROM platform.admin_console_access a
            JOIN platform.user_account u ON u.id = a.user_id
            JOIN platform.tenant t ON t.id = a.tenant_id
            LEFT JOIN platform.application app ON app.id = a.application_id
            LEFT JOIN platform.user_account g ON g.id = a.granted_by
            WHERE (a.expires_at IS NULL OR a.expires_at > now())
            """.formatted(MFA_EXISTS);

    private Entry platformRow(ResultSet rs, int i) throws SQLException {
        String status = rs.getString(4);
        Instant lastLogin = instant(rs.getTimestamp(10));
        boolean mfa = rs.getBoolean(11);
        return new Entry(
                rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), status,
                rs.getObject(5, UUID.class), rs.getString(6), rs.getString(7),
                "PLATFORM_OPERATOR", null, null, null,
                instant(rs.getTimestamp(8)), rs.getString(9), null,
                lastLogin, mfa, flags(status, lastLogin, mfa, null));
    }

    private Entry consoleRow(ResultSet rs, int i) throws SQLException {
        String status = rs.getString(4);
        Instant expires = instant(rs.getTimestamp(14));
        Instant lastLogin = instant(rs.getTimestamp(15));
        boolean mfa = rs.getBoolean(16);
        return new Entry(
                rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), status,
                rs.getObject(5, UUID.class), rs.getString(6), rs.getString(7),
                rs.getString(9), rs.getObject(8, UUID.class), rs.getObject(10, UUID.class), rs.getString(11),
                instant(rs.getTimestamp(12)), rs.getString(13), expires,
                lastLogin, mfa, flags(status, lastLogin, mfa, expires));
    }

    static List<String> flags(String status, Instant lastLogin, boolean mfa, Instant expiresAt) {
        List<String> flags = new ArrayList<>();
        if (!"ACTIVE".equalsIgnoreCase(status)) {
            flags.add("ACCOUNT_NOT_ACTIVE");
        }
        if (lastLogin == null) {
            flags.add("NEVER_SIGNED_IN");
        } else if (lastLogin.isBefore(Instant.now().minus(STALE_AFTER))) {
            flags.add("UNUSED_90_DAYS");
        }
        if (!mfa) {
            flags.add("NO_MFA");
        }
        return flags;
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
