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
     * Keys published in JWKS and accepted when verifying: the signing key; the next key (published ahead, so
     * apps already have it when a rotation makes it the signing key); the previous key after a rotation (so
     * tokens signed before it stay valid until retired); and {@code SECUREONE_JWK_PREVIOUS_PRIVATE_KEY_PEM}.
     */
    public java.util.List<RSAKey> verificationKeys() {
        java.util.List<RSAKey> keys = new java.util.ArrayList<>();
        Snapshot snap = snapshot();
        keys.add(snap.current());
        for (RSAKey extra : new RSAKey[] {snap.next(), snap.previous()}) {
            if (extra != null && keys.stream().noneMatch(k -> k.getKeyID().equals(extra.getKeyID()))) {
                keys.add(extra.toPublicJWK());
            }
        }
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

    /**
     * Only the current key: what tokens are signed with (an encoder must see exactly one RS256 key). Read on
     * each use, so a rotation (here or on another server) takes effect without a restart.
     */
    public com.nimbusds.jose.jwk.source.JWKSource<com.nimbusds.jose.proc.SecurityContext> signingJwkSource() {
        return (selector, context) -> selector.select(new com.nimbusds.jose.jwk.JWKSet(loadOrCreateSigningKey()));
    }

    public RSAKey loadOrCreateSigningKey() {
        return snapshot().current();
    }

    /** The keys in use, re-read every {@link #REFRESH} (every server sees a rotation within that time). */
    record Snapshot(RSAKey current, RSAKey next, RSAKey previous, Instant previousRetiredAt, Instant loadedAt) {}

    static final java.time.Duration REFRESH = java.time.Duration.ofSeconds(30);
    private volatile Snapshot snapshot;

    private Snapshot snapshot() {
        Snapshot s = snapshot;
        boolean fixed = !envPrivateKeyPem.isBlank();
        if (s != null && (fixed || s.loadedAt().plus(REFRESH).isAfter(Instant.now()))) {
            return s;
        }
        synchronized (this) {
            s = snapshot;
            if (s != null && (fixed || s.loadedAt().plus(REFRESH).isAfter(Instant.now()))) {
                return s;
            }
            snapshot = load();
            return snapshot;
        }
    }

    private Snapshot load() {
        RSAKey current = resolveSigningKey();
        if (!envPrivateKeyPem.isBlank()) {
            return new Snapshot(current, null, null, null, Instant.now());
        }
        Map<String, Object> stored = platformSettings.get(SETTING_KEY);
        RSAKey next = readEntryPrivate(stored.get(NEXT_FIELD));
        if (next == null) {
            next = ensureNextKey();
        }
        RSAKey previous = null;
        Instant retiredAt = null;
        if (stored.get(PREVIOUS_FIELD) instanceof Map<?, ?> prev) {
            previous = parsePublicPem(stringValue(prev.get(PUBLIC_PEM_FIELD)), stringValue(prev.get(KEY_ID_FIELD)));
            retiredAt = parseInstant(prev.get("retiredAt"));
        }
        return new Snapshot(current, next, previous, retiredAt, Instant.now());
    }

    private static final String NEXT_FIELD = "next";
    private static final String PREVIOUS_FIELD = "previous";

    /** Publishes the next key ahead of time (once; a later rotation makes it the signing key). */
    private RSAKey ensureNextKey() {
        try {
            RSAKey next = generateRsaKey();
            Map<String, Object> stored = new LinkedHashMap<>(platformSettings.get(SETTING_KEY));
            if (stored.get(NEXT_FIELD) != null) {
                return readEntryPrivate(stored.get(NEXT_FIELD)); // another server just did it
            }
            stored.put(NEXT_FIELD, privateEntry(next));
            platformSettings.saveMap(SETTING_KEY, stored);
            return next;
        } catch (RuntimeException e) {
            return null; // not fatal: the next key is published on a later load
        }
    }

    /**
     * Rotation: the next key (already published) starts signing; the current one stays published as the previous
     * key, so tokens it signed still verify until it is retired; a new next key is published. Keys from
     * {@code SECUREONE_JWK_PRIVATE_KEY_PEM} are rotated in the environment instead.
     */
    public synchronized Map<String, Object> rotate() {
        if (!envPrivateKeyPem.isBlank()) {
            throw new IllegalArgumentException("The signing key comes from SECUREONE_JWK_PRIVATE_KEY_PEM: rotate it there"
                    + " (set the new key, and the old one as SECUREONE_JWK_PREVIOUS_PRIVATE_KEY_PEM).");
        }
        RSAKey current = readStored();
        Map<String, Object> stored = new LinkedHashMap<>(platformSettings.get(SETTING_KEY));
        RSAKey promoted = readEntryPrivate(stored.get(NEXT_FIELD));
        if (promoted == null) {
            promoted = generateRsaKey();
        }
        try {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put(KEY_ID_FIELD, promoted.getKeyID());
            out.put(PRIVATE_ENCRYPTED_FIELD, secrets.encrypt(toPem(promoted.toRSAPrivateKey().getEncoded(), "PRIVATE KEY")));
            out.put(PUBLIC_PEM_FIELD, toPem(promoted.toRSAPublicKey().getEncoded(), "PUBLIC KEY"));
            out.put("createdAt", Instant.now().toString());
            if (current != null) {
                Map<String, Object> previous = new LinkedHashMap<>();
                previous.put(KEY_ID_FIELD, current.getKeyID());
                previous.put(PUBLIC_PEM_FIELD, toPem(current.toRSAPublicKey().getEncoded(), "PUBLIC KEY"));
                previous.put("retiredAt", Instant.now().toString());
                out.put(PREVIOUS_FIELD, previous);
            }
            out.put(NEXT_FIELD, privateEntry(generateRsaKey()));
            platformSettings.saveMap(SETTING_KEY, out);
        } catch (JOSEException ex) {
            throw new IllegalStateException("Failed to rotate the signing key", ex);
        }
        snapshot = null;
        return status();
    }

    /** Stops accepting tokens signed with the previous key (once they have all expired). */
    public synchronized Map<String, Object> retirePrevious() {
        Map<String, Object> stored = new LinkedHashMap<>(platformSettings.get(SETTING_KEY));
        if (stored.remove(PREVIOUS_FIELD) == null) {
            throw new IllegalArgumentException("There is no previous signing key to retire.");
        }
        platformSettings.saveMap(SETTING_KEY, stored);
        snapshot = null;
        return status();
    }

    /** For the console: key IDs and dates (never key material). */
    public Map<String, Object> status() {
        Snapshot snap = snapshot();
        Map<String, Object> stored = envPrivateKeyPem.isBlank() ? platformSettings.get(SETTING_KEY) : Map.of();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", envPrivateKeyPem.isBlank() ? "stored" : "environment");
        out.put("currentKid", snap.current().getKeyID());
        out.put("currentSince", stringValue(stored.get("createdAt")));
        out.put("nextKid", snap.next() != null ? snap.next().getKeyID() : null);
        out.put("previousKid", snap.previous() != null ? snap.previous().getKeyID() : null);
        out.put("previousRetiredAt", snap.previousRetiredAt() != null ? snap.previousRetiredAt().toString() : null);
        return out;
    }

    private Map<String, Object> privateEntry(RSAKey key) {
        try {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put(KEY_ID_FIELD, key.getKeyID());
            entry.put(PRIVATE_ENCRYPTED_FIELD, secrets.encrypt(toPem(key.toRSAPrivateKey().getEncoded(), "PRIVATE KEY")));
            entry.put("createdAt", Instant.now().toString());
            return entry;
        } catch (JOSEException ex) {
            throw new IllegalStateException("Failed to store a signing key", ex);
        }
    }

    private RSAKey readEntryPrivate(Object raw) {
        if (!(raw instanceof Map<?, ?> entry)) {
            return null;
        }
        String pem = secrets.decrypt(stringValue(entry.get(PRIVATE_ENCRYPTED_FIELD)));
        return pem.isBlank() ? null : parsePrivateKeyPem(pem, stringValue(entry.get(KEY_ID_FIELD)));
    }

    private static RSAKey parsePublicPem(String pem, String kid) {
        if (pem.isBlank() || kid.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(normalizePem(pem)
                    .replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", ""));
            RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new java.security.spec.X509EncodedKeySpec(bytes));
            return new RSAKey.Builder(key).keyID(kid).build();
        } catch (Exception e) {
            return null;
        }
    }

    private static Instant parseInstant(Object raw) {
        try {
            return raw == null ? null : Instant.parse(raw.toString());
        } catch (RuntimeException e) {
            return null;
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
        // None configured: create one and store it encrypted (in production with the key kept outside the database,
        // which EncryptionKeySource requires). Several replicas may start at once: one generates it, the others read it.
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
