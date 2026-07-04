package com.secureone.auth.account.otp;

import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Hot-path OTP state and rate limiting in Redis. */
@Component
public class OtpRedisStore {

    private static final String CHALLENGE_PREFIX = "otp:challenge:";
    private static final String RESEND_PREFIX = "otp:resend:";
    private static final String ATTEMPT_PREFIX = "otp:attempt:";

    private final StringRedisTemplate redis;

    public OtpRedisStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void storeChallenge(UUID challengeId, String codeHash, Duration ttl) {
        redis.opsForValue().set(CHALLENGE_PREFIX + challengeId, codeHash, ttl);
    }

    public String getChallengeHash(UUID challengeId) {
        return redis.opsForValue().get(CHALLENGE_PREFIX + challengeId);
    }

    public void deleteChallenge(UUID challengeId) {
        redis.delete(CHALLENGE_PREFIX + challengeId);
    }

    public boolean isResendCooldown(UUID userId, OtpPurpose purpose, Duration cooldown) {
        String key = RESEND_PREFIX + userId + ":" + purpose.name();
        Boolean set = redis.opsForValue().setIfAbsent(key, "1", cooldown);
        return set == null || !set;
    }

    public int incrementAttempts(UUID challengeId, Duration ttl) {
        String key = ATTEMPT_PREFIX + challengeId;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, ttl);
        }
        return count != null ? count.intValue() : 0;
    }

    public void clearAttempts(UUID challengeId) {
        redis.delete(ATTEMPT_PREFIX + challengeId);
    }
}
