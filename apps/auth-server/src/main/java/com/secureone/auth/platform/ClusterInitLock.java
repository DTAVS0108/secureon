package com.secureone.auth.platform;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Makes one-time creation of shared state (signing keys) safe when several auth-server replicas start
 * together: only the replica holding the Redis lock creates; the others wait and then read what it
 * stored. Without it, two replicas could each generate a key and the last save would win, leaving one
 * replica signing with a key nobody else publishes.
 */
@Component
public class ClusterInitLock {

    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final Duration WAIT = Duration.ofSeconds(35);

    private final StringRedisTemplate redis;

    public ClusterInitLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Runs {@code readExisting}; if it finds nothing, creates under the lock (re-reading first, in case
     * another replica created it meanwhile).
     */
    public <T> T readOrCreate(String name, Supplier<T> readExisting, Supplier<T> create) {
        T existing = readExisting.get();
        if (existing != null) {
            return existing;
        }
        String key = "init-lock:" + name;
        String token = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, LOCK_TTL))) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("Timed out waiting for another replica to create " + name);
            }
            sleep();
            T created = readExisting.get();
            if (created != null) {
                return created;
            }
        }
        try {
            T again = readExisting.get();
            return again != null ? again : create.get();
        } finally {
            if (token.equals(redis.opsForValue().get(key))) {
                redis.delete(key);
            }
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(250);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for cluster init lock", ex);
        }
    }
}
