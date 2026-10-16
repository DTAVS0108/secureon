package com.secureone.auth.application;

import com.secureone.auth.authn.IdentifierResolver;
import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.platform.VerificationPolicyService;
import com.secureone.auth.tenant.Tenant;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves tenant context from an application (1:1 tenant per application in integrated apps). */
@Service
@Transactional(readOnly = true)
public class ApplicationTenantResolver {

    private final ApplicationRepository applications;
    private final TenantRepository tenants;
    private final UserAccountRepository users;
    private final IdentifierResolver identifiers;
    /** Lazy: the policy service sits above this resolver in the dependency graph. */
    private final ObjectProvider<VerificationPolicyService> verificationPolicy;

    public ApplicationTenantResolver(
            ApplicationRepository applications,
            TenantRepository tenants,
            UserAccountRepository users,
            IdentifierResolver identifiers,
            ObjectProvider<VerificationPolicyService> verificationPolicy) {
        this.verificationPolicy = verificationPolicy;
        this.applications = applications;
        this.tenants = tenants;
        this.users = users;
        this.identifiers = identifiers;
    }

    public Application requireActiveApplication(UUID applicationId) {
        Application app = applications
                .findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        if (!"ACTIVE".equalsIgnoreCase(app.getStatus())) {
            throw new ResourceNotFoundException("Application not found: " + applicationId);
        }
        return app;
    }

    public UUID requireTenantId(UUID applicationId) {
        return requireActiveApplication(applicationId).getTenantId();
    }

    public Tenant requireTenant(UUID applicationId) {
        UUID tenantId = requireTenantId(applicationId);
        return tenants.findById(tenantId).orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));
    }

    public String requireTenantSlug(UUID applicationId) {
        return requireTenant(applicationId).getSlug();
    }

    public Optional<UserAccount> findUserByEmail(UUID applicationId, String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        UUID tenantId = requireTenantId(applicationId);
        return users.findByTenantIdAndEmail(tenantId, email.trim().toLowerCase(Locale.ROOT));
    }

    public Optional<UserAccount> findUserByIdentifier(UUID applicationId, String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        return identifiers.resolve(
                requireTenantId(applicationId), identifier, verificationPolicy.getObject().defaultPhoneRegion(applicationId));
    }

    public Optional<UserAccount> findUserById(UUID userId) {
        return users.findById(userId);
    }

    public String loginUsername(UUID applicationId, String email) {
        return requireTenantSlug(applicationId) + ":" + email.trim().toLowerCase(Locale.ROOT);
    }
}
