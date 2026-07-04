package com.secureone.auth.config.oauth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;

/**
 * JDBC-backed OAuth2 authorization store. {@link OAuth2AuthorizationService} primary bean is
 * {@link ReuseDetectingOAuth2AuthorizationService}.
 */
@Configuration
public class OAuth2AuthorizationServerPersistenceConfig {

    @Bean
    ReuseDetectingOAuth2AuthorizationService.JdbcOAuth2AuthorizationServiceDelegate
            jdbcOAuth2AuthorizationServiceDelegate(
                    JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
        return new ReuseDetectingOAuth2AuthorizationService.JdbcOAuth2AuthorizationServiceDelegate(
                jdbcTemplate, registeredClientRepository);
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(
            JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }
}
