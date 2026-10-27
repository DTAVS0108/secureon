package com.secureone.auth.config.oauth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.authentication.JwtClientAssertionDecoderFactory;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/**
 * private_key_jwt (RFC 7523, OIDC Core 9): checks a client's assertion with the public keys it registered, either
 * served at its JWKS URL or stored as a JWKS document. On top of Spring's checks (iss and sub are the client_id,
 * aud names this server, exp, nbf) an assertion must carry a jti, live at most {@link #MAX_LIFETIME}, and is
 * accepted once (a replayed assertion is refused).
 */
public final class ClientAssertionDecoderFactory implements JwtDecoderFactory<RegisteredClient> {

    /** ClientSettings keys the client store fills in for private_key_jwt clients. */
    public static final String JWKS_SETTING = "secureone.client.jwks";

    public static final List<String> ALGORITHM_NAMES =
            List.of("RS256", "RS384", "RS512", "PS256", "PS384", "PS512", "ES256", "ES384", "ES512");

    static final Duration MAX_LIFETIME = Duration.ofMinutes(10);

    private static final Set<JWSAlgorithm> ALGORITHMS =
            Set.copyOf(ALGORITHM_NAMES.stream().map(JWSAlgorithm::parse).toList());

    private final StringRedisTemplate redis;
    private final Map<String, Cached> decoders = new ConcurrentHashMap<>();
    /** Used jti values when Redis is not available (single node). */
    private final Map<String, Instant> usedLocally = new ConcurrentHashMap<>();

    private record Cached(String keys, JwtDecoder decoder) {}

    /** {@code redis} null: used jti values are kept in memory (one node only). */
    public ClientAssertionDecoderFactory(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public JwtDecoder createDecoder(RegisteredClient client) {
        String inline = client.getClientSettings().getSetting(JWKS_SETTING);
        String uri = client.getClientSettings().getJwkSetUrl();
        String keys = inline != null ? inline : uri;
        if (keys == null || keys.isBlank()) {
            throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException(new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_CLIENT, "The client has no public keys registered for private_key_jwt.", null));
        }
        Cached cached = decoders.get(client.getId());
        if (cached != null && cached.keys().equals(keys)) {
            return cached.decoder();
        }
        JwtDecoder decoder = build(client, inline, uri);
        decoders.put(client.getId(), new Cached(keys, decoder));
        return decoder;
    }

    private JwtDecoder build(RegisteredClient client, String inline, String uri) {
        JWKSource<SecurityContext> source;
        try {
            if (inline != null) {
                source = new ImmutableJWKSet<>(publicKeys(inline));
            } else {
                // Short timeouts and a size cap: the URL is the client's, fetched while its token request waits.
                source = JWKSourceBuilder.create(URI.create(uri).toURL(), new DefaultResourceRetriever(3000, 3000, 64 * 1024))
                        .cache(Duration.ofMinutes(5).toMillis(), Duration.ofSeconds(5).toMillis())
                        .build();
            }
        } catch (Exception e) {
            throw new IllegalStateException("The client's public keys cannot be read.", e);
        }
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(ALGORITHMS, source));
        // Claims are checked by the validators below (Spring's own plus SecureOne's).
        processor.setJWTClaimsSetVerifier((claims, context) -> { });
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtClientAssertionDecoderFactory.DEFAULT_JWT_VALIDATOR_FACTORY.apply(client),
                this::shortLived,
                jwt -> onlyOnce(client.getClientId(), jwt)));
        return decoder;
    }

    /** The JWKS document with public keys only (a private key there is refused when it is registered). */
    public static JWKSet publicKeys(String json) throws java.text.ParseException {
        return JWKSet.parse(json).toPublicJWKSet();
    }

    private OAuth2TokenValidatorResult shortLived(Jwt jwt) {
        Instant now = Instant.now();
        if (jwt.getId() == null || jwt.getId().isBlank()) {
            return failure("The client assertion has no jti.");
        }
        if (jwt.getExpiresAt() == null || jwt.getExpiresAt().isAfter(now.plus(MAX_LIFETIME))) {
            return failure("The client assertion must expire within " + MAX_LIFETIME.toMinutes() + " minutes.");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private OAuth2TokenValidatorResult onlyOnce(String clientId, Jwt jwt) {
        String key = "secureone:client-assertion:" + clientId + ":" + jwt.getId();
        // Kept until the assertion expires (plus the minute of clock skew the expiry check allows).
        Duration ttl = Duration.between(Instant.now(), Objects.requireNonNull(jwt.getExpiresAt())).plusMinutes(1);
        if (ttl.isNegative() || ttl.isZero()) {
            ttl = Duration.ofMinutes(1);
        }
        StringRedisTemplate store = redis;
        boolean first;
        if (store != null) {
            first = Boolean.TRUE.equals(store.opsForValue().setIfAbsent(key, "1", ttl));
        } else {
            Instant now = Instant.now();
            usedLocally.values().removeIf(Predicate.not(until -> until.isAfter(now)));
            first = usedLocally.putIfAbsent(key, now.plus(ttl)) == null;
        }
        return first ? OAuth2TokenValidatorResult.success() : failure("The client assertion was already used.");
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT, description, null));
    }

    /** A JWK set that may be registered: 1 to 10 RSA or EC public signing keys. Returns the public part as JSON. */
    public static String validateJwks(String json) {
        JWKSet set;
        try {
            set = JWKSet.parse(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("jwks must be a JWK set: {\"keys\": [...]}.");
        }
        List<JWK> keys = set.getKeys();
        if (keys.isEmpty() || keys.size() > 10) {
            throw new IllegalArgumentException("jwks must hold 1 to 10 keys.");
        }
        for (JWK key : keys) {
            if (key.isPrivate()) {
                throw new IllegalArgumentException("jwks must hold public keys only: never send a private key.");
            }
            if (!(key instanceof com.nimbusds.jose.jwk.RSAKey) && !(key instanceof com.nimbusds.jose.jwk.ECKey)) {
                throw new IllegalArgumentException("jwks keys must be RSA or EC keys.");
            }
            if (key.getKeyUse() != null && !com.nimbusds.jose.jwk.KeyUse.SIGNATURE.equals(key.getKeyUse())) {
                throw new IllegalArgumentException("jwks keys must be signing keys (\"use\": \"sig\").");
            }
        }
        return set.toPublicJWKSet().toString(true);
    }
}
