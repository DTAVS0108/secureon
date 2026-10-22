package com.secureone.auth.mfa;

import com.secureone.auth.account.PhoneNormalizer;
import com.secureone.auth.application.ApplicationEffectiveSettingsService;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.platform.FeatureFlagDefaults;
import com.secureone.auth.account.otp.OtpChallengeService;
import com.secureone.auth.account.otp.OtpChannel;
import com.secureone.auth.account.otp.OtpPurpose;
import com.secureone.auth.authn.AccountLockoutService;
import com.secureone.auth.authn.LoginSuccessHandler;
import com.secureone.auth.authn.SignedInAuthentication;
import com.secureone.auth.authn.TenantUserDetailsService;
import com.secureone.auth.mfa.passkey.PasskeyAuthenticationService;
import com.secureone.auth.mfa.passkey.PasskeyRegistrationService;
import com.secureone.auth.notify.EmailNotificationService;
import com.secureone.auth.notify.SmsNotificationService;
import com.secureone.auth.tenant.RlsScope;
import com.secureone.auth.trusteddevice.TrustedDeviceService;
import com.secureone.auth.twostep.TwoStepMethod;
import com.secureone.auth.twostep.TwoStepService;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

/**
 * The gate every first sign-in step passes through (password, email code, magic link, external identity
 * provider), and the second step itself: an authenticator code, a code by SMS or email, a passkey or security
 * key, or a backup code; or, for users without a usable option, setting one up (now, or later while allowed).
 * API callers get 204, or 428 with a challenge; browsers are sent to the sign-in page's second step. Wrong codes
 * count toward the account lockout like wrong passwords.
 */
@Service
public class MfaLoginService {

    public record MfaChallengeResponse(UUID mfaChallengeId, List<String> methods, int expiresInSeconds) {}

    private static final Set<String> FIRST_STEP_FACTORS = Set.of(
            FactorGrantedAuthority.PASSWORD_AUTHORITY,
            FactorGrantedAuthority.OTT_AUTHORITY,
            FactorGrantedAuthority.AUTHORIZATION_CODE_AUTHORITY,
            FactorGrantedAuthority.SAML_RESPONSE_AUTHORITY);

    private final MfaPolicyService policy;
    private final MfaChallengeStore challenges;
    private final TotpMfaService totp;
    private final MfaRecoveryCodeService recoveryCodes;
    private final TenantUserDetailsService userDetailsService;
    private final SecurityContextRepository securityContextRepository;
    private final LoginSuccessHandler loginSuccessHandler;
    private final TrustedDeviceService trustedDevices;
    private final AccountLockoutService lockout;
    private final UserAccountRepository users;
    private final TwoStepService twoStep;
    private final MfaFactorRepository factors;
    private final ObjectProvider<PasskeyAuthenticationService> passkeyAuth;
    private final ObjectProvider<PasskeyRegistrationService> passkeyRegistration;
    private final OtpChallengeService otp;
    private final ObjectProvider<SmsNotificationService> sms;
    private final ObjectProvider<EmailNotificationService> email;
    private final ApplicationEffectiveSettingsService effectiveSettings;
    private final AuditService audit;

    public MfaLoginService(
            MfaPolicyService policy,
            MfaChallengeStore challenges,
            TotpMfaService totp,
            MfaRecoveryCodeService recoveryCodes,
            TenantUserDetailsService userDetailsService,
            SecurityContextRepository securityContextRepository,
            @Lazy LoginSuccessHandler loginSuccessHandler,
            TrustedDeviceService trustedDevices,
            AccountLockoutService lockout,
            UserAccountRepository users,
            TwoStepService twoStep,
            MfaFactorRepository factors,
            ObjectProvider<PasskeyAuthenticationService> passkeyAuth,
            ObjectProvider<PasskeyRegistrationService> passkeyRegistration,
            OtpChallengeService otp,
            ObjectProvider<SmsNotificationService> sms,
            ObjectProvider<EmailNotificationService> email,
            ApplicationEffectiveSettingsService effectiveSettings,
            AuditService audit) {
        this.policy = policy;
        this.challenges = challenges;
        this.totp = totp;
        this.recoveryCodes = recoveryCodes;
        this.userDetailsService = userDetailsService;
        this.securityContextRepository = securityContextRepository;
        this.loginSuccessHandler = loginSuccessHandler;
        this.trustedDevices = trustedDevices;
        this.lockout = lockout;
        this.users = users;
        this.twoStep = twoStep;
        this.factors = factors;
        this.passkeyAuth = passkeyAuth;
        this.passkeyRegistration = passkeyRegistration;
        this.otp = otp;
        this.sms = sms;
        this.email = email;
        this.effectiveSettings = effectiveSettings;
        this.audit = audit;
    }

    // ---- The gate ------------------------------------------------------------------------------------------

    /** API sign-ins: 204 with a session, or 428 with the challenge (second step or setup). */
    public ResponseEntity<?> completePasswordLogin(
            Authentication authentication,
            UUID applicationId,
            HttpServletRequest request,
            HttpServletResponse response) {
        UserAccount user = resolveUser(authentication);
        TwoStepService.Decision decision = twoStep.decide(user, applicationId, firstFactor(authentication), request);
        if (decision instanceof TwoStepService.SignedIn) {
            establishSession(authentication, request, response);
            loginSuccessHandler.recordSuccessfulLogin(request, authentication);
            return ResponseEntity.noContent().build();
        }
        MfaPendingChallenge pending = issue(user, authentication, applicationId, decision);
        clearSession(request, response);
        Map<String, Object> body = describe(pending, decision);
        return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).body(body);
    }

    /**
     * Browser sign-ins (form login, magic link, external identity provider): {@code null} when signed in,
     * otherwise the sign-in page URL for the second step or the setup.
     */
    public String completeBrowserSignIn(
            Authentication authentication,
            UUID applicationId,
            String continueUrl,
            HttpServletRequest request,
            HttpServletResponse response) {
        UserAccount user = resolveUser(authentication);
        TwoStepService.Decision decision = twoStep.decide(user, applicationId, firstFactor(authentication), request);
        if (decision instanceof TwoStepService.SignedIn) {
            establishSession(authentication, request, response);
            loginSuccessHandler.recordSuccessfulLogin(request, authentication);
            return null;
        }
        MfaPendingChallenge pending = issue(user, authentication, applicationId, decision);
        clearSession(request, response);
        String app = applicationId != null ? "&applicationId=" + encode(applicationId.toString()) : "";
        String next = continueUrl != null && !continueUrl.isBlank() ? "&continue=" + encode(continueUrl) : "";
        return "/login.html?mfa=1&mfaChallengeId=" + encode(pending.challengeId().toString()) + app + next;
    }

    /** For a signed-in session that must prove a second step again (OIDC acr_values): a verify challenge. */
    public UUID issueStepUp(Authentication authentication, UUID applicationId) {
        UserAccount user = resolveUser(authentication);
        List<TwoStepService.UserMethod> usable = twoStep.usable(user.getId(), applicationId, firstFactor(authentication), false);
        if (usable.isEmpty()) {
            return null;
        }
        MfaPendingChallenge pending = issue(user, authentication, applicationId,
                new TwoStepService.SecondStep(usable, recoveryCodes.remaining(user.getId()) > 0));
        return pending.challengeId();
    }

    private MfaPendingChallenge issue(
            UserAccount user, Authentication authentication, UUID applicationId, TwoStepService.Decision decision) {
        String stage = decision instanceof TwoStepService.Setup ? "setup" : "verify";
        MfaPendingChallenge pending = new MfaPendingChallenge(
                UUID.randomUUID(), user.getId(), tenantSlugFromPrincipal(authentication.getName()), user.getEmail(),
                applicationId, firstFactor(authentication), stage, null, null, null);
        challenges.save(pending, Duration.ofSeconds(policy.challengeTtlSeconds(applicationId)));
        return pending;
    }

    /** Public description of a challenge (no secrets): what the sign-in page shows. */
    public ResponseEntity<?> describe(UUID challengeId) {
        MfaPendingChallenge pending = challenges.get(challengeId).orElse(null);
        if (pending == null) {
            return gone();
        }
        UserAccount user = userOf(pending);
        TwoStepService.Decision decision = pending.isSetup()
                ? twoStep.decide(user, pending.applicationId(), pending.firstFactor(), null)
                : new TwoStepService.SecondStep(
                        twoStep.usable(user.getId(), pending.applicationId(), pending.firstFactor(), false),
                        recoveryCodes.remaining(user.getId()) > 0);
        return ResponseEntity.ok(describe(pending, decision));
    }

    private Map<String, Object> describe(MfaPendingChallenge pending, TwoStepService.Decision decision) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mfaChallengeId", pending.challengeId().toString());
        body.put("stage", pending.isSetup() ? "setup" : "verify");
        body.put("expiresInSeconds", policy.challengeTtlSeconds(pending.applicationId()));
        body.put("rememberDeviceDays", policy.rememberDeviceDays(pending.applicationId()));
        if (decision instanceof TwoStepService.SecondStep step) {
            List<String> methods = new java.util.ArrayList<>(step.methods().stream().map(m -> m.method().id()).distinct().toList());
            if (step.recovery()) {
                methods.add("recovery");
            }
            body.put("methods", methods);
            body.put("options", step.methods().stream().map(m -> {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("id", m.factorId().toString());
                o.put("type", m.method().id());
                o.put("label", m.label());
                o.put("masked", m.masked());
                return o;
            }).toList());
            body.put("recovery", step.recovery());
            // Feature flag (platform, then this application): offer the other options when the first can't be used.
            body.put("tryAnotherWay",
                    effectiveSettings.isFeatureEnabled(pending.applicationId(), FeatureFlagDefaults.MFA_TRY_ANOTHER_WAY));
        } else if (decision instanceof TwoStepService.Setup setup) {
            body.put("methods", List.of());
            body.put("required", setup.required());
            body.put("canSkip", setup.canSkip());
            body.put("deadline", setup.deadline() != null ? setup.deadline().toString() : null);
            body.put("skipsLeft", setup.skipsLeft());
            body.put("allowDontAskAgain", setup.allowDontAskAgain());
            body.put("offered", setup.offered().stream().map(TwoStepMethod::id).toList());
        } else {
            body.put("methods", List.of());
        }
        return body;
    }

    // ---- Second step ---------------------------------------------------------------------------------------

    public ResponseEntity<?> verifyTotp(
            UUID challengeId, String code, HttpServletRequest request, HttpServletResponse response) {
        return verifyTotp(challengeId, code, false, request, response);
    }

    public ResponseEntity<?> verifyTotp(
            UUID challengeId, String code, boolean remember, HttpServletRequest request, HttpServletResponse response) {
        return verifyChallenge(challengeId, "totp", remember, request, response, pending -> {
            requireUsable(pending, TwoStepMethod.TOTP);
            if (!totp.verifyForUser(pending.userId(), code)) {
                throw new IllegalArgumentException("Invalid authenticator code.");
            }
        });
    }

    public ResponseEntity<?> verifyRecovery(
            UUID challengeId, String code, HttpServletRequest request, HttpServletResponse response) {
        MfaPendingChallenge pending = challenges.get(challengeId).orElse(null);
        ResponseEntity<?> result = verifyChallenge(challengeId, "recovery", false, request, response, p -> {
            if (!recoveryCodes.verifyAndConsume(p.userId(), code)) {
                throw new IllegalArgumentException("Invalid or already used recovery code.");
            }
        });
        if (pending != null && result.getStatusCode().is2xxSuccessful()) {
            twoStep.recoveryCodeUsed(userOf(pending), pending.applicationId());
        }
        return result;
    }

    /** Sends a code by SMS or email for one of the user's sent-code options. */
    public ResponseEntity<?> sendCode(UUID challengeId, UUID factorId) {
        MfaPendingChallenge pending = challenges.get(challengeId).orElse(null);
        if (pending == null || pending.isSetup()) {
            return gone();
        }
        TwoStepService.UserMethod option = twoStep.usable(pending.userId(), pending.applicationId(), pending.firstFactor(), false)
                .stream().filter(m -> m.factorId().equals(factorId) && m.method().isSentCode()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("That option is not available for this sign-in."));
        MfaFactor factor = factors.findByIdAndUserId(factorId, pending.userId()).orElseThrow();
        String destination = String.valueOf(factor.getMetadata().get("destination"));
        UUID otpId = deliverCode(pending, option.method(), destination);
        challenges.update(pending.withOtp(otpId, factorId.toString(), null));
        return ResponseEntity.ok(Map.of("sentTo", option.masked() != null ? option.masked() : "", "method", option.method().id()));
    }

    public ResponseEntity<?> verifyCode(
            UUID challengeId, String code, boolean remember, HttpServletRequest request, HttpServletResponse response) {
        return verifyChallenge(challengeId, "code", remember, request, response, pending -> {
            if (pending.otpChallengeId() == null || pending.otpTarget() == null) {
                throw new IllegalArgumentException("Send a code first.");
            }
            checkSentCode(pending, code);
            factors.findByIdAndUserId(UUID.fromString(pending.otpTarget()), pending.userId()).ifPresent(f -> {
                f.setLastUsedAt(Instant.now());
                factors.save(f);
            });
        });
    }

    /** Options for a passkey or security key as the second step (this user's credentials only). */
    public ResponseEntity<?> webauthnOptions(UUID challengeId) {
        MfaPendingChallenge pending = challenges.get(challengeId).orElse(null);
        if (pending == null || pending.isSetup()) {
            return gone();
        }
        boolean allowed = twoStep.usable(pending.userId(), pending.applicationId(), pending.firstFactor(), false)
                .stream().anyMatch(m -> m.method().isWebAuthn());
        if (!allowed) {
            throw new IllegalArgumentException("No passkey or security key is available for this sign-in.");
        }
        return ResponseEntity.ok(passkeyAuth.getObject().beginSecondStep(pending.tenantSlug(), pending.email()));
    }

    public ResponseEntity<?> webauthnVerify(
            UUID challengeId, UUID requestId, String credential, boolean remember,
            HttpServletRequest request, HttpServletResponse response) {
        return verifyChallenge(challengeId, "webauthn", remember, request, response, pending -> {
            if (!passkeyAuth.getObject().finishSecondStep(requestId, credential, pending.userId())) {
                throw new IllegalArgumentException("Passkey or security key not accepted.");
            }
        });
    }

    // ---- Setup during sign-in ------------------------------------------------------------------------------

    /** "Later": allowed while reminders or the grace period allow it; signs in with the first step only. */
    public ResponseEntity<?> skipSetup(
            UUID challengeId, boolean dontAskAgain, HttpServletRequest request, HttpServletResponse response) {
        MfaPendingChallenge pending = challenges.get(challengeId).orElse(null);
        if (pending == null || !pending.isSetup()) {
            return gone();
        }
        UserAccount user = userOf(pending);
        if (!twoStep.skip(user, pending.applicationId(), dontAskAgain)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ProblemDetail.forStatusAndDetail(
                    HttpStatus.FORBIDDEN, "Two-step verification must be set up now."));
        }
        endChallenge(challengeId);
        Authentication authentication = rebuildAuthentication(pending, false);
        establishSession(authentication, request, response);
        loginSuccessHandler.recordSuccessfulLogin(request, authentication);
        return ResponseEntity.noContent().build();
    }

    public ResponseEntity<?> setupTotpStart(UUID challengeId) {
        MfaPendingChallenge pending = setupChallenge(challengeId, TwoStepMethod.TOTP);
        TotpMfaService.EnrollResponse enrollment = totp.enrollRequired(userOf(pending), "Authenticator app");
        challenges.update(pending.withPendingSetup(enrollment.factorId().toString()));
        return ResponseEntity.ok(Map.of(
                "factorId", enrollment.factorId().toString(),
                "secret", enrollment.secret(),
                "otpauthUri", enrollment.otpauthUri(),
                "qrCodeDataUrl", enrollment.qrCodeDataUrl()));
    }

    public ResponseEntity<?> setupTotpConfirm(
            UUID challengeId, String code, HttpServletRequest request, HttpServletResponse response) {
        MfaPendingChallenge pending = setupChallenge(challengeId, TwoStepMethod.TOTP);
        if (pending.pendingSetup() == null) {
            throw new IllegalArgumentException("Start the authenticator setup first.");
        }
        UserAccount user = userOf(pending);
        UUID factorId = UUID.fromString(pending.pendingSetup());
        TotpMfaService.ConfirmResponse confirmed;
        try {
            confirmed = totp.confirm(user, factorId, code, pending.applicationId());
        } catch (IllegalArgumentException ex) {
            return wrongCode(pending, ex.getMessage());
        }
        twoStep.methodAdded(user, pending.applicationId(), factorId, TwoStepMethod.TOTP);
        return completeSetup(pending, confirmed.recoveryCodes(), request, response);
    }

    public ResponseEntity<?> setupWebauthnOptions(UUID challengeId, String kind) {
        TwoStepMethod method = "security_key".equals(kind) ? TwoStepMethod.SECURITY_KEY : TwoStepMethod.PASSKEY;
        MfaPendingChallenge pending = setupChallenge(challengeId, method);
        return ResponseEntity.ok(passkeyRegistration.getObject().beginRegistration(
                userOf(pending), pending.tenantSlug(), null, pending.applicationId(), method.id()));
    }

    public ResponseEntity<?> setupWebauthnVerify(
            UUID challengeId, UUID requestId, String credential, HttpServletRequest request, HttpServletResponse response) {
        MfaPendingChallenge pending = challenges.get(challengeId).orElse(null);
        if (pending == null || !pending.isSetup()) {
            return gone();
        }
        UserAccount user = userOf(pending);
        Map<String, Object> saved;
        try {
            saved = passkeyRegistration.getObject().finishRegistration(user, requestId, credential, null);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "The passkey or security key could not be added. Try again."));
        }
        TwoStepMethod method = "security_key".equals(saved.get("kind")) ? TwoStepMethod.SECURITY_KEY : TwoStepMethod.PASSKEY;
        List<String> codes = twoStep.methodAdded(user, pending.applicationId(), UUID.fromString(String.valueOf(saved.get("factorId"))), method);
        return completeSetup(pending, codes, request, response);
    }

    /** SMS (to {@code phone}) or email (to the account's verified email): sends a code to confirm the option. */
    public ResponseEntity<?> setupSendCode(UUID challengeId, String methodId, String phone) {
        TwoStepMethod method = TwoStepMethod.fromId(methodId).filter(TwoStepMethod::isSentCode)
                .orElseThrow(() -> new IllegalArgumentException("method must be sms or email"));
        MfaPendingChallenge pending = setupChallenge(challengeId, method);
        UserAccount user = userOf(pending);
        String destination;
        if (method == TwoStepMethod.SMS) {
            destination = PhoneNormalizer.toE164(phone, PhoneNormalizer.PLATFORM_DEFAULT_REGION);
            if (destination == null) {
                throw new IllegalArgumentException("Enter a valid mobile number with its country code.");
            }
        } else {
            if (!user.isEmailVerified()) {
                throw new IllegalArgumentException("Verify your email address first.");
            }
            destination = user.getEmail();
        }
        UUID otpId = deliverCode(pending, method, destination);
        challenges.update(pending.withOtp(otpId, method.id(), destination));
        return ResponseEntity.ok(Map.of("sentTo", OtpChallengeService.maskDestination(
                method == TwoStepMethod.SMS ? OtpChannel.SMS : OtpChannel.EMAIL, destination)));
    }

    public ResponseEntity<?> setupConfirmCode(
            UUID challengeId, String code, HttpServletRequest request, HttpServletResponse response) {
        MfaPendingChallenge pending = challenges.get(challengeId).orElse(null);
        if (pending == null || !pending.isSetup()) {
            return gone();
        }
        if (pending.otpChallengeId() == null || pending.pendingSetup() == null) {
            throw new IllegalArgumentException("Send a code first.");
        }
        TwoStepMethod method = TwoStepMethod.fromId(pending.otpTarget()).orElseThrow();
        try {
            checkSentCode(pending, code);
        } catch (IllegalArgumentException ex) {
            return wrongCode(pending, ex.getMessage());
        }
        UserAccount user = userOf(pending);
        MfaFactor factor = saveSentCodeFactor(user.getId(), method, pending.pendingSetup());
        List<String> codes = twoStep.methodAdded(user, pending.applicationId(), factor.getId(), method);
        return completeSetup(pending, codes, request, response);
    }

    /** A confirmed SMS or email option. */
    public MfaFactor saveSentCodeFactor(UUID userId, TwoStepMethod method, String destination) {
        factors.findByUserIdAndType(userId, TwoStepService.factorType(method)).stream()
                .filter(f -> destination.equalsIgnoreCase(String.valueOf(f.getMetadata().get("destination"))))
                .forEach(factors::delete);
        MfaFactor factor = new MfaFactor();
        factor.setUserId(userId);
        factor.setType(TwoStepService.factorType(method));
        factor.setLabel(method.label());
        factor.setVerified(true);
        Map<String, Object> meta = new HashMap<>();
        meta.put("destination", destination);
        factor.setMetadata(meta);
        return factors.save(factor);
    }

    private ResponseEntity<?> completeSetup(
            MfaPendingChallenge pending, List<String> codes, HttpServletRequest request, HttpServletResponse response) {
        endChallenge(pending.challengeId());
        // Setting up and confirming a method during sign-in proves it: the session counts as two steps.
        Authentication authentication = rebuildAuthentication(pending, true);
        establishSession(authentication, request, response);
        loginSuccessHandler.recordSuccessfulLogin(request, authentication);
        return ResponseEntity.ok(Map.of("signedIn", true, "recoveryCodes", codes != null ? codes : List.of()));
    }

    private MfaPendingChallenge setupChallenge(UUID challengeId, TwoStepMethod method) {
        MfaPendingChallenge pending = challenges.get(challengeId)
                .filter(MfaPendingChallenge::isSetup)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.GONE, "This sign-in expired. Sign in again."));
        UserAccount user = userOf(pending);
        TwoStepService.Decision d = twoStep.decide(user, pending.applicationId(), pending.firstFactor(), null);
        List<TwoStepMethod> offered = d instanceof TwoStepService.Setup s ? s.offered()
                : twoStep.offered(pending.applicationId(), false);
        if (!offered.contains(method)) {
            throw new IllegalArgumentException(method.label() + " is not offered for this application.");
        }
        return pending;
    }

    // ---- shared -------------------------------------------------------------------------------------------

    private UUID deliverCode(MfaPendingChallenge pending, TwoStepMethod method, String destination) {
        UserAccount user = userOf(pending);
        UUID tenantId = user.getTenantId();
        OtpChannel channel = method == TwoStepMethod.SMS ? OtpChannel.SMS : OtpChannel.EMAIL;
        var issued = otp.issue(user.getId(), tenantId, pending.applicationId(), OtpPurpose.MFA, channel, destination, null, null);
        if (channel == OtpChannel.SMS) {
            sms.getObject().sendOtp(pending.applicationId(), destination, issued.code(), "verification");
        } else {
            email.getObject().sendLoginOtp(pending.applicationId(), user, issued.code(), issued.expiresInSeconds());
        }
        return issued.challengeId();
    }

    private void checkSentCode(MfaPendingChallenge pending, String code) {
        try {
            var verified = otp.verify(pending.otpChallengeId(), code == null ? "" : code);
            if (!pending.userId().equals(verified.getUserId())) {
                throw new IllegalArgumentException("Invalid code.");
            }
        } catch (com.secureone.auth.admin.ResourceNotFoundException | org.springframework.web.server.ResponseStatusException ex) {
            throw new IllegalArgumentException("Invalid or expired code.");
        }
    }

    private void requireUsable(MfaPendingChallenge pending, TwoStepMethod method) {
        boolean ok = twoStep.usable(pending.userId(), pending.applicationId(), pending.firstFactor(), false)
                .stream().anyMatch(m -> m.method() == method);
        if (!ok) {
            throw new IllegalArgumentException(method.label() + " is not available for this sign-in.");
        }
    }

    /** {@code used}: "totp", "code" (SMS or email), "webauthn" (passkey or security key) or "recovery". */
    private ResponseEntity<?> verifyChallenge(
            UUID challengeId,
            String used,
            boolean remember,
            HttpServletRequest request,
            HttpServletResponse response,
            java.util.function.Consumer<MfaPendingChallenge> verifier) {
        MfaPendingChallenge pending = challenges.get(challengeId).orElse(null);
        if (pending == null || pending.isSetup()) {
            return gone();
        }
        // Locked accounts (wrong passwords or wrong codes) cannot finish signing in, whatever the code.
        if (isLocked(pending.userId())) {
            endChallenge(challengeId);
            return locked();
        }
        int attempts = challenges.incrementAttempts(
                challengeId, Duration.ofSeconds(policy.challengeTtlSeconds(pending.applicationId())));
        if (attempts > policy.maxChallengeAttempts(pending.applicationId())) {
            endChallenge(challengeId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Too many MFA attempts."));
        }
        try {
            verifier.accept(pending);
        } catch (IllegalArgumentException ex) {
            return wrongCode(pending, ex.getMessage());
        }
        auditAnotherWay(pending, used);
        endChallenge(challengeId);
        Authentication authentication = rebuildAuthentication(pending, true);
        establishSession(authentication, request, response);
        int rememberDays = policy.rememberDeviceDays(pending.applicationId());
        if (remember && rememberDays > 0) {
            trustedDevices.issue(pending.userId(), rememberDays, request, response);
        }
        loginSuccessHandler.recordSuccessfulLogin(request, authentication);
        return ResponseEntity.noContent().build();
    }

    /**
     * Audit: the second step was done with an option other than the one asked first ("Try another way"), e.g.
     * a backup code after a lost phone. Same order as the sign-in page shows ({@link #describe(UUID)}).
     */
    private void auditAnotherWay(MfaPendingChallenge pending, String used) {
        List<TwoStepService.UserMethod> offered =
                twoStep.usable(pending.userId(), pending.applicationId(), pending.firstFactor(), false);
        TwoStepService.UserMethod first = offered.isEmpty() ? null : offered.get(0);
        boolean askedFirst = first != null && switch (used) {
            case "totp" -> first.method() == TwoStepMethod.TOTP;
            case "webauthn" -> first.method().isWebAuthn();
            case "code" -> first.method().isSentCode() && first.factorId().toString().equals(pending.otpTarget());
            default -> false; // a backup code is never asked first
        };
        if (askedFirst) {
            return;
        }
        String usedLabel = switch (used) {
            case "code" -> offered.stream()
                    .filter(m -> m.factorId().toString().equals(pending.otpTarget()))
                    .map(m -> m.method().id())
                    .findFirst()
                    .orElse("code");
            case "webauthn" -> "passkey or security key";
            default -> used;
        };
        String label = usedLabel + " instead of " + (first != null ? first.method().id() : "none");
        UUID tenantId = userOf(pending).getTenantId();
        RlsScope.runAsPlatform(() -> audit.record(tenantId, pending.applicationId(), pending.email(),
                "user.two_step_another_way", "user_account", pending.userId(), label, true));
    }

    /** Per account, across challenges: signing in again must not give a fresh set of guesses. */
    private ResponseEntity<?> wrongCode(MfaPendingChallenge pending, String message) {
        RlsScope.runAsPlatform(() -> lockout.recordFailedAttempt(pending.userId(), pending.applicationId()));
        if (isLocked(pending.userId())) {
            endChallenge(pending.challengeId());
            return locked();
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, message));
    }

    /** Read across tenants: this request is not yet signed in, so it has no tenant scope. */
    private boolean isLocked(UUID userId) {
        return RlsScope.asPlatform(() -> users.findById(userId)
                .map(UserAccount::getLockedUntil)
                .map(until -> until.isAfter(Instant.now()))
                .orElse(false));
    }

    private UserAccount userOf(MfaPendingChallenge pending) {
        Supplier<UserAccount> load = () -> users.findById(pending.userId()).orElseThrow();
        return RlsScope.asPlatform(load);
    }

    private void endChallenge(UUID challengeId) {
        challenges.delete(challengeId);
        challenges.clearAttempts(challengeId);
    }

    private static ResponseEntity<ProblemDetail> gone() {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.GONE, "MFA challenge expired or invalid."));
    }

    private static ResponseEntity<ProblemDetail> locked() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, AccountLockoutService.LOCKED_MESSAGE));
    }

    public void clearSession(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        SecurityContext empty = SecurityContextHolder.createEmptyContext();
        securityContextRepository.saveContext(empty, request, response);
    }

    private Authentication rebuildAuthentication(MfaPendingChallenge pending, boolean secondStepDone) {
        String username = pending.tenantSlug() + ":" + pending.email();
        UserDetails user = userDetailsService.loadUserByUsername(username);
        String first = pending.firstFactor() != null ? pending.firstFactor() : FactorGrantedAuthority.PASSWORD_AUTHORITY;
        return secondStepDone
                ? SignedInAuthentication.of(user, first, SignedInAuthentication.MFA_FACTOR)
                : SignedInAuthentication.of(user, first);
    }

    /** The factor that completed the first step, so the session records how the user really signed in. */
    private static String firstFactor(Authentication authentication) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (FIRST_STEP_FACTORS.contains(authority.getAuthority())) {
                return authority.getAuthority();
            }
        }
        return FactorGrantedAuthority.PASSWORD_AUTHORITY;
    }

    private void establishSession(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private UserAccount resolveUser(Authentication authentication) {
        String principal = authentication.getName();
        int sep = principal.indexOf(':');
        if (sep <= 0) {
            throw new IllegalStateException("Unexpected principal for MFA.");
        }
        return userDetailsService.resolveAccount(
                principal.substring(0, sep).trim().toLowerCase(Locale.ROOT),
                principal.substring(sep + 1).trim().toLowerCase(Locale.ROOT));
    }

    private static String tenantSlugFromPrincipal(String principal) {
        int sep = principal.indexOf(':');
        if (sep <= 0) {
            throw new IllegalStateException("Unexpected principal for MFA.");
        }
        return principal.substring(0, sep).trim().toLowerCase(Locale.ROOT);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
