package com.secureone.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties({SecureOneEnvironmentProperties.class, SecureOneRateLimitProperties.class})
public class AppConfig {

    /** Prefix of hashes made by {@link #passwordEncoder()} (stored as {@code {argon2}...}). */
    public static final String PASSWORD_HASH_ID = "argon2";

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    /**
     * New passwords are hashed with Argon2id (OWASP's first choice; no 72-byte limit, so long passphrases work).
     * Hashes stored before have no prefix and are bcrypt: they still match, and sign-in replaces them
     * ({@code upgradeEncoding}).
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        PasswordEncoder bcrypt = new LegacyBcrypt();
        DualSecret dual = new DualSecret();
        DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder(
                PASSWORD_HASH_ID,
                Map.of(PASSWORD_HASH_ID, Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(), "bcrypt", bcrypt,
                        DualSecret.ID, dual));
        encoder.setDefaultPasswordEncoderForMatches(bcrypt);
        dual.single = encoder;
        return new BoundedPasswordEncoder(encoder, Math.max(2, Runtime.getRuntime().availableProcessors() * 2));
    }

    /**
     * At most {@code permits} password hashes at a time. Each Argon2id hash takes 16 MB and a CPU core for a
     * moment by design; a burst of sign-ins hashing at once ran the server out of memory under load (about
     * 20 a second on 2 CPUs). Extra sign-ins wait their turn, up to {@link #WAIT}, and are then refused.
     */
    static final class BoundedPasswordEncoder implements PasswordEncoder {
        static final java.time.Duration WAIT = java.time.Duration.ofSeconds(10);
        private final PasswordEncoder delegate;
        private final java.util.concurrent.Semaphore permits;
        private final java.time.Duration wait;

        BoundedPasswordEncoder(PasswordEncoder delegate, int permits) {
            this(delegate, permits, WAIT);
        }

        BoundedPasswordEncoder(PasswordEncoder delegate, int permits, java.time.Duration wait) {
            this.delegate = delegate;
            this.permits = new java.util.concurrent.Semaphore(permits, true);
            this.wait = wait;
        }

        private <T> T bounded(java.util.function.Supplier<T> hash) {
            boolean acquired;
            try {
                acquired = permits.tryAcquire(wait.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                acquired = false;
            }
            if (!acquired) {
                throw new PasswordHashingBusyException();
            }
            try {
                return hash.get();
            } finally {
                permits.release();
            }
        }

        @Override
        public String encode(CharSequence rawPassword) {
            return bounded(() -> delegate.encode(rawPassword));
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return bounded(() -> delegate.matches(rawPassword, encodedPassword));
        }

        @Override
        public boolean upgradeEncoding(String encodedPassword) {
            return delegate.upgradeEncoding(encodedPassword);
        }
    }

    /**
     * Two client secrets during a rotation's overlap: {@code {dual}<current hash>|<previous hash>|<unix expiry>}.
     * The current one always matches; the previous one until the expiry, so apps can switch over without an
     * outage. Only built for OAuth clients (see ApplicationRegisteredClientRepository); never stored.
     */
    public static final class DualSecret implements PasswordEncoder {
        public static final String ID = "dual";
        private PasswordEncoder single;

        public static String encoded(String currentHash, String previousHash, java.time.Instant previousExpiresAt) {
            return "{" + ID + "}" + currentHash + "|" + previousHash + "|" + previousExpiresAt.getEpochSecond();
        }

        @Override
        public String encode(CharSequence rawPassword) {
            throw new UnsupportedOperationException("Dual secrets are built from two stored hashes.");
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            String[] parts = encodedPassword == null ? new String[0] : encodedPassword.split("\\|");
            if (parts.length != 3) {
                return false;
            }
            if (single.matches(rawPassword, parts[0])) {
                return true;
            }
            long expires;
            try {
                expires = Long.parseLong(parts[2]);
            } catch (NumberFormatException e) {
                return false;
            }
            return java.time.Instant.now().getEpochSecond() < expires && single.matches(rawPassword, parts[1]);
        }

        @Override
        public boolean upgradeEncoding(String encodedPassword) {
            return false;
        }
    }

    /** bcrypt for hashes stored before Argon2id; a password over its 72-byte limit simply does not match. */
    private static final class LegacyBcrypt implements PasswordEncoder {
        private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

        @Override
        public String encode(CharSequence rawPassword) {
            return bcrypt.encode(rawPassword);
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            try {
                return bcrypt.matches(rawPassword, encodedPassword);
            } catch (IllegalArgumentException tooLong) {
                return false;
            }
        }
    }
}
