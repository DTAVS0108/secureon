package com.secureone.auth.mfa;

import java.util.UUID;

/** Pending password-authenticated login awaiting MFA verification (stored in Redis). */
public record MfaPendingChallenge(
        UUID challengeId,
        UUID userId,
        String tenantSlug,
        String email,
        UUID applicationId) {}
