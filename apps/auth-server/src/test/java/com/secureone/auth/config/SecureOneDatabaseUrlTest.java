package com.secureone.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class SecureOneDatabaseUrlTest {

    private static StandardEnvironment environment(Map<String, Object> properties) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", properties));
        new SecureOneDatabaseUrl().postProcessEnvironment(env, null);
        return env;
    }

    @Test
    void providerUrlBecomesJdbcWithItsCredentials() {
        StandardEnvironment env = environment(Map.of(
                "SECUREONE_DB_URL", "postgresql://so_user:p%40ss%2Fw0rd@dpg-abc.internal:5432/secureone_x1y2"));
        assertEquals("jdbc:postgresql://dpg-abc.internal:5432/secureone_x1y2?currentSchema=platform",
                env.getProperty("SECUREONE_DB_URL"));
        assertEquals("so_user", env.getProperty("SECUREONE_DB_USERNAME"));
        assertEquals("p@ss/w0rd", env.getProperty("SECUREONE_DB_PASSWORD"));
    }

    @Test
    void keepsQueryParametersAndAnExplicitSchema() {
        assertEquals("jdbc:postgresql://db.example.com:25060/app?sslmode=require&currentSchema=platform",
                SecureOneDatabaseUrl.convert("postgres://u:p@db.example.com:25060/app?sslmode=require").jdbcUrl());
        assertEquals("jdbc:postgresql://db/app?currentSchema=other",
                SecureOneDatabaseUrl.convert("postgres://db/app?currentSchema=other").jdbcUrl());
        assertEquals("jdbc:postgresql://db/secureone?currentSchema=platform",
                SecureOneDatabaseUrl.convert("postgres://db").jdbcUrl());
    }

    @Test
    void explicitCredentialsWin() {
        StandardEnvironment env = environment(Map.of(
                "SECUREONE_DB_URL", "postgres://url_user:url_pass@db:5432/secureone",
                "SECUREONE_DB_USERNAME", "secureone",
                "SECUREONE_DB_PASSWORD", "chosen"));
        assertEquals("secureone", env.getProperty("SECUREONE_DB_USERNAME"));
        assertEquals("chosen", env.getProperty("SECUREONE_DB_PASSWORD"));
    }

    @Test
    void jdbcUrlIsLeftAlone() {
        String jdbc = "jdbc:postgresql://postgres:5432/secureone?currentSchema=platform";
        StandardEnvironment env = environment(Map.of("SECUREONE_DB_URL", jdbc));
        assertEquals(jdbc, env.getProperty("SECUREONE_DB_URL"));
        assertNull(env.getPropertySources().get(SecureOneDatabaseUrl.SOURCE_NAME));
        assertNull(SecureOneDatabaseUrl.convert(null));
    }

    @Test
    void urlWithoutHostIsRefused() {
        assertThrows(IllegalStateException.class, () -> SecureOneDatabaseUrl.convert("postgres:///secureone"));
    }
}
