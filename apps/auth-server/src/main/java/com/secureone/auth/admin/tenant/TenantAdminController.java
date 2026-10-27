package com.secureone.auth.admin.tenant;

import com.secureone.auth.admin.tenant.TenantAdminDtos.TenantCreateRequest;
import com.secureone.auth.admin.tenant.TenantAdminDtos.TenantResponse;
import com.secureone.auth.admin.tenant.TenantAdminDtos.TenantUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.secureone.auth.admin.tenant.TenantWorkspaceService.TenantAdminOperator;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin — tenants", description = "Multi-tenant organizations")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1/tenants")
public class TenantAdminController {

    private final TenantAdminService service;
    private final TenantWorkspaceService workspace;

    public TenantAdminController(TenantAdminService service, TenantWorkspaceService workspace) {
        this.service = service;
        this.workspace = workspace;
    }

    @GetMapping
    public List<TenantResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public TenantResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse create(@Valid @RequestBody TenantCreateRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public TenantResponse update(@PathVariable UUID id, @Valid @RequestBody TenantUpdateRequest request) {
        TenantResponse before = service.get(id);
        TenantResponse after = service.update(id, request);
        // Suspending signs every user out: grants, browser sessions, and a back-channel logout to their apps.
        if ("active".equalsIgnoreCase(before.status()) && !"active".equalsIgnoreCase(after.status())) {
            int users = termination.endAll(id, after.slug());
            service.recordAccessEnded(id, after.name(), users, "suspended");
        }
        return after;
    }

    /**
     * Deletes the tenant with everything in it. Needs a recent second step and the tenant's slug typed again
     * ({@code ?confirm=slug}), like GitHub's repository deletion; its users are signed out first.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            jakarta.servlet.http.HttpServletRequest stepUpRequest,
            @PathVariable UUID id,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String confirm) {
        stepUp.require(stepUpRequest);
        TenantResponse tenant = service.get(id);
        if (confirm == null || !confirm.trim().equalsIgnoreCase(tenant.slug())) {
            throw new IllegalArgumentException("Type the tenant's slug (" + tenant.slug() + ") to confirm deletion.");
        }
        service.assertDeletable(id);
        int users = termination.endAll(id, tenant.slug());
        service.recordAccessEnded(id, tenant.name(), users, "deleted");
        service.delete(id);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private TenantAccessTermination termination;

    @org.springframework.beans.factory.annotation.Autowired
    private com.secureone.auth.admin.AdminStepUpService stepUp;

    /** Super-admin: users with Tenant Admin role in this tenant. */
    @GetMapping("/{tenantId}/admin-operators")
    public List<TenantAdminOperator> listAdminOperators(@PathVariable UUID tenantId) {
        service.get(tenantId);
        return workspace.listTenantAdminOperators(tenantId);
    }

    @PostMapping("/{tenantId}/admin-operators/{userId}/applications/{applicationId}")
    public java.util.Map<String, Object> assignAdminOperator(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID tenantId,
            @PathVariable UUID userId,
            @PathVariable UUID applicationId) {
        service.get(tenantId);
        var user = workspace.assignTenantAdminRole(authentication, actAsEmail, userId, applicationId);
        return java.util.Map.of("ok", true, "userId", user.id(), "email", user.email());
    }

    @DeleteMapping("/{tenantId}/admin-operators/{userId}/applications/{applicationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAdminOperator(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID tenantId,
            @PathVariable UUID userId,
            @PathVariable UUID applicationId) {
        service.get(tenantId);
        workspace.revokeTenantAdminRole(authentication, actAsEmail, userId, applicationId);
    }
}
