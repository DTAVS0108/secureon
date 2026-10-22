package com.secureone.auth.admin.tenant;

import com.secureone.auth.admin.AdminAccessService;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.mfa.MfaPolicyDefaults;
import com.secureone.auth.twostep.TwoStepPolicyResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant defaults for two-step verification: what every application of the tenant starts from, between the
 * platform policy and each application's own settings. Only what the platform's locks allow can be changed;
 * the tenant can lock settings for its applications in turn.
 */
@Tag(name = "Admin — tenants", description = "Two-step verification defaults of a tenant")
@RestController
@RequestMapping("/api/admin/v1/tenants/{tenantId}/two-step-policy")
public class TenantTwoStepPolicyController {

    private final TwoStepPolicyResolver resolver;
    private final AdminAccessService access;
    private final AuditService audit;

    public TenantTwoStepPolicyController(TwoStepPolicyResolver resolver, AdminAccessService access, AuditService audit) {
        this.resolver = resolver;
        this.access = access;
        this.audit = audit;
    }

    @Operation(summary = "Effective tenant defaults, the tenant's own overrides and locks, and the platform's locks")
    @GetMapping
    public Map<String, Object> get(Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs, @PathVariable UUID tenantId) {
        access.requireTenantGovernanceAccess(authentication, actAs, tenantId);
        return view(tenantId);
    }

    @Operation(summary = "Save the tenant's defaults and locks (refused where the platform does not allow it)")
    @PutMapping
    public Map<String, Object> save(Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs,
            @PathVariable UUID tenantId, @RequestBody Map<String, Object> body) {
        access.requireTenantGovernanceAccess(authentication, actAs, tenantId);
        // Store only what differs from the platform, so later platform changes still apply.
        Map<String, Object> platform = resolver.platform();
        Map<String, Object> changes = new LinkedHashMap<>(MfaPolicyDefaults.sanitizeOverride(body));
        Object locks = body.get(MfaPolicyDefaults.LOCKS);
        changes.entrySet().removeIf(e -> !MfaPolicyDefaults.LOCKS.equals(e.getKey()) && e.getValue().equals(platform.get(e.getKey())));
        changes.put(MfaPolicyDefaults.LOCKS, MfaPolicyDefaults.locks(locks));
        resolver.saveTenant(tenantId, changes);
        audit.record(tenantId, "admin", "tenant.two_step_policy_saved", "tenant", tenantId, String.valueOf(changes.keySet()), true);
        return view(tenantId);
    }

    @Operation(summary = "Back to the platform policy (removes the tenant's defaults and locks)")
    @DeleteMapping
    public Map<String, Object> reset(Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs, @PathVariable UUID tenantId) {
        access.requireTenantGovernanceAccess(authentication, actAs, tenantId);
        resolver.resetTenant(tenantId);
        audit.record(tenantId, "admin", "tenant.two_step_policy_reset", "tenant", tenantId, "platform defaults", true);
        return view(tenantId);
    }

    private Map<String, Object> view(UUID tenantId) {
        TwoStepPolicyResolver.Resolved resolved = resolver.resolveTenant(tenantId);
        Map<String, Object> out = new LinkedHashMap<>(resolved.policy());
        out.put("scope", "tenant");
        out.put("platformLocks", resolved.locks());
        Map<String, Object> overrides = resolver.tenantOverrides(tenantId);
        out.put("overrides", overrides.keySet().stream().filter(k -> !MfaPolicyDefaults.LOCKS.equals(k)).toList());
        out.put("inheritsPlatformDefaults", overrides.isEmpty());
        return out;
    }
}
