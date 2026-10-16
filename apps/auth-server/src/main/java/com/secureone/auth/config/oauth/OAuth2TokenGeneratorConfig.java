package com.secureone.auth.config.oauth;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

@Configuration
public class OAuth2TokenGeneratorConfig {

    @Bean
    OAuth2TokenGenerator<? extends OAuth2Token> oauth2TokenGenerator(
            OAuth2SigningKeyService signingKeys,
            OAuth2TokenCustomizer<JwtEncodingContext> jwtEncodingContextCustomizer) {
        // Sign with the current key only; JWKS may also publish the previous key during a rotation.
        JwtGenerator jwtGenerator = new JwtGenerator(new NimbusJwtEncoder(signingKeys.signingJwkSource()));
        jwtGenerator.setJwtCustomizer(jwtEncodingContextCustomizer);
        OAuth2AccessTokenGenerator accessTokenGenerator = new OAuth2AccessTokenGenerator();
        OAuth2RefreshTokenGenerator defaultRefreshGenerator = new OAuth2RefreshTokenGenerator();
        OAuth2TokenGenerator<OAuth2RefreshToken> refreshTokenGenerator =
                new PublicClientRefreshTokenGenerator(defaultRefreshGenerator);
        return new DelegatingOAuth2TokenGenerator(jwtGenerator, accessTokenGenerator, refreshTokenGenerator);
    }
}
