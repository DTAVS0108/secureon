package com.secureone.auth.config.oauth;

import com.secureone.auth.application.TokenPolicyDefaults;
import java.time.Duration;
import java.util.Map;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

/** Maps platform/application token policy JSON to Spring Authorization Server {@link TokenSettings}. */
public final class TokenSettingsMapper {

    private TokenSettingsMapper() {}

    public static TokenSettings toTokenSettings(Map<String, Object> policy) {
        Map<String, Object> resolved = policy != null ? policy : TokenPolicyDefaults.platformDefaults();
        boolean refreshEnabled = bool(resolved, "refreshTokensEnabled", true);
        boolean reuseRefresh = bool(resolved, "reuseRefreshTokens", false);
        if (resolved.containsKey("rotateRefreshTokens")) {
            reuseRefresh = !bool(resolved, "rotateRefreshTokens", true);
        }
        // Reuse detection requires one-time refresh tokens (rotation).
        if (bool(resolved, "refreshTokenReuseDetection", true)) {
            reuseRefresh = false;
        }

        TokenSettings.Builder builder = TokenSettings.builder()
                .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                .accessTokenTimeToLive(seconds(resolved, "accessTokenTtlSeconds", 3600))
                .authorizationCodeTimeToLive(seconds(resolved, "authorizationCodeTtlSeconds", 300))
                .deviceCodeTimeToLive(seconds(resolved, "deviceCodeTtlSeconds", 600))
                .reuseRefreshTokens(reuseRefresh);

        if (refreshEnabled) {
            builder.refreshTokenTimeToLive(seconds(resolved, "refreshTokenTtlSeconds", 604_800));
        }
        return builder.build();
    }

    private static Duration seconds(Map<String, Object> policy, String key, int fallback) {
        Object raw = policy.get(key);
        int value;
        if (raw instanceof Number number) {
            value = number.intValue();
        } else {
            value = fallback;
        }
        return Duration.ofSeconds(Math.max(0, value));
    }

    private static boolean bool(Map<String, Object> policy, String key, boolean fallback) {
        Object raw = policy.get(key);
        return raw instanceof Boolean b ? b : fallback;
    }
}
