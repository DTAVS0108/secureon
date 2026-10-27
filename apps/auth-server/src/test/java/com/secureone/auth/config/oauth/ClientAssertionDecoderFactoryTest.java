package com.secureone.auth.config.oauth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

class ClientAssertionDecoderFactoryTest {

    private static final String ISSUER = "https://auth.example.com";

    private RSAKey key;
    private RegisteredClient client;

    @BeforeEach
    void setUp() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("app-1").generate();
        client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("billing")
                .clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientSettings(ClientSettings.builder()
                        .setting(ClientAssertionDecoderFactory.JWKS_SETTING, new JWKSet(key.toPublicJWK()).toString())
                        .build())
                .build();
        AuthorizationServerSettings settings = AuthorizationServerSettings.builder().issuer(ISSUER).build();
        AuthorizationServerContextHolder.setContext(new AuthorizationServerContext() {
            @Override
            public String getIssuer() {
                return ISSUER;
            }

            @Override
            public AuthorizationServerSettings getAuthorizationServerSettings() {
                return settings;
            }
        });
    }

    @AfterEach
    void tearDown() {
        AuthorizationServerContextHolder.resetContext();
    }

    private String assertion(RSAKey signingKey, String jti, long lifetimeSeconds) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer("billing").subject("billing").audience(ISSUER + "/oauth2/token")
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(lifetimeSeconds)));
        if (jti != null) {
            claims.jwtID(jti);
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID())
                .type(JOSEObjectType.JWT).build(), claims.build());
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    @Test
    void acceptsASignedAssertionOnceOnly() throws Exception {
        JwtDecoder decoder = new ClientAssertionDecoderFactory(null).createDecoder(client);
        String token = assertion(key, UUID.randomUUID().toString(), 60);
        assertDoesNotThrow(() -> decoder.decode(token));
        JwtException replay = assertThrows(JwtException.class, () -> decoder.decode(token));
        assertTrue(replay.getMessage().contains("already used"), replay.getMessage());
    }

    @Test
    void refusesOtherKeysMissingJtiAndLongLifetimes() throws Exception {
        JwtDecoder decoder = new ClientAssertionDecoderFactory(null).createDecoder(client);
        RSAKey other = new RSAKeyGenerator(2048).keyID("app-1").generate();
        assertThrows(JwtException.class, () -> decoder.decode(assertion(other, UUID.randomUUID().toString(), 60)));
        assertThrows(JwtException.class, () -> decoder.decode(assertion(key, null, 60)));
        assertThrows(JwtException.class, () -> decoder.decode(assertion(key, UUID.randomUUID().toString(), 3600)));
    }

    @Test
    void registeredKeysMustBePublicSigningKeys() throws Exception {
        assertDoesNotThrow(() -> ClientAssertionDecoderFactory.validateJwks(new JWKSet(key.toPublicJWK()).toString()));
        assertThrows(IllegalArgumentException.class,
                () -> ClientAssertionDecoderFactory.validateJwks(new JWKSet(key).toString(false)), "private key");
        assertThrows(IllegalArgumentException.class, () -> ClientAssertionDecoderFactory.validateJwks("{\"keys\": []}"));
        assertThrows(IllegalArgumentException.class, () -> ClientAssertionDecoderFactory.validateJwks("not json"));
    }
}
