package com.secureone.auth.authn;

import com.secureone.auth.tenant.TenantRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TenantSlugResolver {

    private final TenantRepository tenants;

    public TenantSlugResolver(TenantRepository tenants) {
        this.tenants = tenants;
    }

    public String slugForTenant(UUID tenantId) {
        return tenants.findById(tenantId).map(t -> t.getSlug()).orElse("tenant");
    }
}
