package com.secureone.auth.admin.console;

import com.secureone.auth.admin.AdminAccessService;
import com.secureone.auth.admin.AdminOperatorService;
import com.secureone.auth.admin.console.AdminConsoleAccessService.ConsoleAccessAssignment;
import com.secureone.auth.audit.AuditService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Console roles in one tenant. Platform super admins manage every role; a tenant super admin
 * manages application admins and tenant admins in their own tenant, but not tenant super admins.
 */
@Tag(name = "Admin — console access", description = "Who may sign in to SecureOne Admin and at which scope")
@RestController
@RequestMapping("/api/admin/v1/tenants/{tenantId}/console-access")
public class AdminConsoleAccessController {

    private final AdminConsoleAccessService consoleAccess;
    private final AdminOperatorService operators;
    private final AdminAccessService access;
    private final AuditService audit;

    public AdminConsoleAccessController(
            AdminConsoleAccessService consoleAccess,
            AdminOperatorService operators,
            AdminAccessService access,
            AuditService audit) {
        this.consoleAccess = consoleAccess;
        this.operators = operators;
        this.access = access;
        this.audit = audit;
    }

    public record GrantConsoleAccessRequest(UUID userId, String roleType, UUID applicationId) {}

    @GetMapping
    public List<ConsoleAccessAssignment> list(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID tenantId) {
        access.requireTenantGovernanceAccess(authentication, actAsEmail, tenantId);
        return consoleAccess.listForTenant(tenantId);
    }

    @PostMapping
    public ConsoleAccessAssignment grant(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID tenantId,
            @RequestBody GrantConsoleAccessRequest body) {
        access.requireTenantGovernanceAccess(authentication, actAsEmail, tenantId);
        if (body.userId() == null || body.roleType() == null || body.roleType().isBlank()) {
            throw new IllegalArgumentException("userId and roleType are required");
        }
        AdminConsoleRoleType roleType;
        try {
            roleType = AdminConsoleRoleType.valueOf(body.roleType().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown roleType: " + body.roleType());
        }
        boolean platform = operators.isPlatformSuperAdmin(authentication, actAsEmail);
        if (roleType == AdminConsoleRoleType.TENANT_SUPER_ADMIN && !platform) {
            throw new AccessDeniedException("Only a platform super admin can grant tenant super admin");
        }
        UUID grantedBy = operators
                .resolveTenantUser(authentication, actAsEmail)
                .map(u -> u.getId())
                .orElse(null);
        ConsoleAccessAssignment granted =
                consoleAccess.grant(tenantId, body.userId(), roleType, body.applicationId(), grantedBy);
        audit.record(
                tenantId,
                body.applicationId(),
                actor(authentication, actAsEmail),
                "console_access.granted",
                "user_account",
                body.userId(),
                granted.email() + " " + roleType,
                true);
        return granted;
    }

    @DeleteMapping("/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID tenantId,
            @PathVariable UUID assignmentId) {
        access.requireTenantGovernanceAccess(authentication, actAsEmail, tenantId);
        boolean platform = operators.isPlatformSuperAdmin(authentication, actAsEmail);
        consoleAccess.revoke(tenantId, assignmentId, platform);
        audit.record(
                tenantId, actor(authentication, actAsEmail), "console_access.revoked", "admin_console_access",
                assignmentId, assignmentId.toString(), true);
    }

    private static String actor(Authentication authentication, String actAsEmail) {
        String name = authentication != null ? authentication.getName() : "admin";
        return actAsEmail != null && !actAsEmail.isBlank() ? name + " as " + actAsEmail : name;
    }
}
