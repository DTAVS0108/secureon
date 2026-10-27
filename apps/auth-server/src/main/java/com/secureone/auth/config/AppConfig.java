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
        return encoder;
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
