package com.secureone.auth.admin.tenant;

import com.secureone.auth.admin.AdminAccessService;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.mfa.MfaPolicyDefaults;
import com.secureone.auth.twostep.TwoStepPolicyResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final com.secureone.auth.admin.approval.TwoFactorChangeRequests twoFactorChanges;
    private final com.secureone.auth.admin.approval.ChangeRequestViews changeViews;

    public TenantTwoStepPolicyController(TwoStepPolicyResolver resolver, AdminAccessService access, AuditService audit,
            com.secureone.auth.admin.approval.TwoFactorChangeRequests twoFactorChanges,
            com.secureone.auth.admin.approval.ChangeRequestViews changeViews) {
        this.resolver = resolver;
        this.access = access;
        this.audit = audit;
        this.twoFactorChanges = twoFactorChanges;
        this.changeViews = changeViews;
    }

    @Operation(summary = "Effective tenant defaults, the tenant's own overrides and locks, and the platform's locks")
    @GetMapping
    public Map<String, Object> get(Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs, @PathVariable UUID tenantId) {
        access.requireTenantGovernanceAccess(authentication, actAs, tenantId);
        return view(tenantId, authentication, actAs);
    }

    @Operation(summary = "Save the tenant's defaults and locks (weaker values or removed locks wait for a platform operator)")
    @PutMapping
    public Map<String, Object> save(jakarta.servlet.http.HttpServletRequest request, Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs,
            @PathVariable UUID tenantId, @RequestBody Map<String, Object> body) {
        access.requireTenantGovernanceAccess(authentication, actAs, tenantId);
        // Store only what differs from the platform, so later platform changes still apply.
        Map<String, Object> platform = resolver.platform();
        Map<String, Object> proposed = new LinkedHashMap<>(MfaPolicyDefaults.sanitizeOverride(body));
        Object locks = body.get(MfaPolicyDefaults.LOCKS);
        proposed.put(MfaPolicyDefaults.LOCKS, MfaPolicyDefaults.locks(locks));
        Map<String, Object> changes = new LinkedHashMap<>(proposed);
        changes.entrySet().removeIf(e -> !MfaPolicyDefaults.LOCKS.equals(e.getKey()) && e.getValue().equals(platform.get(e.getKey())));
        List<String> weakenings = MfaPolicyDefaults.weakenings(currentTenant(tenantId), proposed);
        return saveOrRequest(request, authentication, actAs, tenantId, changes, weakenings,
                body.get("reason") != null ? String.valueOf(body.get("reason")) : null);
    }

    @Operation(summary = "Back to the platform policy (waits for a platform operator when that is weaker)")
    @DeleteMapping
    public Map<String, Object> reset(jakarta.servlet.http.HttpServletRequest request, Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs, @PathVariable UUID tenantId) {
        access.requireTenantGovernanceAccess(authentication, actAs, tenantId);
        Map<String, Object> platform = new LinkedHashMap<>(resolver.platform());
        platform.put(MfaPolicyDefaults.LOCKS, Map.of());
        List<String> weakenings = MfaPolicyDefaults.weakenings(currentTenant(tenantId), platform);
        if (weakenings.isEmpty()) {
            resolver.resetTenant(tenantId);
            audit.record(tenantId, "admin", "tenant.two_step_policy_reset", "tenant", tenantId, "platform defaults", true);
            return view(tenantId, authentication, actAs);
        }
        Map<String, Object> noOverrides = new LinkedHashMap<>();
        noOverrides.put(MfaPolicyDefaults.LOCKS, Map.of());
        return saveOrRequest(request, authentication, actAs, tenantId, noOverrides, weakenings, "Back to the platform policy");
    }

    /** The tenant's effective defaults with its own locks (what a change is compared with). */
    private Map<String, Object> currentTenant(UUID tenantId) {
        return resolver.resolveTenant(tenantId).policy();
    }

    /** Tightening saves now; weakening waits for a platform operator (or applies now when there is none other). */
    private Map<String, Object> saveOrRequest(jakarta.servlet.http.HttpServletRequest request,
            Authentication authentication, String actAs, UUID tenantId, Map<String, Object> changes,
            List<String> weakenings, String reason) {
        if (weakenings.isEmpty()) {
            resolver.saveTenant(tenantId, changes);
            audit.record(tenantId, "admin", "tenant.two_step_policy_saved", "tenant", tenantId, String.valueOf(changes.keySet()), true);
        } else {
            twoFactorChanges.tenantPolicy(tenantId, changes, weakenings, changeViews.email(authentication, actAs), reason, request);
        }
        return view(tenantId, authentication, actAs);
    }

    private Map<String, Object> view(UUID tenantId, Authentication authentication, String actAs) {
        Map<String, Object> out = view(tenantId);
        out.put("pendingChange", twoFactorChanges.pending(
                        com.secureone.auth.admin.approval.TwoFactorChangeRequests.TENANT_POLICY, tenantId)
                .map(row -> changeViews.view(row, authentication, actAs)).orElse(null));
        return out;
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
