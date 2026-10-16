package com.secureone.auth.config.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2DeviceCode;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2DeviceCodeAuthenticationToken;

/**
 * Token-endpoint rules for the device_code grant (RFC 8628) on top of Spring's provider:
 * <ul>
 *   <li>a device that polls faster than the advertised interval gets {@code slow_down} (section 3.5);
 *   <li>replaying a device code that was already exchanged revokes the tokens it produced, as for a
 *       replayed authorization code (RFC 6749 section 4.1.2): the replay means the code leaked.
 * </ul>
 */
public final class DeviceCodeGrantGuard implements AuthenticationProvider {

    /** Seconds between polls, as advertised in the device authorization response. */
    public static final int INTERVAL_SECONDS = 5;

    private static final OAuth2TokenType DEVICE_CODE = new OAuth2TokenType(OAuth2ParameterNames.DEVICE_CODE);
    // A poll window slightly shorter than the interval, so a client polling exactly on time is never slowed.
    private static final Duration POLL_WINDOW = Duration.ofSeconds(INTERVAL_SECONDS - 1);

    private final AuthenticationProvider delegate;
    private final OAuth2AuthorizationService authorizations;
    private final StringRedisTemplate redis;

    public DeviceCodeGrantGuard(
            AuthenticationProvider delegate, OAuth2AuthorizationService authorizations, StringRedisTemplate redis) {
        this.delegate = delegate;
        this.authorizations = authorizations;
        this.redis = redis;
    }

    @Override
    public @Nullable Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2DeviceCodeAuthenticationToken grant = (OAuth2DeviceCodeAuthenticationToken) authentication;
        String deviceCode = grant.getDeviceCode();
        OAuth2Authorization authorization = deviceCode == null ? null : authorizations.findByToken(deviceCode, DEVICE_CODE);
        if (authorization != null) {
            OAuth2Authorization.Token<OAuth2DeviceCode> code = authorization.getToken(OAuth2DeviceCode.class);
            OAuth2Authorization.Token<OAuth2AccessToken> issued = authorization.getAccessToken();
            if (code != null && code.isInvalidated() && issued != null) {
                revokeIssuedTokens(authorization);
                throw new OAuth2AuthenticationException(new OAuth2Error(
                        OAuth2ErrorCodes.INVALID_GRANT, "This device code was already used; its tokens are revoked.", null));
            }
            if (code != null && !code.isInvalidated()) {
                Boolean first = redis.opsForValue().setIfAbsent("device:poll:" + sha256(deviceCode), "1", POLL_WINDOW);
                if (Boolean.FALSE.equals(first)) {
                    throw new OAuth2AuthenticationException(new OAuth2Error(
                            "slow_down", "Polling too fast; wait " + INTERVAL_SECONDS + " seconds between requests.", null));
                }
            }
        }
        return delegate.authenticate(authentication);
    }

    private void revokeIssuedTokens(OAuth2Authorization authorization) {
        OAuth2Authorization.Builder builder = OAuth2Authorization.from(authorization);
        OAuth2Authorization.Token<OAuth2AccessToken> access = authorization.getAccessToken();
        if (access != null && !access.isInvalidated()) {
            builder.invalidate(access.getToken());
        }
        OAuth2Authorization.Token<OAuth2RefreshToken> refresh = authorization.getRefreshToken();
        if (refresh != null && !refresh.isInvalidated()) {
            builder.invalidate(refresh.getToken());
        }
        authorizations.save(builder.build());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }
}
