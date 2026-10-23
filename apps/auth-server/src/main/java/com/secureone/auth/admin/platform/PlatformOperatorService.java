package com.secureone.auth.admin.platform;

import com.secureone.auth.admin.ConflictException;
import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.admin.user.UserAdminDtos.UserCreateRequest;
import com.secureone.auth.admin.user.UserAdminService;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.tenant.Tenant;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds and removes named platform operators. New operators are created as accounts in the reserved
 * {@code platform} tenant and receive a password-setup email; console sign-in then requires MFA.
 */
@Service
public class PlatformOperatorService {

    private final PlatformOperators operators;
    private final TenantRepository tenants;
    private final UserAccountRepository users;
    private final UserAdminService userAdmin;
    private final AuditService audit;

    public PlatformOperatorService(
            PlatformOperators operators,
            TenantRepository tenants,
            UserAccountRepository users,
            UserAdminService userAdmin,
            AuditService audit) {
        this.operators = operators;
        this.tenants = tenants;
        this.users = users;
        this.userAdmin = userAdmin;
        this.audit = audit;
    }

    public record AddOperatorRequest(String email, String firstName, String lastName) {}

    @Transactional(readOnly = true)
    public List<PlatformOperators.Row> list() {
        return operators.list();
    }

    @Transactional
    public PlatformOperators.Row add(AddOperatorRequest request, String actor) {
        if (request == null || request.email() == null || !request.email().contains("@")) {
            throw new IllegalArgumentException("A valid email is required.");
        }
        Tenant platform = platformTenant();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        UserAccount user = users.findByTenantIdAndEmail(platform.getId(), email).orElse(null);
        if (user == null) {
            userAdmin.create(new UserCreateRequest(
                    platform.getId(), email, null, request.firstName(), request.lastName(), "ACTIVE", null, null));
            user = users.findByTenantIdAndEmail(platform.getId(), email)
                    .orElseThrow(() -> new IllegalStateException("Operator account was not created"));
        } else if (operators.isOperator(user.getId())) {
            throw new ConflictException("Already a platform operator: " + email);
        }
        operators.grant(user.getId(), actor);
        audit.record(platform.getId(), actor, "platform_operator.granted", "user_account", user.getId(), email, true);
        UUID id = user.getId();
        return operators.list().stream()
                .filter(r -> r.userId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public void remove(UUID userId, String actor, String actorEmail) {
        UserAccount user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Platform operator not found"));
        if (actorEmail != null && actorEmail.equalsIgnoreCase(user.getEmail())
                && user.getTenantId().equals(platformTenant().getId())) {
            throw new ConflictException("You cannot remove your own platform operator role.");
        }
        // With none left, nobody can run the platform (and approvals have no one to decide them).
        if (operators.isOperator(userId) && operators.list().size() <= 1) {
            throw new ConflictException("Keep at least one platform operator: add another before removing this one.");
        }
        if (!operators.revoke(userId)) {
            throw new ResourceNotFoundException("Platform operator not found");
        }
        audit.record(user.getTenantId(), actor, "platform_operator.revoked", "user_account", userId, user.getEmail(), true);
    }

    private Tenant platformTenant() {
        return tenants.findBySlug(PlatformOperators.PLATFORM_TENANT_SLUG)
                .orElseThrow(() -> new IllegalStateException("Reserved platform tenant is missing (migration V62)"));
    }
}
