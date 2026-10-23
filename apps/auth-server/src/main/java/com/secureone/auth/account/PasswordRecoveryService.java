package com.secureone.auth.account;

import com.secureone.auth.authn.AccountLockoutService;
import com.secureone.auth.account.otp.OtpChallenge;
import com.secureone.auth.account.otp.OtpChallengeService;
import com.secureone.auth.account.otp.OtpChannel;
import com.secureone.auth.account.otp.OtpPolicySection;
import com.secureone.auth.account.otp.OtpPurpose;
import com.secureone.auth.application.ApplicationEffectiveSettingsService;
import com.secureone.auth.application.ApplicationSettingsService;
import com.secureone.auth.notify.SmsNotificationService;
import com.secureone.auth.application.ApplicationTenantResolver;
import com.secureone.auth.notify.EmailNotificationService;
import com.secureone.auth.notify.EmailTokenType;
import com.secureone.auth.platform.VerificationPolicyService;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Password recovery via email link or email/SMS OTP (policy-driven). */
@Service
@Transactional
public class PasswordRecoveryService {

    private static final String GENERIC_MESSAGE = "If an account exists, reset instructions have been sent.";

    private final ApplicationTenantResolver tenants;
    private final ApplicationEffectiveSettingsService effectiveSettings;
    private final VerificationPolicyService verificationPolicy;
    private final ApplicationSettingsService applicationSettings;
    private final EmailTokenService emailTokens;
    private final OtpChallengeService otpChallenges;
    private final EmailNotificationService email;
    private final SmsNotificationService sms;
    private final UserPasswordService passwords;
    private final UserAccountRepository users;
    private final String publicBaseUrl;

    public PasswordRecoveryService(
            ApplicationTenantResolver tenants,
            ApplicationEffectiveSettingsService effectiveSettings,
            VerificationPolicyService verificationPolicy,
            ApplicationSettingsService applicationSettings,
            EmailTokenService emailTokens,
            OtpChallengeService otpChallenges,
            EmailNotificationService email,
            SmsNotificationService sms,
            UserPasswordService passwords,
            UserAccountRepository users,
            @Value("${secureone.public-base-url:http://localhost:9000}") String publicBaseUrl) {
        this.tenants = tenants;
        this.effectiveSettings = effectiveSettings;
        this.verificationPolicy = verificationPolicy;
        this.applicationSettings = applicationSettings;
        this.emailTokens = emailTokens;
        this.otpChallenges = otpChallenges;
        this.email = email;
        this.sms = sms;
        this.passwords = passwords;
        this.users = users;
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    public Map<String, Object> requestRecovery(
            UUID applicationId, String identifier, String requestedMode, String requestedChannel) {
        if (!effectiveSettings.isFeatureEnabled(applicationId, "self_service_recovery")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Self-service recovery is disabled for this application.");
        }
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier or email is required.");
        }

        // Everything decided by policy comes before the account lookup, and every outcome below answers
        // in the same shape: whether an account exists (or has a verified phone) must not show.
        String mode = resolveRecoveryMode(applicationId, requestedMode);
        // Verification & OTP › Password recovery is the one place that allows codes (the "Recovery OTP" flag was
        // retired): a default of "otp" that the allowed modes no longer include falls back to the link when allowed.
        if ("otp".equals(mode) && !verificationPolicy.allowsRecoveryMode(applicationId, "otp")) {
            if (verificationPolicy.recoveryFallbackToLink(applicationId)) {
                mode = "link";
            } else {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Password reset via OTP is disabled for this application.");
            }
        }
        Optional<UserAccount> userOpt = tenants.findUserByIdentifier(applicationId, identifier.trim())
                .filter(u -> !"SUSPENDED".equalsIgnoreCase(u.getStatus()) && !"DISABLED".equalsIgnoreCase(u.getStatus()));

        if (!"otp".equals(mode)) {
            userOpt.ifPresent(user -> sendRecoveryLink(applicationId, user));
            return genericResponse("link", null);
        }

        OtpChannel channel = requestedOtpChannel(applicationId, requestedChannel, identifier);
        UUID challengeId = null;
        if (userOpt.isPresent()) {
            UserAccount user = userOpt.get();
            try {
                challengeId = sendRecoveryOtp(applicationId, user, channel);
            } catch (IllegalStateException ex) {
                // This account cannot get a code on that channel (unverified, no phone): fall back to the
                // link if policy allows, but answer exactly as if the code had been sent.
                if (verificationPolicy.recoveryFallbackToLink(applicationId)) {
                    sendRecoveryLink(applicationId, user);
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("challengeId", (challengeId != null ? challengeId : UUID.randomUUID()).toString());
        out.put("maskedDestination", maskTyped(channel, identifier.trim()));
        out.put("expiresIn", verificationPolicy.otpTtlSeconds(applicationId, OtpPolicySection.RECOVERY));
        out.put("mode", "otp");
        out.put("channel", channel == OtpChannel.SMS ? "sms" : "email");
        out.put("message", GENERIC_MESSAGE);
        return out;
    }

    /** Masks what the requester typed, never the account's stored address (which could link a phone to an email). */
    private static String maskTyped(OtpChannel channel, String identifier) {
        boolean typedEmail = identifier.contains("@");
        if ((channel == OtpChannel.EMAIL) == typedEmail) {
            return OtpChallengeService.maskDestination(channel, identifier);
        }
        return null;
    }

    public Map<String, String> completeRecoveryOtp(UUID applicationId, UUID challengeId, String code, String newPassword) {
        OtpChallenge challenge = otpChallenges.verify(challengeId, code);
        if (!OtpPurpose.RESET_PASSWORD.name().equals(challenge.getPurpose())) {
            throw new IllegalArgumentException("Invalid challenge purpose.");
        }
        if (applicationId != null
                && challenge.getApplicationId() != null
                && !applicationId.equals(challenge.getApplicationId())) {
            throw new IllegalArgumentException("Invalid challenge for this application.");
        }
        UserAccount user = users.findById(challenge.getUserId()).orElseThrow();
        UUID appId = applicationId != null ? applicationId : challenge.getApplicationId();
        passwords.setPassword(user.getId(), newPassword, appId);
        AccountLockoutService.clearOn(user); // a new password ends any lockout; this save must not restore it
        user.setStatus("ACTIVE");
        users.save(user);
        email.sendPasswordChanged(appId, user);
        email.sendAdminSecurityAlert(
                appId,
                user,
                "Password reset completed",
                "User " + user.getEmail() + " reset their password via OTP.");
        return Map.of("message", "Password updated. You can sign in with your new password.");
    }

    private void sendRecoveryLink(UUID applicationId, UserAccount user) {
        String raw = emailTokens.issue(user, EmailTokenType.RESET_PASSWORD).rawToken();
        String link = passwordResetLink(applicationId, raw);
        email.sendPasswordReset(applicationId, user, link);
        email.sendAdminSecurityAlert(
                applicationId,
                user,
                "Password reset requested",
                "User triggered password reset for " + user.getEmail() + ".");
    }

    /** Issues and delivers the code; throws IllegalStateException when this account cannot receive it. */
    private UUID sendRecoveryOtp(UUID applicationId, UserAccount user, OtpChannel channel) {
        if (!verificationPolicy.allowsRecoveryChannel(applicationId, channel == OtpChannel.SMS ? "phone" : "email")) {
            throw new IllegalStateException("Recovery channel not allowed.");
        }
        if (verificationPolicy.recoveryRequireVerifiedChannel(applicationId)) {
            if (channel == OtpChannel.SMS && !user.isPhoneVerified()) {
                throw new IllegalStateException("Phone is not verified for this account.");
            }
            if (channel == OtpChannel.EMAIL && !user.isEmailVerified()) {
                throw new IllegalStateException("Email is not verified for this account.");
            }
        }
        String destination = channel == OtpChannel.SMS ? user.getPhone() : user.getEmail();
        if (destination == null || destination.isBlank()) {
            throw new IllegalStateException("No destination available for recovery OTP.");
        }
        if (channel == OtpChannel.SMS && !sms.isConfigured(applicationId)) {
            throw new IllegalStateException("SMS is not configured for this application.");
        }

        UUID tenantId = tenants.requireTenantId(applicationId);
        var issued = otpChallenges.issue(
                user.getId(),
                tenantId,
                applicationId,
                OtpPurpose.RESET_PASSWORD,
                channel,
                destination,
                null,
                null,
                OtpPolicySection.RECOVERY);
        deliverRecoveryOtp(applicationId, user, channel, issued.code());
        return issued.challengeId();
    }

    private void deliverRecoveryOtp(UUID applicationId, UserAccount user, OtpChannel channel, String code) {
        int ttl = verificationPolicy.otpTtlSeconds(applicationId, OtpPolicySection.RECOVERY);
        if (channel == OtpChannel.SMS) {
            sms.sendOtp(applicationId, user.getPhone(), code, "password reset");
        } else {
            email.sendPasswordResetOtp(applicationId, user, code, ttl);
        }
    }

    private String resolveRecoveryMode(UUID applicationId, String requestedMode) {
        if (requestedMode != null && !requestedMode.isBlank()) {
            if (!verificationPolicy.allowsRecoveryMode(applicationId, requestedMode)) {
                throw new IllegalArgumentException("Recovery mode not allowed: " + requestedMode);
            }
            return requestedMode;
        }
        return verificationPolicy.recoveryDefaultMode(applicationId);
    }

    /** The channel follows the request (when choice is allowed) or what was typed, never the account. */
    private OtpChannel requestedOtpChannel(UUID applicationId, String requestedChannel, String identifier) {
        if (requestedChannel != null && !requestedChannel.isBlank()) {
            if (!verificationPolicy.recoveryAllowUserChannelChoice(applicationId)) {
                throw new IllegalArgumentException("Channel choice is not allowed for password recovery.");
            }
            return switch (requestedChannel.toLowerCase()) {
                case "phone", "sms" -> OtpChannel.SMS;
                default -> OtpChannel.EMAIL;
            };
        }
        if (identifier != null && identifier.contains("@")) {
            return OtpChannel.EMAIL;
        }
        return identifier != null && !identifier.contains("@") ? OtpChannel.SMS : OtpChannel.EMAIL;
    }

    private static Map<String, Object> genericResponse(String mode, String channel) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", GENERIC_MESSAGE);
        if (mode != null) {
            out.put("mode", mode);
        }
        if (channel != null) {
            out.put("channel", channel);
        }
        return out;
    }

    private String passwordResetLink(UUID applicationId, String rawToken) {
        String base = publicBaseUrl + "/account/reset-password.html";
        if (applicationId != null) {
            Object urls = applicationSettings.getClientIntegration(applicationId).get("clientUrls");
            if (urls instanceof Map<?, ?> map) {
                Object configured = map.get("passwordReset");
                if (configured != null && !configured.toString().isBlank()) {
                    base = configured.toString().trim();
                }
            }
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String sep = base.contains("?") ? "&" : "?";
        String link = base + sep + "token=" + rawToken;
        if (applicationId != null) {
            link += "&applicationId=" + applicationId;
        }
        return link;
    }
}
