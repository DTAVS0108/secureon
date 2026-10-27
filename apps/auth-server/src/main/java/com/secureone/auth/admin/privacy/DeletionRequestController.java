package com.secureone.auth.admin.privacy;

import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.admin.user.UserAdminService;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.tenant.RlsScope;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account deletion requests (GDPR right to erasure). A user's request disables the account at once; the
 * tenant's administrators see it here and delete the account, or cancel when it was a mistake. Requests not
 * handled within 30 days are completed automatically, every night.
 */
@Tag(name = "Admin — deletion requests", description = "Users who asked for their account to be deleted")
@RestController
public class DeletionRequestController {

    private static final Logger log = LoggerFactory.getLogger(DeletionRequestController.class);
    static final Duration DEADLINE = Duration.ofDays(30);

    private final JdbcTemplate jdbc;
    private final UserAdminService users;
    private final AuditService audit;

    public DeletionRequestController(JdbcTemplate jdbc, UserAdminService users, AuditService audit) {
        this.jdbc = jdbc;
        this.users = users;
        this.audit = audit;
    }

    @GetMapping("/api/admin/v1/tenants/{tenantId}/deletion-requests")
    public List<Map<String, Object>> list(@PathVariable UUID tenantId) {
        return jdbc.query("""
                SELECT r.user_id, r.requested_at, u.email, u.display_name
                FROM platform.account_deletion_request r JOIN platform.user_account u ON u.id = r.user_id
                WHERE r.tenant_id = ? ORDER BY r.requested_at
                """, (rs, i) -> {
                    Instant requested = rs.getTimestamp("requested_at").toInstant();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("userId", rs.getObject("user_id", UUID.class).toString());
                    row.put("email", rs.getString("email"));
                    row.put("displayName", rs.getString("display_name"));
                    row.put("requestedAt", requested.toString());
                    row.put("deleteBy", requested.plus(DEADLINE).toString());
                    return row;
                }, tenantId);
    }

    /** Deletes the account now (everything: grants, sessions, memberships, roles). */
    @PostMapping("/api/admin/v1/tenants/{tenantId}/deletion-requests/{userId}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(@PathVariable UUID tenantId, @PathVariable UUID userId) {
        requireRequest(tenantId, userId);
        users.delete(userId); // the request row goes with the account (cascade)
    }

    /** The request was a mistake: the account is turned back on. */
    @PostMapping("/api/admin/v1/tenants/{tenantId}/deletion-requests/{userId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID tenantId, @PathVariable UUID userId) {
        requireRequest(tenantId, userId);
        jdbc.update("DELETE FROM platform.account_deletion_request WHERE user_id = ?", userId);
        users.setStatus(userId, "ACTIVE");
        audit.record(tenantId, "admin", "account.deletion_cancelled", "user_account", userId, null, true);
    }

    private void requireRequest(UUID tenantId, UUID userId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM platform.account_deletion_request WHERE user_id = ? AND tenant_id = ?",
                Integer.class, userId, tenantId);
        if (n == null || n == 0) {
            throw new ResourceNotFoundException("No deletion request for this user.");
        }
    }

    /** Nightly: requests older than 30 days are completed. */
    @Scheduled(cron = "${secureone.privacy.deletion-cron:0 40 3 * * *}")
    public void completeOverdue() {
        List<UUID> overdue = RlsScope.asPlatform(() -> jdbc.queryForList(
                "SELECT user_id FROM platform.account_deletion_request WHERE requested_at < now() - make_interval(days => ?)",
                UUID.class, (int) DEADLINE.toDays()));
        for (UUID userId : overdue) {
            try {
                RlsScope.runAsPlatform(() -> users.delete(userId));
                log.info("Account {} deleted: its deletion request is 30 days old", userId);
            } catch (RuntimeException e) {
                log.warn("Overdue deletion of account {} failed: {}", userId, e.getMessage());
            }
        }
    }
}
