package com.secureone.auth.admin.approval;

import com.secureone.auth.admin.AdminAccessService;
import com.secureone.auth.tenant.RlsScope;
import com.secureone.auth.twostep.TwoStepPolicyResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Two Factor Authentication on Access review: where it is off or only optional, who is exempt, which admins have
 * no second step, and whether changes that weaken it wait for a second admin. Platform operators see every
 * tenant; a tenant super admin their own.
 */
@Tag(name = "Admin — change approvals", description = "Two Factor Authentication review")
@RestController
public class TwoFactorReviewController {

    public record ApplicationRow(UUID id, String name, String tenantName, String mode, boolean managed,
            boolean switchedOff, boolean lockedAbove) {}

    public record ExemptionRow(UUID userId, String email, UUID applicationId, String applicationName,
            Instant until, String reason, String exemptedBy) {}

    public record AdminRow(UUID userId, String email, String role) {}

    public record Review(boolean approvalsActive, int approvers, List<ApplicationRow> applications,
            List<ExemptionRow> exemptions, List<AdminRow> adminsWithoutTwoFactor) {}

    private static final UUID NO_APPLICATION = new UUID(0L, 0L);

    private final AdminAccessService access;
    private final TwoStepPolicyResolver resolver;
    private final JdbcTemplate jdbc;
    private final org.springframework.transaction.support.TransactionTemplate platformRead;

    public TwoFactorReviewController(AdminAccessService access, TwoStepPolicyResolver resolver, JdbcTemplate jdbc,
            org.springframework.transaction.PlatformTransactionManager transactions) {
        this.access = access;
        this.resolver = resolver;
        this.jdbc = jdbc;
        // Reads across tenants (platform operators, every tenant's users) in their own platform-scoped transaction:
        // the request's transaction may already be limited to one tenant.
        this.platformRead = new org.springframework.transaction.support.TransactionTemplate(transactions);
        this.platformRead.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.platformRead.setReadOnly(true);
    }

    @Operation(summary = "Two Factor Authentication review for every tenant (platform operators)")
    @GetMapping("/api/admin/v1/two-factor/review")
    public Review platform(Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs) {
        access.requirePlatformSettingsAccess(authentication, actAs);
        return RlsScope.asPlatform(() -> platformRead.execute(status -> review(null)));
    }

    @Operation(summary = "Two Factor Authentication review for one tenant")
    @GetMapping("/api/admin/v1/tenants/{tenantId}/two-factor/review")
    public Review tenant(Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs, @PathVariable UUID tenantId) {
        access.requireTenantGovernanceAccess(authentication, actAs, tenantId);
        return RlsScope.asPlatform(() -> platformRead.execute(status -> review(tenantId)));
    }

    private Review review(UUID tenantId) {
        int approvers = approvers(tenantId);
        return new Review(approvers >= 2, approvers, applications(tenantId), exemptions(tenantId), adminsWithoutTwoFactor(tenantId));
    }

    /** People who can approve: active platform operators, plus the tenant's super admins for a tenant view. */
    private int approvers(UUID tenantId) {
        Integer operators = jdbc.queryForObject(
                "SELECT count(*) FROM platform.platform_operator p JOIN platform.user_account u ON u.id = p.user_id WHERE u.status = 'ACTIVE'",
                Integer.class);
        int count = operators != null ? operators : 0;
        if (tenantId != null) {
            Integer superAdmins = jdbc.queryForObject(
                    """
                    SELECT count(DISTINCT a.user_id) FROM platform.admin_console_access a
                    JOIN platform.user_account u ON u.id = a.user_id
                    WHERE a.tenant_id = ? AND a.role_type = 'TENANT_SUPER_ADMIN'
                      AND (a.expires_at IS NULL OR a.expires_at > now()) AND u.status = 'ACTIVE'
                    """,
                    Integer.class, tenantId);
            count += superAdmins != null ? superAdmins : 0;
        }
        return count;
    }

    private List<ApplicationRow> applications(UUID tenantId) {
        String where = tenantId == null ? "" : " WHERE a.tenant_id = ?";
        Object[] args = tenantId == null ? new Object[0] : new Object[] {tenantId};
        List<Object[]> rows = jdbc.query(
                "SELECT a.id, a.name, t.name FROM platform.application a JOIN platform.tenant t ON t.id = a.tenant_id"
                        + where + " ORDER BY t.name, a.name",
                (rs, i) -> new Object[] {rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)}, args);
        List<ApplicationRow> out = new ArrayList<>();
        for (Object[] r : rows) {
            UUID id = (UUID) r[0];
            TwoStepPolicyResolver.Resolved resolved = resolver.resolve(id);
            Map<String, Object> policy = resolved.policy();
            out.add(new ApplicationRow(id, (String) r[1], (String) r[2], String.valueOf(policy.get("mode")),
                    resolver.hasOwnSettings(id), resolver.switchedOff(id), resolved.locks().containsKey("mode")));
        }
        return out;
    }

    private List<ExemptionRow> exemptions(UUID tenantId) {
        String where = tenantId == null ? "" : " AND u.tenant_id = ?";
        Object[] args = tenantId == null ? new Object[0] : new Object[] {tenantId};
        return jdbc.query(
                """
                SELECT s.user_id, u.email, s.application_id, a.name AS app_name, s.exempt_until, s.exempt_reason, s.exempted_by
                FROM platform.user_two_step_state s
                JOIN platform.user_account u ON u.id = s.user_id
                LEFT JOIN platform.application a ON a.id = s.application_id
                WHERE s.exempt_until > now()""" + where + " ORDER BY s.exempt_until",
                (rs, i) -> {
                    UUID app = rs.getObject("application_id", UUID.class);
                    Timestamp until = rs.getTimestamp("exempt_until");
                    return new ExemptionRow(rs.getObject("user_id", UUID.class), rs.getString("email"),
                            NO_APPLICATION.equals(app) ? null : app, rs.getString("app_name"),
                            until != null ? until.toInstant() : null, rs.getString("exempt_reason"),
                            rs.getString("exempted_by"));
                },
                args);
    }

    /** Console admins (and, for the platform view, platform operators) without an authenticator or passkey. */
    private List<AdminRow> adminsWithoutTwoFactor(UUID tenantId) {
        String noFactor = """
                NOT EXISTS (SELECT 1 FROM platform.mfa_factor f
                            WHERE f.user_id = u.id AND f.verified AND f.type IN ('totp', 'passkey'))
                """;
        List<AdminRow> out = new ArrayList<>();
        if (tenantId == null) {
            out.addAll(jdbc.query(
                    "SELECT u.id, u.email FROM platform.platform_operator p JOIN platform.user_account u ON u.id = p.user_id"
                            + " WHERE u.status = 'ACTIVE' AND " + noFactor + " ORDER BY u.email",
                    (rs, i) -> new AdminRow(rs.getObject(1, UUID.class), rs.getString(2), "Platform operator")));
        }
        String where = tenantId == null ? "" : " AND a.tenant_id = ?";
        Object[] args = tenantId == null ? new Object[0] : new Object[] {tenantId};
        out.addAll(jdbc.query(
                """
                SELECT DISTINCT u.id, u.email, a.role_type FROM platform.admin_console_access a
                JOIN platform.user_account u ON u.id = a.user_id
                WHERE (a.expires_at IS NULL OR a.expires_at > now()) AND u.status = 'ACTIVE'""" + " AND " + noFactor + where
                        + " ORDER BY u.email",
                (rs, i) -> new AdminRow(rs.getObject(1, UUID.class), rs.getString(2), role(rs.getString(3))),
                args));
        return out;
    }

    private static String role(String roleType) {
        return switch (roleType) {
            case "TENANT_SUPER_ADMIN" -> "Tenant super admin";
            case "TENANT_ADMIN" -> "Tenant admin";
            default -> "Application admin";
        };
    }
}
