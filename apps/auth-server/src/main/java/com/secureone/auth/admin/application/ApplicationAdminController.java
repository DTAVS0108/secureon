package com.secureone.auth.admin.application;

import com.secureone.auth.admin.AdminAccessService;
import com.secureone.auth.admin.application.ApplicationAdminDtos.ApplicationCreateRequest;
import com.secureone.auth.admin.application.ApplicationAdminDtos.ApplicationResponse;
import com.secureone.auth.admin.application.ApplicationAdminDtos.ApplicationUpdateRequest;
import com.secureone.auth.admin.approval.ChangeRequestService;
import com.secureone.auth.admin.approval.ChangeRequestViews;
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

@Tag(name = "Admin — applications", description = "Register and manage application products (console scope)")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1/applications")
public class ApplicationAdminController {

    @org.springframework.beans.factory.annotation.Autowired
    private com.secureone.auth.admin.AdminStepUpService stepUp;

    private final ApplicationAdminService service;
    private final AdminAccessService access;
    private final ChangeRequestService changeRequests;
    private final ChangeRequestViews views;

    public ApplicationAdminController(
            ApplicationAdminService service,
            AdminAccessService access,
            ChangeRequestService changeRequests,
            ChangeRequestViews views) {
        this.service = service;
        this.access = access;
        this.changeRequests = changeRequests;
        this.views = views;
    }

    @GetMapping
    public List<ApplicationResponse> list(
            Authentication authentication,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Act-As-Email", required = false)
                    String actAsEmail,
            @RequestParam(required = false) UUID tenantId) {
        if (access.isPlatformSuperAdmin(authentication)) {
            if (tenantId != null) {
                return service.listByTenant(tenantId);
            }
            return service.list();
        }
        return access.accessibleApplications(authentication, actAsEmail).stream()
                .filter(a -> tenantId == null || tenantId.equals(a.tenantId()))
                .map(a -> service.get(a.id()))
                .toList();
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse create(
            Authentication authentication, @Valid @RequestBody ApplicationCreateRequest request) {
        access.requireSuperAdmin(authentication);
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ApplicationResponse update(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody ApplicationUpdateRequest request) {
        access.requireSuperAdmin(authentication);
        return service.update(id, request);
    }

    /**
     * Deletes the application, its schema (users' memberships, roles, groups, settings) and its OAuth clients, and
     * signs everyone out of it. Needs the application's slug typed again ({@code confirm}) and a fresh second step.
     * With OAuth clients it needs a second admin, as deleting one client does: 202 with the pending change request.
     * Without, 204.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            jakarta.servlet.http.HttpServletRequest request,
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs,
            @PathVariable UUID id,
            @RequestParam(required = false) String confirm,
            @RequestParam(required = false) String reason) {
        access.requireSuperAdmin(authentication);
        ApplicationResponse app = service.get(id);
        if (confirm == null || !confirm.trim().equalsIgnoreCase(app.slug())) {
            throw new IllegalArgumentException(
                    "Type the application's slug (" + app.slug() + ") to confirm deleting it.");
        }
        stepUp.require(request); // a recent second step from the admin
        if (app.oauthClientCount() > 0) {
            var row = changeRequests.requestApplicationDelete(app, views.email(authentication, actAs), reason);
            return ResponseEntity.accepted().body(views.view(row, authentication, actAs));
        }
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** The pending delete request for this application, or 204 when there is none. */
    @GetMapping("/{id}/delete-request")
    public ResponseEntity<ChangeRequestViews.ChangeRequestView> deleteRequest(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs,
            @PathVariable UUID id) {
        access.requireSuperAdmin(authentication);
        return changeRequests.pendingApplicationDelete(id)
                .map(row -> ResponseEntity.ok(views.view(row, authentication, actAs)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/isolate")
    public ApplicationResponse isolate(Authentication authentication, @PathVariable UUID id) {
        access.requireSuperAdmin(authentication);
        return service.isolate(id);
    }
}
