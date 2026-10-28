package com.secureone.auth.config;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Defaults that follow {@code SECUREONE_ENV}, so the same image is safe in production and convenient in
 * development without extra settings. Production: console MFA required, API docs (Swagger) off.
 * Development and staging: the opposite. An explicit {@code SECUREONE_ADMIN_MFA_REQUIRED} or
 * {@code SECUREONE_SWAGGER_ENABLED} always wins; the YAML files read these as
 * {@code ${SECUREONE_...:${secureone.defaults....}}}.
 */
public class SecureOneEnvironmentDefaults implements EnvironmentPostProcessor, Ordered {

    static final String SOURCE_NAME = "secureoneEnvironmentDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean production = "production".equalsIgnoreCase(
                environment.getProperty("secureone.environment", "development").trim());
        environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, Map.of(
                "secureone.defaults.admin-mfa-required", production,
                "secureone.defaults.swagger-enabled", !production)));
    }

    /** After the configuration files are loaded, so {@code secureone.environment} is known. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
