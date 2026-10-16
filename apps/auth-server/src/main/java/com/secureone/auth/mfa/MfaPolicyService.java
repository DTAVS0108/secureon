package com.secureone.auth.mfa;

import com.secureone.auth.application.ApplicationSettingsService;
import com.secureone.auth.platform.AuthSettingsService;
import com.secureone.auth.platform.PlatformSettingsService;
import com.secureone.auth.platform.VerificationPolicyService;
import com.secureone.auth.trusteddevice.TrustedDeviceService;
import com.secureone.auth.user.UserAccount;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MfaPolicyService {

    private final AuthSettingsService authSettings;
    private final VerificationPolicyService verificationPolicy;
    private final PlatformSettingsService platformSettings;
    private final ApplicationSettingsService applicationSettings;
    private final MfaFactorRepository mfaFactors;
    private final TrustedDeviceService trustedDevices;
    private final org.springframework.beans.factory.ObjectProvider<com.secureone.auth.application.ApplicationEffectiveSettingsService> effectiveSettings;

    public MfaPolicyService(
            AuthSettingsService authSettings,
            VerificationPolicyService verificationPolicy,
            PlatformSettingsService platformSettings,
            ApplicationSettingsService applicationSettings,
            MfaFactorRepository mfaFactors,
            TrustedDeviceService trustedDevices,
            org.springframework.beans.factory.ObjectProvider<com.secureone.auth.application.ApplicationEffectiveSettingsService> effectiveSettings) {
        this.effectiveSettings = effectiveSettings;
        this.authSettings = authSettings;
        this.verificationPolicy = verificationPolicy;
        this.platformSettings = platformSettings;
        this.applicationSettings = applicationSettings;
        this.mfaFactors = mfaFactors;
        this.trustedDevices = trustedDevices;
    }

    /** Authenticator apps are allowed for the application this request is for (platform default otherwise). */
    public boolean totpAvailable() {
        return totpAvailable(com.secureone.auth.application.ApplicationSchemaContext.getApplicationId());
    }

    public boolean totpAvailable(UUID applicationId) {
        return effectiveSettings.getObject().isMethodAvailable(applicationId, "m_totp");
    }

    public boolean passkeysAvailable(UUID applicationId) {
        return effectiveSettings.getObject().isMethodAvailable(applicationId, "m_passkey");
    }

    public String loginMfaRequired(UUID applicationId) {
        Map<String, Object> login = verificationPolicy.loginPolicy(applicationId);
        Object raw = login.get("mfaRequired");
        return raw != null ? raw.toString().trim().toLowerCase() : "optional";
    }

    public Map<String, Object> resolvePolicy(UUID applicationId) {
        if (applicationId == null) {
            Map<String, Object> platform = platformSettings.get("mfa_policy");
            if (platform == null || platform.isEmpty()) {
                return MfaPolicyDefaults.platformDefaults();
            }
            return MfaPolicyDefaults.merge(platform, Map.of());
        }
        return applicationSettings.resolveMfaPolicy(applicationId);
    }

    public boolean stepUpAfterPassword(UUID applicationId) {
        Object raw = resolvePolicy(applicationId).get("stepUpAfterPassword");
        return raw instanceof Boolean b ? b : true;
    }

    public boolean requirePasskeyWhenEnrolled(UUID applicationId) {
        Object raw = resolvePolicy(applicationId).get("requirePasskeyWhenEnrolled");
        return raw instanceof Boolean b && b;
    }

    public int rememberDeviceDays(UUID applicationId) {
        return intValue(resolvePolicy(applicationId), "rememberDeviceDays", 0);
    }

    public int challengeTtlSeconds(UUID applicationId) {
        return intValue(resolvePolicy(applicationId), "challengeTtlSeconds", 300);
    }

    public int challengeTtlSeconds() {
        return challengeTtlSeconds(null);
    }

    public int maxChallengeAttempts(UUID applicationId) {
        return intValue(resolvePolicy(applicationId), "maxChallengeAttempts", 5);
    }

    public int maxChallengeAttempts() {
        return maxChallengeAttempts(null);
    }

    public int recoveryCodesPerEnrollment(UUID applicationId) {
        return intValue(resolvePolicy(applicationId), "recoveryCodesPerEnrollment", 10);
    }

    public int recoveryCodesPerEnrollment() {
        return recoveryCodesPerEnrollment(null);
    }

    public boolean hasVerifiedTotp(UUID userId) {
        return mfaFactors
                .findFirstByUserIdAndTypeAndVerifiedTrueOrderByCreatedAtAsc(userId, "totp")
                .isPresent();
    }

    public boolean hasVerifiedPasskey(UUID userId) {
        return mfaFactors
                .findFirstByUserIdAndTypeAndVerifiedTrueOrderByCreatedAtAsc(userId, "passkey")
                .isPresent();
    }

    /**
     * @return {@code true} when password auth succeeded but MFA verification is still required before a session
     */
    public boolean requiresMfaStepUp(UserAccount user, UUID applicationId) {
        return requiresMfaStepUp(user, applicationId, null);
    }

    public boolean requiresMfaStepUp(UserAccount user, UUID applicationId, HttpServletRequest request) {
        if (request != null && trustedDevices.isTrusted(user.getId(), request)) {
            return false;
        }
        if (!stepUpAfterPassword(applicationId)) {
            return false;
        }
        String mode = loginMfaRequired(applicationId);
        if ("off".equals(mode)) {
            return false;
        }
        if (requirePasskeyWhenEnrolled(applicationId) && passkeysAvailable(applicationId) && hasVerifiedPasskey(user.getId())) {
            return true;
        }
        if (totpAvailable(applicationId) && hasVerifiedTotp(user.getId())) {
            return true;
        }
        return false;
    }

    public void assertMfaEnrolledIfRequired(UserAccount user, UUID applicationId) {
        if (!totpAvailable(applicationId)) {
            return;
        }
        if (!"required".equals(loginMfaRequired(applicationId))) {
            return;
        }
        if (!hasVerifiedTotp(user.getId())) {
            throw new IllegalStateException(
                    "Multi-factor authentication is required. Enroll an authenticator app before signing in.");
        }
    }

    private static int intValue(Map<String, Object> map, String key, int defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Object val = map.get(key);
        if (val instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }
}
