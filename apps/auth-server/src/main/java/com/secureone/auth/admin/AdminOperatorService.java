package com.secureone.auth.admin;

import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.tenant.Tenant;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves the signed-in admin console operator (platform or tenant user account). */
@Service
@Transactional(readOnly = true)
public class AdminOperatorService {

    private final AdminAccessService access;
    private final AdminOperatorResolver operators;
    private final UserAccountRepository users;
    private final TenantRepository tenants;

    public AdminOperatorService(
            AdminAccessService access,
            AdminOperatorResolver operators,
            UserAccountRepository users,
            TenantRepository tenants) {
        this.access = access;
        this.operators = operators;
        this.users = users;
        this.tenants = tenants;
    }

    public boolean isPlatformSuperAdmin(Authentication authentication, String actAsEmail) {
        return access.canAccessPlatformSettings(authentication, actAsEmail);
    }

    /** The configured emergency account, which has no user record, MFA or tenant. */
    public boolean isBreakGlass(Authentication authentication) {
        return access.isBreakGlass(authentication);
    }

    /** Tenant slug of the signed-in account ({@code platform} for named platform operators). */
    public String signedInTenantSlug(Authentication authentication) {
        return operators.resolveTenantSlug(authentication);
    }

    public Optional<UserAccount> resolveTenantUser(Authentication authentication, String actAsEmail) {
        String email = access.resolveOperatorEmail(authentication, actAsEmail);
        String tenantSlug = operators.resolveTenantSlug(authentication);
        if (email == null || tenantSlug == null) {
            return Optional.empty();
        }
        Tenant tenant = tenants
                .findBySlug(tenantSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantSlug));
        return users.findByTenantIdAndEmail(tenant.getId(), email);
    }

    public Tenant requireOperatorTenant(Authentication authentication, String actAsEmail) {
        if (isPlatformSuperAdmin(authentication, actAsEmail)) {
            throw new IllegalStateException("Platform super-admin has no single tenant workspace");
        }
        String tenantSlug = operators.resolveTenantSlug(authentication);
        if (tenantSlug == null) {
            throw new ResourceNotFoundException("Tenant operator login required");
        }
        return tenants
                .findBySlug(tenantSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantSlug));
    }

    public void requireTenantAccess(Authentication authentication, String actAsEmail, UUID tenantId) {
        if (isPlatformSuperAdmin(authentication, actAsEmail)) {
            return;
        }
        Tenant tenant = requireOperatorTenant(authentication, actAsEmail);
        if (!tenant.getId().equals(tenantId)) {
            throw new org.springframework.security.access.AccessDeniedException("No access to tenant: " + tenantId);
        }
    }
}
