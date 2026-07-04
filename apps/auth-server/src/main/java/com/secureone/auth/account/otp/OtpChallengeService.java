package com.secureone.auth.account.otp;

import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.platform.VerificationPolicyService;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OtpChallengeService {

    public record IssuedChallenge(UUID challengeId, String code, String maskedDestination, int expiresInSeconds) {}

    private final OtpChallengeRepository challenges;
    private final OtpRedisStore redis;
    private final VerificationPolicyService verificationPolicy;
    private final String pepper;

    public OtpChallengeService(
            OtpChallengeRepository challenges,
            OtpRedisStore redis,
            VerificationPolicyService verificationPolicy,
            @Value("${secureone.otp.pepper:change-me-in-production}") String pepper) {
        this.challenges = challenges;
        this.redis = redis;
        this.verificationPolicy = verificationPolicy;
        this.pepper = pepper;
    }

    public IssuedChallenge issue(
            UUID userId,
            UUID tenantId,
            UUID applicationId,
            OtpPurpose purpose,
            OtpChannel channel,
            String destination,
            String ipAddress,
            String userAgent) {
        return issue(userId, tenantId, applicationId, purpose, channel, destination, ipAddress, userAgent, policySectionFor(purpose));
    }

    public IssuedChallenge issue(
            UUID userId,
            UUID tenantId,
            UUID applicationId,
            OtpPurpose purpose,
            OtpChannel channel,
            String destination,
            String ipAddress,
            String userAgent,
            OtpPolicySection section) {
        if (userId != null && redis.isResendCooldown(
                userId,
                purpose,
                Duration.ofSeconds(verificationPolicy.resendCooldownSeconds(applicationId, section)))) {
            throw new IllegalStateException("Please wait before requesting another code.");
        }

        if (userId != null) {
            challenges.invalidateActive(userId, purpose.name());
        }

        int length = verificationPolicy.otpLength(applicationId, section);
        int ttlSeconds = verificationPolicy.otpTtlSeconds(applicationId, section);
        int maxAttempts = verificationPolicy.maxAttempts(applicationId, section);
        String code = OtpGenerator.generate(length);
        UUID challengeId = UUID.randomUUID();

        OtpChallenge row = new OtpChallenge();
        row.setId(challengeId);
        row.setUserId(userId);
        row.setTenantId(tenantId);
        row.setApplicationId(applicationId);
        row.setPurpose(purpose.name());
        row.setChannel(channel.name());
        row.setDestination(destination);
        row.setCodeHash(OtpHasher.hash(challengeId, code, pepper));
        row.setMaxAttempts(maxAttempts);
        row.setExpiresAt(Instant.now().plusSeconds(ttlSeconds));
        row.setIpAddress(ipAddress);
        row.setUserAgent(userAgent);
        challenges.save(row);

        redis.storeChallenge(challengeId, row.getCodeHash(), Duration.ofSeconds(ttlSeconds));

        return new IssuedChallenge(challengeId, code, maskDestination(channel, destination), ttlSeconds);
    }

    private static OtpPolicySection policySectionFor(OtpPurpose purpose) {
        return switch (purpose) {
            case LOGIN -> OtpPolicySection.LOGIN;
            case RESET_PASSWORD -> OtpPolicySection.RECOVERY;
            default -> OtpPolicySection.SIGNUP;
        };
    }

    @Transactional(readOnly = true)
    public OtpChallenge requireActive(UUID challengeId) {
        OtpChallenge challenge = challenges
                .findByIdAndConsumedAtIsNull(challengeId)
                .filter(c -> c.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired code."));
        return challenge;
    }

    public OtpChallenge verify(UUID challengeId, String code) {
        OtpChallenge challenge = requireActive(challengeId);
        int attempts = redis.incrementAttempts(
                challengeId, Duration.ofSeconds(Math.max(60, challenge.getExpiresAt().getEpochSecond()
                        - Instant.now().getEpochSecond())));
        if (attempts > challenge.getMaxAttempts()) {
            challenge.setConsumedAt(Instant.now());
            challenges.save(challenge);
            redis.deleteChallenge(challengeId);
            throw new IllegalStateException("Too many failed attempts. Request a new code.");
        }

        String expected = redis.getChallengeHash(challengeId);
        if (expected == null) {
            expected = challenge.getCodeHash();
        }
        String actual = OtpHasher.hash(challengeId, code.trim(), pepper);
        if (!OtpHasher.constantTimeEquals(expected, actual)) {
            challenge.setAttempts(attempts);
            challenges.save(challenge);
            throw new ResourceNotFoundException("Invalid or expired code.");
        }

        challenge.setConsumedAt(Instant.now());
        challenges.save(challenge);
        redis.deleteChallenge(challengeId);
        redis.clearAttempts(challengeId);
        return challenge;
    }

    public Map<String, Object> challengeResponse(IssuedChallenge issued) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("challengeId", issued.challengeId().toString());
        out.put("maskedDestination", issued.maskedDestination());
        out.put("expiresIn", issued.expiresInSeconds());
        out.put("mode", "otp");
        return out;
    }

    private static String maskDestination(OtpChannel channel, String destination) {
        if (channel == OtpChannel.SMS) {
            return com.secureone.auth.account.PhoneNormalizer.mask(destination);
        }
        int at = destination.indexOf('@');
        if (at <= 1) {
            return "***@" + (at >= 0 ? destination.substring(at + 1) : "***");
        }
        return destination.charAt(0) + "***" + destination.substring(at);
    }
}
