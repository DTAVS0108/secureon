package com.secureone.auth.admin;

import com.secureone.auth.audit.AuditService;
import com.secureone.auth.authn.AccountLockoutService;
import com.secureone.auth.authn.AdminSessionPrincipals;
import com.secureone.auth.mfa.MfaRecoveryCodeService;
import com.secureone.auth.mfa.TotpMfaService;
import com.secureone.auth.notify.EmailNotificationService;
import com.secureone.auth.tenant.RlsScope;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin console sign-in. Step 1 checks the password; step 2 requires an authenticator code (or a
 * recovery code), or enrolls an authenticator when the operator has none. Only then is a console
 * session token issued.
 */
@Tag(name = "Admin — auth", description = "Admin console sign-in: password, then authenticator code")
@RestController
@RequestMapping("/api/admin/v1/auth")
public class AdminAuthController {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthController.class);

    private final AuthenticationManager authenticationManager;
    private final AdminAccessService access;
    private final AdminOperatorService operators;
    private final AdminSessionService sessions;
    private final AdminMfaChallengeService challenges;
    private final AdminSessionPrincipals principals;
    private final TotpMfaService totp;
    private final MfaRecoveryCodeService recoveryCodes;
    private final UserAccountRepository users;
    private final AuditService audit;
    private final EmailNotificationService mail;
    private final AccountLockoutService lockout;
    private final boolean mfaRequired;

    public AdminAuthController(
            AuthenticationManager authenticationManager,
            AdminAccessService access,
            AdminOperatorService operators,
            AdminSessionService sessions,
            AdminMfaChallengeService challenges,
            AdminSessionPrincipals principals,
            TotpMfaService totp,
            MfaRecoveryCodeService recoveryCodes,
            UserAccountRepository users,
            AuditService audit,
            EmailNotificationService mail,
            AccountLockoutService lockout,
            @Value("${secureone.admin.mfa-required:true}") boolean mfaRequired) {
        this.authenticationManager = authenticationManager;
        this.access = access;
        this.operators = operators;
        this.sessions = sessions;
        this.challenges = challenges;
        this.principals = principals;
        this.totp = totp;
        this.recoveryCodes = recoveryCodes;
        this.users = users;
        this.audit = audit;
        this.mail = mail;
        this.lockout = lockout;
        this.mfaRequired = mfaRequired;
    }

    /** Revokes the console session token sent in the {@code Authorization} header. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String prefix = AdminCredentialAuthenticationFilter.SESSION_SCHEME + " ";
        if (authorization != null && authorization.regionMatches(true, 0, prefix, 0, prefix.length())) {
            sessions.revoke(authorization.substring(prefix.length()).trim());
        }
        return ResponseEntity.noContent().build();
    }

    public record AdminLoginRequest(String tenantSlug, String emailOrUsername, String password) {}

    public record MfaVerifyRequest(String mfaToken, String code, String recoveryCode) {}

    public record MfaEnrollConfirmRequest(String mfaToken, String factorId, String code) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AdminLoginRequest body) {
        if (body.emailOrUsername() == null
                || body.emailOrUsername().isBlank()
                || body.password() == null) {
            return problem(HttpStatus.BAD_REQUEST, "Username and password are required.");
        }
        String username = resolveUsername(body.tenantSlug(), body.emailOrUsername());
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, body.password()));
        } catch (AuthenticationException ex) {
            // Unknown account and wrong password look the same, so sign-in cannot be used to probe
            // which accounts exist. Locked and suspended keep their message so people know to wait
            // or contact an administrator.
            boolean generic = ex instanceof org.springframework.security.core.userdetails.UsernameNotFoundException
                    || ex instanceof org.springframework.security.authentication.BadCredentialsException
                    || ex.getMessage() == null
                    || ex.getMessage().isBlank();
            return problem(HttpStatus.UNAUTHORIZED, generic ? "Invalid credentials." : ex.getMessage());
        }

        if (access.isBreakGlass(auth)) {
            return ResponseEntity.ok(issueBreakGlassSession(auth));
        }
        boolean platformOperator = access.isPlatformSuperAdmin(auth);
        if (!platformOperator && access.accessibleApplications(auth, null).isEmpty()) {
            return problem(HttpStatus.FORBIDDEN, "Signed in, but no applications are assigned to this account.");
        }
        UserAccount user = RlsScope.asPlatform(() -> operators.resolveTenantUser(auth, null))
                .orElse(null);
        if (user == null) {
            return problem(HttpStatus.UNAUTHORIZED, "Invalid credentials.");
        }
        if (!mfaRequired) {
            return ResponseEntity.ok(issueSession(auth, user, "password"));
        }
        if (totp.hasVerifiedTotp(user.getId())) {
            String mfaToken = challenges.issue(auth.getName(), user.getId(), "verify");
            return ResponseEntity.ok(Map.of(
                    "mfaRequired", true,
                    "mfaToken", mfaToken,
                    "methods", List.of("totp", "recovery_code"),
                    "expiresIn", AdminMfaChallengeService.TTL.toSeconds()));
        }
        // No authenticator yet: enrollment is part of signing in, not optional.
        TotpMfaService.EnrollResponse enrollment = totp.enrollRequired(user, "Admin console");
        String mfaToken = challenges.issue(auth.getName(), user.getId(), "enroll");
        return ResponseEntity.ok(Map.of(
                "mfaEnrollmentRequired", true,
                "mfaToken", mfaToken,
                "factorId", enrollment.factorId().toString(),
                "secret", enrollment.secret(),
                "otpauthUri", enrollment.otpauthUri(),
                "qrCodeDataUrl", enrollment.qrCodeDataUrl(),
                "expiresIn", AdminMfaChallengeService.TTL.toSeconds()));
    }

    /** Step 2 for an operator with an authenticator: a current code or one unused recovery code. */
    @PostMapping("/mfa/verify")
    public ResponseEntity<?> verifyMfa(@RequestBody MfaVerifyRequest body) {
        var challenge = challenges.peek(body.mfaToken()).filter(c -> "verify".equals(c.stage()));
        if (challenge.isEmpty()) {
            return problem(HttpStatus.UNAUTHORIZED, "Sign-in expired. Enter your password again.");
        }
        var c = challenge.get();
        boolean ok;
        String method;
        if (body.recoveryCode() != null && !body.recoveryCode().isBlank()) {
            ok = recoveryCodes.verifyAndConsume(c.userId(), body.recoveryCode().trim());
            method = "recovery_code";
        } else if (body.code() != null && !body.code().isBlank()) {
            ok = totp.verifyForUser(c.userId(), body.code());
            method = "totp";
        } else {
            return problem(HttpStatus.BAD_REQUEST, "Enter the code from your authenticator app or a recovery code.");
        }
        return completeSecondFactor(body.mfaToken(), c, ok, method, null);
    }

    /** Step 2 for an operator without an authenticator: confirm the one shown at sign-in. */
    @PostMapping("/mfa/enroll/confirm")
    public ResponseEntity<?> confirmEnrollment(@RequestBody MfaEnrollConfirmRequest body) {
        var challenge = challenges.peek(body.mfaToken()).filter(c -> "enroll".equals(c.stage()));
        if (challenge.isEmpty()) {
            return problem(HttpStatus.UNAUTHORIZED, "Sign-in expired. Enter your password again.");
        }
        if (body.factorId() == null || body.code() == null || body.code().isBlank()) {
            return problem(HttpStatus.BAD_REQUEST, "Enter the 6-digit code from your authenticator app.");
        }
        var c = challenge.get();
        UserAccount user = RlsScope.asPlatform(() -> users.findById(c.userId())).orElse(null);
        if (user == null) {
            challenges.consume(body.mfaToken());
            return problem(HttpStatus.UNAUTHORIZED, "Sign-in expired. Enter your password again.");
        }
        List<String> codes = null;
        boolean ok;
        try {
            codes = totp.confirm(user, java.util.UUID.fromString(body.factorId()), body.code()).recoveryCodes();
            ok = true;
        } catch (IllegalArgumentException ex) {
            ok = false;
        }
        return completeSecondFactor(body.mfaToken(), c, ok, "totp_enrolled", codes);
    }

    private ResponseEntity<?> completeSecondFactor(
            String mfaToken, AdminMfaChallengeService.Challenge c, boolean ok, String method, List<String> codes) {
        if (!ok) {
            int left = challenges.recordFailure(mfaToken);
            // Wrong second factors count toward the same lockout as wrong passwords.
            RlsScope.runAsPlatform(() -> lockout.recordFailedAttempt(c.userId()));
            java.util.UUID tenantId = RlsScope.asPlatform(() -> users.findById(c.userId()))
                    .map(UserAccount::getTenantId)
                    .orElse(null);
            audit.record(tenantId, c.principal(), "admin.console_login.mfa_failed", "user_account", c.userId(), method, false);
            String detail = left > 0
                    ? "That code is not valid. " + left + (left == 1 ? " attempt" : " attempts") + " left."
                    : "Too many wrong codes. Enter your password again.";
            return problem(HttpStatus.UNAUTHORIZED, detail);
        }
        challenges.consume(mfaToken);
        RlsScope.runAsPlatform(() -> lockout.recordSuccess(c.userId()));
        // Re-check the account now: it may have been disabled since the password step.
        Optional<Authentication> auth = principals.restore(c.principal());
        UserAccount user = RlsScope.asPlatform(() -> users.findById(c.userId())).orElse(null);
        if (auth.isEmpty() || user == null) {
            return problem(HttpStatus.UNAUTHORIZED, "This account can no longer sign in.");
        }
        Map<String, Object> response = issueSession(auth.get(), user, method);
        if (codes != null) {
            response.put("recoveryCodes", codes);
        }
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> issueSession(Authentication auth, UserAccount user, String method) {
        RlsScope.runAsPlatform(() -> {
            user.setLastLoginAt(Instant.now());
            users.save(user);
        });
        audit.record(user.getTenantId(), auth.getName(), "admin.console_login", "user_account", user.getId(), method, true);
        AdminSessionService.IssuedSession session = sessions.create(auth, false);
        return sessionResponse(auth, session, false);
    }

    private Map<String, Object> issueBreakGlassSession(Authentication auth) {
        log.warn("Break-glass platform account '{}' signed in to the admin console.", auth.getName());
        audit.record(null, auth.getName(), "admin.break_glass_login", "platform", null, auth.getName(), true);
        try {
            mail.sendAdminSecurityAlert(
                    null,
                    "Break-glass sign-in",
                    "The emergency platform account '" + auth.getName() + "' signed in to the admin console at "
                            + Instant.now() + ". If this was not planned, rotate SECUREONE_DEV_PASSWORD now.");
        } catch (RuntimeException ex) {
            log.warn("Could not send break-glass alert email: {}", ex.getMessage());
        }
        return sessionResponse(auth, sessions.create(auth, true), true);
    }

    private Map<String, Object> sessionResponse(
            Authentication auth, AdminSessionService.IssuedSession session, boolean breakGlass) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("principal", auth.getName());
        body.put("platformSuperAdmin", access.canAccessPlatformSettings(auth, null));
        body.put("breakGlass", breakGlass);
        body.put("sessionToken", session.token());
        body.put("expiresIn", session.expiresIn());
        body.put("idleTimeout", session.idleTimeout());
        return body;
    }

    private static ResponseEntity<Map<String, Object>> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(Map.of("detail", detail));
    }

    private static String resolveUsername(String tenantSlug, String emailOrUsername) {
        String value = emailOrUsername.trim();
        if (tenantSlug == null || tenantSlug.isBlank()) {
            return value;
        }
        return tenantSlug.trim().toLowerCase() + ":" + value.toLowerCase();
    }
}
