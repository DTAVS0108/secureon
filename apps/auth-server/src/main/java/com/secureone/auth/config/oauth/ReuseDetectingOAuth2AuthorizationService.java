package com.secureone.auth.config.oauth;

import com.secureone.auth.oauth.RefreshTokenReuseAuditService;
import com.secureone.auth.oauth.RefreshTokenReuseContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Service;

/**
 * Delegates to JDBC storage and enforces refresh-token reuse detection (RFC 9700 4.14.2).
 *
 * <p>When a refresh token is rotated, a hash of the retired token is kept in Redis until it would
 * have expired. If a retired token is presented again, the whole authorization (its access and
 * current refresh tokens) is revoked and the reuse is audited, because either the client or an
 * attacker holds a stolen token.
 */
@Service
@Primary
public class ReuseDetectingOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(ReuseDetectingOAuth2AuthorizationService.class);
    private static final String RETIRED_PREFIX = "secureone:oauth:retired-refresh:";
    /** Upper bound when a refresh token carries no expiry. */
    private static final Duration MAX_RETIRED_TTL = Duration.ofDays(90);

    private final OAuth2AuthorizationService delegate;
    private final RefreshTokenReuseAuditService reuseAudit;
    private final StringRedisTemplate redis;

    public ReuseDetectingOAuth2AuthorizationService(
            @Qualifier("jdbcOAuth2AuthorizationServiceDelegate")
                    JdbcOAuth2AuthorizationServiceDelegate delegate,
            RefreshTokenReuseAuditService reuseAudit,
            StringRedisTemplate redis) {
        this.delegate = delegate;
        this.reuseAudit = reuseAudit;
        this.redis = redis;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        retirePreviousRefreshToken(authorization);
        delegate.save(authorization);
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        String presented = RefreshTokenReuseContext.getPresentedRefreshToken();
        if (presented != null
                && authorization != null
                && authorization.getRefreshToken() != null
                && authorization.getRefreshToken().getToken() != null) {
            String stored = authorization.getRefreshToken().getToken().getTokenValue();
            if (!presented.equals(stored)) {
                reuseAudit.recordReuse(authorization, presented);
            }
        }
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        OAuth2Authorization found = delegate.findByToken(token, tokenType);
        if (found == null && token != null && (tokenType == null || OAuth2TokenType.REFRESH_TOKEN.equals(tokenType))) {
            revokeIfRetired(token);
        }
        return found;
    }

    /** Remembers the refresh token this save replaces, so a later replay of it can be detected. */
    private void retirePreviousRefreshToken(OAuth2Authorization next) {
        if (next == null || next.getId() == null) {
            return;
        }
        OAuth2Authorization.Token<OAuth2RefreshToken> nextRefresh = next.getRefreshToken();
        if (nextRefresh == null) {
            return;
        }
        OAuth2Authorization previous = delegate.findById(next.getId());
        if (previous == null || previous.getRefreshToken() == null) {
            return;
        }
        OAuth2RefreshToken old = previous.getRefreshToken().getToken();
        if (old == null || old.getTokenValue().equals(nextRefresh.getToken().getTokenValue())) {
            return;
        }
        Duration ttl = MAX_RETIRED_TTL;
        if (old.getExpiresAt() != null) {
            Duration remaining = Duration.between(Instant.now(), old.getExpiresAt());
            if (remaining.isNegative() || remaining.isZero()) {
                return;
            }
            ttl = remaining.compareTo(MAX_RETIRED_TTL) < 0 ? remaining : MAX_RETIRED_TTL;
        }
        redis.opsForValue().set(retiredKey(old.getTokenValue()), next.getId(), ttl);
    }

    private void revokeIfRetired(String presentedToken) {
        String key = retiredKey(presentedToken);
        String authorizationId = redis.opsForValue().get(key);
        if (authorizationId == null) {
            return;
        }
        redis.delete(key);
        OAuth2Authorization family = delegate.findById(authorizationId);
        if (family == null) {
            return;
        }
        log.warn(
                "Refresh token reuse detected for client {} principal {}; revoking the authorization",
                family.getRegisteredClientId(),
                family.getPrincipalName());
        reuseAudit.recordReuse(family, presentedToken);
        delegate.remove(family);
    }

    static String retiredKey(String tokenValue) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(tokenValue.getBytes(StandardCharsets.UTF_8));
            return RETIRED_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    /** JDBC delegate; registered as a bean in {@link OAuth2AuthorizationServerPersistenceConfig}. */
    public static class JdbcOAuth2AuthorizationServiceDelegate
            extends org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService {
        public JdbcOAuth2AuthorizationServiceDelegate(
                org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
                org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
                        registeredClientRepository) {
            super(jdbcTemplate, registeredClientRepository);
        }
    }
}
