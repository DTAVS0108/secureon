package com.secureone.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class StartupWaitTest {

    @Test
    void readsTheDatabaseHostAndPort() {
        assertEquals(new StartupWait.Endpoint("PostgreSQL", "db.internal", 6432),
                StartupWait.database("jdbc:postgresql://db.internal:6432/secureone?currentSchema=platform"));
        assertEquals(new StartupWait.Endpoint("PostgreSQL", "postgres", 5432),
                StartupWait.database("jdbc:postgresql://postgres/secureone"));
        // Several hosts: the first one is enough to know the database is up.
        assertEquals(new StartupWait.Endpoint("PostgreSQL", "a", 5432),
                StartupWait.database("jdbc:postgresql://a:5432,b:5432/secureone"));
        assertNull(StartupWait.database("not a url"));
    }

    @Test
    void readsTheRedisHostAndPort() {
        assertEquals(new StartupWait.Endpoint("Redis", "redis", 6379), StartupWait.redis("redis://redis:6379"));
        assertEquals(new StartupWait.Endpoint("Redis", "cache.example.com", 6380),
                StartupWait.redis("rediss://user:secret@cache.example.com:6380/0"));
        assertEquals(new StartupWait.Endpoint("Redis", "redis", 6379), StartupWait.redis("redis://redis"));
        assertNull(StartupWait.redis(""));
    }
}
