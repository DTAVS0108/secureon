package com.secureone.auth.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class BoundedPasswordEncoderTest {

    /** Matches when raw equals encoded; blocks inside the hash until released. */
    private static final class SlowEncoder implements PasswordEncoder {
        final CountDownLatch inside = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @Override
        public String encode(CharSequence raw) {
            return raw.toString();
        }

        @Override
        public boolean matches(CharSequence raw, String encoded) {
            inside.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return raw.toString().equals(encoded);
        }
    }

    @Test
    void refusesWhenEverySlotStaysBusy() throws Exception {
        SlowEncoder slow = new SlowEncoder();
        AppConfig.BoundedPasswordEncoder bounded = new AppConfig.BoundedPasswordEncoder(slow, 1, Duration.ofMillis(100));
        Thread holder = new Thread(() -> bounded.matches("pw", "pw"));
        holder.start();
        assertTrue(slow.inside.await(5, TimeUnit.SECONDS));
        assertThrows(PasswordHashingBusyException.class, () -> bounded.encode("other"));
        slow.release.countDown();
        holder.join(5000);
        assertTrue(bounded.encode("next").equals("next"), "a free slot is used again");
    }

    @Test
    void matchesNormallyWithinTheLimit() {
        SlowEncoder slow = new SlowEncoder();
        slow.release.countDown();
        AppConfig.BoundedPasswordEncoder bounded = new AppConfig.BoundedPasswordEncoder(slow, 2, Duration.ofSeconds(1));
        assertTrue(bounded.matches("pw", "pw"));
    }
}
