package com.secureone.auth.cache;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Short-lived in-memory cache for configuration read on every OAuth request (clients, applications, settings).
 * A load test showed a client-credentials token running 59 SQL statements, nearly all re-reading the same
 * configuration; with this cache it is a few.
 *
 * <p>Correctness first: every successful change through the admin API, SCIM, configuration import or client
 * registration clears the whole cache at once ({@link ConfigChangeFilter}), on this instance and, through a
 * Redis message, on every other one. Anything changed another way (scheduled jobs, direct SQL) is picked up
 * within {@code secureone.cache.config-ttl} (30 s; 0 turns the cache off). Missing values are never cached,
 * so a newly created client works at once. Callers get the cached object itself: cache only values they do
 * not change (immutable, or copied by the caller).
 */
@Component
public class ConfigCache implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(ConfigCache.class);
    static final String CHANNEL = "secureone:config-changed";

    private record Entry(Object value, long expiresAtMillis) {}

    private record Key(String region, Object key) {}

    private final ConcurrentHashMap<Key, Entry> entries = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final Clock clock;
    private final StringRedisTemplate redis;
    /** Tells this instance's own messages apart, so a change is not cleared twice. */
    private final String instanceId = UUID.randomUUID().toString();
    /** Bumped by every clear: a value loaded while a clear ran is not stored (it may be from before the change). */
    private volatile long generation;

    @org.springframework.beans.factory.annotation.Autowired
    public ConfigCache(
            @Value("${secureone.cache.config-ttl:30s}") Duration ttl,
            @org.springframework.beans.factory.annotation.Autowired(required = false) StringRedisTemplate redis) {
        this(ttl, redis, Clock.systemUTC());
    }

    ConfigCache(Duration ttl, StringRedisTemplate redis, Clock clock) {
        this.ttlMillis = ttl.toMillis();
        this.redis = redis;
        this.clock = clock;
    }

    /**
     * The cached value, or {@code loader}'s (stored unless null). Only platform-scoped work (the sign-in and
     * protocol endpoints, see RlsSystemScopeFilter) uses the cache: it is shared by all tenants, so a
     * tenant-scoped caller always reads through row-level security instead.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String region, Object key, Supplier<T> loader) {
        if (ttlMillis <= 0 || key == null || !com.secureone.auth.tenant.RlsScope.isPlatform()) {
            return loader.get();
        }
        Key k = new Key(region, key);
        long now = clock.millis();
        Entry hit = entries.get(k);
        if (hit != null && hit.expiresAtMillis() > now) {
            return (T) hit.value();
        }
        long before = generation;
        T value = loader.get();
        if (value != null && before == generation) {
            entries.put(k, new Entry(value, now + ttlMillis));
        }
        return value;
    }

    /** Clears this instance's cache only. */
    public void clearLocal() {
        generation++;
        entries.clear();
    }

    /** A configuration change: clears this instance's cache and tells the other instances to clear theirs. */
    public void changed() {
        clearLocal();
        if (redis != null) {
            try {
                redis.convertAndSend(CHANNEL, instanceId);
            } catch (RuntimeException e) {
                // The other instances still expire their entries within the TTL.
                log.warn("Could not tell other instances to clear their configuration cache: {}", e.getMessage());
            }
        }
    }

    /**
     * A change written in the current transaction: cleared now and again once it commits, so a request that
     * reloaded the old value in between does not keep it.
     */
    public void changedAfterCommit() {
        changed();
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            changed();
                        }
                    });
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (!Objects.equals(instanceId, new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8))) {
            clearLocal();
        }
    }

    int size() {
        return entries.size();
    }

    @org.springframework.context.annotation.Configuration
    static class Listener {
        @org.springframework.context.annotation.Bean
        RedisMessageListenerContainer configCacheListener(RedisConnectionFactory connections, ConfigCache cache) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connections);
            container.addMessageListener(cache, new ChannelTopic(CHANNEL));
            return container;
        }
    }
}
