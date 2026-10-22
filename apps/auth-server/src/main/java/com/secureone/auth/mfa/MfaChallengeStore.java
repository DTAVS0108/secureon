package com.secureone.auth.mfa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class MfaChallengeStore {

    private static final String PREFIX = "mfa:challenge:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public MfaChallengeStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void save(MfaPendingChallenge challenge, Duration ttl) {
        try {
            redis.opsForValue().set(PREFIX + challenge.challengeId(), objectMapper.writeValueAsString(challenge), ttl);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to store MFA challenge", ex);
        }
    }

    /** Replaces a challenge's state and keeps its remaining lifetime. */
    public void update(MfaPendingChallenge challenge) {
        Long seconds = redis.getExpire(PREFIX + challenge.challengeId());
        save(challenge, Duration.ofSeconds(seconds != null && seconds > 0 ? seconds : 60));
    }

    public Optional<MfaPendingChallenge> get(UUID challengeId) {
        String raw = redis.opsForValue().get(PREFIX + challengeId);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(raw, MfaPendingChallenge.class));
        } catch (JsonProcessingException ex) {
            redis.delete(PREFIX + challengeId);
            return Optional.empty();
        }
    }

    public void delete(UUID challengeId) {
        redis.delete(PREFIX + challengeId);
    }

    public int incrementAttempts(UUID challengeId, Duration ttl) {
        String key = PREFIX + challengeId + ":attempts";
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, ttl);
        }
        return count != null ? count.intValue() : 0;
    }

    public void clearAttempts(UUID challengeId) {
        redis.delete(PREFIX + challengeId + ":attempts");
    }
}
