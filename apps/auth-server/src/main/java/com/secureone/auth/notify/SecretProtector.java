package com.secureone.auth.notify;

import com.secureone.auth.platform.PlatformSettingsService;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * AES-GCM encryption for secrets stored in the database (SMTP/SMS credentials, IdP client secrets, the
 * OAuth and SAML signing keys). The key is {@code SECUREONE_ENCRYPTION_KEY} ({@code base64:<32 bytes>}) when set, so
 * a database copy alone cannot decrypt anything; otherwise a key generated into the platform settings
 * (development). Values written under the database key still decrypt after the environment key is set,
 * and are re-encrypted under it when next saved.
 */
@Component
public class SecretProtector {

    private static final String SETTINGS_KEY = "mail_secrets";
    private static final String ENCRYPTION_KEY_FIELD = "encryptionKey";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;

    private final PlatformSettingsService settings;
    private final SecretKey environmentKey;
    private volatile SecretKey cachedKey;

    public SecretProtector(
            PlatformSettingsService settings,
            @org.springframework.beans.factory.annotation.Value("${SECUREONE_ENCRYPTION_KEY:}") String environmentKey) {
        this.settings = settings;
        this.environmentKey = parseEnvironmentKey(environmentKey);
    }

    private static SecretKey parseEnvironmentKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.startsWith("base64:")) {
            value = value.substring("base64:".length());
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("SECUREONE_ENCRYPTION_KEY must be base64 (32 random bytes).");
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("SECUREONE_ENCRYPTION_KEY must decode to 32 bytes (AES-256).");
        }
        return new SecretKeySpec(bytes, "AES");
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    void warnWithoutEnvironmentKey() {
        if (environmentKey == null) {
            org.slf4j.LoggerFactory.getLogger(SecretProtector.class).warn(
                    "SECUREONE_ENCRYPTION_KEY is not set: stored secrets and signing keys are encrypted with a key kept "
                            + "in the database. Set it (base64:<32 random bytes>) in production.");
        }
    }

    /** True when secrets are protected by a key kept outside the database. */
    public boolean usesEnvironmentKey() {
        return environmentKey != null;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return "";
        }
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, environmentKey != null ? environmentKey : resolveKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt secret", ex);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return "";
        }
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            if (combined.length <= GCM_IV_BYTES) {
                throw new IllegalArgumentException("Invalid ciphertext");
            }
            byte[] iv = new byte[GCM_IV_BYTES];
            byte[] encrypted = new byte[combined.length - GCM_IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_BYTES);
            System.arraycopy(combined, GCM_IV_BYTES, encrypted, 0, encrypted.length);
            if (environmentKey != null) {
                try {
                    return decryptWith(environmentKey, iv, encrypted);
                } catch (javax.crypto.AEADBadTagException ex) {
                    // Written before SECUREONE_ENCRYPTION_KEY was set: fall back to the database key.
                }
            }
            return decryptWith(resolveKey(), iv, encrypted);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt secret", ex);
        }
    }

    private static String decryptWith(SecretKey key, byte[] iv, byte[] encrypted) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private SecretKey resolveKey() {
        SecretKey key = cachedKey;
        if (key != null) {
            return key;
        }
        synchronized (this) {
            if (cachedKey != null) {
                return cachedKey;
            }
            Map<String, Object> secrets = settings.get(SETTINGS_KEY);
            Object raw = secrets.get(ENCRYPTION_KEY_FIELD);
            if (raw == null || raw.toString().isBlank()) {
                byte[] keyBytes = new byte[32];
                new SecureRandom().nextBytes(keyBytes);
                String encoded = Base64.getEncoder().encodeToString(keyBytes);
                Map<String, Object> updated = new HashMap<>(secrets);
                updated.put(ENCRYPTION_KEY_FIELD, encoded);
                settings.saveMap(SETTINGS_KEY, updated);
                cachedKey = new SecretKeySpec(keyBytes, "AES");
            } else {
                byte[] keyBytes = Base64.getDecoder().decode(raw.toString());
                cachedKey = new SecretKeySpec(keyBytes, "AES");
            }
            return cachedKey;
        }
    }
}
