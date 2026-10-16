package com.secureone.auth.tenant;

import com.secureone.auth.logging.RequestContext;
import java.util.UUID;
import org.slf4j.MDC;

/** Thread-local tenant id for PostgreSQL RLS ({@code app.tenant_id}) within a request/transaction. */
public final class TenantContext {

    private static final ThreadLocal<UUID> TENANT_ID = new ThreadLocal<>();
    /** Key ApplicationLogWriter reads the tenant from. */
    private static final String MDC_KEY = "tenantId";

    private TenantContext() {}

    /** Mirrored into the logging MDC like ApplicationSchemaContext, so log lines carry the tenant. */
    public static void setTenantId(UUID tenantId) {
        if (tenantId == null) {
            TENANT_ID.remove();
        } else {
            TENANT_ID.set(tenantId);
            MDC.put(MDC_KEY, tenantId.toString());
        }
    }

    public static UUID getTenantId() {
        return TENANT_ID.get();
    }

    public static void clear() {
        TENANT_ID.remove();
        if (MDC.get(RequestContext.REQUEST_ID) == null) {
            MDC.remove(MDC_KEY);
        }
    }
}
