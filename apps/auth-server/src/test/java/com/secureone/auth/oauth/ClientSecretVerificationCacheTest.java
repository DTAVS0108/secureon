package com.secureone.auth.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ClientSecretVerificationCacheTest {

    private final AtomicInteger slowChecks = new AtomicInteger();

    private ClientSecretVerificationCache cache(Clock clock) {
        return new ClientSecretVerificationCache((raw, stored) -> {
            slowChecks.incrementAndGet();
            return ("hash:" + raw).equals(stored) || stored.startsWith("{dual}hash:" + raw);
        }, clock);
    }

    @Test
    void rightSecretPaysTheSlowCheckOnce() {
        ClientSecretVerificationCache cache = cache(Clock.systemUTC());
        assertTrue(cache.matches("s3cret", "hash:s3cret"));
        assertTrue(cache.matches("s3cret", "hash:s3cret"));
        assertTrue(cache.matches("s3cret", "hash:s3cret"));
        assertEquals(1, slowChecks.get());
    }

    @Test
    void wrongSecretIsNeverCachedAndNeverMatches() {
        ClientSecretVerificationCache cache = cache(Clock.systemUTC());
        assertTrue(cache.matches("s3cret", "hash:s3cret"));
        assertFalse(cache.matches("guess", "hash:s3cret"));
        assertFalse(cache.matches("guess", "hash:s3cret"));
        assertEquals(3, slowChecks.get());
    }

    @Test
    void rotatedSecretHasAnotherHashSoTheOldEntryIsNotUsed() {
        ClientSecretVerificationCache cache = cache(Clock.systemUTC());
        assertTrue(cache.matches("old", "hash:old"));
        assertFalse(cache.matches("old", "hash:new"));
    }

    @Test
    void entriesExpire() {
        Instant[] now = {Instant.parse("2026-10-28T00:00:00Z")};
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now[0];
            }
        };
        ClientSecretVerificationCache cache = cache(clock);
        assertTrue(cache.matches("s3cret", "hash:s3cret"));
        assertTrue(cache.matches("s3cret", "hash:s3cret"));
        assertEquals(1, slowChecks.get());
        now[0] = now[0].plus(ClientSecretVerificationCache.TTL).plusSeconds(1);
        assertTrue(cache.matches("s3cret", "hash:s3cret"));
        assertEquals(2, slowChecks.get());
    }

    @Test
    void rotationOverlapIsNotCached() {
        ClientSecretVerificationCache cache = cache(Clock.systemUTC());
        assertTrue(cache.matches("s3cret", "{dual}hash:s3cret|hash:prev|1"));
        assertTrue(cache.matches("s3cret", "{dual}hash:s3cret|hash:prev|1"));
        assertEquals(2, slowChecks.get());
    }
}
