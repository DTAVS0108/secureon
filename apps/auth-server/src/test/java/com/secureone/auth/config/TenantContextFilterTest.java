package com.secureone.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantContextFilterTest {

    @Test
    void parsesTenantFromAdminPath() {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111101");
        assertEquals(tenantId, TenantContextFilter.tenantFromPath(
                "/api/admin/v1/tenants/11111111-1111-1111-1111-111111111101/console-access"));
    }

    @Test
    void ignoresNonTenantPaths() {
        assertNull(TenantContextFilter.tenantFromPath("/api/admin/v1/applications"));
        assertNull(TenantContextFilter.tenantFromPath("/api/v1/auth/me"));
    }
}
