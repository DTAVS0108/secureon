package com.secureone.auth.mfa;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Public auth", description = "MFA step-up after password login")
@RestController
@RequestMapping("/api/v1/auth/mfa")
public class MfaAuthController {

    public record TotpVerifyRequest(@NotNull UUID mfaChallengeId, @NotBlank String code) {}

    public record RecoveryVerifyRequest(@NotNull UUID mfaChallengeId, @NotBlank String code) {}

    private final MfaLoginService mfaLogin;

    public MfaAuthController(MfaLoginService mfaLogin) {
        this.mfaLogin = mfaLogin;
    }

    @Operation(summary = "Complete login with TOTP code")
    @PostMapping("/totp/verify")
    public ResponseEntity<?> verifyTotp(
            @Valid @RequestBody TotpVerifyRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        return mfaLogin.verifyTotp(body.mfaChallengeId(), body.code(), request, response);
    }

    @Operation(summary = "Complete login with one-time recovery code")
    @PostMapping("/recovery/verify")
    public ResponseEntity<?> verifyRecovery(
            @Valid @RequestBody RecoveryVerifyRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        return mfaLogin.verifyRecovery(body.mfaChallengeId(), body.code(), request, response);
    }
}
