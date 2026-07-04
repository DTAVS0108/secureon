package com.secureone.auth.tenant;

import java.util.UUID;

/** Thread-local tenant id for PostgreSQL RLS ({@code app.tenant_id}) within a request/transaction. */
public final class TenantContext {

    private static final ThreadLocal<UUID> TENANT_ID = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenantId(UUID tenantId) {
        if (tenantId == null) {
            TENANT_ID.remove();
        } else {
            TENANT_ID.set(tenantId);
        }
    }

    public static UUID getTenantId() {
        return TENANT_ID.get();
    }

    public static void clear() {
        TENANT_ID.remove();
    }
}
