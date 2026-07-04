package com.secureone.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class SecureOneEnvironmentPropertiesTest {

    @Test
    void parsesCommaSeparatedCorsOrigins() {
        SecureOneEnvironmentProperties props = new SecureOneEnvironmentProperties();
        props.setCorsAllowedOrigins("https://app.example.com, https://admin.example.com");
        assertEquals(2, props.getCorsAllowedOrigins().size());
        assertEquals("https://app.example.com", props.getCorsAllowedOrigins().get(0));
    }

    @Test
    void recognizesProductionEnvironment() {
        SecureOneEnvironmentProperties props = new SecureOneEnvironmentProperties();
        props.setEnvironment("production");
        assertFalse(props.isDevelopment());
    }
}
