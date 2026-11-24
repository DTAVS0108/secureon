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
    void rangesOfAnyPrefixLength() {
        // A typical cloud subnet (/20) and the private 172.16.0.0/12 block
        assertThat(trusted("10.0.16.0/20", "10.0.16.1")).isTrue();
        assertThat(trusted("10.0.16.0/20", "10.0.31.254")).isTrue();
        assertThat(trusted("10.0.16.0/20", "10.0.32.1")).isFalse();
        assertThat(trusted("10.0.16.0/20", "10.0.15.255")).isFalse();
        assertThat(trusted("172.16.0.0/12", "172.31.255.1")).isTrue();
        assertThat(trusted("172.16.0.0/12", "172.32.0.1")).isFalse();
        assertThat(trusted("172.16.0.0/12", "172.15.0.1")).isFalse();
        // Host bits in the given address are ignored
        assertThat(trusted("10.0.20.7/20", "10.0.16.9")).isTrue();
        assertThat(trusted("10.0.0.0/27", "10.0.0.31")).isTrue();
        assertThat(trusted("10.0.0.0/27", "10.0.0.32")).isFalse();
        assertThat(trusted("10.0.0.9/32", "10.0.0.9")).isTrue();
        assertThat(trusted("10.0.0.9/32", "10.0.0.10")).isFalse();
    }

    @Test
    void unsupportedEntriesStopStartUp() {
        assertThatThrownBy(() -> TrustedProxiesConfig.pattern("10.0.0.0/33"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrustedProxiesConfig.pattern("10.0.0/16"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrustedProxiesConfig.pattern("10.0.0.256/24"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrustedProxiesConfig.pattern("proxy.example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrustedProxiesConfig.pattern(".*"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
