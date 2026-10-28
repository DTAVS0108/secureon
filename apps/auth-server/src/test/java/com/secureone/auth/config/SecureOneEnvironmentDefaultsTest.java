package com.secureone.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class SecureOneEnvironmentDefaultsTest {

    private static StandardEnvironment environment(Map<String, Object> properties) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", properties));
        // The same placeholders as application.yml.
        env.getPropertySources().addLast(new MapPropertySource("yaml", Map.of(
                "secureone.admin.mfa-required",
                "${SECUREONE_ADMIN_MFA_REQUIRED:${secureone.defaults.admin-mfa-required:false}}",
                "secureone.swagger-enabled",
                "${SECUREONE_SWAGGER_ENABLED:${secureone.defaults.swagger-enabled:true}}")));
        new SecureOneEnvironmentDefaults().postProcessEnvironment(env, null);
        return env;
    }

    @Test
    void productionRequiresConsoleMfaAndHidesApiDocs() {
        StandardEnvironment env = environment(Map.of("secureone.environment", "production"));
        assertEquals("true", env.getProperty("secureone.admin.mfa-required"));
        assertEquals("false", env.getProperty("secureone.swagger-enabled"));
    }

    @Test
    void developmentDoesNotRequireConsoleMfaAndShowsApiDocs() {
        StandardEnvironment env = environment(Map.of("secureone.environment", "development"));
        assertEquals("false", env.getProperty("secureone.admin.mfa-required"));
        assertEquals("true", env.getProperty("secureone.swagger-enabled"));
    }

    @Test
    void explicitSettingsWin() {
        StandardEnvironment env = environment(Map.of(
                "secureone.environment", "production",
                "SECUREONE_ADMIN_MFA_REQUIRED", "false",
                "SECUREONE_SWAGGER_ENABLED", "true"));
        assertEquals("false", env.getProperty("secureone.admin.mfa-required"));
        assertEquals("true", env.getProperty("secureone.swagger-enabled"));
    }
}
