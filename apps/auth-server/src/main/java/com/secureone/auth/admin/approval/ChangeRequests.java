package com.secureone.auth.admin.approval;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Storage for {@code platform.change_request}: changes waiting for a second admin's approval. */
@Repository
public class ChangeRequests {

    public enum Status { PENDING, APPROVED, REJECTED, WITHDRAWN, FAILED }

    public record Row(
            UUID id,
            String action,
            UUID tenantId,
            UUID applicationId,
            UUID targetId,
            String targetLabel,
            String reason,
            Status status,
            String requestedBy,
            Instant requestedAt,
            String decidedBy,
            Instant decidedAt,
            String decisionNote,
            String tenantName,
            /** What the change does, as JSON (values to apply, the "before" picture); null for client deletes. */
            String payload,
            /** The change in words for the approver, e.g. "Mode: required → off". */
            String details) {}

    private static final String COLUMNS = """
            id, action, tenant_id, application_id, target_id, target_label, reason, status,
            requested_by, requested_at, decided_by, decided_at, decision_note,
            (SELECT t.name FROM platform.tenant t WHERE t.id = tenant_id) AS tenant_name,
            payload::text AS payload, details
            """;

    private final JdbcTemplate jdbc;

    public ChangeRequests(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Row create(String action, UUID tenantId, UUID applicationId, UUID targetId, String targetLabel,
                      String reason, String requestedBy) {
        return create(action, tenantId, applicationId, targetId, targetLabel, reason, requestedBy, null, null);
    }

    public Row create(String action, UUID tenantId, UUID applicationId, UUID targetId, String targetLabel,
                      String reason, String requestedBy, String payloadJson, String details) {
        UUID id = jdbc.queryForObject(
                """
                INSERT INTO platform.change_request
                    (action, tenant_id, application_id, target_id, target_label, reason, requested_by, payload, details)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?) RETURNING id
                """,
                UUID.class,
                action, tenantId, applicationId, targetId, targetLabel, reason, requestedBy, payloadJson,
                details != null && details.length() > 1000 ? details.substring(0, 1000) : details);
        return find(id).orElseThrow();
    }

    public Optional<Row> find(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM platform.change_request WHERE id = ?", ChangeRequests::map, id)
                .stream().findFirst();
    }

    public Optional<Row> findPending(String action, UUID targetId) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM platform.change_request WHERE action = ? AND target_id = ? AND status = 'PENDING'",
                ChangeRequests::map, action, targetId).stream().findFirst();
    }

    /** Pending requests first, then the most recent decisions; {@code tenantId} null means every tenant. */
    public List<Row> list(UUID tenantId, int decidedLimit) {
        String where = tenantId == null ? "" : " AND tenant_id = ?";
        Object[] args = tenantId == null ? new Object[0] : new Object[] {tenantId};
        List<Row> pending = jdbc.query(
                "SELECT " + COLUMNS + " FROM platform.change_request WHERE status = 'PENDING'" + where
                        + " ORDER BY requested_at",
                ChangeRequests::map, args);
        List<Row> decided = jdbc.query(
                "SELECT " + COLUMNS + " FROM platform.change_request WHERE status <> 'PENDING'" + where
                        + " ORDER BY decided_at DESC NULLS LAST LIMIT " + decidedLimit,
                ChangeRequests::map, args);
        return java.util.stream.Stream.concat(pending.stream(), decided.stream()).toList();
    }

    /** Moves a PENDING request to {@code status}; false when it was no longer pending (someone else decided). */
    public boolean decide(UUID id, Status status, String decidedBy, String note) {
        return jdbc.update(
                """
                UPDATE platform.change_request
                SET status = ?, decided_by = ?, decided_at = now(), decision_note = ?
                WHERE id = ? AND status = 'PENDING'
                """,
                status.name(), decidedBy, note, id) == 1;
    }

    private static Row map(ResultSet rs, int i) throws SQLException {
        return new Row(
                rs.getObject("id", UUID.class),
                rs.getString("action"),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("application_id", UUID.class),
                rs.getObject("target_id", UUID.class),
                rs.getString("target_label"),
                rs.getString("reason"),
                Status.valueOf(rs.getString("status")),
                rs.getString("requested_by"),
                instant(rs.getTimestamp("requested_at")),
                rs.getString("decided_by"),
                instant(rs.getTimestamp("decided_at")),
                rs.getString("decision_note"),
                rs.getString("tenant_name"),
                rs.getString("payload"),
                rs.getString("details"));
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
