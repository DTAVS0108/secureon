package com.secureone.auth.config.oauth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import com.secureone.auth.config.SecureOneEnvironmentProperties;
import com.secureone.auth.platform.PlatformSettingsService;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Loads or bootstraps the RSA key used to sign OAuth2/OIDC JWTs. */
@Service
public class OAuth2SigningKeyService {

    private static final String SETTING_KEY = "oauth2_signing_jwk";
    private static final String KEY_ID_FIELD = "kid";
    private static final String PRIVATE_PEM_FIELD = "privateKeyPem";
    private static final String PUBLIC_PEM_FIELD = "publicKeyPem";

    private final PlatformSettingsService platformSettings;
    private final SecureOneEnvironmentProperties environment;
    private final String envPrivateKeyPem;
    private volatile RSAKey cachedKey;

    public OAuth2SigningKeyService(
            PlatformSettingsService platformSettings,
            SecureOneEnvironmentProperties environment,
            @Value("${SECUREONE_JWK_PRIVATE_KEY_PEM:}") String envPrivateKeyPem) {
        this.platformSettings = platformSettings;
        this.environment = environment;
        this.envPrivateKeyPem = normalizePem(envPrivateKeyPem);
    }

    public RSAKey loadOrCreateSigningKey() {
        RSAKey key = cachedKey;
        if (key != null) {
            return key;
        }
        synchronized (this) {
            if (cachedKey != null) {
                return cachedKey;
            }
            cachedKey = resolveSigningKey();
            return cachedKey;
        }
    }

    private RSAKey resolveSigningKey() {
        if (!envPrivateKeyPem.isBlank()) {
            return parsePrivateKeyPem(envPrivateKeyPem, stableKidFromPem(envPrivateKeyPem));
        }
        Map<String, Object> stored = platformSettings.get(SETTING_KEY);
        String privatePem = stringValue(stored.get(PRIVATE_PEM_FIELD));
        String kid = stringValue(stored.get(KEY_ID_FIELD));
        if (!privatePem.isBlank()) {
            if (kid.isBlank()) {
                kid = stableKidFromPem(privatePem);
            }
            return parsePrivateKeyPem(privatePem, kid);
        }
        if (environment.isProduction()) {
            throw new IllegalStateException(
                    "No OAuth2 signing key configured for production. Set SECUREONE_JWK_PRIVATE_KEY_PEM "
                            + "or seed platform_setting key '" + SETTING_KEY + "'.");
        }
        RSAKey generated = generateRsaKey();
        persistKey(generated);
        return generated;
    }

    private void persistKey(RSAKey key) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put(KEY_ID_FIELD, key.getKeyID());
            payload.put(PRIVATE_PEM_FIELD, toPem(key.toRSAPrivateKey().getEncoded(), "PRIVATE KEY"));
            payload.put(PUBLIC_PEM_FIELD, toPem(key.toRSAPublicKey().getEncoded(), "PUBLIC KEY"));
            payload.put("createdAt", Instant.now().toString());
            platformSettings.saveMap(SETTING_KEY, payload);
        } catch (JOSEException ex) {
            throw new IllegalStateException("Failed to persist OAuth2 signing key", ex);
        }
    }

    private static RSAKey generateRsaKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            String kid = UUID.randomUUID().toString();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(kid)
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate RSA signing key", ex);
        }
    }

    private static RSAKey parsePrivateKeyPem(String pem, String kid) {
        try {
            byte[] keyBytes = decodePem(pem);
            RSAPrivateKey privateKey =
                    (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            RSAPublicKey publicKey = derivePublicKey(privateKey);
            return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(kid).build();
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid SECUREONE_JWK_PRIVATE_KEY_PEM", ex);
        }
    }

    private static RSAPublicKey derivePublicKey(RSAPrivateKey privateKey) throws Exception {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        // Reconstruct public key from modulus and public exponent stored in CRT private key.
        java.security.spec.RSAPrivateCrtKeySpec spec =
                factory.getKeySpec(privateKey, java.security.spec.RSAPrivateCrtKeySpec.class);
        java.security.spec.RSAPublicKeySpec publicSpec =
                new java.security.spec.RSAPublicKeySpec(spec.getModulus(), spec.getPublicExponent());
        return (RSAPublicKey) factory.generatePublic(publicSpec);
    }

    private static byte[] decodePem(String pem) {
        String normalized = normalizePem(pem)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }

    private static String toPem(byte[] encoded, String label) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded);
        return "-----BEGIN " + label + "-----\n" + base64 + "\n-----END " + label + "-----";
    }

    private static String normalizePem(String pem) {
        return pem == null ? "" : pem.trim();
    }

    private static String stringValue(Object raw) {
        return raw == null ? "" : raw.toString().trim();
    }

    private static String stableKidFromPem(String pem) {
        return UUID.nameUUIDFromBytes(normalizePem(pem).getBytes()).toString();
    }
}
