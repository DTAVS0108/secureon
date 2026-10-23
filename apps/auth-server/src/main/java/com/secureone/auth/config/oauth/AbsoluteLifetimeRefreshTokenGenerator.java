package com.secureone.auth.config.oauth;

import java.time.Duration;
import java.time.Instant;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

/**
 * Gives refresh tokens an absolute limit. Rotation hands out a new refresh token on every refresh, each with the
 * full idle lifetime, so without a limit an app that keeps refreshing stays signed in for ever. Each new refresh
 * token expires no later than the original sign-in plus the policy's {@code refreshTokenAbsoluteSeconds}; once that
 * has passed, refreshing is refused and the user signs in again.
 */
public final class AbsoluteLifetimeRefreshTokenGenerator implements OAuth2TokenGenerator<OAuth2RefreshToken> {

    private final OAuth2TokenGenerator<OAuth2RefreshToken> delegate;

    public AbsoluteLifetimeRefreshTokenGenerator(OAuth2TokenGenerator<OAuth2RefreshToken> delegate) {
        this.delegate = delegate;
    }

    @Nullable
    @Override
    public OAuth2RefreshToken generate(OAuth2TokenContext context) {
        OAuth2RefreshToken token = delegate.generate(context);
        if (token == null) {
            return null;
        }
        Object setting = context.getRegisteredClient().getTokenSettings().getSetting(TokenSettingsMapper.REFRESH_ABSOLUTE_TTL);
        if (!(setting instanceof Duration absolute)) {
            return token;
        }
        Instant limit = signedInAt(context.getAuthorization(), token.getIssuedAt()).plus(absolute);
        if (!limit.isAfter(Instant.now())) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_GRANT, "This sign-in has reached its maximum lifetime; sign in again.", null));
        }
        if (token.getExpiresAt() == null || token.getExpiresAt().isAfter(limit)) {
            return new OAuth2RefreshToken(token.getTokenValue(), token.getIssuedAt(), limit);
        }
        return token;
    }

    /** When this chain of tokens started: the authorization or device code's issue time, else now (first issue). */
    static Instant signedInAt(@Nullable OAuth2Authorization authorization, Instant fallback) {
        if (authorization != null) {
            OAuth2Authorization.Token<OAuth2AuthorizationCode> code = authorization.getToken(OAuth2AuthorizationCode.class);
            if (code != null && code.getToken().getIssuedAt() != null) {
                return code.getToken().getIssuedAt();
            }
            OAuth2Authorization.Token<org.springframework.security.oauth2.core.OAuth2DeviceCode> device =
                    authorization.getToken(org.springframework.security.oauth2.core.OAuth2DeviceCode.class);
            if (device != null && device.getToken().getIssuedAt() != null) {
                return device.getToken().getIssuedAt();
            }
        }
        return fallback != null ? fallback : Instant.now();
    }
}
