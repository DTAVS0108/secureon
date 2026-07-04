package com.secureone.auth.admin;

import com.secureone.auth.mfa.MfaFactorRepository;
import com.secureone.auth.mfa.MfaFactorViews;
import com.secureone.auth.mfa.TotpMfaService;
import com.secureone.auth.mfa.passkey.PasskeyRegistrationService;
import com.secureone.auth.tenant.Tenant;
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

    public AdminOperatorMfaController(
            AdminOperatorService operators,
            TotpMfaService totp,
            PasskeyRegistrationService passkeys,
            MfaFactorRepository factors) {
        this.operators = operators;
        this.totp = totp;
        this.passkeys = passkeys;
        this.factors = factors;
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
        TotpMfaService.EnrollResponse response = totp.enroll(user, body != null ? body.label() : null);
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

    @PostMapping("/passkey/register/options")
    public Map<String, Object> passkeyRegisterOptions(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @RequestBody(required = false) PasskeyRegisterOptionsRequest body) {
        UserAccount user = requireTenantOperator(authentication, actAsEmail);
        Tenant tenant = operators.requireOperatorTenant(authentication, actAsEmail);
        return passkeys.beginRegistration(user, tenant.getSlug(), body != null ? body.label() : null);
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
        if (operators.isPlatformSuperAdmin(authentication, actAsEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Platform super-admins cannot enroll MFA here. Use a tenant operator account.");
        }
        return operators
                .resolveTenantUser(authentication, actAsEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Operator account not found"));
    }
}
