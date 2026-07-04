package com.secureone.auth.tenant;

import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.user.UserAccountRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TenantQuotaService {

    private final TenantQuotaRepository quotas;
    private final UserAccountRepository users;
    private final ApplicationRepository applications;

    public TenantQuotaService(
            TenantQuotaRepository quotas, UserAccountRepository users, ApplicationRepository applications) {
        this.quotas = quotas;
        this.users = users;
        this.applications = applications;
    }

    public void assertCanCreateUser(UUID tenantId) {
        TenantQuota quota = resolveQuota(tenantId);
        long current = users.countByTenantId(tenantId);
        if (current >= quota.getMaxUsers()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Tenant user quota reached (" + quota.getMaxUsers() + ").");
        }
    }

    public void assertCanCreateApplication(UUID tenantId) {
        TenantQuota quota = resolveQuota(tenantId);
        long current = applications.countByTenantId(tenantId);
        if (current >= quota.getMaxApplications()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Tenant application quota reached (" + quota.getMaxApplications() + ").");
        }
    }

    private TenantQuota resolveQuota(UUID tenantId) {
        return quotas.findById(tenantId).orElseGet(() -> {
            TenantQuota defaults = new TenantQuota();
            defaults.setTenantId(tenantId);
            defaults.setMaxUsers(1000);
            defaults.setMaxApplications(50);
            return defaults;
        });
    }
}
