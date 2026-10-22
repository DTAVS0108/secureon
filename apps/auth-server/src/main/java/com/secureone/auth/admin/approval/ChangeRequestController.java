package com.secureone.auth.admin.approval;

import com.secureone.auth.admin.AdminAccessService;
import com.secureone.auth.admin.approval.ChangeRequestViews.ChangeRequestView;
import com.secureone.auth.admin.approval.ChangeRequests.Row;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Changes waiting for approval, shown on Access review. Platform operators see and decide every request;
 * a tenant super admin those of their tenant. Nobody decides their own request.
 */
@Tag(name = "Admin — change approvals", description = "High-impact changes that wait for a second admin")
@RestController
public class ChangeRequestController {

    @org.springframework.beans.factory.annotation.Autowired
    private com.secureone.auth.admin.AdminStepUpService stepUp;

    private final ChangeRequestService service;
    private final AdminAccessService access;
    private final ChangeRequestViews views;

    public ChangeRequestController(ChangeRequestService service, AdminAccessService access, ChangeRequestViews views) {
        this.service = service;
        this.access = access;
        this.views = views;
    }

    public record DecisionRequest(String note) {}

    @GetMapping("/api/admin/v1/change-requests")
    public List<ChangeRequestView> platformList(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs) {
        access.requirePlatformSettingsAccess(authentication, actAs);
        return service.list(null).stream().map(row -> views.view(row, authentication, actAs)).toList();
    }

    @GetMapping("/api/admin/v1/tenants/{tenantId}/change-requests")
    public List<ChangeRequestView> tenantList(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs,
            @PathVariable UUID tenantId) {
        access.requireTenantGovernanceAccess(authentication, actAs, tenantId);
        return service.list(tenantId).stream().map(row -> views.view(row, authentication, actAs)).toList();
    }

    @PostMapping("/api/admin/v1/change-requests/{id}/approve")
    public ChangeRequestView approve(
            jakarta.servlet.http.HttpServletRequest stepUpRequest,
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs,
            @PathVariable UUID id,
            @RequestBody(required = false) DecisionRequest body) {
        stepUp.require(stepUpRequest); // a recent second step from the admin
        views.requireApprover(service.get(id), authentication, actAs);
        Row row = service.approve(id, views.email(authentication, actAs), body == null ? null : body.note());
        return views.view(row, authentication, actAs);
    }

    @PostMapping("/api/admin/v1/change-requests/{id}/reject")
    public ChangeRequestView reject(
            jakarta.servlet.http.HttpServletRequest stepUpRequest,
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs,
            @PathVariable UUID id,
            @RequestBody(required = false) DecisionRequest body) {
        stepUp.require(stepUpRequest); // a recent second step from the admin
        views.requireApprover(service.get(id), authentication, actAs);
        Row row = service.reject(id, views.email(authentication, actAs), body == null ? null : body.note());
        return views.view(row, authentication, actAs);
    }

    @PostMapping("/api/admin/v1/change-requests/{id}/withdraw")
    public ChangeRequestView withdraw(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs,
            @PathVariable UUID id) {
        Row row = service.withdraw(id, views.email(authentication, actAs));
        return views.view(row, authentication, actAs);
    }
}
