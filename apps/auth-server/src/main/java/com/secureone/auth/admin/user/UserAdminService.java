package com.secureone.auth.admin.user;

import com.secureone.auth.admin.ConflictException;
import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.admin.user.UserAdminDtos.UserCreateRequest;
import com.secureone.auth.admin.user.UserAdminDtos.UserResponse;
import com.secureone.auth.admin.user.UserAdminDtos.UserUpdateRequest;
import com.secureone.auth.account.AccountNotificationService;
import com.secureone.auth.account.UserPasswordService;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.application.ApplicationSettingsService;
import com.secureone.auth.application.UserApplication;
import com.secureone.auth.application.UserApplicationRepository;
import com.secureone.auth.mfa.MfaFactor;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import com.secureone.auth.admin.user.UserAdminDtos.MfaFactorResponse;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.mfa.MfaFactorRepository;
import com.secureone.auth.notify.EmailNotificationService;
import com.secureone.auth.rbac.ApplicationRbacScope;
import com.secureone.auth.rbac.Role;
import com.secureone.auth.rbac.RoleRepository;
import com.secureone.auth.rbac.UserRole;
import com.secureone.auth.rbac.UserRoleRepository;
import com.secureone.auth.tenant.TenantQuotaService;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.tenant.TenantUserRosterService;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import com.secureone.auth.user.UserInviteContext;
import com.secureone.auth.webhook.UserLifecycleWebhookPublisher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserAdminService {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.secureone.auth.oauth.BackchannelLogoutService> backchannelLogout;


    /** Lazy: two-step reset ends sessions and emails the user; it depends on beans built after this one. */
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.secureone.auth.twostep.TwoStepService> twoStep;


    private final UserAccountRepository userRepository;
    private final TenantRepository tenantRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final AuditService auditService;
    private final AccountNotificationService accountNotifications;
    private final MfaFactorRepository mfaFactors;
    private final EmailNotificationService emailService;
    private final UserPasswordService passwords;
    private final ApplicationRepository applicationRepository;
    private final UserApplicationRepository userApplications;
    private final org.springframework.beans.factory.ObjectProvider<com.secureone.auth.application.ApplicationSchemaService> schemaService;
    private final ApplicationSettingsService applicationSettings;
    private final TenantUserRosterService tenantUserRoster;
    private final TenantQuotaService tenantQuotas;
    private final UserLifecycleWebhookPublisher userLifecycleWebhooks;

    public UserAdminService(
            UserAccountRepository userRepository,
            TenantRepository tenantRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository,
            AuditService auditService,
            AccountNotificationService accountNotifications,
            MfaFactorRepository mfaFactors,
            EmailNotificationService emailService,
            UserPasswordService passwords,
            ApplicationRepository applicationRepository,
            UserApplicationRepository userApplications,
            org.springframework.beans.factory.ObjectProvider<com.secureone.auth.application.ApplicationSchemaService> schemaService,
            ApplicationSettingsService applicationSettings,
            TenantUserRosterService tenantUserRoster,
            TenantQuotaService tenantQuotas,
            UserLifecycleWebhookPublisher userLifecycleWebhooks) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.auditService = auditService;
        this.accountNotifications = accountNotifications;
        this.mfaFactors = mfaFactors;
        this.emailService = emailService;
        this.passwords = passwords;
        this.applicationRepository = applicationRepository;
        this.schemaService = schemaService;
        this.userApplications = userApplications;
        this.applicationSettings = applicationSettings;
        this.tenantUserRoster = tenantUserRoster;
        this.tenantQuotas = tenantQuotas;
        this.userLifecycleWebhooks = userLifecycleWebhooks;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listByTenant(UUID tenantId) {
        requireTenant(tenantId);
        return userRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Users with at least one role whose name contains "admin" (e.g. Tenant Admin, Super Admin). */
    @Transactional(readOnly = true)
    public List<UserResponse> listWithAdminRole(UUID tenantId) {
        List<UUID> adminRoleIds =
                roleRepository.findByNameContainingIgnoreCase("admin").stream()
                        .map(Role::getId)
                        .toList();
        if (adminRoleIds.isEmpty()) {
            return List.of();
        }
        List<UUID> userIds = userRoleRepository.findDistinctUserIdsByRoleIdIn(adminRoleIds);
        if (userIds.isEmpty()) {
            return List.of();
        }
        List<UserAccount> users = new ArrayList<>();
        for (UUID userId : userIds) {
            userRepository.findById(userId).ifPresent(users::add);
        }
        return users.stream()
                .filter(u -> tenantId == null || tenantId.equals(u.getTenantId()))
                .sorted(Comparator.comparing(UserAccount::getEmail))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return get(id, null);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id, UUID applicationId) {
        UserAccount user = require(id);
        return toResponse(user, effectiveAuthMethods(user, applicationId), applicationId);
    }

    public UserResponse updateAuthMethods(UUID id, UUID applicationId, Map<String, Boolean> updates) {
        UserAccount user = require(id);
        requireApplication(applicationId);
        List<Map<String, Object>> appMethods = applicationSettings.getAuthMethods(applicationId);
        Map<String, Boolean> current = UserAuthPreferences.read(user.getAttributes());
        Map<String, Boolean> merged = new LinkedHashMap<>(UserAuthPreferences.effective(appMethods, current));
        for (Map<String, Object> method : appMethods) {
            if (!Boolean.TRUE.equals(method.get("enabled"))) {
                continue;
            }
            String methodId = String.valueOf(method.get("id"));
            if (updates.containsKey(methodId)) {
                merged.put(methodId, Boolean.TRUE.equals(updates.get(methodId)));
            }
        }
        user.setAttributes(UserAuthPreferences.write(user.getAttributes(), merged));
        userRepository.save(user);
        auditService.record(
                user.getTenantId(), "admin", "user.auth_methods_updated", "user_account", user.getId(), user.getEmail(), true);
        return toResponse(user, merged);
    }

    public void deleteMfaFactor(UUID userId, UUID factorId) {
        UserAccount user = require(userId);
        MfaFactor factor =
                mfaFactors
                        .findById(factorId)
                        .orElseThrow(() -> new ResourceNotFoundException("MFA factor not found: " + factorId));
        if (!user.getId().equals(factor.getUserId())) {
            throw new ResourceNotFoundException("MFA factor not found for this user.");
        }
        mfaFactors.delete(factor);
        auditService.record(
                user.getTenantId(), "admin", "user.mfa_factor_removed", "user_account", user.getId(), factor.getType(), true);
    }

    public UserResponse resetMfaForMethod(UUID userId, UUID applicationId, String methodId) {
        UserAccount user = require(userId);
        requireApplication(applicationId);
        String factorType = UserAuthPreferences.mfaFactorTypeForMethodId(methodId);
        if (factorType == null) {
            throw new IllegalArgumentException("Not an MFA method: " + methodId);
        }
        com.secureone.auth.twostep.TwoStepMethod method = switch (factorType) {
            case "totp" -> com.secureone.auth.twostep.TwoStepMethod.TOTP;
            case "passkey" -> com.secureone.auth.twostep.TwoStepMethod.PASSKEY;
            case "sms" -> com.secureone.auth.twostep.TwoStepMethod.SMS;
            case "email" -> com.secureone.auth.twostep.TwoStepMethod.EMAIL;
            default -> null;
        };
        if (method == com.secureone.auth.twostep.TwoStepMethod.PASSKEY) {
            // Passkeys and security keys share the factor type: remove both kinds.
            mfaFactors.findByUserIdOrderByCreatedAtAsc(userId).stream()
                    .filter(f -> factorType.equalsIgnoreCase(f.getType()))
                    .forEach(mfaFactors::delete);
            method = null;
            twoStep.getObject().endSessions(user);
        } else if (method != null) {
            twoStep.getObject().reset(user, applicationId, method, "admin");
        }
        auditService.record(
                user.getTenantId(), "admin", "user.mfa_reset_method", "user_account", user.getId(), methodId, true);
        return toResponse(user, effectiveAuthMethods(user, applicationId), applicationId);
    }

    /** Used by application-scoped admin to map entities without exposing private helpers. */
    @Transactional(readOnly = true)
    public UserResponse toResponsePublic(UserAccount user) {
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse toResponsePublic(UserAccount user, UUID applicationId) {
        return toResponse(user, effectiveAuthMethods(user, applicationId), applicationId);
    }

    public UserResponse create(UserCreateRequest request) {
        UUID tenantId = resolveTenantId(request);
        requireTenant(tenantId);
        enterApplicationSchema(request.applicationId());
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        var existing = userRepository.findByTenantIdAndEmail(tenantId, email);
        if (existing.isPresent()) {
            return grantExistingUserToApplication(existing.get(), request);
        }
        tenantQuotas.assertCanCreateUser(tenantId);
        UserAccount user = new UserAccount();
        user.setTenantId(tenantId);
        user.setEmail(email);
        user.setUsername(resolveUsername(request.username(), email));
        user.setDisplayName(displayName(request.firstName(), request.lastName()));
        String status = normalizeStatus(request.status(), "PENDING");
        user.setStatus(status);
        user.setEmailVerified("ACTIVE".equalsIgnoreCase(status));
        user.setType("USER");
        if (request.applicationId() != null) {
            UserInviteContext.markPendingApplicationInvite(user, request.applicationId());
        }
        userRepository.saveAndFlush(user);
        syncUserRoles(user.getId(), request.roleIds());
        ensureApplicationAccessForOperatorRoles(user.getId(), request.roleIds());
        if (request.applicationId() != null) {
            grantApplicationAccess(request.applicationId(), user.getId());
        } else {
            tenantUserRoster.addToRoster(tenantId, user.getId());
        }
        auditService.record(user.getTenantId(), "admin", "user.created", "user_account", user.getId(), user.getEmail(), true);
        userLifecycleWebhooks.userCreated(user, request.applicationId(), "admin");
        accountNotifications.onUserInvited(request.applicationId(), user);
        return toResponse(user);
    }

    /** Links an existing tenant identity to another application instead of creating a duplicate account. */
    private UserResponse grantExistingUserToApplication(UserAccount user, UserCreateRequest request) {
        UUID applicationId = request.applicationId();
        if (applicationId == null) {
            throw new ConflictException("User email already exists in tenant: " + user.getEmail());
        }
        if (userApplications.existsByUserIdAndApplicationId(user.getId(), applicationId)) {
            throw new ConflictException(
                    "This user already has access to this application. Open their profile to update roles.");
        }
        grantApplicationAccess(applicationId, user.getId());
        if (request.roleIds() != null && !request.roleIds().isEmpty()) {
            addApplicationRoles(user.getId(), applicationId, request.roleIds());
        }
        ensureApplicationAccessForOperatorRoles(user.getId(), request.roleIds());
        auditService.record(
                user.getTenantId(),
                "admin",
                "user.application_access_granted",
                "user_account",
                user.getId(),
                user.getEmail(),
                true);
        accountNotifications.onUserInvited(applicationId, user);
        return toResponse(user);
    }

    public UserResponse unlockAccount(UUID id) {
        UserAccount user = require(id);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLockoutLevel(0);
        if ("LOCKED".equalsIgnoreCase(user.getStatus())) {
            user.setStatus("ACTIVE");
        }
        auditService.record(user.getTenantId(), "admin", "user.unlocked", "user_account", user.getId(), user.getEmail(), true);
        return toResponse(user);
    }

    public void adminSetPassword(UUID id, String plainPassword) {
        adminSetPassword(id, plainPassword, null);
    }

    public void adminSetPassword(UUID id, String plainPassword, UUID applicationId) {
        UserAccount user = require(id);
        passwords.setPassword(user.getId(), plainPassword, applicationId);
        auditService.record(
                user.getTenantId(), "admin", "user.password_set", "user_account", user.getId(), user.getEmail(), true);
        emailService.sendAdminSecurityAlert(
                applicationId,
                user,
                "Password set by admin",
                "An administrator set a new password for " + user.getEmail() + ".");
    }

    public void sendPasswordResetEmail(UUID id) {
        sendPasswordResetEmail(id, null);
    }

    public void sendPasswordResetEmail(UUID id, UUID applicationId) {
        UserAccount user = require(id);
        if (applicationId != null) {
            requireUserInApplication(user, applicationId);
        }
        accountNotifications.sendPasswordResetEmail(applicationId, user, "admin");
        auditService.record(
                user.getTenantId(), "admin", "user.password_reset_sent", "user_account", user.getId(), user.getEmail(), true);
    }

    public void adminRemovePassword(UUID id) {
        adminRemovePassword(id, null);
    }

    public void adminRemovePassword(UUID id, UUID applicationId) {
        UserAccount user = require(id);
        if (applicationId != null) {
            requireUserInApplication(user, applicationId);
        }
        if (!passwords.hasPassword(user.getId())) {
            return;
        }
        passwords.removePassword(user.getId());
        auditService.record(
                user.getTenantId(), "admin", "user.password_removed", "user_account", user.getId(), user.getEmail(), true);
        emailService.sendAdminSecurityAlert(
                applicationId,
                user,
                "Password removed",
                "An administrator removed the password credential for " + user.getEmail() + ".");
    }

    public void sendSetPasswordInviteEmail(UUID id) {
        sendSetPasswordInviteEmail(id, null);
    }

    public void sendSetPasswordInviteEmail(UUID id, UUID applicationId) {
        UserAccount user = require(id);
        if (applicationId != null) {
            requireUserInApplication(user, applicationId);
        }
        accountNotifications.sendSetPasswordEmail(applicationId, user);
        auditService.record(
                user.getTenantId(),
                "admin",
                "user.set_password_invite_sent",
                "user_account",
                user.getId(),
                user.getEmail(),
                true);
    }

    public void resendVerificationEmail(UUID id) {
        resendVerificationEmail(id, null);
    }

    public void resendVerificationEmail(UUID id, UUID applicationId) {
        UserAccount user = require(id);
        if (applicationId != null) {
            requireUserInApplication(user, applicationId);
        }
        if (user.isEmailVerified()) {
            // Nothing would be sent; say so instead of reporting "sent".
            throw new ConflictException("Email address is already verified.");
        }
        accountNotifications.sendVerificationEmail(applicationId, user, "admin");
        recordUserAudit(
                user,
                applicationId,
                "user.verification_resent",
                true);
    }

    public UserResponse markEmailVerified(UUID id) {
        return setEmailVerified(id, true, null);
    }

    public UserResponse markEmailVerified(UUID id, UUID applicationId) {
        return setEmailVerified(id, true, applicationId);
    }

    public UserResponse setEmailVerified(UUID id, boolean verified, UUID applicationId) {
        UserAccount user = require(id);
        if (applicationId != null) {
            requireUserInApplication(user, applicationId);
        }
        if (verified) {
            accountNotifications.adminMarkEmailVerified(id, applicationId);
            recordUserAudit(user, applicationId, "user.email_verified", true);
        } else {
            user.setEmailVerified(false);
            userRepository.save(user);
            recordUserAudit(user, applicationId, "user.email_unverified", true);
            emailService.sendAdminSecurityAlert(
                    applicationId,
                    user,
                    "Email marked unverified",
                    "Admin cleared email verification for " + user.getEmail() + ".");
        }
        UserAccount updated = require(id);
        return toResponse(updated, effectiveAuthMethods(updated, applicationId), applicationId);
    }

    /** Every option, backup codes and remembered devices; ends the user's sessions and emails them. */
    public void resetMfa(UUID id) {
        UserAccount user = require(id);
        twoStep.getObject().reset(user, null, null, "admin");
        auditService.record(user.getTenantId(), "admin", "user.mfa_reset", "user_account", user.getId(), user.getEmail(), true);
        emailService.sendAdminSecurityAlert(null, user, "MFA reset", "Admin cleared MFA factors for " + user.getEmail() + ".");
    }

    public UserResponse update(UUID id, UserUpdateRequest request) {
        UserAccount user = require(id);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        userRepository.findByTenantIdAndEmail(user.getTenantId(), email).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new ConflictException("User email already exists in tenant: " + email);
            }
        });
        user.setEmail(email);
        user.setUsername(resolveUsername(request.username(), email));
        user.setDisplayName(displayName(request.firstName(), request.lastName()));
        String previousStatus = user.getStatus();
        String nextStatus = normalizeStatus(request.status(), user.getStatus());
        user.setStatus(nextStatus);
        userRepository.save(user);
        notifyStatusChange(user, previousStatus, nextStatus, null);
        if (request.emailVerified() != null) {
            boolean verified = Boolean.TRUE.equals(request.emailVerified());
            if (verified != require(id).isEmailVerified()) {
                setEmailVerified(id, verified, null);
            }
        }
        if (request.roleIds() != null) {
            syncUserRoles(user.getId(), request.roleIds());
            ensureApplicationAccessForOperatorRoles(user.getId(), request.roleIds());
        }
        UserAccount updated = require(id);
        auditService.record(
                updated.getTenantId(), "admin", "user.updated", "user_account", updated.getId(), updated.getEmail(), true);
        return toResponse(updated);
    }

    public void delete(UUID id) {
        UserAccount user = require(id);
        signOutOfApplications(user);
        userApplications.deleteByUserId(id);
        userRoleRepository.deleteByUserId(id);
        mfaFactors.deleteByUserId(id);
        passwords.removePassword(id);
        userRepository.delete(user);
        auditService.record(user.getTenantId(), "admin", "user.deleted", "user_account", id, user.getEmail(), true);
    }

    public UserResponse setStatus(UUID id, String status) {
        UserAccount user = require(id);
        String previousStatus = user.getStatus();
        String nextStatus = normalizeStatus(status, user.getStatus());
        user.setStatus(nextStatus);
        userRepository.save(user);
        auditService.record(user.getTenantId(), "admin", "user.status_changed", "user_account", user.getId(), user.getEmail(), true);
        notifyStatusChange(user, previousStatus, nextStatus, null);
        if (!"ACTIVE".equalsIgnoreCase(nextStatus)) {
            signOutOfApplications(user);
        }
        return toResponse(user);
    }

    /** Back-channel logout: applications end the sessions of a user who is suspended or deleted. */
    private void signOutOfApplications(UserAccount user) {
        if (backchannelLogout != null) backchannelLogout.ifAvailable(b -> tenantRepository.findById(user.getTenantId())
                .ifPresent(t -> b.signedOut(t.getSlug(), user.getEmail(), user.getId())));
    }

    private void notifyStatusChange(
            UserAccount user, String previousStatus, String nextStatus, UUID applicationId) {
        if (previousStatus != null && previousStatus.equalsIgnoreCase(nextStatus)) {
            return;
        }
        if ("SUSPENDED".equalsIgnoreCase(nextStatus)) {
            emailService.sendAccountSuspended(applicationId, user);
            emailService.sendAdminSecurityAlert(
                    applicationId,
                    user,
                    "Account suspended",
                    "Administrator suspended the account for " + user.getEmail() + ".");
            return;
        }
        if ("SUSPENDED".equalsIgnoreCase(previousStatus) && "ACTIVE".equalsIgnoreCase(nextStatus)) {
            emailService.sendAccountReactivated(applicationId, user);
            emailService.sendAdminSecurityAlert(
                    applicationId,
                    user,
                    "Account reactivated",
                    "Administrator reactivated the account for " + user.getEmail() + ".");
        }
    }

    private UserAccount require(UUID id) {
        return userRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private UUID resolveTenantId(UserCreateRequest request) {
        if (request.tenantId() != null) {
            return request.tenantId();
        }
        if (request.applicationId() != null) {
            return applicationRepository
                    .findById(request.applicationId())
                    .map(app -> app.getTenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + request.applicationId()));
        }
        throw new IllegalArgumentException("tenantId or applicationId is required");
    }

    private void requireTenant(UUID tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new ResourceNotFoundException("Tenant not found: " + tenantId);
        }
    }

    private Map<String, Boolean> effectiveAuthMethods(UserAccount user, UUID applicationId) {
        if (applicationId == null) {
            return Map.of();
        }
        requireApplication(applicationId);
        return UserAuthPreferences.effective(
                applicationSettings.getAuthMethods(applicationId),
                UserAuthPreferences.read(user.getAttributes()));
    }

    private void requireApplication(UUID applicationId) {
        if (!applicationRepository.existsById(applicationId)) {
            throw new ResourceNotFoundException("Application not found: " + applicationId);
        }
    }

    private void requireUserInApplication(UserAccount user, UUID applicationId) {
        requireApplication(applicationId);
        var app = applicationRepository
                .findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        if (!user.getTenantId().equals(app.getTenantId())) {
            throw new IllegalArgumentException("User must belong to the same tenant as the application");
        }
        if (!userApplications.existsByUserIdAndApplicationId(user.getId(), applicationId)) {
            throw new ResourceNotFoundException("User is not a member of this application");
        }
    }

    private void recordUserAudit(
            UserAccount user, UUID applicationId, String action, boolean success) {
        if (applicationId != null) {
            auditService.record(
                    user.getTenantId(),
                    applicationId,
                    "admin",
                    action,
                    "user_account",
                    user.getId(),
                    user.getEmail(),
                    success);
        } else {
            auditService.record(
                    user.getTenantId(), "admin", action, "user_account", user.getId(), user.getEmail(), success);
        }
    }

    private UserResponse toResponse(UserAccount user, Map<String, Boolean> allowedAuthMethods) {
        return toResponse(user, allowedAuthMethods, null);
    }

    private UserResponse toResponse(
            UserAccount user, Map<String, Boolean> allowedAuthMethods, UUID applicationId) {
        NameParts parts = splitDisplayName(user.getDisplayName());
        RoleGrantSummary roleGrants = resolveRoleGrants(user.getId(), applicationId);
        List<MfaFactorResponse> factors = mfaFactors.findByUserIdOrderByCreatedAtAsc(user.getId()).stream()
                .map(f -> new MfaFactorResponse(
                        f.getId().toString(),
                        f.getType(),
                        f.getLabel() != null ? f.getLabel() : f.getType(),
                        f.isVerified()))
                .toList();
        boolean locked =
                user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())
                        || "LOCKED".equalsIgnoreCase(user.getStatus());
        return new UserResponse(
                user.getId(),
                user.getTenantId(),
                user.getEmail(),
                user.getUsername() != null ? user.getUsername() : "",
                parts.firstName(),
                parts.lastName(),
                user.getStatus().toLowerCase(Locale.ROOT),
                user.isEmailVerified(),
                passwords.hasPassword(user.getId()),
                locked,
                user.getFailedLoginCount(),
                roleGrants.roleIds(),
                roleGrants.roleNames(),
                factors,
                allowedAuthMethods,
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getPhone(),
                user.isPhoneVerified());
    }

    private record RoleGrantSummary(List<String> roleIds, List<String> roleNames) {}

    private RoleGrantSummary resolveRoleGrants(UUID userId, UUID applicationId) {
        List<UserRole> grants = userRoleRepository.findByUserId(userId);
        if (applicationId != null) {
            Map<UUID, Role> rolesById = roleRepository.findByApplicationIdOrderByNameAsc(applicationId).stream()
                    .collect(Collectors.toMap(Role::getId, r -> r, (a, b) -> a, LinkedHashMap::new));
            List<Role> matched = grants.stream()
                    .map(UserRole::getRoleId)
                    .map(rolesById::get)
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparing(Role::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            return new RoleGrantSummary(
                    matched.stream().map(r -> r.getId().toString()).toList(),
                    matched.stream().map(Role::getName).toList());
        }
        Map<UUID, String> namesById = new LinkedHashMap<>();
        for (Role role : roleRepository.findAll()) {
            namesById.putIfAbsent(role.getId(), role.getName());
        }
        List<String> roleIds = grants.stream()
                .map(ur -> ur.getRoleId().toString())
                .sorted()
                .toList();
        List<String> roleNames = grants.stream()
                .map(UserRole::getRoleId)
                .map(namesById::get)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        return new RoleGrantSummary(roleIds, roleNames);
    }

    private UserResponse toResponse(UserAccount user) {
        return toResponse(user, Map.of());
    }

    private void grantApplicationAccess(UUID applicationId, UUID userId) {
        var app =
                applicationRepository
                        .findById(applicationId)
                        .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        var user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (!user.getTenantId().equals(app.getTenantId())) {
            throw new IllegalArgumentException("User must belong to the same tenant as the application");
        }
        if (!userApplications.existsByUserIdAndApplicationId(userId, applicationId)) {
            UserApplication link = new UserApplication();
            link.setUserId(userId);
            link.setApplicationId(applicationId);
            userApplications.saveAndFlush(link);
        }
        userRepository
                .findById(userId)
                .ifPresent(
                        u -> {
                            UserInviteContext.clearPendingApplicationInvite(u);
                            userRepository.save(u);
                        });
    }

    /**
     * Inviting into an application assigns that application's roles, which live in its own schema. The
     * request may arrive on the shared route without the app context; switch this transaction to it.
     */
    private void enterApplicationSchema(UUID applicationId) {
        if (applicationId == null || applicationId.equals(com.secureone.auth.application.ApplicationSchemaContext.getApplicationId())) {
            return;
        }
        schemaService.getObject().applySearchPathForApplicationId(applicationId);
        com.secureone.auth.application.ApplicationSchemaContext.setApplicationId(applicationId);
    }

    private void syncUserRoles(UUID userId, List<UUID> roleIds) {
        // Editing a user inside one application replaces only that application's roles; apps that
        // share the platform tables would otherwise lose each other's assignments.
        UUID applicationId = com.secureone.auth.application.ApplicationSchemaContext.getApplicationId();
        auditService.record(null, applicationId, "admin", "user.roles_changed", "user_account", userId,
                (roleIds == null ? 0 : roleIds.size()) + " role(s)", true);
        if (applicationId != null) {
            userRoleRepository.deleteByUserIdAndApplicationId(userId, applicationId);
        } else {
            userRoleRepository.deleteByUserId(userId);
        }
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        LinkedHashSet<UUID> unique = new LinkedHashSet<>(roleIds);
        for (UUID roleId : unique) {
            Role role = roleRepository
                    .findById(roleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));
            if (applicationId != null && !applicationId.equals(role.getApplicationId())) {
                throw new IllegalArgumentException("Role " + role.getName() + " belongs to another application.");
            }
            UserRole grant = new UserRole();
            grant.setUserId(userId);
            grant.setRoleId(roleId);
            userRoleRepository.save(grant);
        }
    }

    private void addApplicationRoles(UUID userId, UUID applicationId, List<UUID> roleIds) {
        LinkedHashSet<UUID> unique = new LinkedHashSet<>(roleIds);
        for (UUID roleId : unique) {
            Role role = roleRepository
                    .findById(roleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));
            if (!role.getApplicationId().equals(applicationId)) {
                throw new IllegalArgumentException("Role does not belong to this application");
            }
            boolean alreadyGranted = userRoleRepository.findByUserId(userId).stream()
                    .anyMatch(grant -> grant.getRoleId().equals(roleId));
            if (alreadyGranted) {
                continue;
            }
            UserRole grant = new UserRole();
            grant.setUserId(userId);
            grant.setRoleId(roleId);
            userRoleRepository.save(grant);
        }
    }

    /** Tenant Admin (and other operator roles) require application membership to appear in tenant import. */
    private void ensureApplicationAccessForOperatorRoles(UUID userId, List<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (UUID roleId : new LinkedHashSet<>(roleIds)) {
            Role role = roleRepository.findById(roleId).orElse(null);
            if (role == null || !ApplicationRbacScope.isTenantAdminOperatorRole(role)) {
                continue;
            }
            grantApplicationAccess(role.getApplicationId(), userId);
        }
    }

    private static String resolveUsername(String username, String email) {
        if (username != null && !username.isBlank()) {
            return username.trim();
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private static String displayName(String firstName, String lastName) {
        String f = firstName != null ? firstName.trim() : "";
        String l = lastName != null ? lastName.trim() : "";
        return (f + " " + l).trim();
    }

    private static NameParts splitDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return new NameParts("", "");
        }
        String[] parts = displayName.trim().split("\\s+", 2);
        return new NameParts(parts[0], parts.length > 1 ? parts[1] : "");
    }

    private static String normalizeStatus(String status, String defaultStatus) {
        if (status == null || status.isBlank()) {
            return defaultStatus;
        }
        return switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "active" -> "ACTIVE";
            case "suspended" -> "SUSPENDED";
            case "disabled" -> "DISABLED";
            case "invited", "pending" -> "PENDING";
            case "locked" -> "LOCKED";
            default -> throw new IllegalArgumentException(
                    "Unknown status '" + status.trim() + "'. Use active, suspended, disabled or pending.");
        };
    }

    private record NameParts(String firstName, String lastName) {}
}
