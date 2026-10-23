package com.secureone.auth.mfa;

import com.secureone.auth.twostep.TwoStepMethod;
import com.secureone.auth.twostep.TwoStepPolicyResolver;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Typed reads of the effective two-step policy (platform, tenant, application; see
 * {@link TwoStepPolicyResolver}). The sign-in decision itself is in {@code TwoStepService}.
 */
@Service
public class MfaPolicyService {

    private final TwoStepPolicyResolver resolver;
    private final MfaFactorRepository mfaFactors;

    public MfaPolicyService(TwoStepPolicyResolver resolver, MfaFactorRepository mfaFactors) {
        this.resolver = resolver;
        this.mfaFactors = mfaFactors;
    }

    public Map<String, Object> resolvePolicy(UUID applicationId) {
        return resolver.resolve(applicationId).policy();
    }

    /** off / optional / required. */
    public String loginMfaRequired(UUID applicationId) {
        return String.valueOf(resolvePolicy(applicationId).get("mode"));
    }

    @SuppressWarnings("unchecked")
    public List<TwoStepMethod> allowedMethods(UUID applicationId) {
        Object raw = resolvePolicy(applicationId).get("methods");
        List<String> ids = raw instanceof List<?> list ? (List<String>) list : List.of();
        return ids.stream().map(TwoStepMethod::fromId).flatMap(java.util.Optional::stream).toList();
    }

    public TwoStepMethod.Strength minimumStrength(UUID applicationId) {
        return TwoStepMethod.Strength.fromId(String.valueOf(resolvePolicy(applicationId).get("minimumStrength")));
    }

    /** Authenticator apps are allowed for the application this request is for (platform default otherwise). */
    public boolean totpAvailable() {
        return totpAvailable(com.secureone.auth.application.ApplicationSchemaContext.getApplicationId());
    }

    public boolean totpAvailable(UUID applicationId) {
        return allowedMethods(applicationId).contains(TwoStepMethod.TOTP);
    }

    public boolean passkeysAvailable(UUID applicationId) {
        List<TwoStepMethod> methods = allowedMethods(applicationId);
        return methods.contains(TwoStepMethod.PASSKEY) || methods.contains(TwoStepMethod.SECURITY_KEY);
    }

    public boolean stepUpAfterPassword(UUID applicationId) {
        return bool(resolvePolicy(applicationId), "stepUpAfterPassword", true);
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

    public int freshStepMinutes(UUID applicationId) {
        return intValue(resolvePolicy(applicationId), "freshStepMinutes", 5);
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

    static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object val = map != null ? map.get(key) : null;
        return val instanceof Boolean b ? b : fallback;
    }

    static int intValue(Map<String, Object> map, String key, int defaultValue) {
        Object val = map != null ? map.get(key) : null;
        return val instanceof Number n ? n.intValue() : defaultValue;
    }
}
