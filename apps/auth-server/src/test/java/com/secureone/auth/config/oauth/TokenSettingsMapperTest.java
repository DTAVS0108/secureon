package com.secureone.auth.config.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.secureone.auth.application.TokenPolicyDefaults;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

class TokenSettingsMapperTest {

    @Test
    void mapsAccessTokenTtlFromPolicy() {
        Map<String, Object> policy = TokenPolicyDefaults.platformDefaults();
        policy.put("accessTokenTtlSeconds", 900);

        TokenSettings settings = TokenSettingsMapper.toTokenSettings(policy);

        assertEquals(Duration.ofSeconds(900), settings.getAccessTokenTimeToLive());
        assertEquals(Duration.ofSeconds(300), settings.getAuthorizationCodeTimeToLive());
    }

    @Test
    void omitsCustomRefreshTtlWhenPolicyDisabled() {
        Map<String, Object> policy = TokenPolicyDefaults.platformDefaults();
        policy.put("refreshTokensEnabled", false);
        policy.put("refreshTokenTtlSeconds", 120);

        TokenSettings settings = TokenSettingsMapper.toTokenSettings(policy);

        assertNotEquals(Duration.ofSeconds(120), settings.getRefreshTokenTimeToLive());
    }

    @Test
    void productionDefaultsUseFifteenMinuteAccessTokens() {
        TokenSettings settings = TokenSettingsMapper.toTokenSettings(TokenPolicyDefaults.productionDefaults());
        assertEquals(Duration.ofSeconds(900), settings.getAccessTokenTimeToLive());
        assertFalse(settings.isReuseRefreshTokens());
    }

    @Test
    void reuseDetectionForcesRefreshRotation() {
        Map<String, Object> policy = TokenPolicyDefaults.platformDefaults();
        policy.put("refreshTokenReuseDetection", true);
        policy.put("reuseRefreshTokens", true);
        policy.put("rotateRefreshTokens", false);

        TokenSettings settings = TokenSettingsMapper.toTokenSettings(policy);

        assertFalse(settings.isReuseRefreshTokens());
    }
}
