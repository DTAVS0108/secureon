package com.secureone.auth.authn;

import com.secureone.auth.account.AccountVerificationService;
import com.secureone.auth.account.otp.OtpChallengeService;
import com.secureone.auth.account.otp.OtpChannel;
import com.secureone.auth.account.otp.OtpPurpose;
import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.application.ApplicationEffectiveSettingsService;
import com.secureone.auth.application.ApplicationTenantResolver;
import com.secureone.auth.mfa.MfaLoginService;
import com.secureone.auth.notify.EmailNotificationService;
import com.secureone.auth.notify.SmsNotificationService;
import com.secureone.auth.platform.VerificationPolicyService;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AuthOtpService {

    private final ApplicationTenantResolver tenants;
    private final IdentifierResolver identifiers;
    private final OtpChallengeService otpChallenges;
    private final ApplicationEffectiveSettingsService effectiveSettings;
    private final VerificationPolicyService verificationPolicy;
    private final EmailNotificationService email;
    private final SmsNotificationService sms;
    private final TenantUserDetailsService userDetailsService;
    private final UserAccountRepository users;
    private final MfaLoginService mfaLogin;

    public AuthOtpService(
            ApplicationTenantResolver tenants,
            IdentifierResolver identifiers,
            OtpChallengeService otpChallenges,
            ApplicationEffectiveSettingsService effectiveSettings,
            VerificationPolicyService verificationPolicy,
            EmailNotificationService email,
            SmsNotificationService sms,
            TenantUserDetailsService userDetailsService,
            UserAccountRepository users,
            MfaLoginService mfaLogin) {
        this.mfaLogin = mfaLogin;
        this.tenants = tenants;
        this.identifiers = identifiers;
        this.otpChallenges = otpChallenges;
        this.effectiveSettings = effectiveSettings;
        this.verificationPolicy = verificationPolicy;
        this.email = email;
        this.sms = sms;
        this.userDetailsService = userDetailsService;
        this.users = users;
    }

    public Map<String, Object> requestLoginOtp(UUID applicationId, String identifier, String channelHint) {
        tenants.requireActiveApplication(applicationId);
        if (!verificationPolicy.allowsLoginIdentifier(applicationId, identifier)) {
            throw new IllegalArgumentException("Identifier type not allowed for login.");
        }
        UUID tenantId = tenants.requireTenantId(applicationId);
        OtpChannel channel = resolveLoginChannel(channelHint, identifier);
        // Checked before the lookup: a disabled method must answer the same for every identifier.
        ensureLoginOtpAllowed(applicationId, channel);
        // Every request gets a challenge id. For an unknown identifier (or one without a phone for SMS)
        // it is a decoy that no code will ever match, so the response does not reveal whether the
        // account exists, and the client always has an id to send to verify.
        UUID challengeId = tenants.findUserByIdentifier(applicationId, identifier)
                .filter(AuthOtpService::isActiveOrPending)
                .filter(user -> channel != OtpChannel.SMS || (user.getPhone() != null && !user.getPhone().isBlank()))
                .map(user -> {
                    var issued = otpChallenges.issue(
                            user.getId(),
                            tenantId,
                            applicationId,
                            OtpPurpose.LOGIN,
                            channel,
                            channel == OtpChannel.SMS ? user.getPhone() : user.getEmail(),
                            null,
                            null);
                    deliverLoginOtp(applicationId, user, channel, issued.code());
                    return issued.challengeId();
                })
                .orElseGet(UUID::randomUUID);
        Map<String, Object> generic = new LinkedHashMap<>();
        generic.put("message", "If an account exists, a sign-in code has been sent.");
        generic.put("challengeId", challengeId.toString());
        generic.put("channel", channel == OtpChannel.SMS ? "sms" : "email");
        return generic;
    }

    // Own transactions per step (see ApplicationPublicAccountService.sessionLogin).
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ResponseEntity<?> verifyLoginOtp(
            UUID applicationId,
            UUID challengeId,
            String code,
            HttpServletRequest request,
            HttpServletResponse response) {
        tenants.requireActiveApplication(applicationId);
        var challenge = otpChallenges.verify(challengeId, code);
        if (!OtpPurpose.LOGIN.name().equals(challenge.getPurpose())
                || !applicationId.equals(challenge.getApplicationId())) {
            throw new IllegalArgumentException("Invalid challenge.");
        }
        UserAccount user = tenants.findUserById(challenge.getUserId()).orElseThrow();
        if (!isActiveOrPending(user)) {
            throw new ResourceNotFoundException("Invalid or expired code.");
        }
        if (OtpChannel.EMAIL.name().equals(challenge.getChannel())) {
            user.setEmailVerified(true);
        } else if (OtpChannel.SMS.name().equals(challenge.getChannel())) {
            user.setPhoneVerified(true);
        }
        user.setLastLoginAt(java.time.Instant.now());
        users.save(user);
        String tenantSlug = tenants.requireTenantSlug(applicationId);
        UserDetails details = userDetailsService.buildSessionPrincipal(tenantSlug, user);
        Authentication authentication = SignedInAuthentication.of(details, FactorGrantedAuthority.OTT_AUTHORITY);
        // Same completion as a password sign-in: the app's MFA requirement and step-up apply, so a
        // passwordless code is not a way around them. 204 with a session, or 428 with an MFA challenge.
        try {
            return mfaLogin.completePasswordLogin(authentication, applicationId, request, response);
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage()));
        }
    }

    private static boolean isActiveOrPending(UserAccount user) {
        return "ACTIVE".equalsIgnoreCase(user.getStatus()) || "PENDING".equalsIgnoreCase(user.getStatus());
    }

    private void deliverLoginOtp(UUID applicationId, UserAccount user, OtpChannel channel, String code) {
        int ttl = verificationPolicy.otpTtlSeconds(applicationId, com.secureone.auth.account.otp.OtpPolicySection.LOGIN);
        if (channel == OtpChannel.SMS) {
            sms.sendOtp(applicationId, user.getPhone(), code, "sign-in");
        } else {
            email.sendLoginOtp(applicationId, user, code, ttl);
        }
    }

    private void ensureLoginOtpAllowed(UUID applicationId, OtpChannel channel) {
        // Verification & OTP › Passwordless modes is the one switch; text messages also need an SMS provider.
        if (channel == OtpChannel.SMS) {
            if (!verificationPolicy.allowsLoginPasswordlessMode(applicationId, "phone_otp")) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SMS OTP login is not allowed for this application.");
            }
            if (!effectiveSettings.isAuthMethodEnabled(applicationId, "m_sms")) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SMS OTP login needs an SMS provider.");
            }
            return;
        }
        if (!verificationPolicy.allowsLoginPasswordlessMode(applicationId, "email_otp")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Email OTP login is not allowed for this application.");
        }
    }

    private static OtpChannel resolveLoginChannel(String channelHint, String identifier) {
        if (channelHint != null && !channelHint.isBlank()) {
            return switch (channelHint.toLowerCase()) {
                case "phone", "sms" -> OtpChannel.SMS;
                default -> OtpChannel.EMAIL;
            };
        }
        return identifier != null && identifier.contains("@") ? OtpChannel.EMAIL : OtpChannel.SMS;
    }
}
