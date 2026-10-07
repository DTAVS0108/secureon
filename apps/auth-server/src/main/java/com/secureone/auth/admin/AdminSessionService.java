package com.secureone.auth.admin;

import com.secureone.auth.authn.AdminSessionPrincipals;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Opaque admin console session tokens. The console keeps only the token; Redis keeps a SHA-256
 * hash of it mapped to the principal name, so neither side stores the password.
 *
 * <p>A session ends after {@code session-idle-timeout} without requests, and at its absolute
 * lifetime regardless of activity (shorter for the break-glass account).
 */
@Service
public class AdminSessionService {

    private static final String KEY_PREFIX = "secureone:admin-session:";
    private static final Pattern TOKEN_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final StringRedisTemplate redis;
    private final AdminSessionPrincipals principals;
    private final Duration idleTimeout;
    private final Duration maxLifetime;
    private final Duration breakGlassMaxLifetime;
    private final SecureRandom random = new SecureRandom();

    public AdminSessionService(
            StringRedisTemplate redis,
            AdminSessionPrincipals principals,
            @Value("${secureone.admin.session-idle-timeout:30m}") Duration idleTimeout,
            @Value("${secureone.admin.session-max-lifetime:8h}") Duration maxLifetime,
            @Value("${secureone.admin.break-glass-session-max-lifetime:1h}") Duration breakGlassMaxLifetime) {
        this.redis = redis;
        this.principals = principals;
        this.idleTimeout = idleTimeout;
        this.maxLifetime = maxLifetime;
        this.breakGlassMaxLifetime = breakGlassMaxLifetime;
    }

    /** A newly issued token and how long it can live. */
    public record IssuedSession(String token, long expiresIn, long idleTimeout) {}

    /** Issues a new token for a fully authenticated (password and, where required, MFA) principal. */
    public IssuedSession create(Authentication authentication, boolean breakGlass) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Duration max = breakGlass ? breakGlassMaxLifetime : maxLifetime;
        Instant expiresAt = Instant.now().plus(max);
        redis.opsForValue().set(
                key(token), expiresAt.getEpochSecond() + "|" + authentication.getName(), firstTtl(max));
        return new IssuedSession(token, max.toSeconds(), idleTimeout.toSeconds());
    }

    /**
     * Resolves a token to a current principal, or empty when unknown, idle too long, past its
     * absolute lifetime, or the account is no longer usable. Each successful use restarts the idle
     * timer.
     */
    public Optional<Authentication> resolve(String token) {
        return resolveSession(token).map(ResolvedSession::authentication);
    }

    /** A live session: who it belongs to and when it ends at the latest. */
    public record ResolvedSession(Authentication authentication, Instant expiresAt) {}

    public Optional<ResolvedSession> resolveSession(String token) {
        if (!isWellFormed(token)) {
            return Optional.empty();
        }
        String key = key(token);
        String value = redis.opsForValue().get(key);
        int sep = value == null ? -1 : value.indexOf('|');
        if (sep <= 0) {
            if (value != null) {
                redis.delete(key); // issued before idle timeouts existed
            }
            return Optional.empty();
        }
        Instant expiresAt;
        try {
            expiresAt = Instant.ofEpochSecond(Long.parseLong(value.substring(0, sep)));
        } catch (NumberFormatException ex) {
            redis.delete(key);
            return Optional.empty();
        }
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        if (remaining.isNegative() || remaining.isZero()) {
            redis.delete(key);
            return Optional.empty();
        }
        Optional<Authentication> restored = principals.restore(value.substring(sep + 1));
        if (restored.isEmpty()) {
            redis.delete(key);
            return Optional.empty();
        }
        redis.expire(key, remaining.compareTo(idleTimeout) < 0 ? remaining : idleTimeout);
        return Optional.of(new ResolvedSession(restored.get(), expiresAt));
    }

    public long idleTimeoutSeconds() {
        return idleTimeout.toSeconds();
    }

    public void revoke(String token) {
        if (isWellFormed(token)) {
            redis.delete(key(token));
        }
    }

    private Duration firstTtl(Duration max) {
        return max.compareTo(idleTimeout) < 0 ? max : idleTimeout;
    }

    private static boolean isWellFormed(String token) {
        return token != null && TOKEN_FORMAT.matcher(token).matches();
    }

    static String key(String token) {
        return KEY_PREFIX + sha256(token);
    }

    static String sha256(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
