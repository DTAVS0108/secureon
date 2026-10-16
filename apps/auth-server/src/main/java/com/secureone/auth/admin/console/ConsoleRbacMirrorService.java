package com.secureone.auth.admin.console;

import com.secureone.auth.rbac.Permission;
import com.secureone.auth.rbac.PermissionRepository;
import com.secureone.auth.rbac.RbacBootstrapService;
import com.secureone.auth.rbac.Role;
import com.secureone.auth.rbac.RoleRbacRepository;
import com.secureone.auth.rbac.RoleRepository;
import com.secureone.auth.rbac.UserRole;
import com.secureone.auth.rbac.UserRoleRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal mirror roles for admin console operators. Not exposed in application RBAC management UI;
 * authorization for operators is primarily {@link AdminConsoleAccessService} + feature matrix.
 */
@Service
@Transactional
public class ConsoleRbacMirrorService {

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final RoleRbacRepository roleRbac;
    private final UserRoleRepository userRoles;
    private final RbacBootstrapService rbacBootstrap;
    private final com.secureone.auth.application.ApplicationSchemaService schemaService;
    private final jakarta.persistence.EntityManager entityManager;

    public ConsoleRbacMirrorService(
            RoleRepository roles,
            PermissionRepository permissions,
            RoleRbacRepository roleRbac,
            UserRoleRepository userRoles,
            RbacBootstrapService rbacBootstrap,
            com.secureone.auth.application.ApplicationSchemaService schemaService,
            jakarta.persistence.EntityManager entityManager) {
        this.schemaService = schemaService;
        this.entityManager = entityManager;
        this.roles = roles;
        this.permissions = permissions;
        this.roleRbac = roleRbac;
        this.userRoles = userRoles;
        this.rbacBootstrap = rbacBootstrap;
    }

    public void grantMirrorRole(UUID tenantId, UUID applicationId, UUID userId, String roleName) {
        inApplicationSchema(applicationId, () -> grantInCurrentSchema(tenantId, applicationId, userId, roleName));
    }

    public void revokeMirrorRole(UUID userId, UUID applicationId, String roleName) {
        inApplicationSchema(applicationId, () -> revokeInCurrentSchema(userId, applicationId, roleName));
    }

    /**
     * The application's roles live in its own schema; callers usually run in the platform schema.
     * Switch this transaction's search_path there, flushing pending writes at each switch so every
     * statement runs against the schema it belongs to.
     */
    private void inApplicationSchema(UUID applicationId, Runnable work) {
        entityManager.flush();
        schemaService.applySearchPathForApplicationId(applicationId);
        try {
            work.run();
            entityManager.flush();
        } finally {
            schemaService.applySearchPathForContext();
        }
    }

    private void grantInCurrentSchema(UUID tenantId, UUID applicationId, UUID userId, String roleName) {
        rbacBootstrap.seedDefaultPermissions(applicationId);
        Role role = ensureMirrorRole(tenantId, applicationId, roleName);
        boolean hasRole = userRoles.findByUserId(userId).stream()
                .anyMatch(link -> link.getRoleId().equals(role.getId()));
        if (!hasRole) {
            UserRole link = new UserRole();
            link.setUserId(userId);
            link.setRoleId(role.getId());
            userRoles.save(link);
        }
    }

    private void revokeInCurrentSchema(UUID userId, UUID applicationId, String roleName) {
        roles.findByApplicationIdAndName(applicationId, roleName).ifPresent(role -> userRoles.findByUserId(userId).stream()
                .filter(link -> link.getRoleId().equals(role.getId()))
                .forEach(userRoles::delete));
    }

    private Role ensureMirrorRole(UUID tenantId, UUID applicationId, String roleName) {
        return roles.findByApplicationIdAndName(applicationId, roleName)
                .orElseGet(() -> createMirrorRole(tenantId, applicationId, roleName));
    }

    private Role createMirrorRole(UUID tenantId, UUID applicationId, String roleName) {
        Role role = new Role();
        role.setTenantId(tenantId);
        role.setApplicationId(applicationId);
        role.setName(roleName);
        role.setDescription(switch (roleName) {
            case "Application Admin" -> "SecureOne admin console operator (application scope)";
            case "Tenant Admin" -> "SecureOne admin console operator (tenant scope)";
            case "Super Admin" -> "SecureOne admin console operator (full application control)";
            default -> "SecureOne admin console operator";
        });
        role.setComposite(false);
        role.setSystemRole(true);
        role.setDefaultRole(false);
        role = roles.save(role);
        List<String> permissionKeys = mirrorPermissionKeys(roleName);
        Map<String, UUID> byKey = permissions.findByApplicationIdOrderByKeyAsc(applicationId).stream()
                .collect(Collectors.toMap(Permission::getKey, Permission::getId, (a, b) -> a));
        List<UUID> permissionIds =
                permissionKeys.stream().map(byKey::get).filter(java.util.Objects::nonNull).toList();
        if (!permissionIds.isEmpty()) {
            roleRbac.replacePermissions(role.getId(), permissionIds);
        }
        return role;
    }

    private static List<String> mirrorPermissionKeys(String roleName) {
        return switch (roleName) {
            case "Application Admin" -> List.of(
                    "user:read",
                    "user:write",
                    "role:read",
                    "app:read",
                    "audit:read",
                    "session:read",
                    "logs:read");
            case "Tenant Admin" -> List.of(
                    "user:read",
                    "user:write",
                    "user:delete",
                    "role:read",
                    "role:write",
                    "app:read",
                    "app:write",
                    "settings:write",
                    "audit:read",
                    "session:read");
            case "Super Admin" -> List.of(
                    "user:read",
                    "user:write",
                    "user:delete",
                    "role:read",
                    "role:write",
                    "app:read",
                    "app:write",
                    "settings:write",
                    "audit:read",
                    "session:read");
            default -> List.of();
        };
    }
}
