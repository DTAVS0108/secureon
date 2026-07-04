package com.secureone.auth.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class WebConfig {

    private final SecureOneEnvironmentProperties environment;

    public WebConfig(SecureOneEnvironmentProperties environment) {
        this.environment = environment;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        List<String> origins = environment.getCorsAllowedOrigins();
        if (origins.isEmpty()) {
            config.addAllowedOriginPattern("http://localhost:*");
            config.addAllowedOriginPattern("http://127.0.0.1:*");
            config.addAllowedOriginPattern("https://localhost:*");
            config.addAllowedOriginPattern("https://127.0.0.1:*");
        } else {
            origins.forEach(config::addAllowedOrigin);
        }
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
