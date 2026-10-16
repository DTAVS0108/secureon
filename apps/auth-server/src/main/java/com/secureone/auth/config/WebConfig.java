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
    private final RegisteredClientOrigins registeredOrigins;

    public WebConfig(
            SecureOneEnvironmentProperties environment,
            @org.springframework.context.annotation.Lazy RegisteredClientOrigins registeredOrigins) {
        this.environment = environment;
        this.registeredOrigins = registeredOrigins;
    }

    /** SAML endpoints the IdP's page posts to: assertion consumer and single logout. */
    static final java.util.List<String> SAML_BROWSER_POST_PREFIXES =
            java.util.List.of("/api/v1/auth/federation/saml/acs/", "/api/v1/auth/federation/saml/slo/");

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
        // The SAML assertion consumer and single-logout endpoints receive top-level form POSTs from the
        // IdP's page (any origin, or "null" when the IdP sends no referrer). They are not CORS requests;
        // the SAML signatures (and InResponseTo / RelayState) are what authenticate them.
        return request -> {
            if (SAML_BROWSER_POST_PREFIXES.stream().anyMatch(request.getRequestURI()::startsWith)) {
                return null;
            }
            CorsConfiguration base = source.getCorsConfiguration(request);
            String origin = request.getHeader("Origin");
            // Origins of registered apps (their redirect URIs) are allowed without an env change.
            if (base != null && origin != null && base.checkOrigin(origin) == null
                    && registeredOrigins.isRegistered(origin)) {
                CorsConfiguration withApp = new CorsConfiguration(base);
                withApp.addAllowedOrigin(origin);
                return withApp;
            }
            return base;
        };
    }
}
