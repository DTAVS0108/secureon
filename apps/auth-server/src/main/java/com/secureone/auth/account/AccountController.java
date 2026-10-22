package com.secureone.auth.account;

import com.secureone.auth.authn.MagicLinkAuthService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account", description = "Password reset, email verification, magic link (public)")
@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final AccountNotificationService accounts;
    private final AccountVerificationService verification;
    private final PasswordRecoveryService passwordRecovery;
    private final MagicLinkAuthService magicLinkAuth;

    public AccountController(
            AccountNotificationService accounts,
            AccountVerificationService verification,
            PasswordRecoveryService passwordRecovery,
            MagicLinkAuthService magicLinkAuth) {
        this.accounts = accounts;
        this.verification = verification;
        this.passwordRecovery = passwordRecovery;
        this.magicLinkAuth = magicLinkAuth;
    }

    /**
     * @deprecated Prefer {@code POST /api/v1/applications/{applicationId}/account/password/forgot} with
     *     {@code { email }} only.
     */
    @Deprecated
    @PostMapping("/password/forgot")
    public Map<String, String> forgotPassword(@Valid @RequestBody AccountDtos.TenantEmailRequest request) {
        if (request.applicationId() != null) {
            return accounts.requestPasswordResetForApplication(request.applicationId(), request.email());
        }
        requireTenantSlug(request.tenantSlug());
        return accounts.requestPasswordReset(request.tenantSlug(), request.email());
    }

    @PostMapping("/password/reset/otp")
    public Map<String, String> resetPasswordOtp(@Valid @RequestBody AccountDtos.ResetPasswordOtpRequest request) {
        return passwordRecovery.completeRecoveryOtp(
                request.applicationId(), UUID.fromString(request.challengeId()), request.code(), request.password());
    }

    @PostMapping("/password/reset")
    public Map<String, String> resetPassword(@Valid @RequestBody AccountDtos.ResetPasswordRequest request) {
        return accounts.resetPassword(request.token(), request.password());
    }

    /**
     * @deprecated Prefer {@code POST /api/v1/applications/{applicationId}/account/email/resend-verification}.
     */
    @Deprecated
    @PostMapping("/email/resend-verification")
    public Map<String, String> resendVerification(@Valid @RequestBody AccountDtos.TenantEmailRequest request) {
        if (request.applicationId() != null) {
            return accounts.resendVerificationForApplication(request.applicationId(), request.email());
        }
        requireTenantSlug(request.tenantSlug());
        return accounts.resendVerification(request.tenantSlug(), request.email());
    }

    @GetMapping(value = "/email/verify", produces = MediaType.TEXT_HTML_VALUE)
    public String verifyEmailHtml(@RequestParam String token) {
        try {
            var user = accounts.verifyEmail(token);
            return """
                    <!DOCTYPE html><html><head><meta charset="utf-8"><title>Email verified</title></head>
                    <body style="font-family:system-ui,sans-serif;padding:2rem">
                    <h1>Email verified</h1>
                    <p>Thank you. <strong>%s</strong> is now verified.</p>
                    <p>You can close this window and sign in.</p>
                    </body></html>
                    """
                    .formatted(escapeHtml(user.getEmail()));
        } catch (Exception ex) {
            return """
                    <!DOCTYPE html><html><head><meta charset="utf-8"><title>Verification failed</title></head>
                    <body style="font-family:system-ui,sans-serif;padding:2rem">
                    <h1>Verification failed</h1>
                    <p>This link is invalid or has expired. Request a new verification email from your administrator.</p>
                    </body></html>
                    """;
        }
    }

    /**
     * @deprecated Prefer {@code POST /api/v1/applications/{applicationId}/account/magic-link/request}.
     */
    @Deprecated
    @PostMapping("/magic-link/request")
    public Map<String, String> requestMagicLink(@Valid @RequestBody AccountDtos.TenantEmailRequest request) {
        if (request.applicationId() != null) {
            return accounts.requestMagicLinkForApplication(request.applicationId(), request.email());
        }
        requireTenantSlug(request.tenantSlug());
        return accounts.requestMagicLink(request.tenantSlug(), request.email());
    }

    /** The emailed link: first step only; the browser continues to the second step when one is needed. */
    @GetMapping(value = "/magic-link/verify", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<?> magicLinkVerifyHtml(
            @RequestParam String token,
            @RequestParam(required = false) UUID applicationId,
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response) {
        try {
            return magicLinkAuth.completeInBrowser(token, applicationId, request, response);
        } catch (Exception ex) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_HTML)
                    .body("""
                            <!DOCTYPE html><html><body style="font-family:system-ui;padding:2rem">
                            <h1>Invalid or expired sign-in link</h1>
                            <p>Request a new link, or <a href="/login.html">sign in another way</a>.</p>
                            </body></html>
                            """);
        }
    }

    /** For apps handling the link on their own page: 200 when signed in, 428 with an MFA challenge, or 403. */
    @PostMapping("/magic-link/verify")
    public ResponseEntity<?> magicLinkVerifyApi(
            @RequestBody Map<String, String> body,
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token is required");
        }
        UUID applicationId = null;
        String rawApp = body.get("applicationId");
        if (rawApp != null && !rawApp.isBlank()) {
            applicationId = UUID.fromString(rawApp.trim());
        }
        return magicLinkAuth.completeAndCreateSessionApi(token, applicationId, request, response);
    }

    @PostMapping("/verify/otp/confirm")
    public Map<String, Object> confirmVerificationOtp(@RequestBody Map<String, String> body) {
        String challengeId = body.get("challengeId");
        String code = body.get("code");
        String channel = body.getOrDefault("channel", "email");
        if (challengeId == null || code == null) {
            throw new IllegalArgumentException("challengeId and code are required");
        }
        UUID id = UUID.fromString(challengeId);
        var user = "phone".equalsIgnoreCase(channel) || "sms".equalsIgnoreCase(channel)
                ? verification.confirmPhoneOtp(id, code)
                : verification.confirmEmailOtp(id, code);
        return Map.of("verified", true, "email", user.getEmail(), "phoneVerified", user.isPhoneVerified());
    }

    @GetMapping("/verify/options")
    public Map<String, Object> verifyOptions(@RequestParam(required = false) UUID applicationId) {
        return verification.verificationOptions(applicationId);
    }

    @PostMapping("/set-password")
    public Map<String, String> setPassword(@Valid @RequestBody AccountDtos.ResetPasswordRequest request) {
        return accounts.setPasswordFromInvite(request.token(), request.password(), request.applicationId());
    }

    @PostMapping("/email/verify")
    public Map<String, Object> verifyEmailApi(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token is required");
        }
        var user = accounts.verifyEmail(token);
        return Map.of("verified", true, "email", user.getEmail());
    }

    private static void requireTenantSlug(String tenantSlug) {
        if (tenantSlug == null || tenantSlug.isBlank()) {
            throw new IllegalArgumentException("applicationId or tenantSlug is required");
        }
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
