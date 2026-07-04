package com.secureone.auth.tenant.rbac;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TenantRbacRepository {

    private final JdbcTemplate jdbc;

    public TenantRbacRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<UUID> findPermissionIdsByRoleId(UUID roleId) {
        return jdbc.queryForList(
                "SELECT permission_id FROM tenant_role_permission WHERE role_id = ? ORDER BY permission_id",
                UUID.class,
                roleId);
    }

    public List<UUID> findApplicationIdsByRoleId(UUID roleId) {
        return jdbc.queryForList(
                "SELECT application_id FROM tenant_role_application WHERE role_id = ? ORDER BY application_id",
                UUID.class,
                roleId);
    }

    public void replacePermissions(UUID roleId, List<UUID> permissionIds) {
        jdbc.update("DELETE FROM tenant_role_permission WHERE role_id = ?", roleId);
        for (UUID permissionId : new LinkedHashSet<>(permissionIds)) {
            jdbc.update(
                    "INSERT INTO tenant_role_permission (role_id, permission_id) VALUES (?, ?)",
                    roleId,
                    permissionId);
        }
    }

    public void replaceApplications(UUID roleId, List<UUID> applicationIds) {
        jdbc.update("DELETE FROM tenant_role_application WHERE role_id = ?", roleId);
        for (UUID applicationId : new LinkedHashSet<>(applicationIds)) {
            jdbc.update(
                    "INSERT INTO tenant_role_application (role_id, application_id) VALUES (?, ?)",
                    roleId,
                    applicationId);
        }
    }

    public long countUsersByRoleId(UUID roleId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tenant_role WHERE role_id = ?", Long.class, roleId);
        return count != null ? count : 0L;
    }

    public long countPermissionsByRoleId(UUID roleId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_role_permission WHERE role_id = ?", Long.class, roleId);
        return count != null ? count : 0L;
    }

    public long countRolesByPermissionId(UUID permissionId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_role_permission WHERE permission_id = ?", Long.class, permissionId);
        return count != null ? count : 0L;
    }

    public Set<String> permissionKeysForRole(UUID roleId) {
        return new LinkedHashSet<>(jdbc.queryForList(
                """
                SELECT p.key
                FROM tenant_role_permission rp
                JOIN tenant_permission p ON p.id = rp.permission_id
                WHERE rp.role_id = ?
                ORDER BY p.key
                """,
                String.class,
                roleId));
    }

    public List<UUID> findRoleIdsByUserId(UUID userId) {
        return jdbc.queryForList(
                """
                SELECT utr.role_id
                FROM user_tenant_role utr
                JOIN tenant_role tr ON tr.id = utr.role_id
                WHERE utr.user_id = ?
                ORDER BY tr.name
                """,
                UUID.class,
                userId);
    }

    public List<String> findRoleNamesByUserId(UUID userId) {
        return jdbc.queryForList(
                """
                SELECT tr.name
                FROM user_tenant_role utr
                JOIN tenant_role tr ON tr.id = utr.role_id
                WHERE utr.user_id = ?
                ORDER BY tr.name
                """,
                String.class,
                userId);
    }

    public boolean hasAnyConsolePermission(UUID userId) {
        Long count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM user_tenant_role utr
                JOIN tenant_role_permission rp ON rp.role_id = utr.role_id
                JOIN tenant_permission p ON p.id = rp.permission_id
                WHERE utr.user_id = ? AND p.key LIKE 'console:%'
                """,
                Long.class,
                userId);
        return count != null && count > 0;
    }

    public void grantUserRole(UUID userId, UUID roleId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tenant_role WHERE user_id = ? AND role_id = ?", Long.class, userId, roleId);
        if (count != null && count > 0) {
            return;
        }
        jdbc.update(
                "INSERT INTO user_tenant_role (id, user_id, role_id) VALUES (gen_random_uuid(), ?, ?)", userId, roleId);
    }

    public void replaceUserRoles(UUID userId, List<UUID> roleIds) {
        jdbc.update("DELETE FROM user_tenant_role WHERE user_id = ?", userId);
        for (UUID roleId : new LinkedHashSet<>(roleIds)) {
            jdbc.update(
                    "INSERT INTO user_tenant_role (id, user_id, role_id) VALUES (gen_random_uuid(), ?, ?)",
                    userId,
                    roleId);
        }
    }
}
