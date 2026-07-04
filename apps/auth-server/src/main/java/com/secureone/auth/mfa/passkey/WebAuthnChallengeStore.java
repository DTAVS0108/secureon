package com.secureone.auth.mfa.passkey;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class WebAuthnChallengeStore {

    private static final String REG_PREFIX = "passkey:reg:";
    private static final String AUTH_PREFIX = "passkey:auth:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public WebAuthnChallengeStore(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public record RegistrationPending(UUID userId, String username, String optionsJson) {}

    public record AuthenticationPending(String username, String requestJson) {}

    public String saveRegistration(RegistrationPending pending) {
        return save(REG_PREFIX, pending);
    }

    public Optional<RegistrationPending> consumeRegistration(String requestId) {
        return consume(REG_PREFIX, requestId, RegistrationPending.class);
    }

    public String saveAuthentication(AuthenticationPending pending) {
        return save(AUTH_PREFIX, pending);
    }

    public Optional<AuthenticationPending> consumeAuthentication(String requestId) {
        return consume(AUTH_PREFIX, requestId, AuthenticationPending.class);
    }

    private String save(String prefix, Object pending) {
        String requestId = UUID.randomUUID().toString();
        try {
            redis.opsForValue().set(prefix + requestId, mapper.writeValueAsString(pending), TTL);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize WebAuthn challenge", ex);
        }
        return requestId;
    }

    private <T> Optional<T> consume(String prefix, String requestId, Class<T> type) {
        if (requestId == null || requestId.isBlank()) {
            return Optional.empty();
        }
        String key = prefix + requestId.trim();
        String raw = redis.opsForValue().get(key);
        redis.delete(key);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(raw, type));
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }
}
