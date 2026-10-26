package com.secureone.auth.admin.overview;

import com.secureone.auth.tenant.RlsScope;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The console home: how the platform (or one tenant) is doing at a glance. Sign-ins and failures, active users,
 * how many people use two-factor, locked accounts, daily sign-ins for two weeks, and recent security events.
 */
@Service
public class PlatformOverviewService {

    /** Audit actions shown as security events on the home page. */
    static final List<String> SECURITY_ACTIONS = List.of(
            "admin.console_login.mfa_failed", "user.locked", "user.login.locked", "user.suspended",
            "user.mfa_removed", "two_step.factor_removed", "user.password_reset", "platform_operator.granted",
            "platform_operator.revoked", "change_request.applied_without_approval", "change_request.approved",
            "admin.impersonation_started", "refresh_token.reuse_detected", "oauth_client.deleted",
            "application.deleted", "tenant.deleted", "settings.feature_flag_changed");

    private final JdbcTemplate jdbc;

    public PlatformOverviewService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Everything for the home page; {@code tenantId} null = the whole platform. */
    public Map<String, Object> overview(UUID tenantId) {
        return RlsScope.asPlatform(() -> {
            Instant now = Instant.now();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("generatedAt", now.toString());
            out.put("scope", tenantId == null ? "platform" : "tenant");

            Map<String, Object> counts = new LinkedHashMap<>();
            if (tenantId == null) {
                counts.put("tenants", count("SELECT count(*) FROM platform.tenant WHERE slug <> 'platform'"));
            }
            counts.put("applications", count("SELECT count(*) FROM platform.application a" + tenantFilter("a", tenantId), tenantId));
            counts.put("oauthClients", count("SELECT count(*) FROM platform.oauth_client c JOIN platform.application a ON a.id = c.application_id"
                    + tenantFilter("a", tenantId), tenantId));
            counts.put("users", count("SELECT count(*) FROM platform.user_account u" + userFilter(tenantId), tenantId));
            counts.put("lockedUsers", count("SELECT count(*) FROM platform.user_account u" + userFilter(tenantId)
                    + " AND u.locked_until IS NOT NULL AND u.locked_until > now()", tenantId));
            counts.put("suspendedUsers", count("SELECT count(*) FROM platform.user_account u" + userFilter(tenantId)
                    + " AND u.status = 'SUSPENDED'", tenantId));
            out.put("counts", counts);

            Map<String, Object> signIns = new LinkedHashMap<>();
            signIns.put("succeeded24h", loginCount(tenantId, "= 'SUCCESS'", now.minus(1, ChronoUnit.DAYS)));
            signIns.put("failed24h", loginCount(tenantId, "<> 'SUCCESS'", now.minus(1, ChronoUnit.DAYS)));
            signIns.put("succeeded7d", loginCount(tenantId, "= 'SUCCESS'", now.minus(7, ChronoUnit.DAYS)));
            signIns.put("failed7d", loginCount(tenantId, "<> 'SUCCESS'", now.minus(7, ChronoUnit.DAYS)));
            signIns.put("activeUsers30d", count("SELECT count(DISTINCT h.user_id) FROM platform.login_history h WHERE h.result = 'SUCCESS'"
                    + " AND h.created_at > now() - interval '30 days'" + (tenantId != null ? " AND h.tenant_id = ?" : ""), tenantId));
            out.put("signIns", signIns);
            out.put("daily", daily(tenantId, now));

            long users = (long) counts.get("users");
            long withTwoFactor = count("SELECT count(DISTINCT f.user_id) FROM platform.mfa_factor f JOIN platform.user_account u ON u.id = f.user_id"
                    + userFilter(tenantId) + " AND f.verified", tenantId);
            Map<String, Object> twoFactor = new LinkedHashMap<>();
            twoFactor.put("usersWithTwoFactor", withTwoFactor);
            twoFactor.put("percent", users == 0 ? 0 : Math.round(withTwoFactor * 100.0 / users));
            out.put("twoFactor", twoFactor);

            out.put("recentSecurityEvents", recentEvents(tenantId));
            return out;
        });
    }

    private static String tenantFilter(String alias, UUID tenantId) {
        return tenantId != null ? " WHERE " + alias + ".tenant_id = ?" : "";
    }

    /** End users only: the reserved platform tenant holds operators. */
    private static String userFilter(UUID tenantId) {
        return " WHERE u.tenant_id <> (SELECT id FROM platform.tenant WHERE slug = 'platform')"
                + (tenantId != null ? " AND u.tenant_id = ?" : "");
    }

    private long count(String sql, UUID... tenantId) {
        Object[] args = tenantId.length > 0 && tenantId[0] != null ? new Object[] {tenantId[0]} : new Object[0];
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value != null ? value : 0;
    }

    private long loginCount(UUID tenantId, String resultCondition, Instant since) {
        String sql = "SELECT count(*) FROM platform.login_history h WHERE h.result " + resultCondition + " AND h.created_at > ?"
                + (tenantId != null ? " AND h.tenant_id = ?" : "");
        Long value = tenantId != null
                ? jdbc.queryForObject(sql, Long.class, Timestamp.from(since), tenantId)
                : jdbc.queryForObject(sql, Long.class, Timestamp.from(since));
        return value != null ? value : 0;
    }

    /** Sign-ins per day (UTC) for the last 14 days, oldest first, with days without any included. */
    private List<Map<String, Object>> daily(UUID tenantId, Instant now) {
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate first = today.minusDays(13);
        String sql = "SELECT (h.created_at AT TIME ZONE 'UTC')::date AS day,"
                + " count(*) FILTER (WHERE h.result = 'SUCCESS') AS ok, count(*) FILTER (WHERE h.result <> 'SUCCESS') AS failed"
                + " FROM platform.login_history h WHERE h.created_at >= ?" + (tenantId != null ? " AND h.tenant_id = ?" : "")
                + " GROUP BY 1";
        Map<LocalDate, long[]> byDay = new LinkedHashMap<>();
        Object[] args = tenantId != null
                ? new Object[] {Timestamp.from(first.atStartOfDay(ZoneOffset.UTC).toInstant()), tenantId}
                : new Object[] {Timestamp.from(first.atStartOfDay(ZoneOffset.UTC).toInstant())};
        jdbc.query(sql, rs -> {
            byDay.put(rs.getDate("day").toLocalDate(), new long[] {rs.getLong("ok"), rs.getLong("failed")});
        }, args);
        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            long[] v = byDay.getOrDefault(d, new long[] {0, 0});
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("day", d.toString());
            row.put("succeeded", v[0]);
            row.put("failed", v[1]);
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> recentEvents(UUID tenantId) {
        String in = String.join(",", SECURITY_ACTIONS.stream().map(a -> "?").toList());
        String sql = "SELECT l.created_at, l.action, l.actor_type, l.metadata ->> 'actorEmail' AS actor, t.name AS tenant, app.name AS app"
                + " FROM platform.audit_log l LEFT JOIN platform.tenant t ON t.id = l.tenant_id"
                + " LEFT JOIN platform.application app ON app.id = l.application_id"
                + " WHERE (l.action IN (" + in + ") OR l.action LIKE '%.failed' OR l.action LIKE '%reuse%')"
                + (tenantId != null ? " AND l.tenant_id = ?" : "")
                + " ORDER BY l.created_at DESC LIMIT 10";
        List<Object> args = new ArrayList<>(SECURITY_ACTIONS);
        if (tenantId != null) {
            args.add(tenantId);
        }
        return jdbc.query(sql, (rs, i) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("at", rs.getTimestamp("created_at").toInstant().toString());
            row.put("action", rs.getString("action"));
            row.put("actor", rs.getString("actor") != null ? rs.getString("actor") : rs.getString("actor_type"));
            row.put("tenant", rs.getString("tenant"));
            row.put("application", rs.getString("app"));
            return row;
        }, args.toArray());
    }
}
