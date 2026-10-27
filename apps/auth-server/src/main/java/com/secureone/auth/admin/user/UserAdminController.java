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

    /**
     * Users, optionally of one application, searched (email, username, name) and filtered by status (active,
     * invited, suspended, disabled) on the server. With {@code page} (from 0) the list is one page of {@code size}
     * (default 50, at most 500); {@code X-Total-Count} always gives the number of matching users.
     */
    @GetMapping
    public org.springframework.http.ResponseEntity<List<UserResponse>> list(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(required = false) Boolean adminOnly,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        int pageSize = size == null ? 50 : Math.max(1, Math.min(500, size));
        if (applicationId != null) {
            // Filtered and paged before the (costlier) full responses are built.
            access.requireApplicationAccess(authentication, actAsEmail, applicationId);
            List<com.secureone.auth.user.UserAccount> matching = applicationUsers.memberAccounts(applicationId).stream()
                    .filter(u -> ApplicationUserAdminService.matches(u, search, status))
                    .filter(u -> listVisibility.canViewUserInOperatorList(authentication, actAsEmail, u.getId()))
                    .toList();
            return withTotal(applicationUsers.toResponses(slice(matching, page, pageSize), applicationId), matching.size());
        }
        List<UserResponse> users = listScoped(authentication, actAsEmail, tenantId, adminOnly);
        String needle = search == null ? "" : search.trim().toLowerCase(java.util.Locale.ROOT);
        List<UserResponse> matching = users.stream()
                .filter(u -> needle.isEmpty() || java.util.stream.Stream.of(u.email(), u.username(), u.firstName(), u.lastName())
                        .anyMatch(v -> v != null && v.toLowerCase(java.util.Locale.ROOT).contains(needle)))
                .filter(u -> status == null || status.isBlank() || status.equalsIgnoreCase(
                        "pending".equalsIgnoreCase(u.status()) ? "invited" : u.status()))
                .toList();
        return withTotal(slice(matching, page, pageSize), matching.size());
    }

    private static <T> List<T> slice(List<T> rows, Integer page, int size) {
        if (page == null) {
            return rows;
        }
        int from = (int) Math.min((long) Math.max(0, page) * size, rows.size());
        return rows.subList(from, Math.min(from + size, rows.size()));
    }

    private static org.springframework.http.ResponseEntity<List<UserResponse>> withTotal(List<UserResponse> rows, int total) {
        return org.springframework.http.ResponseEntity.ok()
                .header("X-Total-Count", Integer.toString(total))
                .body(rows);
    }

    private List<UserResponse> listScoped(
            Authentication authentication, String actAsEmail, UUID tenantId, Boolean adminOnly) {
        boolean platform = access.canAccessPlatformSettings(authentication, actAsEmail);
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

    @io.swagger.v3.oas.annotations.Operation(summary = "Where the user is signed in",
            description = "Browser sessions at SecureOne and the applications holding a grant (no token values).")
    @GetMapping("/{id}/sessions")
    public Map<String, Object> sessions(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID id) {
        UserResponse user = service.get(id);
        if (!access.canAccessPlatformSettings(authentication, actAsEmail)) {
            operators.requireTenantAccess(authentication, actAsEmail, user.tenantId());
        }
        return service.activeSessions(id, null);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Sign the user out everywhere",
            description = "Ends their browser sessions and every application's grants (refresh tokens), with a "
                    + "back-channel logout to the applications. The account stays active.")
    @PostMapping("/{id}/sign-out")
    public Map<String, Object> signOutEverywhere(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID id) {
        UserResponse existing = service.get(id);
        requirePlatformOrTenantWrite(authentication, actAsEmail, existing.tenantId());
        return service.signOutEverywhere(id, null);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "The user's profile attributes",
            description = "Sign-up fields, imported values and attributes set by an administrator.")
    @GetMapping("/{id}/attributes")
    public Map<String, Object> attributes(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID id) {
        UserResponse user = service.get(id);
        if (!access.canAccessPlatformSettings(authentication, actAsEmail)) {
            operators.requireTenantAccess(authentication, actAsEmail, user.tenantId());
        }
        return Map.of("attributes", service.profileAttributes(id));
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Replace the user's profile attributes",
            description = "Body: {\"attributes\": {\"key\": value}}. An empty value removes the attribute.")
    @PutMapping("/{id}/attributes")
    @SuppressWarnings("unchecked")
    public Map<String, Object> saveAttributes(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        UserResponse existing = service.get(id);
        requirePlatformOrTenantWrite(authentication, actAsEmail, existing.tenantId());
        Object raw = body != null ? body.get("attributes") : null;
        return Map.of("attributes", service.saveProfileAttributes(id, raw instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of()));
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.secureone.auth.federation.LinkedIdentityService linkedIdentities;

    @io.swagger.v3.oas.annotations.Operation(summary = "The user's linked sign-in accounts (Google, SAML…)")
    @GetMapping("/{id}/identities")
    public List<Map<String, Object>> identities(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID id) {
        UserResponse user = service.get(id);
        if (!access.canAccessPlatformSettings(authentication, actAsEmail)) {
            operators.requireTenantAccess(authentication, actAsEmail, user.tenantId());
        }
        return linkedIdentities.list(id);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Unlink a sign-in account",
            description = "Refused (409) when it is the user's only way to sign in.")
    @DeleteMapping("/{id}/identities/{identityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkIdentity(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID id,
            @PathVariable UUID identityId) {
        UserResponse existing = service.get(id);
        requirePlatformOrTenantWrite(authentication, actAsEmail, existing.tenantId());
        linkedIdentities.unlink(id, identityId, "admin");
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
        service.adminSetPassword(id, request.password(), null, Boolean.TRUE.equals(request.temporary()));
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
