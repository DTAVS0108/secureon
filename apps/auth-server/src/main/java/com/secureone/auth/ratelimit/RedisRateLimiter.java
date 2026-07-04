package com.secureone.auth.ratelimit;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

/** Fixed-window request counter backed by Redis (shared across replicas). */
@Component
public class RedisRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * @return {@code true} if the request is allowed, {@code false} if the limit is exceeded
     */
    public boolean tryConsume(String bucket, int limit) {
        if (limit <= 0) {
            return true;
        }
        String key = "secureone:rate:" + bucket;
        ValueOperations<String, String> ops = redis.opsForValue();
        Long count = ops.increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, WINDOW);
        }
        return count != null && count <= limit;
    }
}
