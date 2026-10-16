package com.secureone.auth.catalog;

import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.application.ApplicationSchemaContext;
import com.secureone.auth.rbac.RbacBootstrapService;
import com.secureone.auth.tenant.Tenant;
import com.secureone.auth.tenant.rbac.TenantRbacBootstrapService;
import com.secureone.auth.tenant.TenantRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Re-seeds catalog rows (permissions, roles) after manual table truncation. Each tenant and each
 * application is repaired in its own transaction, and each application inside its own schema (its
 * roles and permissions live there); one failure is logged and skipped instead of stopping startup.
 */
@Component
public class CatalogRepairBootstrap {

    private static final Logger log = LoggerFactory.getLogger(CatalogRepairBootstrap.class);

    private final TenantRepository tenants;
    private final ApplicationRepository applications;
    private final TenantRbacBootstrapService tenantRbac;
    private final RbacBootstrapService applicationRbac;
    private final TransactionTemplate transactions;

    public CatalogRepairBootstrap(
            TenantRepository tenants,
            ApplicationRepository applications,
            TenantRbacBootstrapService tenantRbac,
            RbacBootstrapService applicationRbac,
            TransactionTemplate transactions) {
        this.tenants = tenants;
        this.applications = applications;
        this.tenantRbac = tenantRbac;
        this.applicationRbac = applicationRbac;
        this.transactions = transactions;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void repairCatalogs() {
        int tenantCount = 0;
        int appCount = 0;
        for (Tenant tenant : transactions.execute(status -> List.copyOf(tenants.findAll()))) {
            try {
                transactions.executeWithoutResult(status -> tenantRbac.seedForTenant(tenant.getId()));
                tenantCount++;
            } catch (RuntimeException ex) {
                log.warn("Catalog repair skipped tenant {}: {}", tenant.getId(), ex.getMessage());
            }
        }
        for (Application app : transactions.execute(status -> List.copyOf(applications.findAll()))) {
            // The transaction manager points search_path at this application's schema.
            ApplicationSchemaContext.setApplicationId(app.getId());
            try {
                transactions.executeWithoutResult(
                        status -> applicationRbac.seedDefaultRoles(app.getTenantId(), app.getId()));
                appCount++;
            } catch (RuntimeException ex) {
                log.warn("Catalog repair skipped application {}: {}", app.getId(), ex.getMessage());
            } finally {
                ApplicationSchemaContext.clear();
            }
        }
        log.info("Catalog repair checked {} tenant(s) and {} application(s)", tenantCount, appCount);
    }
}
