package com.secureone.auth.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.secureone.auth.tenant.RlsScope;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConfigCacheTest {

    private final Instant[] now = {Instant.parse("2026-10-28T00:00:00Z")};
    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now[0];
        }
    };
    private final ConfigCache cache = new ConfigCache(Duration.ofSeconds(30), null, clock);
    private final AtomicInteger loads = new AtomicInteger();

    private String load() {
        loads.incrementAndGet();
        return "value";
    }

    private <T> T asPlatform(java.util.function.Supplier<T> work) {
        return RlsScope.asPlatform(work);
    }

    @Test
    void secondReadIsServedFromMemory() {
        assertEquals("value", asPlatform(() -> cache.get("r", "k", this::load)));
        assertEquals("value", asPlatform(() -> cache.get("r", "k", this::load)));
        assertEquals(1, loads.get());
    }

    @Test
    void missingValuesAreNotCached() {
        assertNull(asPlatform(() -> cache.get("r", "k", () -> {
            loads.incrementAndGet();
            return null;
        })));
        assertEquals("value", asPlatform(() -> cache.get("r", "k", this::load)));
        assertEquals(2, loads.get());
    }

    @Test
    void entriesExpireAfterTheTtl() {
        asPlatform(() -> cache.get("r", "k", this::load));
        now[0] = now[0].plusSeconds(31);
        asPlatform(() -> cache.get("r", "k", this::load));
        assertEquals(2, loads.get());
    }

    @Test
    void aChangeClearsEverything() {
        asPlatform(() -> cache.get("r", "k", this::load));
        cache.changed();
        asPlatform(() -> cache.get("r", "k", this::load));
        assertEquals(2, loads.get());
    }

    @Test
    void aValueLoadedWhileAChangeRanIsNotKept() {
        asPlatform(() -> cache.get("r", "k", () -> {
            cache.changed(); // a change commits while this request is still reading the old value
            return load();
        }));
        asPlatform(() -> cache.get("r", "k", this::load));
        assertEquals(2, loads.get());
    }

    @Test
    void tenantScopedCallersAlwaysReadTheDatabase() {
        cache.get("r", "k", this::load);
        cache.get("r", "k", this::load);
        assertEquals(2, loads.get());
        assertEquals(0, cache.size());
    }

    @Test
    void zeroTtlTurnsTheCacheOff() {
        ConfigCache off = new ConfigCache(Duration.ZERO, null, clock);
        asPlatform(() -> off.get("r", "k", this::load));
        asPlatform(() -> off.get("r", "k", this::load));
        assertEquals(2, loads.get());
    }
}
