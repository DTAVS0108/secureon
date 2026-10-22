package com.secureone.auth.mfa.passkey;

import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.authn.SessionUserResolver;
import com.secureone.auth.mfa.MfaFactorRepository;
import com.secureone.auth.user.UserAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account MFA", description = "Self-service passkey enrollment (session-authenticated)")
@RestController
@RequestMapping("/api/v1/account/mfa/passkey")
public class PasskeyAccountController {

    /** {@code applicationId}: the app the user is in, so the browser's passkey prompt names it. */
    public record RegisterOptionsRequest(String label, UUID applicationId) {}

    public record RegisterVerifyRequest(@NotNull UUID requestId, @NotBlank String credential) {}

    private final SessionUserResolver users;
    private final PasskeyRegistrationService registration;
    private final MfaFactorRepository factors;
    private final com.secureone.auth.twostep.FreshStep freshStep;
    private final com.secureone.auth.twostep.TwoStepService twoStep;

    public PasskeyAccountController(
            SessionUserResolver users,
            PasskeyRegistrationService registration,
            MfaFactorRepository factors,
            com.secureone.auth.twostep.FreshStep freshStep,
            com.secureone.auth.twostep.TwoStepService twoStep) {
        this.freshStep = freshStep;
        this.twoStep = twoStep;
        this.users = users;
        this.registration = registration;
        this.factors = factors;
    }

    @Operation(summary = "Begin passkey registration")
    @PostMapping("/register/options")
    public Map<String, Object> registerOptions(
            Authentication authentication, @RequestBody(required = false) RegisterOptionsRequest body) {
        UserAccount user = users.requireTenantUser(authentication);
        String tenantSlug = tenantSlug(authentication);
        return registration.beginRegistration(
                user, tenantSlug, body != null ? body.label() : null, body != null ? body.applicationId() : null);
    }

    @Operation(summary = "Finish passkey registration")
    @PostMapping("/register/verify")
    public Map<String, Object> registerVerify(
            Authentication authentication, @Valid @RequestBody RegisterVerifyRequest body) {
        UserAccount user = users.requireTenantUser(authentication);
        return registration.finishRegistration(user, body.requestId(), body.credential(), null);
    }

    @Operation(summary = "Remove a passkey")
    @DeleteMapping("/{factorId}")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            jakarta.servlet.http.HttpServletRequest request,
            @PathVariable UUID factorId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) UUID applicationId) {
        UserAccount user = users.requireTenantUser(authentication);
        var factor = factors.findByIdAndUserId(factorId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Passkey not found."));
        if (!WebAuthnCredentialRepository.FACTOR_TYPE.equals(factor.getType())) {
            throw new ResourceNotFoundException("Passkey not found.");
        }
        // Removing a sign-in method needs a recent second step and tells the user (a taken-over session must
        // not be able to strip it silently).
        freshStep.require(request, user.getId(), applicationId);
        var method = com.secureone.auth.twostep.TwoStepService.methodOf(factor)
                .orElse(com.secureone.auth.twostep.TwoStepMethod.PASSKEY);
        factors.delete(factor);
        twoStep.methodRemoved(user, applicationId, method);
        return ResponseEntity.noContent().build();
    }

    private String tenantSlug(Authentication authentication) {
        String principal = authentication.getName();
        int sep = principal.indexOf(':');
        if (sep <= 0) {
            throw new IllegalStateException("Unexpected session principal.");
        }
        return principal.substring(0, sep);
    }
}
