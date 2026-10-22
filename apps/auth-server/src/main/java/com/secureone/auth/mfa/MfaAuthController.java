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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The second sign-in step, and setting up two-step verification during sign-in. Every call names the
 * {@code mfaChallengeId} a first step (password, code, magic link, external IdP) returned with 428.
 */
@Tag(name = "Public auth", description = "Two-step verification after the first sign-in step")
@RestController
@RequestMapping("/api/v1/auth/mfa")
public class MfaAuthController {

    public record TotpVerifyRequest(@NotNull UUID mfaChallengeId, @NotBlank String code, Boolean rememberDevice) {}

    public record RecoveryVerifyRequest(@NotNull UUID mfaChallengeId, @NotBlank String code) {}

    public record SendCodeRequest(@NotNull UUID mfaChallengeId, @NotNull UUID factorId) {}

    public record CodeVerifyRequest(@NotNull UUID mfaChallengeId, @NotBlank String code, Boolean rememberDevice) {}

    public record ChallengeRequest(@NotNull UUID mfaChallengeId) {}

    public record WebAuthnVerifyRequest(
            @NotNull UUID mfaChallengeId, @NotNull UUID requestId, @NotBlank String credential, Boolean rememberDevice) {}

    public record SkipRequest(@NotNull UUID mfaChallengeId, Boolean dontAskAgain) {}

    public record SetupWebAuthnRequest(@NotNull UUID mfaChallengeId, String kind) {}

    public record SetupTotpConfirmRequest(@NotNull UUID mfaChallengeId, @NotBlank String code) {}

    public record SetupSendCodeRequest(@NotNull UUID mfaChallengeId, @NotBlank String method, String phone) {}

    public record SetupCodeConfirmRequest(@NotNull UUID mfaChallengeId, @NotBlank String code) {}

    private final MfaLoginService mfaLogin;

    public MfaAuthController(MfaLoginService mfaLogin) {
        this.mfaLogin = mfaLogin;
    }

    @Operation(summary = "Describe a pending second step or setup (options, deadline, whether it can be skipped)")
    @GetMapping("/challenge/{mfaChallengeId}")
    public ResponseEntity<?> challenge(@PathVariable UUID mfaChallengeId) {
        return mfaLogin.describe(mfaChallengeId);
    }

    @Operation(summary = "Complete login with TOTP code")
    @PostMapping("/totp/verify")
    public ResponseEntity<?> verifyTotp(
            @Valid @RequestBody TotpVerifyRequest body, HttpServletRequest request, HttpServletResponse response) {
        return mfaLogin.verifyTotp(body.mfaChallengeId(), body.code(), Boolean.TRUE.equals(body.rememberDevice()), request, response);
    }

    @Operation(summary = "Complete login with one-time recovery code")
    @PostMapping("/recovery/verify")
    public ResponseEntity<?> verifyRecovery(
            @Valid @RequestBody RecoveryVerifyRequest body, HttpServletRequest request, HttpServletResponse response) {
        return mfaLogin.verifyRecovery(body.mfaChallengeId(), body.code(), request, response);
    }

    @Operation(summary = "Send a code by text message or email to one of the user's options")
    @PostMapping("/send")
    public ResponseEntity<?> sendCode(@Valid @RequestBody SendCodeRequest body) {
        return mfaLogin.sendCode(body.mfaChallengeId(), body.factorId());
    }

    @Operation(summary = "Complete login with the code sent by text message or email")
    @PostMapping("/code/verify")
    public ResponseEntity<?> verifyCode(
            @Valid @RequestBody CodeVerifyRequest body, HttpServletRequest request, HttpServletResponse response) {
        return mfaLogin.verifyCode(body.mfaChallengeId(), body.code(), Boolean.TRUE.equals(body.rememberDevice()), request, response);
    }

    @Operation(summary = "Passkey or security key as the second step: assertion options")
    @PostMapping("/webauthn/options")
    public ResponseEntity<?> webauthnOptions(@Valid @RequestBody ChallengeRequest body) {
        return mfaLogin.webauthnOptions(body.mfaChallengeId());
    }

    @Operation(summary = "Complete login with a passkey or security key")
    @PostMapping("/webauthn/verify")
    public ResponseEntity<?> webauthnVerify(
            @Valid @RequestBody WebAuthnVerifyRequest body, HttpServletRequest request, HttpServletResponse response) {
        return mfaLogin.webauthnVerify(body.mfaChallengeId(), body.requestId(), body.credential(),
                Boolean.TRUE.equals(body.rememberDevice()), request, response);
    }

    @Operation(summary = "Set up later: sign in without a second step while reminders or the grace period allow")
    @PostMapping("/skip")
    public ResponseEntity<?> skip(
            @Valid @RequestBody SkipRequest body, HttpServletRequest request, HttpServletResponse response) {
        return mfaLogin.skipSetup(body.mfaChallengeId(), Boolean.TRUE.equals(body.dontAskAgain()), request, response);
    }

    @Operation(summary = "Setup during sign-in: start an authenticator app (QR code)")
    @PostMapping("/setup/totp/start")
    public ResponseEntity<?> setupTotpStart(@Valid @RequestBody ChallengeRequest body) {
        return mfaLogin.setupTotpStart(body.mfaChallengeId());
    }

    @Operation(summary = "Setup during sign-in: confirm the authenticator app code; signs in, returns backup codes")
    @PostMapping("/setup/totp/confirm")
    public ResponseEntity<?> setupTotpConfirm(
            @Valid @RequestBody SetupTotpConfirmRequest body, HttpServletRequest request, HttpServletResponse response) {
        return mfaLogin.setupTotpConfirm(body.mfaChallengeId(), body.code(), request, response);
    }

    @Operation(summary = "Setup during sign-in: passkey or security key registration options")
    @PostMapping("/setup/webauthn/options")
    public ResponseEntity<?> setupWebauthnOptions(@Valid @RequestBody SetupWebAuthnRequest body) {
        return mfaLogin.setupWebauthnOptions(body.mfaChallengeId(), body.kind());
    }

    @Operation(summary = "Setup during sign-in: register the passkey or security key; signs in, returns backup codes")
    @PostMapping("/setup/webauthn/verify")
    public ResponseEntity<?> setupWebauthnVerify(
            @Valid @RequestBody WebAuthnVerifyRequest body, HttpServletRequest request, HttpServletResponse response) {
        return mfaLogin.setupWebauthnVerify(body.mfaChallengeId(), body.requestId(), body.credential(), request, response);
    }

    @Operation(summary = "Setup during sign-in: send a code by text message (phone) or to the account email")
    @PostMapping("/setup/code/send")
    public ResponseEntity<?> setupSendCode(@Valid @RequestBody SetupSendCodeRequest body) {
        return mfaLogin.setupSendCode(body.mfaChallengeId(), body.method(), body.phone());
    }

    @Operation(summary = "Setup during sign-in: confirm the code; signs in, returns backup codes")
    @PostMapping("/setup/code/confirm")
    public ResponseEntity<?> setupConfirmCode(
            @Valid @RequestBody SetupCodeConfirmRequest body, HttpServletRequest request, HttpServletResponse response) {
        return mfaLogin.setupConfirmCode(body.mfaChallengeId(), body.code(), request, response);
    }
}
