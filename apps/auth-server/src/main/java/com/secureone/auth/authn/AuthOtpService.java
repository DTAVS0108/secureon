package com.secureone.auth.authn;

import com.secureone.auth.account.AccountVerificationService;
import com.secureone.auth.account.otp.OtpChallengeService;
import com.secureone.auth.account.otp.OtpChannel;
import com.secureone.auth.account.otp.OtpPurpose;
import com.secureone.auth.application.ApplicationEffectiveSettingsService;
import com.secureone.auth.application.ApplicationTenantResolver;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final SecurityContextRepository securityContextRepository;
    private final LoginSuccessHandler loginSuccessHandler;
    private final UserAccountRepository users;

    public AuthOtpService(
            ApplicationTenantResolver tenants,
            IdentifierResolver identifiers,
            OtpChallengeService otpChallenges,
            ApplicationEffectiveSettingsService effectiveSettings,
            VerificationPolicyService verificationPolicy,
            EmailNotificationService email,
            SmsNotificationService sms,
            TenantUserDetailsService userDetailsService,
            SecurityContextRepository securityContextRepository,
            LoginSuccessHandler loginSuccessHandler,
            UserAccountRepository users) {
        this.tenants = tenants;
        this.identifiers = identifiers;
        this.otpChallenges = otpChallenges;
        this.effectiveSettings = effectiveSettings;
        this.verificationPolicy = verificationPolicy;
        this.email = email;
        this.sms = sms;
        this.userDetailsService = userDetailsService;
        this.securityContextRepository = securityContextRepository;
        this.loginSuccessHandler = loginSuccessHandler;
        this.users = users;
    }

    public Map<String, Object> requestLoginOtp(UUID applicationId, String identifier, String channelHint) {
        tenants.requireActiveApplication(applicationId);
        if (!verificationPolicy.allowsLoginIdentifier(applicationId, identifier)) {
            throw new IllegalArgumentException("Identifier type not allowed for login.");
        }
        UUID tenantId = tenants.requireTenantId(applicationId);
        tenants.findUserByIdentifier(applicationId, identifier).ifPresent(user -> {
            OtpChannel channel = resolveLoginChannel(channelHint, identifier);
            ensureLoginOtpAllowed(applicationId, channel);
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
        });
        Map<String, Object> generic = new LinkedHashMap<>();
        generic.put("message", "If an account exists, a sign-in code has been sent.");
        return generic;
    }

    public ResponseEntity<?> verifyLoginOtp(
            UUID applicationId,
            UUID challengeId,
            String code,
            HttpServletRequest request,
            HttpServletResponse response) {
        tenants.requireActiveApplication(applicationId);
        var challenge = otpChallenges.verify(challengeId, code);
        if (!OtpPurpose.LOGIN.name().equals(challenge.getPurpose())) {
            throw new IllegalArgumentException("Invalid challenge.");
        }
        UserAccount user = tenants.findUserById(challenge.getUserId()).orElseThrow();
        if (OtpChannel.EMAIL.name().equals(challenge.getChannel())) {
            user.setEmailVerified(true);
        } else if (OtpChannel.SMS.name().equals(challenge.getChannel())) {
            user.setPhoneVerified(true);
        }
        user.setLastLoginAt(java.time.Instant.now());
        users.save(user);
        String tenantSlug = tenants.requireTenantSlug(applicationId);
        UserDetails details = userDetailsService.buildSessionPrincipal(tenantSlug, user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        user.setLastLoginAt(java.time.Instant.now());
        loginSuccessHandler.recordSuccessfulLogin(request, authentication);
        return ResponseEntity.noContent().build();
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
        if (channel == OtpChannel.SMS) {
            if (!verificationPolicy.allowsLoginPasswordlessMode(applicationId, "phone_otp")) {
                throw new IllegalStateException("SMS OTP login is not allowed by verification policy.");
            }
            if (!effectiveSettings.isAuthMethodEnabled(applicationId, "m_sms")) {
                throw new IllegalStateException("SMS OTP login is disabled.");
            }
            return;
        }
        if (!verificationPolicy.allowsLoginPasswordlessMode(applicationId, "email_otp")) {
            throw new IllegalStateException("Email OTP login is not allowed by verification policy.");
        }
        if (!effectiveSettings.isAuthMethodEnabled(applicationId, "m_email_otp")) {
            throw new IllegalStateException("Email OTP login is disabled.");
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
