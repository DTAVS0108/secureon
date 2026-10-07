package com.secureone.auth.admin;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Short-lived proof that an operator passed the password step of console sign-in. It is exchanged,
 * together with a second factor, for a console session token. It never grants API access by itself.
 */
@Service
public class AdminMfaChallengeService {

    static final Duration TTL = Duration.ofMinutes(5);
    static final int MAX_ATTEMPTS = 5;

    private static final String KEY_PREFIX = "secureone:admin-mfa:";
    private static final Pattern TOKEN_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    public AdminMfaChallengeService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** What the password step established. {@code stage} is {@code verify} or {@code enroll}. */
    public record Challenge(String principal, UUID userId, String stage) {}

    public String issue(String principal, UUID userId, String stage) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(key(token), stage + "|" + userId + "|" + principal, TTL);
        return token;
    }

    public Optional<Challenge> peek(String token) {
        if (token == null || !TOKEN_FORMAT.matcher(token).matches()) {
            return Optional.empty();
        }
        String value = redis.opsForValue().get(key(token));
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split("\\|", 3);
        if (parts.length != 3) {
            return Optional.empty();
        }
        return Optional.of(new Challenge(parts[2], UUID.fromString(parts[1]), parts[0]));
    }

    /** Records a wrong code; after {@link #MAX_ATTEMPTS} the challenge is gone and the password step must be repeated. */
    public int recordFailure(String token) {
        String attemptsKey = key(token) + ":attempts";
        Long attempts = redis.opsForValue().increment(attemptsKey);
        redis.expire(attemptsKey, TTL);
        long n = attempts == null ? MAX_ATTEMPTS : attempts;
        if (n >= MAX_ATTEMPTS) {
            consume(token);
        }
        return (int) Math.max(0, MAX_ATTEMPTS - n);
    }

    public void consume(String token) {
        redis.delete(key(token));
        redis.delete(key(token) + ":attempts");
    }

    private static String key(String token) {
        return KEY_PREFIX + AdminSessionService.sha256(token);
    }
}
