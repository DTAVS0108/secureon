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

    /**
     * What a sign-in started here needs at the callback. {@code codeVerifier} (PKCE, RFC 7636) and {@code nonce}
     * (OpenID Connect) are kept server side with the state and never leave SecureOne except as the code challenge
     * and nonce sent to the identity provider; both are null for SAML.
     */
    public record PendingState(
            UUID providerId, UUID tenantId, UUID applicationId, String continueUrl, String codeVerifier, String nonce) {

        public PendingState(UUID providerId, UUID tenantId, UUID applicationId, String continueUrl) {
            this(providerId, tenantId, applicationId, continueUrl, null, null);
        }
    }

    public String issue(PendingState state) {
        String token = UUID.randomUUID().toString().replace("-", "");
        try {
            redis.opsForValue().set(PREFIX + token, mapper.writeValueAsString(state), TTL);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize federation state", ex);
        }
        return token;
    }

    /** Reads the state without using it up (to know where to send the user if the callback fails). */
    public Optional<PendingState> peek(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String raw = redis.opsForValue().get(PREFIX + token.trim());
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(raw, PendingState.class));
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
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
