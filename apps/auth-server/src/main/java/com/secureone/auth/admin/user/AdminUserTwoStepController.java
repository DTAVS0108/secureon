package com.secureone.auth.admin.user;

import com.secureone.auth.admin.AdminAccessService;
import com.secureone.auth.admin.AdminOperatorService;
import com.secureone.auth.admin.AdminStepUpService;
import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.trusteddevice.TrustedDeviceService;
import com.secureone.auth.twostep.TwoStepMethod;
import com.secureone.auth.twostep.TwoStepService;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A user's two-step verification, for admins: status, reset (one option or all), extend the setup deadline,
 * exempt. Platform operators and the tenant's super admins can do everything; tenant admins can extend once by
 * up to 14 days; application admins can see the status. Reset and exempt need a recent second step from the admin.
 *
 * <p>Each application has its own settings, so the setup deadline and exemptions are per application
 * ({@code applicationId}); without one, they apply to every application of the tenant (super admins only for
 * extending). The user's options and backup codes are one set shared by every application, so a reset applies
 * everywhere.
 */
@Tag(name = "Admin — users", description = "Two-step verification of a user")
@RestController
public class AdminUserTwoStepController {

    public record ResetRequest(String method, UUID applicationId) {}

    /** {@code applicationId} null: every application where the user has a deadline. */
    public record ExtendRequest(Integer days, UUID applicationId) {}

    /** {@code applicationId} null: every application of the tenant. */
    public record ExemptRequest(String until, String reason, UUID applicationId) {}

    private static final int TENANT_ADMIN_MAX_EXTEND_DAYS = 14;

    private final TwoStepService twoStep;
    private final UserAccountRepository users;
    private final AdminAccessService access;
    private final AdminOperatorService operators;
    private final AdminStepUpService stepUp;
    private final AuditService audit;
    private final TrustedDeviceService trustedDevices;

    public AdminUserTwoStepController(
            TwoStepService twoStep,
            UserAccountRepository users,
            AdminAccessService access,
            AdminOperatorService operators,
            AdminStepUpService stepUp,
            AuditService audit,
            TrustedDeviceService trustedDevices) {
        this.twoStep = twoStep;
        this.users = users;
        this.access = access;
        this.operators = operators;
        this.stepUp = stepUp;
        this.audit = audit;
        this.trustedDevices = trustedDevices;
    }

    @Operation(summary = "Two-step status of a user (options without secrets, deadline, exemption)")
    @GetMapping("/api/admin/v1/users/{id}/two-step")
    public Map<String, Object> status(Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs,
            @PathVariable UUID id, @RequestParam(required = false) UUID applicationId) {
        UserAccount user = requireUser(id);
        requireTenantWrite(authentication, actAs, user.getTenantId());
        return view(user, applicationId, authentication, actAs);
    }

    /** Application admins see the status of their application's users (the interceptor checks the app). */
    @Operation(summary = "Two-step status of an application's user")
    @GetMapping("/api/admin/v1/applications/{applicationId}/users/{userId}/two-step")
    public Map<String, Object> appStatus(Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs,
            @PathVariable UUID applicationId, @PathVariable UUID userId) {
        return view(requireUser(userId), applicationId, authentication, actAs);
    }

    public record StatusesRequest(java.util.List<UUID> userIds) {}

    /** Status of several users at once (the application console's user list and its filter). */
    @Operation(summary = "Two-step status of several of an application's users")
    @PostMapping("/api/admin/v1/applications/{applicationId}/users/two-step-statuses")
    public Map<String, String> appStatuses(@PathVariable UUID applicationId, @RequestBody StatusesRequest body) {
        Map<String, String> out = new LinkedHashMap<>();
        if (body == null || body.userIds() == null) {
            return out;
        }
        body.userIds().stream().distinct().limit(500).forEach(id -> users.findById(id).ifPresent(user ->
                out.put(id.toString(), String.valueOf(twoStep.status(user, applicationId).get("status")))));
        return out;
    }

    @Operation(summary = "Reset a user's two-step verification (one option or all); ends their sessions")
    @PostMapping("/api/admin/v1/users/{id}/two-step/reset")
    public Map<String, Object> reset(Authentication authentication, HttpServletRequest request,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs,
            @PathVariable UUID id, @RequestBody(required = false) ResetRequest body) {
        UserAccount user = requireUser(id);
        requireTenantWrite(authentication, actAs, user.getTenantId());
        stepUp.require(request);
        TwoStepMethod method = body != null && body.method() != null && !body.method().isBlank()
                ? TwoStepMethod.fromId(body.method()).orElseThrow(() -> new IllegalArgumentException("Unknown method"))
                : null;
        UUID applicationId = body != null ? body.applicationId() : null;
        twoStep.reset(user, applicationId, method, operator(authentication, actAs));
        audit.record(user.getTenantId(), applicationId, "admin", "user.two_step_reset", "user_account", user.getId(),
                user.getEmail() + (method != null ? " · " + method.id() : " · all"), true);
        return view(user, applicationId, authentication, actAs);
    }

    @Operation(summary = "Give a user more time to set up two-step verification")
    @PostMapping("/api/admin/v1/users/{id}/two-step/extend")
    public Map<String, Object> extend(Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs,
            @PathVariable UUID id, @RequestBody ExtendRequest body) {
        UserAccount user = requireUser(id);
        requireTenantWrite(authentication, actAs, user.getTenantId());
        int days = body != null && body.days() != null ? body.days() : 0;
        UUID applicationId = body != null ? body.applicationId() : null;
        boolean governance = access.canManageTenantGovernance(authentication, actAs, user.getTenantId());
        int max = governance ? 180 : TENANT_ADMIN_MAX_EXTEND_DAYS;
        if (days < 1 || days > max) {
            throw new IllegalArgumentException("days must be between 1 and " + max + ".");
        }
        if (!governance && applicationId == null) {
            throw new AccessDeniedException("Choose the application; only the tenant's super admin can extend every application.");
        }
        if (!governance && twoStep.extendedBy(user.getId(), applicationId) != null) {
            throw new AccessDeniedException("The deadline was already extended; ask the tenant's super admin.");
        }
        twoStep.extend(user, applicationId, days, operator(authentication, actAs));
        audit.record(user.getTenantId(), applicationId, "admin", "user.two_step_extended", "user_account", user.getId(),
                user.getEmail() + " · +" + days + " days" + (applicationId == null ? " · every application" : ""), true);
        return view(user, applicationId, authentication, actAs);
    }

    @Operation(summary = "Exempt a user from two-step verification until a date (reason required)")
    @PostMapping("/api/admin/v1/users/{id}/two-step/exempt")
    public Map<String, Object> exempt(Authentication authentication, HttpServletRequest request,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs,
            @PathVariable UUID id, @RequestBody ExemptRequest body) {
        UserAccount user = requireUser(id);
        requireGovernance(authentication, actAs, user.getTenantId());
        stepUp.require(request);
        if (body == null || body.reason() == null || body.reason().isBlank()) {
            throw new IllegalArgumentException("Give a reason for the exemption.");
        }
        Instant until = parseUntil(body.until());
        if (!until.isAfter(Instant.now()) || until.isAfter(Instant.now().plusSeconds(366L * 86400))) {
            throw new IllegalArgumentException("The exemption must end within a year.");
        }
        String reason = body.reason().trim();
        UUID applicationId = body.applicationId();
        twoStep.exempt(user, applicationId, until, reason.length() > 500 ? reason.substring(0, 500) : reason,
                operator(authentication, actAs));
        twoStep.notifyUser(applicationId, user, "An administrator exempted you from two-step verification"
                + (applicationId == null ? "" : " in this application") + " until " + until.toString().substring(0, 10) + ".");
        audit.record(user.getTenantId(), applicationId, "admin", "user.two_step_exempted", "user_account", user.getId(),
                user.getEmail() + " until " + until + (applicationId == null ? " · every application" : "") + ": " + reason, true);
        return view(user, applicationId, authentication, actAs);
    }

    @Operation(summary = "End a user's exemption (in one application, or the one for every application)")
    @DeleteMapping("/api/admin/v1/users/{id}/two-step/exempt")
    public Map<String, Object> endExemption(Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAs, @PathVariable UUID id,
            @RequestParam(required = false) UUID applicationId) {
        UserAccount user = requireUser(id);
        requireGovernance(authentication, actAs, user.getTenantId());
        twoStep.exempt(user, applicationId, null, null, null);
        audit.record(user.getTenantId(), applicationId, "admin", "user.two_step_exemption_ended", "user_account", user.getId(),
                user.getEmail() + (applicationId == null ? " · every application" : ""), true);
        return view(user, applicationId, authentication, actAs);
    }

    private Map<String, Object> view(UserAccount user, UUID applicationId, Authentication authentication, String actAs) {
        Map<String, Object> out = new LinkedHashMap<>(twoStep.status(user, applicationId));
        out.put("rememberedDevices", trustedDevices.list(user.getId()).size());
        boolean governance = access.canManageTenantGovernance(authentication, actAs, user.getTenantId());
        out.put("canExempt", governance);
        out.put("maxExtendDays", governance ? 180 : TENANT_ADMIN_MAX_EXTEND_DAYS);
        return out;
    }

    private UserAccount requireUser(UUID id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private void requireTenantWrite(Authentication authentication, String actAs, UUID tenantId) {
        if (access.canAccessPlatformSettings(authentication, actAs)
                || access.canManageTenantGovernance(authentication, actAs, tenantId)) {
            return;
        }
        if (!"tenant".equals(access.resolveOperatorTier(authentication, actAs))) {
            throw new AccessDeniedException("Tenant operator access required");
        }
        operators.requireTenantAccess(authentication, actAs, tenantId);
    }

    private void requireGovernance(Authentication authentication, String actAs, UUID tenantId) {
        if (!access.canManageTenantGovernance(authentication, actAs, tenantId)) {
            throw new AccessDeniedException("Only a platform operator or the tenant's super admin can exempt users.");
        }
    }

    private String operator(Authentication authentication, String actAs) {
        String email = access.resolveOperatorEmail(authentication, actAs);
        return email != null ? email : authentication.getName();
    }

    private static Instant parseUntil(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("until is required (a date).");
        }
        String v = raw.trim();
        try {
            return v.length() <= 10
                    ? LocalDate.parse(v).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                    : Instant.parse(v);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("until must be a date like 2026-12-31.");
        }
    }
}
