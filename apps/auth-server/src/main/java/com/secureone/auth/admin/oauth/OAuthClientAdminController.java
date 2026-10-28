package com.secureone.auth.admin.oauth;

import com.secureone.auth.admin.AdminAccessService;
import com.secureone.auth.admin.approval.ChangeRequestService;
import com.secureone.auth.admin.approval.ChangeRequestViews;
import com.secureone.auth.admin.approval.ChangeRequestViews.ChangeRequestView;
import com.secureone.auth.admin.approval.ChangeRequests.Row;
import com.secureone.auth.admin.oauth.OAuthClientAdminDtos.OAuthClientCreateRequest;
import com.secureone.auth.admin.oauth.OAuthClientAdminDtos.OAuthClientCreateResult;
import com.secureone.auth.admin.oauth.OAuthClientAdminDtos.OAuthClientResponse;
import com.secureone.auth.admin.oauth.OAuthClientAdminDtos.OAuthClientSecretResponse;
import com.secureone.auth.admin.oauth.OAuthClientAdminDtos.OAuthClientUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin — OAuth clients", description = "Register and manage OAuth/OIDC client credentials")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1/oauth-clients")
public class OAuthClientAdminController {

    @org.springframework.beans.factory.annotation.Autowired
    private com.secureone.auth.admin.AdminStepUpService stepUp;

    private final OAuthClientAdminService service;
    private final AdminAccessService access;
    private final ChangeRequestService changeRequests;
    private final ChangeRequestViews views;

    public OAuthClientAdminController(
            OAuthClientAdminService service,
            AdminAccessService access,
            ChangeRequestService changeRequests,
            ChangeRequestViews views) {
        this.service = service;
        this.access = access;
        this.changeRequests = changeRequests;
        this.views = views;
    }

    @GetMapping
    public List<OAuthClientResponse> list(
            Authentication authentication,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID applicationId) {
        if (applicationId == null) {
            access.requireSuperAdmin(authentication);
        }
        // With applicationId, AdminAccessInterceptor has already checked access to that application;
        // its operators may see (never edit) the app's clients. Secrets are never returned here.
        return service.list(applicationId == null ? tenantId : null, applicationId);
    }

    // Platform super admins manage every client. The tenant super admin manages the clients of the
    // tenant's applications, a tenant admin those of the applications assigned to them; application
    // admins only view (docs/25). Only platform super admins create applications here.

    @GetMapping("/{id}")
    public OAuthClientResponse get(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs,
            @PathVariable UUID id) {
        OAuthClientResponse client = service.get(id);
        if (!access.canAccessPlatformSettings(authentication, actAs)) {
            access.requireApplicationAccess(authentication, actAs, client.applicationId());
        }
        return client;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OAuthClientCreateResult create(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs,
            @Valid @RequestBody OAuthClientCreateRequest request) {
        access.requireClientManagement(authentication, actAs, request.applicationId());
        return service.create(request);
    }

    @PutMapping("/{id}")
    public OAuthClientResponse update(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs,
            @PathVariable UUID id,
            @Valid @RequestBody OAuthClientUpdateRequest request) {
        access.requireClientManagement(authentication, actAs, service.get(id).applicationId());
        return service.update(id, request);
    }

    @PostMapping("/{id}/rotate-secret")
    public OAuthClientSecretResponse rotateSecret(
            jakarta.servlet.http.HttpServletRequest stepUpRequest,
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs,
            @PathVariable UUID id,
            /* The old secret keeps working this many hours (0 to 168; 0 for a leaked secret). */
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int keepOldHours) {
        stepUp.require(stepUpRequest); // a recent second step from the admin
        access.requireClientManagement(authentication, actAs, service.get(id).applicationId());
        return service.rotateClientSecret(id, keepOldHours);
    }

    /**
     * Deleting a client needs a second admin: this asks for it (202 with the pending change request), and a
     * platform operator or the tenant's super admin approves it on Access review.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ChangeRequestView delete(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs,
            @PathVariable UUID id,
            @RequestParam(required = false) String reason) {
        OAuthClientResponse client = service.get(id);
        access.requireClientManagement(authentication, actAs, client.applicationId());
        Row request = changeRequests.requestClientDelete(client, views.email(authentication, actAs), reason);
        return views.view(request, authentication, actAs);
    }

    /** The pending delete request for this client, or 204 when there is none. */
    @GetMapping("/{id}/delete-request")
    public ResponseEntity<ChangeRequestView> deleteRequest(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs,
            @PathVariable UUID id) {
        OAuthClientResponse client = service.get(id);
        if (!access.canAccessPlatformSettings(authentication, actAs)) {
            access.requireApplicationAccess(authentication, actAs, client.applicationId());
        }
        return changeRequests.pendingClientDelete(id)
                .map(row -> ResponseEntity.ok(views.view(row, authentication, actAs)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
