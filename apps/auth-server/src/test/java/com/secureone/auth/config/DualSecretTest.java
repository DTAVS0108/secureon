package com.secureone.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureone.auth.oauth.ClientSecrets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/** During a client secret rotation's overlap, both secrets work; afterwards only the new one. */
class DualSecretTest {

    private final PasswordEncoder encoder = new AppConfig().passwordEncoder();
    private final String current = ClientSecrets.hash("new-secret");
    private final String previous = ClientSecrets.hash("old-secret");

    @Test
    void bothSecretsWorkDuringTheOverlap() {
        String dual = AppConfig.DualSecret.encoded(current, previous, Instant.now().plus(Duration.ofHours(1)));
        assertThat(encoder.matches("new-secret", dual)).isTrue();
        assertThat(encoder.matches("old-secret", dual)).isTrue();
        assertThat(encoder.matches("other", dual)).isFalse();
    }

    @Test
    void theOldSecretStopsWhenTheOverlapEnds() {
        String dual = AppConfig.DualSecret.encoded(current, previous, Instant.now().minusSeconds(1));
        assertThat(encoder.matches("new-secret", dual)).isTrue();
        assertThat(encoder.matches("old-secret", dual)).isFalse();
    }
}
