package com.secureone.auth.admin.audit;

import com.secureone.auth.tenant.RlsScope;
import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Searching and exporting the audit log. Who may ask is decided before this runs (the admin interceptor: an
 * application's admins for that application, platform super admins for everything), so the query itself runs
 * across tenants and narrows by the filters.
 */
@Service
public class AuditQueryService {

    /** Most rows one export returns. */
    public static final int EXPORT_LIMIT = 100_000;

    private final JdbcTemplate jdbc;

    public AuditQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Filter(
            UUID applicationId, UUID tenantId, Instant from, Instant to, String action, String actor, String result) {}

    public record Row(
            UUID id,
            Instant timestamp,
            String actor,
            String action,
            String target,
            String ip,
            String result,
            String tenant,
            String application) {}

    public List<Row> search(Filter filter, int limit) {
        List<Object> args = new ArrayList<>();
        String sql = sql(filter, args) + " ORDER BY l.created_at DESC LIMIT " + Math.max(1, Math.min(limit, EXPORT_LIMIT));
        return RlsScope.asPlatform(() -> jdbc.query(sql, this::row, args.toArray()));
    }

    /** CSV (RFC 4180), newest first, at most {@link #EXPORT_LIMIT} rows. */
    public void exportCsv(Filter filter, Writer out) throws IOException {
        out.write("timestamp,actor,action,target,result,ip,tenant,application,id\r\n");
        for (Row r : search(filter, EXPORT_LIMIT)) {
            out.write(String.join(",",
                    csv(r.timestamp() != null ? r.timestamp().toString() : ""), csv(r.actor()), csv(r.action()), csv(r.target()),
                    csv(r.result()), csv(r.ip()), csv(r.tenant()), csv(r.application()), csv(String.valueOf(r.id()))));
            out.write("\r\n");
        }
        out.flush();
    }

    private static String sql(Filter f, List<Object> args) {
        StringBuilder sql = new StringBuilder("""
                SELECT l.id, l.created_at, l.action, l.target_type, l.ip, l.metadata ->> 'actorEmail' AS actor,
                       l.metadata ->> 'targetLabel' AS target_label, coalesce(l.metadata ->> 'result', 'success') AS result,
                       t.name AS tenant, a.name AS app
                FROM platform.audit_log l
                LEFT JOIN platform.tenant t ON t.id = l.tenant_id
                LEFT JOIN platform.application a ON a.id = l.application_id
                WHERE 1 = 1""");
        if (f.applicationId() != null) {
            sql.append(" AND l.application_id = ?");
            args.add(f.applicationId());
        }
        if (f.tenantId() != null) {
            sql.append(" AND l.tenant_id = ?");
            args.add(f.tenantId());
        }
        if (f.from() != null) {
            sql.append(" AND l.created_at >= ?");
            args.add(Timestamp.from(f.from()));
        }
        if (f.to() != null) {
            sql.append(" AND l.created_at < ?");
            args.add(Timestamp.from(f.to()));
        }
        if (f.action() != null && !f.action().isBlank()) {
            sql.append(" AND l.action ILIKE ?");
            args.add("%" + like(f.action()) + "%");
        }
        if (f.actor() != null && !f.actor().isBlank()) {
            sql.append(" AND l.metadata ->> 'actorEmail' ILIKE ?");
            args.add("%" + like(f.actor()) + "%");
        }
        if ("success".equals(f.result()) || "failure".equals(f.result())) {
            sql.append(" AND coalesce(l.metadata ->> 'result', 'success') = ?");
            args.add(f.result());
        }
        return sql.toString();
    }

    private Row row(ResultSet rs, int i) throws SQLException {
        String label = rs.getString("target_label");
        return new Row(
                rs.getObject("id", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("actor") != null ? rs.getString("actor") : "system",
                rs.getString("action"),
                label != null ? label : (rs.getString("target_type") != null ? rs.getString("target_type") : "—"),
                rs.getString("ip"),
                rs.getString("result"),
                rs.getString("tenant"),
                rs.getString("app"));
    }

    private static String like(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Quotes a CSV field; a leading = + - @ is prefixed so spreadsheets do not run it as a formula. */
    static String csv(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
