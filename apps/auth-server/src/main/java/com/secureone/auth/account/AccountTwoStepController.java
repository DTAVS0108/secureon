package com.secureone.auth.account;

import com.secureone.auth.account.otp.OtpChallengeService;
import com.secureone.auth.account.otp.OtpChannel;
import com.secureone.auth.account.otp.OtpPurpose;
import com.secureone.auth.authn.SessionUserResolver;
import com.secureone.auth.mfa.MfaFactor;
import com.secureone.auth.mfa.MfaFactorRepository;
import com.secureone.auth.mfa.MfaLoginService;
import com.secureone.auth.mfa.MfaRecoveryCodeService;
import com.secureone.auth.mfa.TotpMfaService;
import com.secureone.auth.mfa.passkey.PasskeyAuthenticationService;
import com.secureone.auth.mfa.passkey.PasskeyRegistrationService;
import com.secureone.auth.notify.EmailNotificationService;
import com.secureone.auth.notify.SmsNotificationService;
import com.secureone.auth.trusteddevice.TrustedDevice;
import com.secureone.auth.trusteddevice.TrustedDeviceService;
import com.secureone.auth.twostep.FreshStep;
import com.secureone.auth.twostep.TwoStepMethod;
import com.secureone.auth.twostep.TwoStepService;
import com.secureone.auth.user.UserAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The signed-in user's two-step verification: options, default, backup codes, remembered devices. Adding,
 * removing, renewing backup codes and turning it off need a recent second step ({@link FreshStep}): call
 * {@code /verify} first when the answer is 403 with {@code freshStepRequired}.
 */
@Tag(name = "Account", description = "Two-step verification for the signed-in user")
@RestController
@RequestMapping("/api/v1/account/2fa")
public class AccountTwoStepController {

    private static final String SETUP_KEY = "secureone.twoStepCodeSetup";
    private static final String VERIFY_KEY = "secureone.twoStepCodeVerify";

    public record VerifyRequest(String method, String code, UUID factorId, UUID requestId, String credential) {}

    public record SendRequest(UUID factorId) {}

    public record TotpConfirmRequest(UUID factorId, String code) {}

    public record WebAuthnStartRequest(String kind, String label) {}

    public record WebAuthnFinishRequest(UUID requestId, String credential, String label) {}

    public record CodeSendRequest(String method, String phone) {}

    public record CodeConfirmRequest(String code) {}

    private final SessionUserResolver users;
    private final TwoStepService twoStep;
    private final FreshStep freshStep;
    private final TotpMfaService totp;
    private final MfaRecoveryCodeService recoveryCodes;
    private final MfaFactorRepository factors;
    private final PasskeyAuthenticationService passkeyAuth;
    private final PasskeyRegistrationService passkeyRegistration;
    private final TrustedDeviceService trustedDevices;
    private final OtpChallengeService otp;
    private final SmsNotificationService sms;
    private final EmailNotificationService email;
    private final MfaLoginService mfaLogin;

    public AccountTwoStepController(
            SessionUserResolver users,
            TwoStepService twoStep,
            FreshStep freshStep,
            TotpMfaService totp,
            MfaRecoveryCodeService recoveryCodes,
            MfaFactorRepository factors,
            PasskeyAuthenticationService passkeyAuth,
            PasskeyRegistrationService passkeyRegistration,
            TrustedDeviceService trustedDevices,
            OtpChallengeService otp,
            SmsNotificationService sms,
            EmailNotificationService email,
            MfaLoginService mfaLogin) {
        this.users = users;
        this.twoStep = twoStep;
        this.freshStep = freshStep;
        this.totp = totp;
        this.recoveryCodes = recoveryCodes;
        this.factors = factors;
        this.passkeyAuth = passkeyAuth;
        this.passkeyRegistration = passkeyRegistration;
        this.trustedDevices = trustedDevices;
        this.otp = otp;
        this.sms = sms;
        this.email = email;
        this.mfaLogin = mfaLogin;
    }

    @Operation(summary = "Two-step status: options, backup codes, remembered devices, requirement and deadline")
    @GetMapping
    public Map<String, Object> status(
            Authentication authentication, HttpServletRequest request, @RequestParam(required = false) UUID applicationId) {
        UserAccount user = users.requireTenantUser(authentication);
        Map<String, Object> out = new LinkedHashMap<>(twoStep.status(user, applicationId));
        out.put("devices", trustedDevices.list(user.getId()).stream().map(AccountTwoStepController::device).toList());
        out.put("verifiedRecently", freshStep.isFresh(request, applicationId));
        return out;
    }

    // ---- Verify it's you -----------------------------------------------------------------------------------

    @Operation(summary = "Send a code (text message or email option) to verify it's you")
    @PostMapping("/verify/send")
    public Map<String, Object> verifySend(Authentication authentication, HttpServletRequest request,
            @RequestBody SendRequest body, @RequestParam(required = false) UUID applicationId) {
        UserAccount user = users.requireTenantUser(authentication);
        TwoStepService.UserMethod option = twoStep.method(user.getId(), body.factorId())
                .filter(m -> m.method().isSentCode())
                .orElseThrow(() -> new IllegalArgumentException("Choose a text message or email option."));
        MfaFactor factor = factors.findByIdAndUserId(body.factorId(), user.getId()).orElseThrow();
        String destination = String.valueOf(factor.getMetadata().get("destination"));
        UUID otpId = sendCode(user, applicationId, option.method(), destination);
        request.getSession(true).setAttribute(VERIFY_KEY, otpId.toString());
        return Map.of("sentTo", option.masked() != null ? option.masked() : "");
    }

    @Operation(summary = "Passkey or security key to verify it's you: options")
    @PostMapping("/verify/webauthn/options")
    public Map<String, Object> verifyWebauthnOptions(Authentication authentication) {
        UserAccount user = users.requireTenantUser(authentication);
        return passkeyAuth.beginSecondStep(tenantSlug(authentication), user.getEmail());
    }

    @Operation(summary = "Verify it's you (authenticator, sent code, passkey or backup code) for sensitive changes")
    @PostMapping("/verify")
    public Map<String, Object> verify(Authentication authentication, HttpServletRequest request,
            @RequestBody VerifyRequest body) {
        UserAccount user = users.requireTenantUser(authentication);
        boolean ok = switch (String.valueOf(body.method())) {
            case "totp" -> totp.verifyForUser(user.getId(), body.code());
            case "recovery" -> recoveryCodes.verifyAndConsume(user.getId(), body.code());
            case "code" -> checkCode(request, user, body.code());
            case "webauthn" -> body.requestId() != null
                    && passkeyAuth.finishSecondStep(body.requestId(), body.credential(), user.getId());
            default -> throw new IllegalArgumentException("method must be totp, code, webauthn or recovery");
        };
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "That didn't work. Try again.");
        }
        FreshStep.mark(request);
        return Map.of("verified", true);
    }

    // ---- Add an option -------------------------------------------------------------------------------------

    @Operation(summary = "Add an authenticator app: QR code")
    @PostMapping("/methods/totp/start")
    public Map<String, Object> totpStart(Authentication authentication, HttpServletRequest request,
            @RequestParam(required = false) UUID applicationId) {
        UserAccount user = users.requireTenantUser(authentication);
        requireOffered(applicationId, TwoStepMethod.TOTP);
        freshStep.require(request, user.getId(), applicationId);
        if (factors.findFirstByUserIdAndTypeAndVerifiedTrueOrderByCreatedAtAsc(user.getId(), "totp").isPresent()) {
            throw new IllegalArgumentException("An authenticator app is already set up. Remove it first to replace it.");
        }
        TotpMfaService.EnrollResponse e = totp.enrollRequired(user, "Authenticator app");
        return Map.of("factorId", e.factorId().toString(), "secret", e.secret(), "otpauthUri", e.otpauthUri(),
                "qrCodeDataUrl", e.qrCodeDataUrl());
    }

    @Operation(summary = "Add an authenticator app: confirm the code")
    @PostMapping("/methods/totp/confirm")
    public Map<String, Object> totpConfirm(Authentication authentication, HttpServletRequest request,
            @RequestBody TotpConfirmRequest body, @RequestParam(required = false) UUID applicationId) {
        UserAccount user = users.requireTenantUser(authentication);
        TotpMfaService.ConfirmResponse confirmed = totp.confirm(user, body.factorId(), body.code(), applicationId);
        List<String> codes = twoStep.methodAdded(user, applicationId, confirmed.factorId(), TwoStepMethod.TOTP);
        FreshStep.mark(request);
        return added(confirmed.factorId(), confirmed.recoveryCodes().isEmpty() ? codes : confirmed.recoveryCodes());
    }

    @Operation(summary = "Add a passkey or security key: registration options")
    @PostMapping("/methods/webauthn/options")
    public Map<String, Object> webauthnStart(Authentication authentication, HttpServletRequest request,
            @RequestBody(required = false) WebAuthnStartRequest body, @RequestParam(required = false) UUID applicationId) {
        UserAccount user = users.requireTenantUser(authentication);
        TwoStepMethod method = body != null && "security_key".equals(body.kind()) ? TwoStepMethod.SECURITY_KEY : TwoStepMethod.PASSKEY;
        requireOffered(applicationId, method);
        freshStep.require(request, user.getId(), applicationId);
        return passkeyRegistration.beginRegistration(user, tenantSlug(authentication),
                body != null ? body.label() : null, applicationId, method.id());
    }

    @Operation(summary = "Add a passkey or security key: register it")
    @PostMapping("/methods/webauthn/verify")
    public Map<String, Object> webauthnFinish(Authentication authentication, HttpServletRequest request,
            @RequestBody WebAuthnFinishRequest body, @RequestParam(required = false) UUID applicationId) {
        UserAccount user = users.requireTenantUser(authentication);
        Map<String, Object> saved = passkeyRegistration.finishRegistration(user, body.requestId(), body.credential(), body.label());
        TwoStepMethod method = "security_key".equals(saved.get("kind")) ? TwoStepMethod.SECURITY_KEY : TwoStepMethod.PASSKEY;
        UUID factorId = UUID.fromString(String.valueOf(saved.get("factorId")));
        List<String> codes = twoStep.methodAdded(user, applicationId, factorId, method);
        FreshStep.mark(request);
        return added(factorId, codes);
    }

    @Operation(summary = "Add a text message or email option: send a code")
    @PostMapping("/methods/code/send")
    public Map<String, Object> codeSend(Authentication authentication, HttpServletRequest request,
            @RequestBody CodeSendRequest body, @RequestParam(required = false) UUID applicationId) {
        UserAccount user = users.requireTenantUser(authentication);
        TwoStepMethod method = TwoStepMethod.fromId(String.valueOf(body.method())).filter(TwoStepMethod::isSentCode)
                .orElseThrow(() -> new IllegalArgumentException("method must be sms or email"));
        requireOffered(applicationId, method);
        freshStep.require(request, user.getId(), applicationId);
        String destination;
        if (method == TwoStepMethod.SMS) {
            destination = PhoneNormalizer.toE164(body.phone(), PhoneNormalizer.PLATFORM_DEFAULT_REGION);
            if (destination == null) {
                throw new IllegalArgumentException("Enter a valid mobile number with its country code.");
            }
        } else {
            if (!user.isEmailVerified()) {
                throw new IllegalArgumentException("Verify your email address first.");
            }
            destination = user.getEmail();
        }
        UUID otpId = sendCode(user, applicationId, method, destination);
        HttpSession session = request.getSession(true);
        session.setAttribute(SETUP_KEY, Map.of("otp", otpId.toString(), "method", method.id(), "destination", destination));
        return Map.of("sentTo", OtpChallengeService.maskDestination(
                method == TwoStepMethod.SMS ? OtpChannel.SMS : OtpChannel.EMAIL, destination));
    }

    @Operation(summary = "Add a text message or email option: confirm the code")
    @PostMapping("/methods/code/confirm")
    @SuppressWarnings("unchecked")
    public Map<String, Object> codeConfirm(Authentication authentication, HttpServletRequest request,
            @RequestBody CodeConfirmRequest body, @RequestParam(required = false) UUID applicationId) {
        UserAccount user = users.requireTenantUser(authentication);
        HttpSession session = request.getSession(false);
        Object raw = session != null ? session.getAttribute(SETUP_KEY) : null;
        if (!(raw instanceof Map<?, ?> setup)) {
            throw new IllegalArgumentException("Send a code first.");
        }
        Map<String, String> s = (Map<String, String>) setup;
        try {
            var verified = otp.verify(UUID.fromString(s.get("otp")), body.code() == null ? "" : body.code());
            if (!user.getId().equals(verified.getUserId())) {
                throw new IllegalArgumentException("Invalid code.");
            }
        } catch (com.secureone.auth.admin.ResourceNotFoundException ex) {
            throw new IllegalArgumentException("Invalid or expired code.");
        }
        session.removeAttribute(SETUP_KEY);
        TwoStepMethod method = TwoStepMethod.fromId(s.get("method")).orElseThrow();
        MfaFactor factor = mfaLogin.saveSentCodeFactor(user.getId(), method, s.get("destination"));
        List<String> codes = twoStep.methodAdded(user, applicationId, factor.getId(), method);
        FreshStep.mark(request);
        return added(factor.getId(), codes);
    }

    // ---- Manage --------------------------------------------------------------------------------------------

    @Operation(summary = "Remove an option")
    @DeleteMapping("/methods/{factorId}")
    public ResponseEntity<Void> remove(Authentication authentication, HttpServletRequest request,
            @PathVariable UUID factorId, @RequestParam(required = false) UUID applicationId) {
        UserAccount user = users.requireTenantUser(authentication);
        TwoStepService.UserMethod option = twoStep.method(user.getId(), factorId)
                .orElseThrow(() -> new com.secureone.auth.admin.ResourceNotFoundException("Option not found."));
        freshStep.require(request, user.getId(), applicationId);
        Map<String, Object> st = twoStep.status(user, applicationId);
        if ("required".equals(st.get("mode")) && twoStep.methods(user.getId()).size() <= 1) {
            throw new IllegalArgumentException("Two-step verification is required: add another option before removing this one.");
        }
        factors.findByIdAndUserId(factorId, user.getId()).ifPresent(factors::delete);
        twoStep.methodRemoved(user, applicationId, option.method());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Make an option the one asked for first")
    @PostMapping("/methods/{factorId}/default")
    public Map<String, Object> makeDefault(Authentication authentication, @PathVariable UUID factorId) {
        UserAccount user = users.requireTenantUser(authentication);
        twoStep.method(user.getId(), factorId)
                .orElseThrow(() -> new com.secureone.auth.admin.ResourceNotFoundException("Option not found."));
        twoStep.setDefault(user.getId(), factorId);
        return Map.of("defaultMethod", factorId.toString());
    }

    @Operation(summary = "New backup codes (the old ones stop working)")
    @PostMapping("/backup-codes")
    public Map<String, Object> newBackupCodes(Authentication authentication, HttpServletRequest request,
            @RequestParam(required = false) UUID applicationId) {
        UserAccount user = users.requireTenantUser(authentication);
        if (twoStep.methods(user.getId()).isEmpty()) {
            throw new IllegalArgumentException("Set up a sign-in option first.");
        }
        freshStep.require(request, user.getId(), applicationId);
        return Map.of("recoveryCodes", twoStep.regenerateRecoveryCodes(user, applicationId));
    }

    @Operation(summary = "Forget a remembered device (it will ask for the second step again)")
    @DeleteMapping("/devices/{deviceId}")
    public ResponseEntity<Void> forgetDevice(Authentication authentication, @PathVariable UUID deviceId) {
        UserAccount user = users.requireTenantUser(authentication);
        if (!trustedDevices.revoke(user.getId(), deviceId)) {
            throw new com.secureone.auth.admin.ResourceNotFoundException("Device not found.");
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Forget all remembered devices")
    @DeleteMapping("/devices")
    public Map<String, Object> forgetAllDevices(Authentication authentication) {
        UserAccount user = users.requireTenantUser(authentication);
        return Map.of("forgotten", trustedDevices.revokeAll(user.getId()));
    }

    @Operation(summary = "Turn two-step verification off (only when it is optional)")
    @PostMapping("/off")
    public ResponseEntity<Void> turnOff(Authentication authentication, HttpServletRequest request,
            @RequestParam(required = false) UUID applicationId) {
        UserAccount user = users.requireTenantUser(authentication);
        freshStep.require(request, user.getId(), applicationId);
        twoStep.turnOff(user, applicationId);
        return ResponseEntity.noContent().build();
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    private void requireOffered(UUID applicationId, TwoStepMethod method) {
        if (!twoStep.offered(applicationId, false).contains(method)) {
            throw new IllegalArgumentException(method.label() + " is not offered for this application.");
        }
    }

    private UUID sendCode(UserAccount user, UUID applicationId, TwoStepMethod method, String destination) {
        OtpChannel channel = method == TwoStepMethod.SMS ? OtpChannel.SMS : OtpChannel.EMAIL;
        var issued = otp.issue(user.getId(), user.getTenantId(), applicationId, OtpPurpose.MFA, channel, destination, null, null);
        if (channel == OtpChannel.SMS) {
            sms.sendOtp(applicationId, destination, issued.code(), "verification");
        } else {
            email.sendLoginOtp(applicationId, user, issued.code(), issued.expiresInSeconds());
        }
        return issued.challengeId();
    }

    private boolean checkCode(HttpServletRequest request, UserAccount user, String code) {
        HttpSession session = request.getSession(false);
        Object raw = session != null ? session.getAttribute(VERIFY_KEY) : null;
        if (raw == null) {
            throw new IllegalArgumentException("Send a code first.");
        }
        try {
            var verified = otp.verify(UUID.fromString(raw.toString()), code == null ? "" : code);
            session.removeAttribute(VERIFY_KEY);
            return user.getId().equals(verified.getUserId());
        } catch (com.secureone.auth.admin.ResourceNotFoundException ex) {
            return false;
        }
    }

    private static Map<String, Object> added(UUID factorId, List<String> codes) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("factorId", factorId.toString());
        out.put("recoveryCodes", codes != null ? codes : List.of());
        return out;
    }

    private static Map<String, Object> device(TrustedDevice d) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", d.getId().toString());
        out.put("label", d.getLabel());
        out.put("createdAt", d.getCreatedAt() != null ? d.getCreatedAt().toString() : null);
        out.put("lastUsedAt", d.getLastUsedAt() != null ? d.getLastUsedAt().toString() : null);
        out.put("expiresAt", d.getExpiresAt() != null ? d.getExpiresAt().toString() : null);
        return out;
    }

    private static String tenantSlug(Authentication authentication) {
        String name = authentication.getName();
        int sep = name.indexOf(':');
        return sep > 0 ? name.substring(0, sep) : name;
    }
}
