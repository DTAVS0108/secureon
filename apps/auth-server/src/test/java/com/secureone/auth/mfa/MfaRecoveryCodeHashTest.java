package com.secureone.auth.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class MfaRecoveryCodeHashTest {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(4);
    private final MfaRecoveryCodeService service =
            new MfaRecoveryCodeService(mock(MfaFactorRepository.class), bcrypt, "test-pepper");

    @Test
    void codesAreHmacedPerUserUnderThePepper() {
        UUID user = UUID.randomUUID();
        String stored = service.hash(user, "123456789");

        assertThat(stored).startsWith(MfaRecoveryCodeService.HMAC_PREFIX);
        assertThat(service.matches(user, "123456789", stored)).isTrue();
        assertThat(service.matches(user, "123456780", stored)).isFalse();
        assertThat(service.matches(UUID.randomUUID(), "123456789", stored)).isFalse();
        assertThat(new MfaRecoveryCodeService(mock(MfaFactorRepository.class), bcrypt, "other-pepper")
                .matches(user, "123456789", stored)).isFalse();
    }

    @Test
    void olderPasswordHashedCodesStillWork() {
        UUID user = UUID.randomUUID();
        String legacy = bcrypt.encode("123456789");

        assertThat(service.matches(user, "123456789", legacy)).isTrue();
        assertThat(service.matches(user, "000000000", legacy)).isFalse();
    }
}
