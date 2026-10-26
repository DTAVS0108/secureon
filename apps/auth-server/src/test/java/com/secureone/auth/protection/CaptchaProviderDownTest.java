package com.secureone.auth.protection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.secureone.auth.notify.SecretProtector;
import com.secureone.auth.platform.PlatformSettingsService;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class CaptchaProviderDownTest {

    private static AttackProtectionService service(boolean failOpen) {
        Map<String, Object> bot = new HashMap<>();
        bot.put("provider", "turnstile");
        bot.put("siteKey", "site");
        bot.put("secretEncrypted", "encrypted");
        bot.put("failOpen", failOpen);
        PlatformSettingsService settings = mock(PlatformSettingsService.class);
        when(settings.get(AttackProtectionService.SETTINGS_KEY)).thenReturn(Map.of("botProtection", bot));
        SecretProtector secrets = mock(SecretProtector.class);
        when(secrets.decrypt(anyString())).thenReturn("secret");
        AttackProtectionService service = new AttackProtectionService(settings, secrets, mock(StringRedisTemplate.class));
        // Nothing listens on port 9: the provider cannot be reached.
        service.verifyUrls = Map.of("turnstile", "http://127.0.0.1:9/siteverify");
        return service;
    }

    @Test
    void providerDownRefusesByDefault() {
        assertThat(service(false).verify("token", "198.51.100.1")).isFalse();
    }

    @Test
    void providerDownLetsThroughWhenFailOpenIsOn() {
        assertThat(service(true).verify("token", "198.51.100.1")).isTrue();
    }

    @Test
    void aMissingTokenIsRefusedEvenWithFailOpen() {
        assertThat(service(true).verify("", "198.51.100.1")).isFalse();
    }
}
