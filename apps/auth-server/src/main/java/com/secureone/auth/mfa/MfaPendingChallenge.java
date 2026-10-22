package com.secureone.auth.mfa;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * A first sign-in step (password, email code, magic link, external IdP) awaiting the second step or a setup
 * (stored in Redis). Fields after {@code firstFactor} are null in older entries.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MfaPendingChallenge(
        UUID challengeId,
        UUID userId,
        String tenantSlug,
        String email,
        UUID applicationId,
        /** Factor authority of the first step; null = password. */
        String firstFactor,
        /** "verify" (enter a second step) or "setup" (add a first method); null = verify. */
        String stage,
        /** A code sent by SMS or email for this challenge, and the factor (verify) or method (setup) it is for. */
        UUID otpChallengeId,
        String otpTarget,
        /** Setup in progress: the unconfirmed authenticator factor, or the phone/email the code went to. */
        String pendingSetup) {

    public MfaPendingChallenge(UUID challengeId, UUID userId, String tenantSlug, String email, UUID applicationId,
            String firstFactor) {
        this(challengeId, userId, tenantSlug, email, applicationId, firstFactor, "verify", null, null, null);
    }

    @JsonIgnore
    public boolean isSetup() {
        return "setup".equals(stage);
    }

    public MfaPendingChallenge withOtp(UUID otpChallengeId, String otpTarget, String pendingSetup) {
        return new MfaPendingChallenge(challengeId, userId, tenantSlug, email, applicationId, firstFactor, stage,
                otpChallengeId, otpTarget, pendingSetup);
    }

    public MfaPendingChallenge withPendingSetup(String pendingSetup) {
        return new MfaPendingChallenge(challengeId, userId, tenantSlug, email, applicationId, firstFactor, stage,
                otpChallengeId, otpTarget, pendingSetup);
    }
}
