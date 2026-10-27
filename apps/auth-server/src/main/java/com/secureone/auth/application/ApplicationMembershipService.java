package com.secureone.auth.application;

import com.secureone.auth.rbac.Role;
import com.secureone.auth.rbac.RoleRepository;
import com.secureone.auth.rbac.UserRole;
import com.secureone.auth.rbac.UserRoleRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Links tenant identities to application memberships (shared across apps in the same tenant). */
@Service
@Transactional
public class ApplicationMembershipService {

    private final UserApplicationRepository memberships;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;

    public ApplicationMembershipService(
            UserApplicationRepository memberships, RoleRepository roles, UserRoleRepository userRoles) {
        this.memberships = memberships;
        this.roles = roles;
        this.userRoles = userRoles;
    }

    public void ensureMember(UUID applicationId, UUID userId) {
        if (memberships.existsByUserIdAndApplicationId(userId, applicationId)) {
            return;
        }
        UserApplication link = new UserApplication();
        link.setUserId(userId);
        link.setApplicationId(applicationId);
        memberships.saveAndFlush(link);
    }

    public void ensureMemberWithDefaultRole(UUID applicationId, UUID userId) {
        ensureMember(applicationId, userId);
        assignDefaultRole(applicationId, userId);
    }

    public boolean isMember(UUID applicationId, UUID userId) {
        return memberships.existsByUserIdAndApplicationId(userId, applicationId);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    /** Removed by an administrator: self-registration does not make them a member again. */
    public boolean wasRemoved(UUID applicationId, UUID userId) {
        if (jdbc == null) {
            return false;
        }
        Integer n = com.secureone.auth.tenant.RlsScope.asPlatform(() -> jdbc.queryForObject(
                "SELECT count(*) FROM platform.application_access_removal WHERE application_id = ? AND user_id = ?",
                Integer.class, applicationId, userId));
        return n != null && n > 0;
    }

    public void recordRemoval(UUID applicationId, UUID userId, UUID tenantId) {
        if (jdbc != null) {
            com.secureone.auth.tenant.RlsScope.runAsPlatform(() -> jdbc.update("""
                    INSERT INTO platform.application_access_removal (application_id, user_id, tenant_id)
                    VALUES (?, ?, ?) ON CONFLICT (application_id, user_id) DO UPDATE SET removed_at = now()
                    """, applicationId, userId, tenantId));
        }
    }

    public void clearRemoval(UUID applicationId, UUID userId) {
        if (jdbc != null) {
            com.secureone.auth.tenant.RlsScope.runAsPlatform(() -> jdbc.update(
                    "DELETE FROM platform.application_access_removal WHERE application_id = ? AND user_id = ?",
                    applicationId, userId));
        }
    }

    private void assignDefaultRole(UUID applicationId, UUID userId) {
        Role role = roles.findByApplicationIdOrderByNameAsc(applicationId).stream()
                .filter(Role::isDefaultRole)
                .filter(r -> "Member".equalsIgnoreCase(r.getName()))
                .findFirst()
                .or(() -> roles.findFirstByApplicationIdAndDefaultRoleTrue(applicationId))
                .orElse(null);
        if (role == null) {
            return;
        }
        boolean alreadyGranted = userRoles.findByUserId(userId).stream()
                .anyMatch(grant -> grant.getRoleId().equals(role.getId()));
        if (alreadyGranted) {
            return;
        }
        UserRole grant = new UserRole();
        grant.setUserId(userId);
        grant.setRoleId(role.getId());
        userRoles.saveAndFlush(grant);
    }
}
