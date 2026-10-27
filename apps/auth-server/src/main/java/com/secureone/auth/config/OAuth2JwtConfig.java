package com.secureone.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.secureone.auth.config.oauth.OAuth2SigningKeyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class OAuth2JwtConfig {

    @Bean
    @Primary
    JWKSource<SecurityContext> jwkSource(OAuth2SigningKeyService signingKeys) {
        // Published in JWKS and used for verification: the current key, the next one (published ahead of a
        // rotation) and the previous one (until retired). Read on each use, so a rotation shows up everywhere.
        return (selector, context) -> selector.select(
                new JWKSet(java.util.List.<com.nimbusds.jose.jwk.JWK>copyOf(signingKeys.verificationKeys())));
    }

    @Bean
    @Primary
    JwtDecoder jwtDecoder(
            JWKSource<SecurityContext> jwkSource,
            @Value("${spring.security.oauth2.authorizationserver.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(jwkSource).build();
        OAuth2TokenValidator<Jwt> validator =
                new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer));
        decoder.setJwtValidator(validator);
        return decoder;
    }
}
