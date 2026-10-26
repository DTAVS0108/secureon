package com.secureone.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TrustedProxiesConfigTest {

    private static boolean trusted(String configured, String address) {
        return Pattern.compile(TrustedProxiesConfig.pattern(configured)).matcher(address).matches();
    }

    @Test
    void onlyLoopbackIsTrustedByDefault() {
        assertThat(trusted("", "127.0.0.1")).isTrue();
        assertThat(trusted("", "0:0:0:0:0:0:0:1")).isTrue();
        // A published Docker port: every request arrives from the bridge gateway, which must not be trusted.
        assertThat(trusted("", "172.17.0.1")).isFalse();
        assertThat(trusted(null, "10.0.0.5")).isFalse();
    }

    @Test
    void configuredAddressesAndRangesAreTrusted() {
        String configured = "10.0.0.5, 10.1.0.0/16,192.168.4.0/24";

        assertThat(trusted(configured, "10.0.0.5")).isTrue();
        assertThat(trusted(configured, "10.0.0.50")).isFalse();
        assertThat(trusted(configured, "10.0.0.5x")).isFalse();
        assertThat(trusted(configured, "10.1.200.7")).isTrue();
        assertThat(trusted(configured, "10.2.0.1")).isFalse();
        assertThat(trusted(configured, "192.168.4.254")).isTrue();
        assertThat(trusted(configured, "192.168.5.1")).isFalse();
    }

    @Test
    void unsupportedEntriesStopStartUp() {
        assertThatThrownBy(() -> TrustedProxiesConfig.pattern("10.0.0.0/12"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrustedProxiesConfig.pattern("proxy.example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrustedProxiesConfig.pattern(".*"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
