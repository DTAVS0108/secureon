package com.secureone.auth.admin;

import com.secureone.auth.rbac.RoleRbacRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves effective RBAC permission keys for an operator within an application. */
@Service
@Transactional(readOnly = true)
public class AdminPermissionService {

    private final JdbcTemplate jdbc;
    private final RoleRbacRepository roleRbac;

    public AdminPermissionService(JdbcTemplate jdbc, RoleRbacRepository roleRbac) {
        this.jdbc = jdbc;
        this.roleRbac = roleRbac;
    }

    public Set<String> effectivePermissionKeys(UUID applicationId, String email) {
        if (email == null || email.isBlank()) {
            return Set.of();
        }
        List<UUID> roleIds = jdbc.queryForList(
                """
                SELECT DISTINCT role_id FROM (
                    SELECT ur.role_id
                    FROM user_role ur
                    JOIN user_account u ON u.id = ur.user_id
                    JOIN role r ON r.id = ur.role_id
                    WHERE LOWER(u.email) = LOWER(?) AND r.application_id = ?
                      AND (ur.expires_at IS NULL OR ur.expires_at > now())
                    UNION
                    SELECT gr.role_id
                    FROM rbac_group_member gm
                    JOIN rbac_group g ON g.id = gm.group_id
                    JOIN rbac_group_role gr ON gr.group_id = g.id
                    JOIN user_account u ON u.id = gm.user_id
                    WHERE LOWER(u.email) = LOWER(?) AND g.application_id = ?
                ) combined
                """,
                UUID.class,
                email.trim(),
                applicationId,
                email.trim(),
                applicationId);
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        Set<String> keys = new HashSet<>();
        for (UUID roleId : roleIds) {
            keys.addAll(permissionKeysForRole(roleId));
        }
        return keys;
    }

    public boolean hasPermission(UUID applicationId, String email, String permissionKey) {
        return effectivePermissionKeys(applicationId, email).contains(permissionKey);
    }

    public void requirePermission(UUID applicationId, String email, String permissionKey) {
        if (!hasPermission(applicationId, email, permissionKey)) {
            throw new AccessDeniedException("Missing permission: " + permissionKey);
        }
    }

    private Set<String> permissionKeysForRole(UUID roleId) {
        Set<UUID> permissionIds = roleRbac.collectEffectivePermissionIds(roleId);
        if (permissionIds.isEmpty()) {
            return Set.of();
        }
        String placeholders = permissionIds.stream().map(id -> "?").reduce((a, b) -> a + "," + b).orElse("");
        Object[] args = permissionIds.toArray();
        return new HashSet<>(jdbc.queryForList(
                "SELECT key FROM permission WHERE id IN (" + placeholders + ")", String.class, args));
    }
}
