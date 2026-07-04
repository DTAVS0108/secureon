package com.secureone.auth.platform;

import com.secureone.auth.account.otp.OtpPolicySection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class VerificationPolicyService {

    private final PlatformSettingsService platformSettings;
    private final com.secureone.auth.application.ApplicationSettingsService applicationSettings;

    public VerificationPolicyService(
            PlatformSettingsService platformSettings,
            com.secureone.auth.application.ApplicationSettingsService applicationSettings) {
        this.platformSettings = platformSettings;
        this.applicationSettings = applicationSettings;
    }

    public Map<String, Object> getPlatformPolicy() {
        Map<String, Object> stored = platformSettings.get("verification_policy");
        if (stored == null || stored.isEmpty()) {
            return VerificationPolicyDefaults.platformDefaults();
        }
        return VerificationPolicyDefaults.sanitizeForSave(stored);
    }

    public Map<String, Object> savePlatformPolicy(Map<String, Object> body) {
        return platformSettings.saveMap("verification_policy", VerificationPolicyDefaults.sanitizeForSave(body));
    }

    public Map<String, Object> resolvePolicy(UUID applicationId) {
        if (applicationId == null) {
            return getPlatformPolicy();
        }
        return applicationSettings.resolveVerificationPolicy(applicationId);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> signupPolicy(UUID applicationId) {
        return (Map<String, Object>) resolvePolicy(applicationId).get("signup");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> loginPolicy(UUID applicationId) {
        return (Map<String, Object>) resolvePolicy(applicationId).get("login");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> recoveryPolicy(UUID applicationId) {
        return (Map<String, Object>) resolvePolicy(applicationId).get("recovery");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sectionPolicy(UUID applicationId, OtpPolicySection section) {
        return switch (section) {
            case SIGNUP -> signupPolicy(applicationId);
            case LOGIN -> loginPolicy(applicationId);
            case RECOVERY -> recoveryPolicy(applicationId);
        };
    }

    public String signupDefaultMode(UUID applicationId) {
        return stringValue(signupPolicy(applicationId), "defaultMode", "link");
    }

    public String recoveryDefaultMode(UUID applicationId) {
        return stringValue(recoveryPolicy(applicationId), "passwordResetMode", "link");
    }

    @SuppressWarnings("unchecked")
    public List<String> signupAllowedModes(UUID applicationId) {
        return stringList(signupPolicy(applicationId).get("allowedModes"), List.of("link", "otp"));
    }

    @SuppressWarnings("unchecked")
    public List<String> recoveryAllowedModes(UUID applicationId) {
        return stringList(recoveryPolicy(applicationId).get("allowedModes"), List.of("link", "otp"));
    }

    @SuppressWarnings("unchecked")
    public List<String> recoveryAllowedChannels(UUID applicationId) {
        return stringList(recoveryPolicy(applicationId).get("allowedChannels"), List.of("email"));
    }

    @SuppressWarnings("unchecked")
    public List<String> loginIdentifierTypes(UUID applicationId) {
        return stringList(loginPolicy(applicationId).get("identifierTypes"), List.of("email", "phone"));
    }

    @SuppressWarnings("unchecked")
    public List<String> loginPasswordlessModes(UUID applicationId) {
        return stringList(loginPolicy(applicationId).get("passwordlessModes"), List.of("email_otp", "phone_otp", "magic_link"));
    }

    public boolean recoveryFallbackToLink(UUID applicationId) {
        return boolValue(recoveryPolicy(applicationId), "fallbackToLink", true);
    }

    public boolean recoveryRequireVerifiedChannel(UUID applicationId) {
        return boolValue(recoveryPolicy(applicationId), "requireVerifiedChannel", true);
    }

    public boolean recoveryAllowUserChannelChoice(UUID applicationId) {
        return boolValue(recoveryPolicy(applicationId), "allowUserChannelChoice", true);
    }

    public boolean signupAllowUserChannelChoice(UUID applicationId) {
        return boolValue(signupPolicy(applicationId), "allowUserChannelChoice", false);
    }

    public int otpLength(UUID applicationId) {
        return otpLength(applicationId, OtpPolicySection.SIGNUP);
    }

    public int otpLength(UUID applicationId, OtpPolicySection section) {
        return intValue(sectionPolicy(applicationId, section), "otpLength", 6);
    }

    public int otpTtlSeconds(UUID applicationId) {
        return otpTtlSeconds(applicationId, OtpPolicySection.SIGNUP);
    }

    public int otpTtlSeconds(UUID applicationId, OtpPolicySection section) {
        return intValue(sectionPolicy(applicationId, section), "otpTtlSeconds", 600);
    }

    public int maxAttempts(UUID applicationId) {
        return maxAttempts(applicationId, OtpPolicySection.SIGNUP);
    }

    public int maxAttempts(UUID applicationId, OtpPolicySection section) {
        return intValue(sectionPolicy(applicationId, section), "maxAttempts", 5);
    }

    public int resendCooldownSeconds(UUID applicationId) {
        return resendCooldownSeconds(applicationId, OtpPolicySection.SIGNUP);
    }

    public int resendCooldownSeconds(UUID applicationId, OtpPolicySection section) {
        return intValue(sectionPolicy(applicationId, section), "resendCooldownSeconds", 60);
    }

    public boolean allowsSignupMode(UUID applicationId, String mode) {
        return signupAllowedModes(applicationId).contains(mode);
    }

    public boolean allowsRecoveryMode(UUID applicationId, String mode) {
        return recoveryAllowedModes(applicationId).contains(mode);
    }

    public boolean allowsRecoveryChannel(UUID applicationId, String channel) {
        String normalized = normalizeChannel(channel);
        return recoveryAllowedChannels(applicationId).stream()
                .map(VerificationPolicyService::normalizeChannel)
                .anyMatch(normalized::equals);
    }

    public boolean allowsLoginPasswordlessMode(UUID applicationId, String mode) {
        return loginPasswordlessModes(applicationId).contains(mode);
    }

    public boolean allowsLoginIdentifier(UUID applicationId, String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return false;
        }
        List<String> types = loginIdentifierTypes(applicationId);
        if (identifier.contains("@")) {
            return types.contains("email");
        }
        return types.contains("phone");
    }

    private static String normalizeChannel(String channel) {
        if (channel == null) {
            return "email";
        }
        return switch (channel.toLowerCase()) {
            case "phone", "sms" -> "phone";
            default -> "email";
        };
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object raw, List<String> fallback) {
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return fallback;
    }

    private static String stringValue(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private static boolean boolValue(Map<String, Object> map, String key, boolean defaultValue) {
        Object val = map.get(key);
        return val instanceof Boolean b ? b : defaultValue;
    }

    private static int intValue(Map<String, Object> map, String key, int defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }
}
