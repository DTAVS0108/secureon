package com.secureone.auth.mfa;

import com.secureone.auth.authn.AuthContextAttributes;
import com.secureone.auth.authn.SignedInAuthentication;
import com.secureone.auth.trusteddevice.TrustedDeviceService;
import com.secureone.auth.authn.LoginSuccessHandler;
import com.secureone.auth.authn.TenantUserDetailsService;
import com.secureone.auth.user.UserAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class MfaLoginService {

    public record MfaChallengeResponse(UUID mfaChallengeId, List<String> methods, int expiresInSeconds) {}

    private final MfaPolicyService policy;
    private final MfaChallengeStore challenges;
    private final TotpMfaService totp;
    private final MfaRecoveryCodeService recoveryCodes;
    private final TenantUserDetailsService userDetailsService;
    private final SecurityContextRepository securityContextRepository;
    private final LoginSuccessHandler loginSuccessHandler;
    private final TrustedDeviceService trustedDevices;

    public MfaLoginService(
            MfaPolicyService policy,
            MfaChallengeStore challenges,
            TotpMfaService totp,
            MfaRecoveryCodeService recoveryCodes,
            TenantUserDetailsService userDetailsService,
            SecurityContextRepository securityContextRepository,
            @Lazy LoginSuccessHandler loginSuccessHandler,
            TrustedDeviceService trustedDevices) {
        this.policy = policy;
        this.challenges = challenges;
        this.totp = totp;
        this.recoveryCodes = recoveryCodes;
        this.userDetailsService = userDetailsService;
        this.securityContextRepository = securityContextRepository;
        this.loginSuccessHandler = loginSuccessHandler;
        this.trustedDevices = trustedDevices;
    }

    public ResponseEntity<?> completePasswordLogin(
            Authentication authentication,
            UUID applicationId,
            HttpServletRequest request,
            HttpServletResponse response) {
        UserAccount user = resolveUser(authentication);
        policy.assertMfaEnrolledIfRequired(user, applicationId);
        if (policy.requiresMfaStepUp(user, applicationId, request)) {
            String tenantSlug = tenantSlugFromPrincipal(authentication.getName());
            MfaChallengeResponse challenge = issueChallenge(user, tenantSlug, applicationId);
            clearSession(request, response);
            return mfaRequiredResponse(challenge);
        }
        establishSession(authentication, request, response);
        loginSuccessHandler.recordSuccessfulLogin(request, authentication);
        return ResponseEntity.noContent().build();
    }

    public boolean shouldChallengeFormLogin(Authentication authentication, UUID applicationId) {
        return shouldChallengeFormLogin(authentication, applicationId, null);
    }

    public boolean shouldChallengeFormLogin(
            Authentication authentication, UUID applicationId, HttpServletRequest request) {
        // applicationId may be null: platform MFA policy applies then.
        UserAccount user = resolveUser(authentication);
        return policy.requiresMfaStepUp(user, applicationId, request);
    }

    /** MFA is required for this app but the user has no authenticator: the sign-in must not complete. */
    public boolean enrollmentRequiredButMissing(Authentication authentication, UUID applicationId) {
        UserAccount user = resolveUser(authentication);
        try {
            policy.assertMfaEnrolledIfRequired(user, applicationId);
            return false;
        } catch (IllegalStateException ex) {
            return true;
        }
    }

    public MfaChallengeResponse issueChallenge(UserAccount user, String tenantSlug, UUID applicationId) {
        UUID challengeId = UUID.randomUUID();
        MfaPendingChallenge pending = new MfaPendingChallenge(
                challengeId, user.getId(), tenantSlug, user.getEmail(), applicationId);
        int ttl = policy.challengeTtlSeconds(applicationId);
        challenges.save(pending, Duration.ofSeconds(ttl));
        return new MfaChallengeResponse(challengeId, List.of("totp", "recovery"), ttl);
    }

    public void clearSession(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        SecurityContext empty = SecurityContextHolder.createEmptyContext();
        securityContextRepository.saveContext(empty, request, response);
    }

    public ResponseEntity<?> verifyTotp(
            UUID challengeId, String code, HttpServletRequest request, HttpServletResponse response) {
        return verifyChallenge(challengeId, request, response, pending -> {
            if (!totp.verifyForUser(pending.userId(), code)) {
                throw new IllegalArgumentException("Invalid authenticator code.");
            }
        });
    }

    public ResponseEntity<?> verifyRecovery(
            UUID challengeId, String code, HttpServletRequest request, HttpServletResponse response) {
        return verifyChallenge(challengeId, request, response, pending -> {
            if (!recoveryCodes.verifyAndConsume(pending.userId(), code)) {
                throw new IllegalArgumentException("Invalid or already used recovery code.");
            }
        });
    }

    private ResponseEntity<?> verifyChallenge(
            UUID challengeId,
            HttpServletRequest request,
            HttpServletResponse response,
            java.util.function.Consumer<MfaPendingChallenge> verifier) {
        MfaPendingChallenge pending = challenges.get(challengeId).orElse(null);
        if (pending == null) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.GONE, "MFA challenge expired or invalid."));
        }
        // The application signed in to decides (its MFA policy override, else platform defaults).
        int attempts = challenges.incrementAttempts(
                challengeId, Duration.ofSeconds(policy.challengeTtlSeconds(pending.applicationId())));
        if (attempts > policy.maxChallengeAttempts(pending.applicationId())) {
            challenges.delete(challengeId);
            challenges.clearAttempts(challengeId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Too many MFA attempts."));
        }
        try {
            verifier.accept(pending);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage()));
        }
        challenges.delete(challengeId);
        challenges.clearAttempts(challengeId);
        Authentication authentication = rebuildAuthentication(pending);
        establishSession(authentication, request, response);
        AuthContextAttributes.recordMfaAuth(request, "otp");
        int rememberDays = policy.rememberDeviceDays(pending.applicationId());
        if (rememberDays > 0) {
            trustedDevices.issue(pending.userId(), rememberDays, request, response);
        }
        loginSuccessHandler.recordSuccessfulLogin(request, authentication);
        return ResponseEntity.noContent().build();
    }

    private Authentication rebuildAuthentication(MfaPendingChallenge pending) {
        String username = pending.tenantSlug() + ":" + pending.email();
        UserDetails user = userDetailsService.loadUserByUsername(username);
        return SignedInAuthentication.of(
                user, FactorGrantedAuthority.PASSWORD_AUTHORITY, SignedInAuthentication.MFA_FACTOR);
    }

    private void establishSession(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private ResponseEntity<Map<String, Object>> mfaRequiredResponse(MfaChallengeResponse challenge) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mfaChallengeId", challenge.mfaChallengeId().toString());
        body.put("methods", challenge.methods());
        body.put("expiresInSeconds", challenge.expiresInSeconds());
        return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).body(body);
    }

    private UserAccount resolveUser(Authentication authentication) {
        return resolveUser(authentication.getName());
    }

    private UserAccount resolveUser(String principal) {
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
}
