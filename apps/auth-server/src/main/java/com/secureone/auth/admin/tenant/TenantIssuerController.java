package com.secureone.auth.admin.tenant;

import com.secureone.auth.admin.AdminAccessService;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.tenant.TenantDomainService;
import com.secureone.auth.tenant.TenantIssuers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A tenant's issuers: its own ({@code <issuer>/t/<slug>}), whether its clients may still use the shared one, and
 * its custom domains (each verified before it is served).
 */
@Tag(name = "Admin — tenant issuer and domains", description = "Issuer per tenant and custom domains")
@RestController
@RequestMapping("/api/admin/v1/tenants/{tenantId}/issuer")
public class TenantIssuerController {

    private final TenantIssuers issuers;
    private final TenantDomainService domains;
    private final AdminAccessService access;
    private final ObjectProvider<AuditService> audit;

    public TenantIssuerController(
            TenantIssuers issuers, TenantDomainService domains, AdminAccessService access, ObjectProvider<AuditService> audit) {
        this.issuers = issuers;
        this.domains = domains;
        this.access = access;
        this.audit = audit;
    }

    @Operation(summary = "The tenant's issuers, issuer mode and custom domains")
    @GetMapping
    public Map<String, Object> get(Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs, @PathVariable UUID tenantId) {
        access.requireTenantGovernanceAccess(authentication, actAs, tenantId);
        return view(tenantId);
    }

    @Operation(
            summary = "Issuer mode: 'shared' (the shared issuer and the tenant's own) or 'tenant' (its own issuers only)",
            description = "With 'tenant', the tenant's apps must use their tenant issuer: sign-ins and tokens on the shared issuer are refused.")
    @PutMapping
    public Map<String, Object> setMode(Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs, @PathVariable UUID tenantId,
            @RequestBody Map<String, Object> body) {
        access.requireTenantGovernanceAccess(authentication, actAs, tenantId);
        String mode = body.get("issuerMode") == null ? null : String.valueOf(body.get("issuerMode"));
        domains.setIssuerMode(tenantId, mode);
        audited(tenantId, "tenant.issuer_mode_changed", null, mode);
        return view(tenantId);
    }

    @Operation(summary = "Register a custom domain (pending until verified)")
    @PostMapping("/domains")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addDomain(Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs, @PathVariable UUID tenantId,
            @RequestBody Map<String, Object> body) {
        access.requireTenantGovernanceAccess(authentication, actAs, tenantId);
        var domain = domains.add(tenantId, body.get("domain") == null ? null : String.valueOf(body.get("domain")));
        audited(tenantId, "tenant.domain_added", domain.id(), domain.domain());
        return domains.view(domain);
    }

    @Operation(
            summary = "Check a custom domain",
            description = "Verified when the TXT record _secureone-challenge.<domain> holds the token, or the domain "
                    + "already points at SecureOne. Otherwise lastError says what is missing.")
    @PostMapping("/domains/{domainId}/verify")
    public Map<String, Object> verifyDomain(Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs, @PathVariable UUID tenantId,
            @PathVariable UUID domainId) {
        access.requireTenantGovernanceAccess(authentication, actAs, tenantId);
        boolean before = "VERIFIED".equals(domains.require(tenantId, domainId).status());
        var domain = domains.verify(tenantId, domainId);
        if (!before && "VERIFIED".equals(domain.status())) {
            audited(tenantId, "tenant.domain_verified", domain.id(), domain.domain());
        }
        return domains.view(domain);
    }

    @Operation(summary = "Remove a custom domain (it stops being served at once)")
    @DeleteMapping("/domains/{domainId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDomain(Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs, @PathVariable UUID tenantId,
            @PathVariable UUID domainId) {
        access.requireTenantGovernanceAccess(authentication, actAs, tenantId);
        var domain = domains.delete(tenantId, domainId);
        audited(tenantId, "tenant.domain_removed", domain.id(), domain.domain());
    }

    private Map<String, Object> view(UUID tenantId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("issuerMode", issuers.issuerMode(tenantId));
        out.put("sharedIssuer", issuers.sharedIssuer());
        out.put("issuers", issuers.issuersOf(tenantId));
        out.put("domains", domains.list(tenantId).stream().map(domains::view).toList());
        return out;
    }

    private void audited(UUID tenantId, String action, UUID targetId, String label) {
        audit.ifAvailable(a -> a.record(tenantId, "admin", action, "tenant", targetId != null ? targetId : tenantId,
                label, true));
    }
}
