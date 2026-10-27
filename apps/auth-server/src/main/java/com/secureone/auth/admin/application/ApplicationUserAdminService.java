package com.secureone.auth.admin.application;

import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.admin.user.UserAdminDtos.UserResponse;
import com.secureone.auth.admin.user.UserAdminService;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.application.UserApplication;
import com.secureone.auth.application.UserApplicationRepository;
import com.secureone.auth.audit.AuditLogRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import com.secureone.auth.user.UserInviteContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ApplicationUserAdminService {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.secureone.auth.audit.AuditService> auditLog;


    private final ApplicationRepository applications;
    private final UserApplicationRepository memberships;
    private final UserAccountRepository users;
    private final UserAdminService userAdminService;
    private final AuditLogRepository auditLogs;
    private final JdbcTemplate jdbc;

    public ApplicationUserAdminService(
            ApplicationRepository applications,
            UserApplicationRepository memberships,
            UserAccountRepository users,
            UserAdminService userAdminService,
            AuditLogRepository auditLogs,
            JdbcTemplate jdbc) {
        this.applications = applications;
        this.memberships = memberships;
        this.users = users;
        this.userAdminService = userAdminService;
        this.auditLogs = auditLogs;
        this.jdbc = jdbc;
    }

    /** The application's users: its members, and users invited to it who have not accepted yet. */
    @Transactional(readOnly = true)
    public Set<UUID> resolveApplicationMemberUserIds(UUID applicationId) {
        Set<UUID> userIds = new LinkedHashSet<>();
        memberAccounts(applicationId).forEach(u -> userIds.add(u.getId()));
        return userIds;
    }

    /**
     * Same, as accounts sorted by email, loaded in one query. Reading never changes anything (it used to re-add
     * users from old self-registration entries, undoing an administrator's removal).
     */
    @Transactional(readOnly = true)
    public List<UserAccount> memberAccounts(UUID applicationId) {
        var app = requireApplication(applicationId);
        Set<UUID> ids = new LinkedHashSet<>(memberships.findUserIdsByApplicationId(applicationId));
        ids.addAll(findPendingInviteUserIds(app.getTenantId(), applicationId));
        List<UserAccount> out = new ArrayList<>(users.findAllById(ids));
        out.sort(Comparator.comparing(u -> u.getEmail() == null ? "" : u.getEmail(), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /** Case-insensitive match on email, username or display name, and an optional status (as the list shows it). */
    public static boolean matches(UserAccount user, String search, String status) {
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase(listStatus(user))) {
            return false;
        }
        if (search == null || search.isBlank()) {
            return true;
        }
        String needle = search.trim().toLowerCase(Locale.ROOT);
        return Stream.of(user.getEmail(), user.getUsername(), user.getDisplayName())
                .anyMatch(v -> v != null && v.toLowerCase(Locale.ROOT).contains(needle));
    }

    /** The status filter's words: active, invited (pending), suspended, disabled. */
    private static String listStatus(UserAccount user) {
        String s = user.getStatus() == null ? "" : user.getStatus().toLowerCase(Locale.ROOT);
        return "pending".equals(s) ? "invited" : s;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> toResponses(List<UserAccount> accounts, UUID applicationId) {
        List<UserResponse> result = new ArrayList<>(accounts.size());
        accounts.forEach(u -> result.add(userAdminService.toResponsePublic(u, applicationId)));
        return result;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(UUID applicationId) {
        return toResponses(memberAccounts(applicationId), applicationId);
    }

    public void grantAccess(UUID applicationId, UUID userId) {
        var user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        var app = requireApplication(applicationId);
        if (!user.getTenantId().equals(app.getTenantId())) {
            throw new IllegalArgumentException("User must belong to the same tenant as the application");
        }
        linkAccessIfAbsent(applicationId, userId);
        // Added back by an administrator: self-registration may admit them again too.
        membershipService.clearRemoval(applicationId, userId);
    }

    private void linkAccessIfAbsent(UUID applicationId, UUID userId) {
        var userOpt = users.findById(userId);
        if (userOpt.isEmpty()) {
            return;
        }
        var user = userOpt.get();
        if (!memberships.existsByUserIdAndApplicationId(userId, applicationId)) {
            UserApplication link = new UserApplication();
            link.setUserId(userId);
            link.setApplicationId(applicationId);
            memberships.save(link);
        }
        if (applicationId.equals(UserInviteContext.pendingApplicationId(user).orElse(null))) {
            UserInviteContext.clearPendingApplicationInvite(user);
            users.save(user);
        }
    }

    /**
     * Removes the user from the application: membership, the application's roles and groups, a pending invitation to
     * it, the grants and consents of its OAuth clients (refresh tokens stop working) with a back-channel logout to
     * them, and self-registration no longer admits them. Their account and other applications are untouched.
     */
    public void revokeAccess(UUID applicationId, UUID userId) {
        var app = requireApplication(applicationId);
        var user = users.findById(userId).orElse(null);
        memberships.deleteByUserIdAndApplicationId(userId, applicationId);
        String schema = app.getSchemaName();
        if (schema != null && schema.matches("[a-z][a-z0-9_]{0,62}")) {
            jdbc.update("DELETE FROM \"" + schema + "\".user_role ur USING \"" + schema + "\".role r"
                    + " WHERE r.id = ur.role_id AND r.application_id = ? AND ur.user_id = ?", applicationId, userId);
            jdbc.update("DELETE FROM \"" + schema + "\".rbac_group_member gm USING \"" + schema + "\".rbac_group g"
                    + " WHERE g.id = gm.group_id AND g.application_id = ? AND gm.user_id = ?", applicationId, userId);
        }
        String label = null;
        if (user != null) {
            membershipService.recordRemoval(applicationId, userId, user.getTenantId());
            if (applicationId.equals(UserInviteContext.pendingApplicationId(user).orElse(null))) {
                UserInviteContext.clearPendingApplicationInvite(user);
                users.save(user);
            }
            if (accessTermination != null) accessTermination.ifAvailable(t -> t.endInApplication(user, applicationId));
            label = tenantSlug(user.getTenantId()) + ":" + user.getEmail();
        }
        String target = label;
        if (auditLog != null) auditLog.ifAvailable(a -> a.record(app.getTenantId(), applicationId, "admin",
                "user.application_access_revoked", "user_account", userId, target, true));
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.secureone.auth.application.ApplicationMembershipService membershipService;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.secureone.auth.account.UserAccessTermination> accessTermination;

    private String tenantSlug(UUID tenantId) {
        try {
            return jdbc.queryForObject("SELECT slug FROM platform.tenant WHERE id = ?", String.class, tenantId);
        } catch (RuntimeException e) {
            return "?";
        }
    }

    private List<UUID> findPendingInviteUserIds(UUID tenantId, UUID applicationId) {
        return jdbc.queryForList(
                """
                SELECT id
                FROM user_account
                WHERE tenant_id = ?
                  AND attributes->>'pendingInviteApplicationId' = ?
                """,
                UUID.class,
                tenantId,
                applicationId.toString());
    }

    private com.secureone.auth.application.Application requireApplication(UUID applicationId) {
        return applications
                .findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
    }
}
