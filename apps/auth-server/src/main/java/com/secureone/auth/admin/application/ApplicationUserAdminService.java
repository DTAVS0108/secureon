package com.secureone.auth.admin.application;

import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.admin.user.UserAdminDtos.UserResponse;
import com.secureone.auth.admin.user.UserAdminService;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.application.UserApplication;
import com.secureone.auth.application.UserApplicationRepository;
import com.secureone.auth.audit.AuditLogRepository;
import com.secureone.auth.user.UserAccountRepository;
import com.secureone.auth.user.UserInviteContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ApplicationUserAdminService {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.secureone.auth.audit.AuditService> auditLog;


    private static final String SELF_REGISTERED_ACTION = "user.self_registered";

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

    /** Same membership rules as {@link #listUsers(UUID)} — includes pending invites and self-registrations. */
    @Transactional(readOnly = true)
    public Set<UUID> resolveApplicationMemberUserIds(UUID applicationId) {
        var app = requireApplication(applicationId);
        Set<UUID> userIds = new LinkedHashSet<>();
        for (UUID userId : findPendingInviteUserIds(app.getTenantId(), applicationId)) {
            userIds.add(userId);
        }
        for (UUID userId : memberships.findUserIdsByApplicationId(applicationId)) {
            if (users.findById(userId).isPresent()) {
                userIds.add(userId);
            }
        }
        for (UUID userId : auditLogs.findDistinctTargetIdsByApplicationIdAndAction(applicationId, SELF_REGISTERED_ACTION)) {
            if (users.findById(userId).isPresent()) {
                userIds.add(userId);
            }
        }
        return userIds;
    }

    @Transactional
    public List<UserResponse> listUsers(UUID applicationId) {
        Set<UUID> userIds = new LinkedHashSet<>(resolveApplicationMemberUserIds(applicationId));
        for (UUID userId : memberships.findUserIdsByApplicationId(applicationId)) {
            if (users.findById(userId).isEmpty()) {
                memberships.deleteByUserIdAndApplicationId(userId, applicationId);
                userIds.remove(userId);
            }
        }
        for (UUID userId : userIds) {
            linkAccessIfAbsent(applicationId, userId);
        }
        if (userIds.isEmpty()) {
            return List.of();
        }
        List<UserResponse> result = new ArrayList<>();
        for (UUID userId : userIds) {
            users.findById(userId)
                    .ifPresent(u -> result.add(userAdminService.toResponsePublic(u, applicationId)));
        }
        result.sort((a, b) -> a.email().compareToIgnoreCase(b.email()));
        return result;
    }

    public void grantAccess(UUID applicationId, UUID userId) {
        var user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        var app = requireApplication(applicationId);
        if (!user.getTenantId().equals(app.getTenantId())) {
            throw new IllegalArgumentException("User must belong to the same tenant as the application");
        }
        linkAccessIfAbsent(applicationId, userId);
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

    public void revokeAccess(UUID applicationId, UUID userId) {
        requireApplication(applicationId);
        memberships.deleteByUserIdAndApplicationId(userId, applicationId);
        if (auditLog != null) auditLog.ifAvailable(a -> a.record(null, applicationId, "admin", "user.application_access_revoked",
                "user_account", userId, null, true));
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
