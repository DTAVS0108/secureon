package com.secureone.auth.admin.user;

import com.secureone.auth.admin.AdminAccessService;
import com.secureone.auth.admin.AdminOperatorService;
import com.secureone.auth.admin.OperatorListVisibilityService;
import com.secureone.auth.admin.application.ApplicationUserAdminService;
import com.secureone.auth.admin.impersonation.AdminImpersonationService;
import com.secureone.auth.admin.user.UserAdminDtos.UserCreateRequest;
import com.secureone.auth.admin.user.UserAdminDtos.UserResponse;
import com.secureone.auth.admin.user.UserAdminDtos.UserUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

@Tag(name = "Admin — users", description = "Platform-wide user directory")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1/users")
public class UserAdminController {

    @org.springframework.beans.factory.annotation.Autowired
    private com.secureone.auth.admin.AdminStepUpService stepUp;

    private final UserAdminService service;
    private final ApplicationUserAdminService applicationUsers;
    private final AdminAccessService access;
    private final AdminOperatorService operators;
    private final OperatorListVisibilityService listVisibility;
    private final AdminImpersonationService impersonation;

    public UserAdminController(
            UserAdminService service,
            ApplicationUserAdminService applicationUsers,
            AdminAccessService access,
            AdminOperatorService operators,
            OperatorListVisibilityService listVisibility,
            AdminImpersonationService impersonation) {
        this.service = service;
        this.applicationUsers = applicationUsers;
        this.access = access;
        this.operators = operators;
        this.listVisibility = listVisibility;
        this.impersonation = impersonation;
    }

    @GetMapping
    public List<UserResponse> list(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(required = false) Boolean adminOnly,
            @RequestParam(required = false) String search) {
        List<UserResponse> users = listScoped(authentication, actAsEmail, tenantId, applicationId, adminOnly);
        if (search == null || search.isBlank()) {
            return users;
        }
        // Case-insensitive match on email, username, first/last name.
        String needle = search.trim().toLowerCase(java.util.Locale.ROOT);
        return users.stream()
                .filter(u -> java.util.stream.Stream.of(u.email(), u.username(), u.firstName(), u.lastName())
                        .anyMatch(v -> v != null && v.toLowerCase(java.util.Locale.ROOT).contains(needle)))
                .toList();
    }

    private List<UserResponse> listScoped(
            Authentication authentication, String actAsEmail, UUID tenantId, UUID applicationId, Boolean adminOnly) {
        boolean platform = access.canAccessPlatformSettings(authentication, actAsEmail);
        if (applicationId != null) {
            access.requireApplicationAccess(authentication, actAsEmail, applicationId);
            return applicationUsers.listUsers(applicationId).stream()
                    .filter(u -> listVisibility.canViewUserInOperatorList(
                            authentication, actAsEmail, u.id()))
                    .toList();
        }
        if (!platform) {
            UUID operatorTenantId = operators.requireOperatorTenant(authentication, actAsEmail).getId();
            if (tenantId != null && !tenantId.equals(operatorTenantId)) {
                throw new AccessDeniedException("No access to tenant: " + tenantId);
            }
            tenantId = operatorTenantId;
            if (Boolean.TRUE.equals(adminOnly)) {
                return service.listWithAdminRole(tenantId);
            }
            return service.listByTenant(tenantId);
        }
        if (Boolean.TRUE.equals(adminOnly)) {
            return service.listWithAdminRole(tenantId);
        }
        if (tenantId != null) {
            return service.listByTenant(tenantId);
        }
        return service.list();
    }

    @GetMapping("/{id}")
    public UserResponse get(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID id) {
        UserResponse user = service.get(id);
        if (!access.canAccessPlatformSettings(authentication, actAsEmail)) {
            operators.requireTenantAccess(authentication, actAsEmail, user.tenantId());
        }
        return user;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @Valid @RequestBody UserCreateRequest request) {
        requirePlatformOrTenantWrite(authentication, actAsEmail, request.tenantId());
        return service.create(request);
    }

    @PutMapping("/{id}")
    public UserResponse update(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID id,
            @Valid @RequestBody UserUpdateRequest request) {
        UserResponse existing = service.get(id);
        requirePlatformOrTenantWrite(authentication, actAsEmail, existing.tenantId());
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public UserResponse setStatus(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        UserResponse existing = service.get(id);
        requirePlatformOrTenantWrite(authentication, actAsEmail, existing.tenantId());
        return service.setStatus(id, body.get("status"));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID id) {
        UserResponse existing = service.get(id);
        if (!access.canAccessPlatformSettings(authentication, actAsEmail)) {
            throw new AccessDeniedException("Only platform super-admin may delete users");
        }
        service.delete(id);
    }

    @PostMapping("/{id}/password/reset-email")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> sendPasswordResetEmail(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID id) {
        UserResponse existing = service.get(id);
        requirePlatformOrTenantWrite(authentication, actAsEmail, existing.tenantId());
        service.sendPasswordResetEmail(id);
        return Map.of("status", "sent", "message", "Password reset email sent to the user.");
    }

    @PostMapping("/{id}/email/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> resendVerification(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID id) {
        UserResponse existing = service.get(id);
        requirePlatformOrTenantWrite(authentication, actAsEmail, existing.tenantId());
        service.resendVerificationEmail(id);
        return Map.of("status", "sent", "message", "Verification email sent to the user.");
    }

    @PostMapping("/{id}/email/verify")
    public UserResponse markEmailVerified(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID id) {
        UserResponse existing = service.get(id);
        requirePlatformOrTenantWrite(authentication, actAsEmail, existing.tenantId());
        return service.markEmailVerified(id);
    }

    @PatchMapping("/{id}/email-verification")
    public UserResponse updateEmailVerification(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID id,
            @RequestBody Map<String, Boolean> body) {
        UserResponse existing = service.get(id);
        requirePlatformOrTenantWrite(authentication, actAsEmail, existing.tenantId());
        boolean verified = body != null && Boolean.TRUE.equals(body.get("verified"));
        return service.setEmailVerified(id, verified, null);
    }

    @PostMapping("/{id}/mfa/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetMfa(
            Authentication authentication,
            jakarta.servlet.http.HttpServletRequest request,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID id) {
        UserResponse existing = service.get(id);
        requirePlatformOrTenantWrite(authentication, actAsEmail, existing.tenantId());
        stepUp.require(request);
        service.resetMfa(id);
    }

    @PostMapping("/{id}/unlock")
    public UserResponse unlock(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID id) {
        UserResponse existing = service.get(id);
        requirePlatformOrTenantWrite(authentication, actAsEmail, existing.tenantId());
        return service.unlockAccount(id);
    }

    @PostMapping("/{id}/password/set")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void adminSetPassword(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID id,
            @Valid @RequestBody UserAdminDtos.AdminSetPasswordRequest request) {
        UserResponse existing = service.get(id);
        requirePlatformOrTenantWrite(authentication, actAsEmail, existing.tenantId());
        service.adminSetPassword(id, request.password());
    }

    @PostMapping("/{id}/password/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void adminRemovePassword(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID id) {
        UserResponse existing = service.get(id);
        requirePlatformOrTenantWrite(authentication, actAsEmail, existing.tenantId());
        service.adminRemovePassword(id);
    }

    @PostMapping("/{id}/password/set-password-email")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> sendSetPasswordInviteEmail(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID id) {
        UserResponse existing = service.get(id);
        requirePlatformOrTenantWrite(authentication, actAsEmail, existing.tenantId());
        service.sendSetPasswordInviteEmail(id);
        return Map.of("status", "sent", "message", "Set-password email sent to the user.");
    }

    @PostMapping("/{id}/impersonate")
    public AdminImpersonationService.ImpersonationTokenResponse impersonate(
            jakarta.servlet.http.HttpServletRequest stepUpRequest,
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        stepUp.require(stepUpRequest); // a recent second step from the admin
        UserResponse existing = service.get(id);
        requirePlatformOrTenantWrite(authentication, actAsEmail, existing.tenantId());
        String reason = body != null ? String.valueOf(body.get("reason")) : null;
        UUID applicationId = null;
        if (body != null && body.get("applicationId") != null) {
            applicationId = UUID.fromString(String.valueOf(body.get("applicationId")));
            access.requireApplicationAccess(authentication, actAsEmail, applicationId);
        }
        String adminPrincipal = authentication.getName();
        if (actAsEmail != null && !actAsEmail.isBlank()) {
            adminPrincipal = actAsEmail.trim();
        }
        return impersonation.createToken(id, adminPrincipal, reason, applicationId);
    }

    private void requirePlatformOrTenantWrite(
            Authentication authentication, String actAsEmail, UUID tenantId) {
        if (access.canAccessPlatformSettings(authentication, actAsEmail)) {
            return;
        }
        if (!"tenant".equals(access.resolveOperatorTier(authentication, actAsEmail))) {
            throw new AccessDeniedException("Tenant operator access required");
        }
        operators.requireTenantAccess(authentication, actAsEmail, tenantId);
    }
}
