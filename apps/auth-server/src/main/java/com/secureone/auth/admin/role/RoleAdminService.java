package com.secureone.auth.admin.role;

import com.secureone.auth.admin.ConflictException;
import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.rbac.Permission;
import com.secureone.auth.rbac.PermissionRepository;
import com.secureone.auth.rbac.ApplicationRbacScope;
import com.secureone.auth.rbac.Role;
import com.secureone.auth.rbac.RoleRbacRepository;
import com.secureone.auth.rbac.RoleRepository;
import com.secureone.auth.rbac.UserRole;
import com.secureone.auth.rbac.UserRoleRepository;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RoleAdminService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RoleRbacRepository rbac;
    private final UserRoleRepository userRoleRepository;
    private final UserAccountRepository userAccountRepository;
    private final TenantRepository tenantRepository;
    private final ApplicationRepository applicationRepository;
    private final AuditService auditService;

    public RoleAdminService(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            RoleRbacRepository rbac,
            UserRoleRepository userRoleRepository,
            UserAccountRepository userAccountRepository,
            TenantRepository tenantRepository,
            ApplicationRepository applicationRepository,
            AuditService auditService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rbac = rbac;
        this.userRoleRepository = userRoleRepository;
        this.userAccountRepository = userAccountRepository;
        this.tenantRepository = tenantRepository;
        this.applicationRepository = applicationRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<RoleAdminDtos.PermissionResponse> listPermissions(UUID applicationId) {
        requireApplication(applicationId);
        return permissionRepository.findByApplicationIdOrderByKeyAsc(applicationId).stream()
                .filter(p -> ApplicationRbacScope.isApplicationScopedPermissionKey(p.getKey()))
                .map(this::toPermissionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoleAdminDtos.RoleSummaryResponse> listRoles(UUID tenantId, UUID applicationId) {
        List<Role> roles =
                applicationId != null
                        ? roleRepository.findByApplicationIdOrderByNameAsc(applicationId)
                        : tenantId != null
                                ? roleRepository.findByTenantIdOrderByNameAsc(tenantId)
                                : roleRepository.findAll();
        return roles.stream()
                .filter(role -> applicationId == null || ApplicationRbacScope.isListedInApplicationRoleCatalog(role))
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleAdminDtos.RoleDetailResponse getRole(UUID id) {
        return getRole(id, null);
    }

    @Transactional(readOnly = true)
    public RoleAdminDtos.RoleDetailResponse getRole(UUID id, UUID applicationId) {
        Role role = requireRole(id, applicationId);
        return toDetail(role);
    }

    @Transactional(readOnly = true)
    public List<RoleAdminDtos.RoleAssignedUserResponse> listUsersForRole(UUID roleId, UUID applicationId) {
        Role role = requireRole(roleId, applicationId);
        Map<UUID, Instant> grantedAtByUser = new LinkedHashMap<>();
        for (UserRole assignment : userRoleRepository.findByRoleIdOrderByGrantedAtDesc(role.getId())) {
            if (assignment.getUserId() != null) {
                grantedAtByUser.putIfAbsent(assignment.getUserId(), assignment.getGrantedAt());
            }
        }
        return grantedAtByUser.keySet().stream()
                .map(userAccountRepository::findById)
                .flatMap(java.util.Optional::stream)
                .filter(u -> role.getTenantId().equals(u.getTenantId()))
                .map(u ->
                        new RoleAdminDtos.RoleAssignedUserResponse(
                                u.getId(),
                                u.getLoginKey(),
                                u.getUsername(),
                                u.getDisplayName(),
                                u.getStatus(),
                                u.isEmailVerified(),
                                grantedAtByUser.get(u.getId())))
                .sorted(Comparator.comparing(
                        RoleAdminDtos.RoleAssignedUserResponse::email, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public RoleAdminDtos.RoleDetailResponse create(RoleAdminDtos.RoleCreateRequest request) {
        requireTenant(request.tenantId());
        requireApplication(request.applicationId());
        String name = request.name().trim();
        if (roleRepository.existsByApplicationIdAndName(request.applicationId(), name)) {
            throw new ConflictException("Role name already exists for this application: " + name);
        }
        Role role = new Role();
        role.setTenantId(request.tenantId());
        role.setApplicationId(request.applicationId());
        role.setName(name);
        role.setDescription(request.description());
        role.setComposite(request.isComposite());
        role.setSystemRole(false);
        role.setDefaultRole(false);
        try {
            roleRepository.save(role);
            roleRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Role name already exists for this application: " + name);
        }
        syncAssignments(role, request.permissionIds(), request.childRoleIds());
        auditService.record(request.tenantId(), "admin", "role.created", "role", role.getId(), role.getName(), true);
        return toDetail(role);
    }

    public RoleAdminDtos.RoleDetailResponse update(UUID id, RoleAdminDtos.RoleUpdateRequest request) {
        return update(id, null, request);
    }

    public RoleAdminDtos.RoleDetailResponse update(
            UUID id, UUID applicationId, RoleAdminDtos.RoleUpdateRequest request) {
        Role role = requireRole(id, applicationId);
        String name = request.name().trim();
        if (role.isSystemRole() && !role.getName().equals(name)) {
            throw new ConflictException("System roles cannot be renamed.");
        }
        if (!role.getName().equals(name)
                && roleRepository.existsByApplicationIdAndName(role.getApplicationId(), name)) {
            throw new ConflictException("Role name already exists for this application: " + name);
        }
        role.setName(name);
        role.setDescription(request.description());
        role.setComposite(request.isComposite());
        try {
            roleRepository.save(role);
            roleRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Role name already exists for this application: " + name);
        }
        syncAssignments(role, request.permissionIds(), request.childRoleIds());
        auditService.record(role.getTenantId(), "admin", "role.updated", "role", role.getId(), role.getName(), true);
        return toDetail(role);
    }

    public void delete(UUID id) {
        delete(id, null);
    }

    public void delete(UUID id, UUID applicationId) {
        Role role = requireRole(id, applicationId);
        if (role.isSystemRole()) {
            throw new ConflictException("System roles cannot be deleted.");
        }
        if (userRoleRepository.countByRoleId(id) > 0) {
            throw new ConflictException("Remove all user assignments before deleting this role.");
        }
        roleRepository.delete(role);
        auditService.record(role.getTenantId(), "admin", "role.deleted", "role", id, role.getName(), true);
    }

    public RoleAdminDtos.RoleDetailResponse assignPermission(UUID roleId, UUID applicationId, UUID permissionId) {
        Role role = requireRole(roleId, applicationId);
        assertApplicationProductRole(role);
        validatePermissions(role.getApplicationId(), List.of(permissionId));
        if (!rbac.hasPermission(role.getId(), permissionId)) {
            rbac.addPermission(role.getId(), permissionId);
            auditService.record(
                    role.getTenantId(),
                    "admin",
                    "role.permission.assigned",
                    "role",
                    role.getId(),
                    permissionId.toString(),
                    true);
        }
        return toDetail(role);
    }

    public RoleAdminDtos.RoleDetailResponse removePermission(UUID roleId, UUID applicationId, UUID permissionId) {
        Role role = requireRole(roleId, applicationId);
        assertApplicationProductRole(role);
        if (rbac.hasPermission(role.getId(), permissionId)) {
            rbac.removePermission(role.getId(), permissionId);
            auditService.record(
                    role.getTenantId(),
                    "admin",
                    "role.permission.removed",
                    "role",
                    role.getId(),
                    permissionId.toString(),
                    true);
        }
        return toDetail(role);
    }

    private void syncAssignments(Role role, List<UUID> permissionIds, List<UUID> childRoleIds) {
        List<UUID> perms = permissionIds != null ? permissionIds : List.of();
        List<UUID> children = childRoleIds != null ? childRoleIds : List.of();
        validatePermissions(role.getApplicationId(), perms);
        validateChildRoles(role, children);
        if (role.isComposite() && rbac.wouldCreateCompositeCycle(role.getId(), children)) {
            throw new IllegalArgumentException("Circular role inheritance is not allowed.");
        }
        rbac.replacePermissions(role.getId(), perms);
        if (role.isComposite()) {
            rbac.replaceChildRoles(role.getId(), children);
        } else if (!children.isEmpty()) {
            throw new IllegalArgumentException("Child roles can only be assigned to composite roles.");
        } else {
            rbac.replaceChildRoles(role.getId(), List.of());
        }
    }

    private void validatePermissions(UUID applicationId, List<UUID> permissionIds) {
        for (UUID permissionId : permissionIds) {
            Permission permission =
                    permissionRepository
                            .findById(permissionId)
                            .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + permissionId));
            if (!applicationId.equals(permission.getApplicationId())) {
                throw new IllegalArgumentException("Permission does not belong to this application.");
            }
        }
    }

    private void validateChildRoles(Role parent, List<UUID> childRoleIds) {
        for (UUID childId : childRoleIds) {
            Role child = requireRole(childId);
            if (!parent.getApplicationId().equals(child.getApplicationId())) {
                throw new IllegalArgumentException("Inherited roles must belong to the same application.");
            }
            if (child.isComposite()) {
                throw new IllegalArgumentException("Composite roles cannot inherit other composite roles.");
            }
            if (!ApplicationRbacScope.isApplicationScopedRole(child)) {
                throw new IllegalArgumentException("Console operator roles cannot be inherited.");
            }
        }
    }

    private RoleAdminDtos.RoleSummaryResponse toSummary(Role role) {
        return new RoleAdminDtos.RoleSummaryResponse(
                role.getId(),
                role.getTenantId(),
                role.getApplicationId(),
                role.getName(),
                role.getDescription(),
                role.isComposite(),
                role.isSystemRole(),
                role.isDefaultRole(),
                RoleLabel.from(role).name(),
                userRoleRepository.countByRoleId(role.getId()),
                rbac.countEffectivePermissions(role.getId()),
                rbac.countChildRoles(role.getId()));
    }

    private RoleAdminDtos.RoleDetailResponse toDetail(Role role) {
        List<UUID> permissionIds = rbac.findPermissionIdsByRoleId(role.getId());
        List<UUID> childRoleIds =
                role.isComposite() ? rbac.findChildRoleIdsByParentId(role.getId()) : List.of();
        Map<UUID, String> roleNamesById = new LinkedHashMap<>();
        for (Role other : roleRepository.findByApplicationIdOrderByNameAsc(role.getApplicationId())) {
            roleNamesById.put(other.getId(), other.getName());
        }
        List<RoleAdminDtos.RoleRefResponse> childRoles = childRoleIds.stream()
                .map(id -> new RoleAdminDtos.RoleRefResponse(id, roleNamesById.getOrDefault(id, id.toString())))
                .toList();
        List<RoleAdminDtos.PermissionResponse> permissions = permissionCatalogForRole(role, permissionIds);
        return new RoleAdminDtos.RoleDetailResponse(
                role.getId(),
                role.getTenantId(),
                role.getApplicationId(),
                role.getName(),
                role.getDescription(),
                role.isComposite(),
                role.isSystemRole(),
                role.isDefaultRole(),
                RoleLabel.from(role).name(),
                userRoleRepository.countByRoleId(role.getId()),
                permissionIds,
                childRoleIds,
                childRoles,
                permissions);
    }

    private List<RoleAdminDtos.PermissionResponse> permissionCatalogForRole(Role role, List<UUID> grantedPermissionIds) {
        var stream = permissionRepository.findByApplicationIdOrderByKeyAsc(role.getApplicationId()).stream();
        if (ApplicationRbacScope.isTenantAdminOperatorRole(role)) {
            return stream
                    .filter(p -> grantedPermissionIds.contains(p.getId()))
                    .map(this::toPermissionResponse)
                    .toList();
        }
        return stream
                .filter(p -> ApplicationRbacScope.isApplicationScopedPermissionKey(p.getKey()))
                .map(this::toPermissionResponse)
                .toList();
    }

    private RoleAdminDtos.PermissionResponse toPermissionResponse(Permission permission) {
        String key = permission.getKey();
        int colon = key.indexOf(':');
        String resource = colon > 0 ? key.substring(0, colon) : key;
        String action = colon > 0 ? key.substring(colon + 1) : "access";
        return new RoleAdminDtos.PermissionResponse(
                permission.getId(),
                permission.getApplicationId(),
                key,
                resource,
                action,
                permission.getDescription(),
                rbac.countRolesUsingPermission(permission.getId()));
    }

    private Role requireRole(UUID id) {
        return requireRole(id, null);
    }

    private Role requireRole(UUID id, UUID applicationId) {
        Role role =
                roleRepository
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));
        if (applicationId != null && !applicationId.equals(role.getApplicationId())) {
            throw new ResourceNotFoundException("Role not found in this application.");
        }
        if (applicationId != null && !ApplicationRbacScope.isListedInApplicationRoleCatalog(role)) {
            throw new ResourceNotFoundException("Role not found in this application.");
        }
        return role;
    }

    private void requireTenant(UUID tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new ResourceNotFoundException("Tenant not found: " + tenantId);
        }
    }

    private void requireApplication(UUID applicationId) {
        if (!applicationRepository.existsById(applicationId)) {
            throw new ResourceNotFoundException("Application not found: " + applicationId);
        }
    }

    private void assertApplicationProductRole(Role role) {
        if (!ApplicationRbacScope.isApplicationScopedRole(role)) {
            throw new ConflictException("Console operator roles cannot be modified from application RBAC.");
        }
    }
}
