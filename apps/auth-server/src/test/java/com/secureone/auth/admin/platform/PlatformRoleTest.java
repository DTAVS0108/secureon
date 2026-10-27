package com.secureone.auth.admin.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PlatformRoleTest {

    @Test
    void rolesAreFullAccessByDefaultOrReadOnly() {
        assertThat(PlatformOperatorService.normalizeRole(null)).isEqualTo(PlatformOperators.ROLE_SUPER_ADMIN);
        assertThat(PlatformOperatorService.normalizeRole("read-only")).isEqualTo(PlatformOperators.ROLE_READ_ONLY);
        assertThat(PlatformOperatorService.normalizeRole("SUPER_ADMIN")).isEqualTo(PlatformOperators.ROLE_SUPER_ADMIN);
        assertThatThrownBy(() -> PlatformOperatorService.normalizeRole("owner"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
