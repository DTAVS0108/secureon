package com.secureone.auth.mfa;

import com.secureone.auth.authn.SessionUserResolver;
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

@Tag(name = "Account MFA", description = "Self-service TOTP enrollment (session-authenticated)")
@RestController
@RequestMapping("/api/v1/account/mfa/totp")
public class MfaAccountController {

    /** {@code applicationId}: the app the user is enrolling from, so its MFA policy applies. */
    public record EnrollRequest(String label, UUID applicationId) {}

    public record ConfirmRequest(@NotNull UUID factorId, @NotBlank String code, UUID applicationId) {}

    private final SessionUserResolver users;
    private final TotpMfaService totp;

    private final com.secureone.auth.twostep.FreshStep freshStep;
    private final com.secureone.auth.twostep.TwoStepService twoStep;

    public MfaAccountController(SessionUserResolver users, TotpMfaService totp,
            com.secureone.auth.twostep.FreshStep freshStep, com.secureone.auth.twostep.TwoStepService twoStep) {
        this.freshStep = freshStep;
        this.twoStep = twoStep;
        this.users = users;
        this.totp = totp;
    }

    @Operation(summary = "Begin TOTP enrollment")
    @PostMapping("/enroll")
    public Map<String, Object> enroll(Authentication authentication, @RequestBody(required = false) EnrollRequest body) {
        UserAccount user = users.requireTenantUser(authentication);
        TotpMfaService.EnrollResponse response = totp.enroll(user, body != null ? body.label() : null);
        return Map.of(
                "factorId", response.factorId().toString(),
                "secret", response.secret(),
                "otpauthUri", response.otpauthUri(),
                "qrCodeDataUrl", response.qrCodeDataUrl());
    }

    @Operation(summary = "Confirm TOTP enrollment")
    @PostMapping("/confirm")
    public Map<String, Object> confirm(
            Authentication authentication, @Valid @RequestBody ConfirmRequest body) {
        UserAccount user = users.requireTenantUser(authentication);
        TotpMfaService.ConfirmResponse response =
                totp.confirm(user, body.factorId(), body.code(), body.applicationId());
        return Map.of(
                "factorId", response.factorId().toString(),
                "recoveryCodes", response.recoveryCodes());
    }

    @Operation(summary = "Remove TOTP factor")
    @DeleteMapping("/{factorId}")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            jakarta.servlet.http.HttpServletRequest request,
            @PathVariable UUID factorId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) UUID applicationId) {
        UserAccount user = users.requireTenantUser(authentication);
        // A recent second step is needed, and the user is told (gap M3).
        freshStep.require(request, user.getId(), applicationId);
        totp.delete(user, factorId);
        twoStep.methodRemoved(user, applicationId, com.secureone.auth.twostep.TwoStepMethod.TOTP);
        return ResponseEntity.noContent().build();
    }
}
