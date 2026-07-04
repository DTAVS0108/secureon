package com.secureone.auth.account;

import com.secureone.auth.account.otp.OtpChallenge;
import com.secureone.auth.account.otp.OtpChallengeService;
import com.secureone.auth.account.otp.OtpChannel;
import com.secureone.auth.account.otp.OtpPurpose;
import com.secureone.auth.notify.EmailNotificationService;
import com.secureone.auth.notify.SmsNotificationService;
import com.secureone.auth.platform.VerificationPolicyService;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orchestrates account verification via link or OTP (email / phone). */
@Service
@Transactional
public class AccountVerificationService {

    private final UserAccountRepository users;
    private final AccountNotificationService notifications;
    private final OtpChallengeService otpChallenges;
    private final EmailNotificationService email;
    private final SmsNotificationService sms;
    private final VerificationPolicyService verificationPolicy;

    public AccountVerificationService(
            UserAccountRepository users,
            AccountNotificationService notifications,
            OtpChallengeService otpChallenges,
            EmailNotificationService email,
            SmsNotificationService sms,
            VerificationPolicyService verificationPolicy) {
        this.users = users;
        this.notifications = notifications;
        this.otpChallenges = otpChallenges;
        this.email = email;
        this.sms = sms;
        this.verificationPolicy = verificationPolicy;
    }

    public Map<String, Object> sendSignupVerification(
            UUID applicationId, UserAccount user, String initiatedBy, String requestedMode, String requestedChannel) {
        String mode = resolveSignupMode(applicationId, requestedMode);
        if ("otp".equals(mode)) {
            OtpChannel channel = resolveChannel(applicationId, requestedChannel, user);
            OtpPurpose purpose = channel == OtpChannel.SMS ? OtpPurpose.VERIFY_PHONE : OtpPurpose.VERIFY_EMAIL;
            return sendOtpVerification(applicationId, user, channel, purpose, initiatedBy);
        }
        notifications.sendVerificationEmail(applicationId, user, initiatedBy);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", "link");
        out.put("verificationSent", true);
        return out;
    }

    public Map<String, Object> sendOtpVerification(
            UUID applicationId, UserAccount user, OtpChannel channel, OtpPurpose purpose, String initiatedBy) {
        String destination = channel == OtpChannel.SMS ? user.getPhone() : user.getEmail();
        if (destination == null || destination.isBlank()) {
            throw new IllegalStateException("No destination available for " + channel.name() + " OTP.");
        }
        var issued = otpChallenges.issue(
                user.getId(),
                user.getTenantId(),
                applicationId,
                purpose,
                channel,
                destination,
                null,
                null);
        deliverOtp(applicationId, user, channel, purpose, issued.code());
        if ("admin".equals(initiatedBy)) {
            notifications.sendAdminVerificationAlert(applicationId, user, channel.name());
        }
        Map<String, Object> response = otpChallenges.challengeResponse(issued);
        response.put("channel", channel.name().toLowerCase());
        response.put("verificationSent", true);
        return response;
    }

    public UserAccount confirmEmailOtp(UUID challengeId, String code) {
        OtpChallenge challenge = otpChallenges.verify(challengeId, code);
        if (!OtpPurpose.VERIFY_EMAIL.name().equals(challenge.getPurpose())
                && !OtpPurpose.LOGIN.name().equals(challenge.getPurpose())) {
            throw new IllegalArgumentException("Invalid challenge purpose.");
        }
        UserAccount user = users.findById(challenge.getUserId()).orElseThrow();
        if (challenge.getChannel().equals(OtpChannel.EMAIL.name())) {
            user.setEmailVerified(true);
        }
        if ("PENDING".equalsIgnoreCase(user.getStatus())) {
            user.setStatus("ACTIVE");
        }
        users.save(user);
        notifications.onEmailVerified(user, challenge.getApplicationId());
        return user;
    }

    public UserAccount confirmPhoneOtp(UUID challengeId, String code) {
        OtpChallenge challenge = otpChallenges.verify(challengeId, code);
        if (!OtpPurpose.VERIFY_PHONE.name().equals(challenge.getPurpose())
                && !OtpPurpose.LOGIN.name().equals(challenge.getPurpose())) {
            throw new IllegalArgumentException("Invalid challenge purpose.");
        }
        UserAccount user = users.findById(challenge.getUserId()).orElseThrow();
        if (challenge.getChannel().equals(OtpChannel.SMS.name())) {
            user.setPhoneVerified(true);
        }
        if ("PENDING".equalsIgnoreCase(user.getStatus())) {
            user.setStatus("ACTIVE");
        }
        users.save(user);
        return user;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> verificationOptions(UUID applicationId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("signup", signupOptions(applicationId));
        out.put("login", loginOptions(applicationId));
        out.put("recovery", recoveryOptions(applicationId));
        return out;
    }

    private Map<String, Object> signupOptions(UUID applicationId) {
        Map<String, Object> signup = verificationPolicy.signupPolicy(applicationId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("defaultMode", signup.getOrDefault("defaultMode", "link"));
        out.put("allowedModes", signup.getOrDefault("allowedModes", java.util.List.of("link", "otp")));
        out.put("requiredChannels", signup.getOrDefault("requiredChannels", java.util.List.of("email")));
        out.put("optionalChannels", signup.getOrDefault("optionalChannels", java.util.List.of("phone")));
        out.put("allowUserChannelChoice", signup.getOrDefault("allowUserChannelChoice", false));
        out.put("otpLength", verificationPolicy.otpLength(applicationId, com.secureone.auth.account.otp.OtpPolicySection.SIGNUP));
        out.put("otpTtlSeconds", verificationPolicy.otpTtlSeconds(applicationId, com.secureone.auth.account.otp.OtpPolicySection.SIGNUP));
        out.put("maxAttempts", verificationPolicy.maxAttempts(applicationId, com.secureone.auth.account.otp.OtpPolicySection.SIGNUP));
        out.put("resendCooldownSeconds", verificationPolicy.resendCooldownSeconds(applicationId, com.secureone.auth.account.otp.OtpPolicySection.SIGNUP));
        return out;
    }

    private Map<String, Object> loginOptions(UUID applicationId) {
        Map<String, Object> login = verificationPolicy.loginPolicy(applicationId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("identifierTypes", login.getOrDefault("identifierTypes", java.util.List.of("email", "phone")));
        out.put("passwordlessModes", login.getOrDefault("passwordlessModes", java.util.List.of("email_otp", "phone_otp", "magic_link")));
        out.put("mfaRequired", login.getOrDefault("mfaRequired", "optional"));
        out.put("otpLength", verificationPolicy.otpLength(applicationId, com.secureone.auth.account.otp.OtpPolicySection.LOGIN));
        out.put("otpTtlSeconds", verificationPolicy.otpTtlSeconds(applicationId, com.secureone.auth.account.otp.OtpPolicySection.LOGIN));
        return out;
    }

    private Map<String, Object> recoveryOptions(UUID applicationId) {
        Map<String, Object> recovery = verificationPolicy.recoveryPolicy(applicationId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("passwordResetMode", recovery.getOrDefault("passwordResetMode", "link"));
        out.put("allowedModes", recovery.getOrDefault("allowedModes", java.util.List.of("link", "otp")));
        out.put("allowedChannels", recovery.getOrDefault("allowedChannels", java.util.List.of("email")));
        out.put("allowUserChannelChoice", recovery.getOrDefault("allowUserChannelChoice", true));
        out.put("requireVerifiedChannel", recovery.getOrDefault("requireVerifiedChannel", true));
        out.put("fallbackToLink", recovery.getOrDefault("fallbackToLink", true));
        out.put("otpLength", verificationPolicy.otpLength(applicationId, com.secureone.auth.account.otp.OtpPolicySection.RECOVERY));
        out.put("otpTtlSeconds", verificationPolicy.otpTtlSeconds(applicationId, com.secureone.auth.account.otp.OtpPolicySection.RECOVERY));
        return out;
    }

    private void deliverOtp(UUID applicationId, UserAccount user, OtpChannel channel, OtpPurpose purpose, String code) {
        if (channel == OtpChannel.SMS) {
            sms.sendOtp(applicationId, user.getPhone(), code, purposeLabel(purpose));
            return;
        }
        email.sendVerifyEmailOtp(
                applicationId,
                user,
                code,
                verificationPolicy.otpTtlSeconds(applicationId, com.secureone.auth.account.otp.OtpPolicySection.SIGNUP));
    }

    private String resolveSignupMode(UUID applicationId, String requestedMode) {
        if (requestedMode != null && !requestedMode.isBlank()) {
            if (!verificationPolicy.allowsSignupMode(applicationId, requestedMode)) {
                throw new IllegalArgumentException("Verification mode not allowed: " + requestedMode);
            }
            return requestedMode;
        }
        return verificationPolicy.signupDefaultMode(applicationId);
    }

    @SuppressWarnings("unchecked")
    private OtpChannel resolveChannel(UUID applicationId, String requestedChannel, UserAccount user) {
        if (requestedChannel != null && !requestedChannel.isBlank()) {
            return switch (requestedChannel.toLowerCase()) {
                case "phone", "sms" -> OtpChannel.SMS;
                default -> OtpChannel.EMAIL;
            };
        }
        List<String> required = (List<String>) verificationPolicy.signupPolicy(applicationId).get("requiredChannels");
        if (required != null && required.contains("phone") && user.getPhone() != null) {
            return OtpChannel.SMS;
        }
        return OtpChannel.EMAIL;
    }

    private static String purposeLabel(OtpPurpose purpose) {
        return switch (purpose) {
            case VERIFY_EMAIL, VERIFY_PHONE -> "verification";
            case LOGIN -> "sign-in";
            case RESET_PASSWORD -> "password reset";
            case MFA -> "security";
        };
    }
}
