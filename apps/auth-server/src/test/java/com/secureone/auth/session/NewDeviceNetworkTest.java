package com.secureone.auth.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NewDeviceNetworkTest {

    @Test
    void networksAreIpv4SlashSixteenAndIpv6SlashThirtyTwo() {
        assertThat(NewDeviceAlertService.network("203.0.113.7")).isEqualTo("203.0");
        assertThat(NewDeviceAlertService.network("203.0.9.1")).isEqualTo("203.0");
        assertThat(NewDeviceAlertService.network("198.51.100.1")).isEqualTo("198.51");
        assertThat(NewDeviceAlertService.network("2001:db8::1")).isEqualTo("2001:db8");
        assertThat(NewDeviceAlertService.network("2001:0db8:ffff::2")).isEqualTo("2001:db8");
    }

    @Test
    void notAnAddressMeansNoNetworkAndNoLookup() {
        assertThat(NewDeviceAlertService.network(null)).isNull();
        assertThat(NewDeviceAlertService.network("")).isNull();
        assertThat(NewDeviceAlertService.network("example.com")).isNull();
    }
}
