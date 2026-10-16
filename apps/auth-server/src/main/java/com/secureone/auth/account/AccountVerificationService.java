package com.secureone.auth.account;

import com.secureone.auth.account.otp.OtpChallenge;
import com.secureone.auth.account.otp.OtpChallengeService;
import com.secureone.auth.account.otp.OtpChannel;
import com.secureone.auth.account.otp.OtpPolicySection;
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

    /**
     * What {@link #sendSignupVerification} would answer, without an account: same mode and channel
     * rules (and errors), same fields, a challenge id that no code matches. Used when a sign-up must
     * not reveal that the email or phone is already taken.
     */
    public Map<String, Object> decoySignupVerification(
            UUID applicationId, String requestedMode, String requestedChannel, String email, String phone) {
        String mode = resolveSignupMode(applicationId, requestedMode);
        Map<String, Object> out = new LinkedHashMap<>();
        if (!"otp".equals(mode)) {
            out.put("mode", "link");
            out.put("verificationSent", true);
            return out;
        }
        UserAccount probe = new UserAccount();
        probe.setEmail(email);
        probe.setPhone(phone);
        OtpChannel channel = resolveChannel(applicationId, requestedChannel, probe);
        String destination = channel == OtpChannel.SMS ? phone : email;
        if (destination == null || destination.isBlank()) {
            throw new IllegalStateException("No destination available for " + channel.name() + " OTP.");
        }
        out.put("challengeId", UUID.randomUUID().toString());
        out.put("maskedDestination", OtpChallengeService.maskDestination(channel, destination));
        out.put("expiresIn", verificationPolicy.otpTtlSeconds(applicationId, OtpPolicySection.SIGNUP));
        out.put("mode", "otp");
        out.put("channel", channel.name().toLowerCase());
        out.put("verificationSent", true);
        return out;
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
        return confirmEmailOtp(challengeId, code, null);
    }

    /** {@code expectedApplicationId}: the app whose endpoint is confirming; the code must be from it. */
    public UserAccount confirmEmailOtp(UUID challengeId, String code, UUID expectedApplicationId) {
        OtpChallenge challenge = otpChallenges.verify(challengeId, code);
        requireApplication(challenge, expectedApplicationId);
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
        return confirmPhoneOtp(challengeId, code, null);
    }

    public UserAccount confirmPhoneOtp(UUID challengeId, String code, UUID expectedApplicationId) {
        OtpChallenge challenge = otpChallenges.verify(challengeId, code);
        requireApplication(challenge, expectedApplicationId);
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

    /** A code issued for one application must not verify (and grant membership) through another. */
    private static void requireApplication(OtpChallenge challenge, UUID expectedApplicationId) {
        if (expectedApplicationId != null && !expectedApplicationId.equals(challenge.getApplicationId())) {
            throw new IllegalArgumentException("Invalid challenge for this application.");
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> verificationOptions(UUID applicationId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("defaultPhoneRegion", verificationPolicy.defaultPhoneRegion(applicationId));
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
        List<String> required = (List<String>) verificationPolicy.signupPolicy(applicationId).get("requiredChannels");
        OtpChannel policyChannel =
                required != null && required.contains("phone") && user.getPhone() != null ? OtpChannel.SMS : OtpChannel.EMAIL;
        if (requestedChannel != null && !requestedChannel.isBlank()) {
            OtpChannel requested = switch (requestedChannel.toLowerCase()) {
                case "phone", "sms" -> OtpChannel.SMS;
                default -> OtpChannel.EMAIL;
            };
            // Without channel choice the policy's channel is the one verified; otherwise a user could
            // verify a phone on an app that requires email and then be unable to sign in.
            if (requested != policyChannel && !verificationPolicy.signupAllowUserChannelChoice(applicationId)) {
                throw new IllegalArgumentException("Choosing the verification channel is not allowed for this application.");
            }
            return requested;
        }
        return policyChannel;
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
