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

    private static final String PRIVATE_ENCRYPTED_FIELD = "privateKeyEncrypted";

    private final PlatformSettingsService platformSettings;
    private final com.secureone.auth.notify.SecretProtector secrets;
    private final com.secureone.auth.platform.ClusterInitLock initLock;
    private final SecureOneEnvironmentProperties environment;
    private final String envPrivateKeyPem;
    private final String envPreviousPrivateKeyPem;
    private volatile RSAKey cachedKey;

    public OAuth2SigningKeyService(
            PlatformSettingsService platformSettings,
            com.secureone.auth.notify.SecretProtector secrets,
            com.secureone.auth.platform.ClusterInitLock initLock,
            SecureOneEnvironmentProperties environment,
            @Value("${SECUREONE_JWK_PRIVATE_KEY_PEM:}") String envPrivateKeyPem,
            @Value("${SECUREONE_JWK_PREVIOUS_PRIVATE_KEY_PEM:}") String envPreviousPrivateKeyPem) {
        this.platformSettings = platformSettings;
        this.secrets = secrets;
        this.initLock = initLock;
        this.environment = environment;
        this.envPrivateKeyPem = normalizePem(envPrivateKeyPem);
        this.envPreviousPrivateKeyPem = normalizePem(envPreviousPrivateKeyPem);
    }

    /**
     * Keys published in JWKS and accepted when verifying: the signing key plus, during a rotation, the
     * previous key's public part ({@code SECUREONE_JWK_PREVIOUS_PRIVATE_KEY_PEM}, same kid as when it was
     * current), so tokens signed before the switch stay valid until they expire.
     */
    public java.util.List<RSAKey> verificationKeys() {
        java.util.List<RSAKey> keys = new java.util.ArrayList<>();
        keys.add(loadOrCreateSigningKey());
        if (!envPreviousPrivateKeyPem.isBlank()) {
            RSAKey previous = parsePrivateKeyPem(envPreviousPrivateKeyPem, stableKidFromPem(envPreviousPrivateKeyPem));
            // Rotating away from a key SecureOne generated: it was published under the kid stored with it.
            RSAKey stored = readStored();
            if (stored != null && sameKey(stored, previous)) {
                previous = stored;
            }
            if (!previous.getKeyID().equals(keys.get(0).getKeyID())) {
                keys.add(previous.toPublicJWK());
            }
        }
        return keys;
    }

    private static boolean sameKey(RSAKey a, RSAKey b) {
        return a.getModulus().equals(b.getModulus()) && a.getPublicExponent().equals(b.getPublicExponent());
    }

    /** Only the current key: what tokens are signed with (an encoder must see exactly one RS256 key). */
    public com.nimbusds.jose.jwk.source.JWKSource<com.nimbusds.jose.proc.SecurityContext> signingJwkSource() {
        return new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(loadOrCreateSigningKey()));
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
        RSAKey stored = readStored();
        if (stored != null) {
            return stored;
        }
        if (environment.isProduction()) {
            throw new IllegalStateException(
                    "No OAuth2 signing key configured for production. Set SECUREONE_JWK_PRIVATE_KEY_PEM "
                            + "or seed platform_setting key '" + SETTING_KEY + "'.");
        }
        // Several replicas may start at once: one generates the key, the others read it.
        return initLock.readOrCreate(SETTING_KEY, this::readStored, () -> {
            RSAKey generated = generateRsaKey();
            persistKey(generated);
            return generated;
        });
    }

    private RSAKey readStored() {
        Map<String, Object> stored = platformSettings.get(SETTING_KEY);
        String privatePem = secrets.decrypt(stringValue(stored.get(PRIVATE_ENCRYPTED_FIELD)));
        String kid = stringValue(stored.get(KEY_ID_FIELD));
        String legacyPlaintext = stringValue(stored.get(PRIVATE_PEM_FIELD));
        if (privatePem.isBlank() && !legacyPlaintext.isBlank()) {
            // Stored before keys were encrypted at rest: keep the key (issued tokens stay valid), encrypt it.
            privatePem = legacyPlaintext;
            Map<String, Object> upgraded = new LinkedHashMap<>(stored);
            upgraded.remove(PRIVATE_PEM_FIELD);
            upgraded.put(PRIVATE_ENCRYPTED_FIELD, secrets.encrypt(legacyPlaintext));
            platformSettings.saveMap(SETTING_KEY, upgraded);
        }
        if (privatePem.isBlank()) {
            return null;
        }
        return parsePrivateKeyPem(privatePem, kid.isBlank() ? stableKidFromPem(privatePem) : kid);
    }

    private void persistKey(RSAKey key) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put(KEY_ID_FIELD, key.getKeyID());
            payload.put(PRIVATE_ENCRYPTED_FIELD, secrets.encrypt(toPem(key.toRSAPrivateKey().getEncoded(), "PRIVATE KEY")));
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
        // One-line env files (docker --env-file) carry PEM line breaks as a literal backslash-n.
        return pem == null ? "" : pem.replace("\\n", "\n").trim();
    }

    private static String stringValue(Object raw) {
        return raw == null ? "" : raw.toString().trim();
    }

    private static String stableKidFromPem(String pem) {
        return UUID.nameUUIDFromBytes(normalizePem(pem).getBytes()).toString();
    }
}
