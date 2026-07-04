package com.secureone.auth.ratelimit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisRateLimiter limiter;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        limiter = new RedisRateLimiter(redis);
    }

    @Test
    void allowsRequestsUnderLimit() {
        when(valueOps.increment("secureone:rate:login:127.0.0.1")).thenReturn(1L);

        assertTrue(limiter.tryConsume("login:127.0.0.1", 5));
        verify(redis).expire(eq("secureone:rate:login:127.0.0.1"), eq(Duration.ofMinutes(1)));
    }

    @Test
    void blocksRequestsOverLimit() {
        when(valueOps.increment("secureone:rate:login:127.0.0.1")).thenReturn(6L);

        assertFalse(limiter.tryConsume("login:127.0.0.1", 5));
    }
}
