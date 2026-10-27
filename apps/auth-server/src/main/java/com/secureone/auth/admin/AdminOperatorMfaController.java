package com.secureone.auth.admin;

import com.secureone.auth.mfa.MfaFactorRepository;
import com.secureone.auth.mfa.MfaFactorViews;
import com.secureone.auth.mfa.MfaRecoveryCodeService;
import com.secureone.auth.mfa.TotpMfaService;
import com.secureone.auth.mfa.passkey.PasskeyRegistrationService;
import com.secureone.auth.user.UserAccount;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Admin — operator MFA", description = "Tenant operator self-service TOTP and passkey enrollment")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1/me/mfa")
public class AdminOperatorMfaController {

    public record TotpEnrollRequest(String label) {}

    public record TotpConfirmRequest(@NotNull UUID factorId, @NotBlank String code) {}

    public record PasskeyRegisterOptionsRequest(String label) {}

    public record PasskeyRegisterVerifyRequest(@NotNull UUID requestId, @NotBlank String credential) {}

    private final AdminOperatorService operators;
    private final TotpMfaService totp;
    private final PasskeyRegistrationService passkeys;
    private final MfaFactorRepository factors;
    private final MfaRecoveryCodeService recoveryCodes;
    private final boolean mfaRequired;
    private final com.secureone.auth.mfa.MfaPolicyService mfaPolicy;

    public AdminOperatorMfaController(
            AdminOperatorService operators,
            TotpMfaService totp,
            PasskeyRegistrationService passkeys,
            MfaFactorRepository factors,
            MfaRecoveryCodeService recoveryCodes,
            com.secureone.auth.mfa.MfaPolicyService mfaPolicy,
            @org.springframework.beans.factory.annotation.Value("${secureone.admin.mfa-required:false}") boolean mfaRequired) {
        this.operators = operators;
        this.totp = totp;
        this.passkeys = passkeys;
        this.factors = factors;
        this.recoveryCodes = recoveryCodes;
        this.mfaRequired = mfaRequired;
        this.mfaPolicy = mfaPolicy;
    }

    @GetMapping("/factors")
    public List<Map<String, Object>> listFactors(
            Authentication authentication, @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail) {
        UserAccount user = requireTenantOperator(authentication, actAsEmail);
        return MfaFactorViews.listForUser(factors, user.getId());
    }

    @PostMapping("/totp/enroll")
    public Map<String, Object> enrollTotp(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @RequestBody(required = false) TotpEnrollRequest body) {
        UserAccount user = requireTenantOperator(authentication, actAsEmail);
        TotpMfaService.EnrollResponse response = totp.enrollRequired(user, body != null ? body.label() : null);
        return Map.of(
                "factorId", response.factorId().toString(),
                "secret", response.secret(),
                "otpauthUri", response.otpauthUri(),
                "qrCodeDataUrl", response.qrCodeDataUrl());
    }

    @PostMapping("/totp/confirm")
    public Map<String, Object> confirmTotp(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @Valid @RequestBody TotpConfirmRequest body) {
        UserAccount user = requireTenantOperator(authentication, actAsEmail);
        TotpMfaService.ConfirmResponse response = totp.confirm(user, body.factorId(), body.code());
        return Map.of(
                "factorId", response.factorId().toString(),
                "recoveryCodes", response.recoveryCodes());
    }

    @DeleteMapping("/totp/{factorId}")
    public ResponseEntity<Void> deleteTotp(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID factorId) {
        UserAccount user = requireTenantOperator(authentication, actAsEmail);
        totp.delete(user, factorId);
        return ResponseEntity.noContent().build();
    }

    /** Unused recovery codes, and whether the operator has an authenticator they can sign in with. */
    @GetMapping("/status")
    public Map<String, Object> status(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail) {
        UserAccount user = requireTenantOperator(authentication, actAsEmail);
        return Map.of(
                "totpEnrolled", totp.hasVerifiedTotp(user.getId()),
                "recoveryCodesRemaining", recoveryCodes.remaining(user.getId()),
                "mfaRequiredForConsole", mfaRequired);
    }

    /** Replaces all recovery codes with a new set (old ones stop working). Needs an authenticator. */
    @PostMapping("/recovery-codes")
    public Map<String, Object> regenerateRecoveryCodes(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail) {
        UserAccount user = requireTenantOperator(authentication, actAsEmail);
        if (!totp.hasVerifiedTotp(user.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Set up an authenticator app first.");
        }
        return Map.of("recoveryCodes", recoveryCodes.issueForUser(user.getId(), mfaPolicy.recoveryCodesPerEnrollment()));
    }

    @PostMapping("/passkey/register/options")
    public Map<String, Object> passkeyRegisterOptions(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @RequestBody(required = false) PasskeyRegisterOptionsRequest body) {
        UserAccount user = requireTenantOperator(authentication, actAsEmail);
        return passkeys.beginRegistration(
                user, operators.signedInTenantSlug(authentication), body != null ? body.label() : null);
    }

    @PostMapping("/passkey/register/verify")
    public Map<String, Object> passkeyRegisterVerify(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @Valid @RequestBody PasskeyRegisterVerifyRequest body) {
        UserAccount user = requireTenantOperator(authentication, actAsEmail);
        return passkeys.finishRegistration(user, body.requestId(), body.credential(), null);
    }

    @DeleteMapping("/passkey/{factorId}")
    public ResponseEntity<Void> deletePasskey(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @PathVariable UUID factorId) {
        UserAccount user = requireTenantOperator(authentication, actAsEmail);
        var factor = factors.findByIdAndUserId(factorId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Passkey not found."));
        if (!"passkey".equals(factor.getType())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Passkey not found.");
        }
        factors.delete(factor);
        return ResponseEntity.noContent().build();
    }

    private UserAccount requireTenantOperator(Authentication authentication, String actAsEmail) {
        if (operators.isBreakGlass(authentication)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The break-glass account cannot enroll MFA. Sign in with your named operator account.");
        }
        return operators
                .resolveTenantUser(authentication, actAsEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Operator account not found"));
    }
}
