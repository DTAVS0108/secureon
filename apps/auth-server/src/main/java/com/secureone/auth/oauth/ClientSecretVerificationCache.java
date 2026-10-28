package com.secureone.auth.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

/**
 * Remembers client secrets that passed the slow (bcrypt) check, so a client calling the token or
 * introspection endpoint many times a second pays that cost once, not on every call. On 2 CPUs bcrypt alone
 * capped client-credentials tokens at about 8 a second.
 *
 * <p>Kept in memory only: the key is the stored hash (a rotated or removed secret has another hash, so its
 * entry is never used again) and the value is a SHA-256 of the secret, which is safe for the long random
 * values SecureOne issues. A wrong secret is never cached and always takes the slow check. Entries expire
 * after {@link #TTL}; the map is emptied when it reaches {@link #MAX_ENTRIES}.
 */
public final class ClientSecretVerificationCache {

    static final Duration TTL = Duration.ofMinutes(10);
    static final int MAX_ENTRIES = 10_000;

    private record Verified(byte[] digest, long expiresAtMillis) {}

    private final ConcurrentHashMap<String, Verified> verified = new ConcurrentHashMap<>();
    private final BiPredicate<String, String> slowMatch;
    private final Clock clock;

    /** {@code slowMatch}: (raw secret, stored hash) to the full check. */
    public ClientSecretVerificationCache(BiPredicate<String, String> slowMatch) {
        this(slowMatch, Clock.systemUTC());
    }

    ClientSecretVerificationCache(BiPredicate<String, String> slowMatch, Clock clock) {
        this.slowMatch = slowMatch;
        this.clock = clock;
    }

    public boolean matches(CharSequence raw, String encoded) {
        if (raw == null || encoded == null || encoded.isBlank()) {
            return false;
        }
        byte[] digest = sha256(raw.toString());
        long now = clock.millis();
        Verified hit = verified.get(encoded);
        if (hit != null && hit.expiresAtMillis() > now && MessageDigest.isEqual(hit.digest(), digest)) {
            return true;
        }
        if (!slowMatch.test(raw.toString(), encoded)) {
            return false;
        }
        // During a rotation's overlap the previous secret stops working at a set time: not cached, so it
        // cannot outlive that time (AppConfig.DualSecret).
        if (encoded.startsWith("{" + com.secureone.auth.config.AppConfig.DualSecret.ID + "}")) {
            return true;
        }
        if (verified.size() >= MAX_ENTRIES) {
            verified.clear();
        }
        verified.put(encoded, new Verified(digest, now + TTL.toMillis()));
        return true;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
