package com.secureone.auth.federation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class FederationStateStore {

    private static final String PREFIX = "federation:state:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public FederationStateStore(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public record PendingState(UUID providerId, UUID tenantId, UUID applicationId, String continueUrl) {}

    public String issue(PendingState state) {
        String token = UUID.randomUUID().toString().replace("-", "");
        try {
            redis.opsForValue().set(PREFIX + token, mapper.writeValueAsString(state), TTL);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize federation state", ex);
        }
        return token;
    }

    public Optional<PendingState> consume(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String key = PREFIX + token.trim();
        String raw = redis.opsForValue().get(key);
        redis.delete(key);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(raw, PendingState.class));
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }
}
