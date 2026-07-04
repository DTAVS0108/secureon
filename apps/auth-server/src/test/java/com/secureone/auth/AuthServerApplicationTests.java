package com.secureone.auth;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.secureone.auth.config.SecureOneEnvironmentProperties;
import com.secureone.auth.web.InfoController;
import org.junit.jupiter.api.Test;

/**
 * Lightweight unit test so the build is green without external infrastructure.
 * Full Spring context + Flyway integration tests (Testcontainers) arrive with Phase 1.
 */
class AuthServerApplicationTests {

    @Test
    void infoEndpointReturnsStatus() {
        SecureOneEnvironmentProperties env = new SecureOneEnvironmentProperties();
        env.setEnvironment("development");
        InfoController controller = new InfoController("http://localhost:3001", env);
        assertNotNull(controller.info().get("status"));
        assertNotNull(controller.info().get("environment"));
    }
}
