package com.secureone.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.secureone.auth.config.SecureOneEnvironmentProperties;
import com.secureone.auth.web.InfoController;
import java.util.Properties;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.info.BuildProperties;
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
        Properties build = new Properties();
        build.setProperty("version", "1.4.0");
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("buildProperties", new BuildProperties(build));
        InfoController controller =
                new InfoController("http://localhost:3001", env, beans.getBeanProvider(BuildProperties.class));
        assertNotNull(controller.info().get("status"));
        assertNotNull(controller.info().get("environment"));
        assertEquals("1.4.0", controller.info().get("version"));

        InfoController withoutBuildInfo = new InfoController(
                "http://localhost:3001", env, new StaticListableBeanFactory().getBeanProvider(BuildProperties.class));
        assertEquals("development", withoutBuildInfo.info().get("version"));
    }
}
