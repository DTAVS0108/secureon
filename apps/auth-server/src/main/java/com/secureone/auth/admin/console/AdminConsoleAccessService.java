package com.secureone.auth.admin.console;

import com.secureone.auth.admin.AdminAccessService.ApplicationSummary;
import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.admin.application.ApplicationUserAdminService;
import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.tenant.Tenant;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.tenant.TenantUserRosterService;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminConsoleAccessService {

    private final AdminConsoleAccessRepository access;
    private final AdminConsoleCapabilityService consoleCapabilities;
    private final UserAccountRepository users;
    private final TenantRepository tenants;
    private final ApplicationRepository applications;
    private final ApplicationUserAdminService applicationUsers;
    private final TenantUserRosterService tenantUserRoster;
    private final ConsoleRbacMirrorService consoleRbacMirror;

    public AdminConsoleAccessService(
            AdminConsoleAccessRepository access,
            AdminConsoleCapabilityService consoleCapabilities,
            UserAccountRepository users,
            TenantRepository tenants,
            ApplicationRepository applications,
            ApplicationUserAdminService applicationUsers,
            TenantUserRosterService tenantUserRoster,
            ConsoleRbacMirrorService consoleRbacMirror) {
        this.access = access;
        this.consoleCapabilities = consoleCapabilities;
        this.users = users;
        this.tenants = tenants;
        this.applications = applications;
        this.applicationUsers = applicationUsers;
        this.tenantUserRoster = tenantUserRoster;
        this.consoleRbacMirror = consoleRbacMirror;
    }

    public record ConsoleAccessAssignment(
            UUID id,
            UUID userId,
            String email,
            String displayName,
            AdminConsoleRoleType roleType,
            UUID tenantId,
            UUID applicationId,
            String applicationName) {}

    public boolean hasActiveConsoleAccess(UUID userId) {
        return consoleCapabilities.hasConsoleAccess(userId);
    }

    public Optional<AdminConsoleRoleType> highestRoleType(UUID userId) {
        List<AdminConsoleAccess> rows = access.findActiveByUserId(userId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        if (rows.stream().anyMatch(r -> r.getRoleType() == AdminConsoleRoleType.TENANT_SUPER_ADMIN)) {
            return Optional.of(AdminConsoleRoleType.TENANT_SUPER_ADMIN);
        }
        if (rows.stream().anyMatch(r -> r.getRoleType() == AdminConsoleRoleType.TENANT_ADMIN)) {
            return Optional.of(AdminConsoleRoleType.TENANT_ADMIN);
        }
        return Optional.of(AdminConsoleRoleType.APPLICATION_ADMIN);
    }

    public String resolveOperatorTier(UUID userId) {
        return highestRoleType(userId)
                .map(role -> switch (role) {
                    case TENANT_SUPER_ADMIN -> "tenant_super";
                    case TENANT_ADMIN -> "tenant";
                    case APPLICATION_ADMIN -> "application";
                })
                .orElse("application");
    }

    public List<ApplicationSummary> accessibleApplications(UUID userId) {
        UserAccount user = users.findById(userId).orElse(null);
        if (user == null) {
            return List.of();
        }
        UUID tenantId = user.getTenantId();
        if (access.findActiveTenantSuperAdmin(userId, tenantId).isPresent()) {
            return applications.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                    .map(this::toSummary)
                    .toList();
        }
        Set<UUID> appIds = new LinkedHashSet<>();
        for (AdminConsoleAccess row : access.findActiveByUserId(userId)) {
            if (row.getApplicationId() != null) {
                appIds.add(row.getApplicationId());
            }
        }
        appIds.addAll(consoleCapabilities.accessibleApplicationIdsFromTenantGovernance(userId));
        return applications.findAllById(appIds).stream()
                .sorted(Comparator.comparing(Application::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toSummary)
                .toList();
    }

    public boolean isTenantSuperAdminForApplication(UUID userId, UUID applicationId) {
        Application app = applications.findById(applicationId).orElse(null);
        if (app == null) {
            return false;
        }
        return access.findActiveTenantSuperAdmin(userId, app.getTenantId()).isPresent();
    }

    public List<ConsoleAccessAssignment> listForTenant(UUID tenantId) {
        tenants.findById(tenantId).orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        Map<UUID, UserAccount> userById = new LinkedHashMap<>();
        Map<UUID, String> appNameById = new LinkedHashMap<>();
        List<ConsoleAccessAssignment> result = new ArrayList<>();
        for (AdminConsoleAccess row : access.findActiveByTenantId(tenantId)) {
            UserAccount user = userById.computeIfAbsent(
                    row.getUserId(), id -> users.findById(id).orElse(null));
            if (user == null) {
                continue;
            }
            String appName = null;
            if (row.getApplicationId() != null) {
                appName = appNameById.computeIfAbsent(
                        row.getApplicationId(),
                        id -> applications.findById(id).map(Application::getName).orElse(id.toString()));
            }
            result.add(new ConsoleAccessAssignment(
                    row.getId(),
                    user.getId(),
                    user.getEmail(),
                    user.getDisplayName(),
                    row.getRoleType(),
                    row.getTenantId(),
                    row.getApplicationId(),
                    appName));
        }
        return result;
    }

    @Transactional
    public ConsoleAccessAssignment grant(
            UUID tenantId, UUID userId, AdminConsoleRoleType roleType, UUID applicationId, UUID grantedBy) {
        Tenant tenant = tenants.findById(tenantId).orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        UserAccount user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!user.getTenantId().equals(tenant.getId())) {
            throw new IllegalArgumentException("User must belong to this tenant");
        }
        tenantUserRoster.addToRoster(
                tenantId,
                userId,
                com.secureone.auth.tenant.TenantRosterSource.CONSOLE_ACCESS,
                applicationId,
                grantedBy);
        validateGrant(roleType, applicationId);
        prepareConsoleOperator(user);

        if (roleType == AdminConsoleRoleType.TENANT_SUPER_ADMIN) {
            access.findActiveByTenantId(tenantId).stream()
                    .filter(row -> row.getUserId().equals(userId))
                    .filter(row -> row.getApplicationId() != null)
                    .forEach(this::revokeRow);

            access.findByUserIdAndTenantIdAndRoleTypeAndApplicationIdIsNull(
                            userId, tenantId, AdminConsoleRoleType.TENANT_SUPER_ADMIN)
                    .orElseGet(() -> {
                        AdminConsoleAccess row = new AdminConsoleAccess();
                        row.setUserId(userId);
                        row.setTenantId(tenantId);
                        row.setRoleType(AdminConsoleRoleType.TENANT_SUPER_ADMIN);
                        row.setGrantedBy(grantedBy);
                        return access.save(row);
                    });
            for (Application app : applications.findByTenantIdOrderByCreatedAtDesc(tenantId)) {
                consoleRbacMirror.grantMirrorRole(tenantId, app.getId(), userId, "Tenant Admin");
                applicationUsers.grantAccess(app.getId(), userId);
            }
            return toAssignment(
                    access.findByUserIdAndTenantIdAndRoleTypeAndApplicationIdIsNull(
                                    userId, tenantId, AdminConsoleRoleType.TENANT_SUPER_ADMIN)
                            .orElseThrow(),
                    user,
                    null);
        }

        Application app = applications
                .findById(applicationId)
                .filter(a -> a.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Application not in tenant"));

        AdminConsoleAccess row = access
                .findByUserIdAndTenantIdAndApplicationIdAndRoleType(userId, tenantId, applicationId, roleType)
                .orElseGet(() -> {
                    AdminConsoleAccess created = new AdminConsoleAccess();
                    created.setUserId(userId);
                    created.setTenantId(tenantId);
                    created.setApplicationId(applicationId);
                    created.setRoleType(roleType);
                    created.setGrantedBy(grantedBy);
                    return access.save(created);
                });

        String rbacRoleName =
                roleType == AdminConsoleRoleType.APPLICATION_ADMIN ? "Application Admin" : "Tenant Admin";
        consoleRbacMirror.grantMirrorRole(tenantId, app.getId(), userId, rbacRoleName);
        applicationUsers.grantAccess(app.getId(), userId);

        return toAssignment(row, user, app.getName());
    }

    /**
     * Revokes one assignment in the given tenant. {@code includeSuperAdmin} is false for tenant super
     * admins, who may manage application and tenant admins but not their peers.
     */
    @Transactional
    public void revoke(UUID tenantId, UUID assignmentId, boolean includeSuperAdmin) {
        AdminConsoleAccess row = access.findById(assignmentId)
                .filter(r -> r.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));
        if (!includeSuperAdmin && row.getRoleType() == AdminConsoleRoleType.TENANT_SUPER_ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only a platform super admin can revoke tenant super admin");
        }
        revokeRow(row);
        syncRosterAfterConsoleRevoke(row.getTenantId(), row.getUserId());
    }

    /** Revokes every active console assignment for a user in a tenant (used when removing from roster). */
    @Transactional
    public void revokeAllForUserInTenant(UUID tenantId, UUID userId) {
        List<AdminConsoleAccess> active =
                access.findActiveByTenantId(tenantId).stream()
                        .filter(row -> row.getUserId().equals(userId))
                        .toList();
        for (AdminConsoleAccess row : active) {
            revokeRow(row);
        }
        syncRosterAfterConsoleRevoke(tenantId, userId);
    }

    @Transactional
    public void revoke(UUID tenantId, UUID userId, AdminConsoleRoleType roleType, UUID applicationId) {
        if (roleType == AdminConsoleRoleType.TENANT_SUPER_ADMIN) {
            access.findByUserIdAndTenantIdAndRoleTypeAndApplicationIdIsNull(
                            userId, tenantId, AdminConsoleRoleType.TENANT_SUPER_ADMIN)
                    .ifPresent(row -> {
                        revokeRow(row);
                        syncRosterAfterConsoleRevoke(tenantId, userId);
                    });
            return;
        }
        if (applicationId == null) {
            throw new IllegalArgumentException("applicationId required");
        }
        access.findByUserIdAndTenantIdAndApplicationIdAndRoleType(userId, tenantId, applicationId, roleType)
                .ifPresent(row -> {
                    revokeRow(row);
                    syncRosterAfterConsoleRevoke(tenantId, userId);
                });
    }

    private void revokeRow(AdminConsoleAccess row) {
        if (row.getRoleType() == AdminConsoleRoleType.TENANT_SUPER_ADMIN) {
            for (Application app : applications.findByTenantIdOrderByCreatedAtDesc(row.getTenantId())) {
                consoleRbacMirror.revokeMirrorRole(row.getUserId(), app.getId(), "Tenant Admin");
            }
            access.delete(row);
            return;
        }
        String rbacRoleName = row.getRoleType() == AdminConsoleRoleType.APPLICATION_ADMIN
                ? "Application Admin"
                : "Tenant Admin";
        UUID applicationId = row.getApplicationId();
        if (applicationId != null) {
            consoleRbacMirror.revokeMirrorRole(row.getUserId(), applicationId, rbacRoleName);
            applicationUsers.revokeAccess(applicationId, row.getUserId());
        }
        access.delete(row);
    }

    private void syncRosterAfterConsoleRevoke(UUID tenantId, UUID userId) {
        boolean stillHasAccess =
                access.findActiveByTenantId(tenantId).stream()
                        .anyMatch(row -> row.getUserId().equals(userId));
        if (!stillHasAccess) {
            tenantUserRoster.removeConsoleAccessRosterEntry(tenantId, userId);
        }
    }

    /** Console operators must be active with a verified email before they can sign in. */
    private void prepareConsoleOperator(UserAccount user) {
        boolean changed = false;
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            user.setStatus("ACTIVE");
            changed = true;
        }
        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            changed = true;
        }
        if (changed) {
            users.save(user);
        }
    }

    private void validateGrant(AdminConsoleRoleType roleType, UUID applicationId) {
        if (roleType == AdminConsoleRoleType.TENANT_SUPER_ADMIN) {
            if (applicationId != null) {
                throw new IllegalArgumentException("Tenant super admin cannot be scoped to an application");
            }
            return;
        }
        if (applicationId == null) {
            throw new IllegalArgumentException("applicationId is required for " + roleType);
        }
    }

    private ConsoleAccessAssignment toAssignment(AdminConsoleAccess row, UserAccount user, String appName) {
        return new ConsoleAccessAssignment(
                row.getId(),
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                row.getRoleType(),
                row.getTenantId(),
                row.getApplicationId(),
                appName);
    }

    private ApplicationSummary toSummary(Application app) {
        return new ApplicationSummary(
                app.getId(), app.getTenantId(), app.getName(), app.getSlug(), app.getStatus());
    }
}
