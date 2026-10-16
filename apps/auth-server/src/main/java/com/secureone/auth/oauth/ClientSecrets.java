package com.secureone.auth.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * OAuth client secrets are stored as bcrypt hashes ({@code {bcrypt}$2a$...}), never in plain text: anyone
 * with database or backup access must not be able to use a client. The plain value is shown once, when
 * it is created or rotated. Secrets stored in plain text by older releases are hashed at start-up
 * ({@link ClientSecretUpgrade}); until then {@link #matches} still accepts them.
 */
public final class ClientSecrets {

    public static final String PREFIX = "{bcrypt}";
    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    private ClientSecrets() {}

    public static String hash(String plain) {
        return PREFIX + BCRYPT.encode(plain);
    }

    public static boolean isHashed(String stored) {
        return stored != null && (stored.startsWith(PREFIX) || stored.startsWith("$2"));
    }

    /** Whether {@code raw} is the secret stored as {@code stored} (a hash, or legacy plain text). */
    public static boolean matches(String raw, String stored) {
        if (raw == null || stored == null || stored.isBlank()) {
            return false;
        }
        if (isHashed(stored)) {
            return BCRYPT.matches(raw, stored.startsWith(PREFIX) ? stored.substring(PREFIX.length()) : stored);
        }
        return MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8), raw.getBytes(StandardCharsets.UTF_8));
    }
}
