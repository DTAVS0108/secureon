package com.secureone.auth.admin.role;

import com.secureone.auth.admin.AdminAccessService;
import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationRepository;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin — platform RBAC", description = "Platform-wide roles and permissions")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1")
public class RoleAdminController {

    private final RoleAdminService roles;
    private final PermissionAdminService permissions;
    private final AdminAccessService access;
    private final ApplicationRepository applications;

    public RoleAdminController(
            RoleAdminService roles,
            PermissionAdminService permissions,
            AdminAccessService access,
            ApplicationRepository applications) {
        this.roles = roles;
        this.permissions = permissions;
        this.access = access;
        this.applications = applications;
    }

    @GetMapping("/permissions")
    public List<RoleAdminDtos.PermissionResponse> listPermissions(
            @RequestParam UUID applicationId) {
        return permissions.list(applicationId);
    }

    @GetMapping("/permissions/{id}")
    public PermissionAdminDtos.PermissionDetailResponse getPermission(
            @PathVariable UUID id, @RequestParam UUID applicationId) {
        return permissions.get(applicationId, id);
    }

    @PostMapping("/permissions")
    @ResponseStatus(HttpStatus.CREATED)
    public PermissionAdminDtos.PermissionDetailResponse createPermission(
            @RequestParam UUID applicationId,
            @Valid @RequestBody PermissionAdminDtos.PermissionCreateRequest request) {
        return permissions.create(applicationId, request);
    }

    @PutMapping("/permissions/{id}")
    public PermissionAdminDtos.PermissionDetailResponse updatePermission(
            @PathVariable UUID id,
            @RequestParam UUID applicationId,
            @Valid @RequestBody PermissionAdminDtos.PermissionUpdateRequest request) {
        return permissions.update(applicationId, id, request);
    }

    @DeleteMapping("/permissions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePermission(@PathVariable UUID id, @RequestParam UUID applicationId) {
        permissions.delete(applicationId, id);
    }

    @GetMapping("/roles")
    public List<RoleAdminDtos.RoleSummaryResponse> list(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID applicationId) {
        List<RoleAdminDtos.RoleSummaryResponse> result = roles.listRoles(tenantId, applicationId);
        if (applicationId != null || access.canAccessPlatformSettings(authentication, actAsEmail)) {
            // An applicationId is already checked by AdminAccessInterceptor.
            return result;
        }
        // Tenant-wide or unfiltered listing: only roles in applications this operator may manage.
        Set<UUID> allowed = access.accessibleApplications(authentication, actAsEmail).stream()
                .map(AdminAccessService.ApplicationSummary::id)
                .collect(Collectors.toSet());
        return result.stream().filter(role -> allowed.contains(role.applicationId())).toList();
    }

    @GetMapping("/roles/{id}")
    public RoleAdminDtos.RoleDetailResponse get(
            @PathVariable UUID id, @RequestParam(required = false) UUID applicationId) {
        return roles.getRole(id, applicationId);
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleAdminDtos.RoleDetailResponse create(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @Valid @RequestBody RoleAdminDtos.RoleCreateRequest request) {
        // The target app is only in the body, which AdminAccessInterceptor does not read.
        access.requireApplicationAccess(authentication, actAsEmail, request.applicationId());
        Application app = applications.findById(request.applicationId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + request.applicationId()));
        if (!app.getTenantId().equals(request.tenantId())) {
            throw new IllegalArgumentException("tenantId does not match the application's tenant.");
        }
        return roles.create(request);
    }

    @PutMapping("/roles/{id}")
    public RoleAdminDtos.RoleDetailResponse update(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID applicationId,
            @Valid @RequestBody RoleAdminDtos.RoleUpdateRequest request) {
        return roles.update(id, applicationId, request);
    }

    @DeleteMapping("/roles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestParam(required = false) UUID applicationId) {
        roles.delete(id, applicationId);
    }
}
