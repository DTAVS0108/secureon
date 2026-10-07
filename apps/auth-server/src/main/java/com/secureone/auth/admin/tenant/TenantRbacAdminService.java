package com.secureone.auth.admin.tenant;

import com.secureone.auth.admin.ConflictException;
import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.admin.tenant.TenantRbacAdminDtos.TenantPermissionCreateRequest;
import com.secureone.auth.admin.tenant.TenantRbacAdminDtos.TenantPermissionResponse;
import com.secureone.auth.admin.tenant.TenantRbacAdminDtos.TenantRoleCreateRequest;
import com.secureone.auth.admin.tenant.TenantRbacAdminDtos.TenantRoleDetailResponse;
import com.secureone.auth.admin.tenant.TenantRbacAdminDtos.TenantRoleSummaryResponse;
import com.secureone.auth.admin.tenant.TenantRbacAdminDtos.TenantRoleUpdateRequest;
import com.secureone.auth.admin.tenant.TenantRbacAdminDtos.UserTenantRoleAssignmentsResponse;
import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.tenant.rbac.TenantPermission;
import com.secureone.auth.tenant.rbac.TenantPermissionCatalog;
import com.secureone.auth.tenant.rbac.TenantPermissionRepository;
import com.secureone.auth.tenant.rbac.TenantRbacBootstrapService;
import com.secureone.auth.tenant.rbac.TenantRbacRepository;
import com.secureone.auth.tenant.rbac.TenantRole;
import com.secureone.auth.tenant.rbac.TenantRoleRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TenantRbacAdminService {

    private final TenantRepository tenants;
    private final ApplicationRepository applications;
    private final TenantPermissionRepository permissions;
    private final TenantRoleRepository roles;
    private final TenantRbacRepository rbac;
    private final TenantRbacBootstrapService bootstrap;
    private final UserAccountRepository users;

    public TenantRbacAdminService(
            TenantRepository tenants,
            ApplicationRepository applications,
            TenantPermissionRepository permissions,
            TenantRoleRepository roles,
            TenantRbacRepository rbac,
            TenantRbacBootstrapService bootstrap,
            UserAccountRepository users) {
        this.tenants = tenants;
        this.applications = applications;
        this.permissions = permissions;
        this.roles = roles;
        this.rbac = rbac;
        this.bootstrap = bootstrap;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<TenantPermissionResponse> listPermissions(UUID tenantId) {
        requireTenant(tenantId);
        ensureSeeded(tenantId);
        return permissions.findByTenantIdOrderByKeyAsc(tenantId).stream()
                .map(this::toPermissionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TenantRoleSummaryResponse> listRoles(UUID tenantId, boolean customOnly) {
        requireTenant(tenantId);
        ensureSeeded(tenantId);
        var rows = customOnly
                ? roles.findByTenantIdAndSystemRoleFalseOrderByNameAsc(tenantId)
                : roles.findByTenantIdOrderByNameAsc(tenantId);
        return rows.stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public TenantRoleDetailResponse getRole(UUID tenantId, UUID roleId) {
        return toDetail(requireRole(tenantId, roleId));
    }

    public TenantRoleDetailResponse createRole(UUID tenantId, TenantRoleCreateRequest request) {
        requireTenant(tenantId);
        ensureSeeded(tenantId);
        String name = request.name().trim();
        if (roles.existsByTenantIdAndName(tenantId, name)) {
            throw new ConflictException("Tenant role already exists: " + name);
        }
        TenantRole role = new TenantRole();
        role.setTenantId(tenantId);
        role.setName(name);
        role.setDescription(request.description());
        role.setSystemRole(false);
        try {
            // Flush now: permission/application links are inserted with JDBC and reference this row.
            roles.saveAndFlush(role);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Tenant role already exists: " + name);
        }
        syncAssignments(tenantId, role, request.permissionIds(), request.applicationIds());
        return toDetail(role);
    }

    public TenantRoleDetailResponse updateRole(UUID tenantId, UUID roleId, TenantRoleUpdateRequest request) {
        TenantRole role = requireRole(tenantId, roleId);
        String name = request.name().trim();
        if (role.isSystemRole() && !role.getName().equals(name)) {
            throw new ConflictException("System tenant roles cannot be renamed.");
        }
        if (!role.getName().equals(name) && roles.existsByTenantIdAndName(tenantId, name)) {
            throw new ConflictException("Tenant role already exists: " + name);
        }
        role.setName(name);
        role.setDescription(request.description());
        roles.save(role);
        syncAssignments(tenantId, role, request.permissionIds(), request.applicationIds());
        return toDetail(role);
    }

    public TenantPermissionResponse createPermission(UUID tenantId, TenantPermissionCreateRequest request) {
        requireTenant(tenantId);
        ensureSeeded(tenantId);
        String key = request.key().trim().toLowerCase();
        if (key.isBlank()) {
            throw new IllegalArgumentException("Permission key is required.");
        }
        if (!key.matches("[a-z0-9][a-z0-9:_-]{1,148}")) {
            throw new IllegalArgumentException(
                    "Permission key must use lowercase letters, numbers, colons, underscores, or hyphens.");
        }
        if (TenantPermissionCatalog.isCatalogKey(key)) {
            throw new ConflictException("Permission key is reserved by the platform catalog: " + key);
        }
        if (permissions.existsByTenantIdAndKey(tenantId, key)) {
            throw new ConflictException("Permission already exists: " + key);
        }
        TenantPermission row = new TenantPermission();
        row.setTenantId(tenantId);
        row.setKey(key);
        row.setDescription(request.description() != null ? request.description().trim() : "");
        try {
            permissions.save(row);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Permission already exists: " + key);
        }
        return toPermissionResponse(row);
    }

    public void deletePermission(UUID tenantId, UUID permissionId) {
        requireTenant(tenantId);
        TenantPermission permission = permissions
                .findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + permissionId));
        if (!permission.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Permission not found in this tenant.");
        }
        if (TenantPermissionCatalog.isCatalogKey(permission.getKey())) {
            throw new ConflictException("Catalog permissions cannot be deleted.");
        }
        if (rbac.countRolesByPermissionId(permissionId) > 0) {
            throw new ConflictException("Remove this permission from all roles before deleting it.");
        }
        permissions.delete(permission);
    }

    public void deleteRole(UUID tenantId, UUID roleId) {
        TenantRole role = requireRole(tenantId, roleId);
        if (role.isSystemRole()) {
            throw new ConflictException("System tenant roles cannot be deleted.");
        }
        if (rbac.countUsersByRoleId(roleId) > 0) {
            throw new ConflictException("Remove all user assignments before deleting this role.");
        }
        roles.delete(role);
    }

    @Transactional(readOnly = true)
    public UserTenantRoleAssignmentsResponse listUserRoleAssignments(UUID tenantId, UUID userId) {
        requireTenant(tenantId);
        requireTenantUser(tenantId, userId);
        return new UserTenantRoleAssignmentsResponse(
                userId, rbac.findRoleIdsByUserId(userId), rbac.findRoleNamesByUserId(userId));
    }

    public UserTenantRoleAssignmentsResponse replaceUserRoleAssignments(
            UUID tenantId, UUID userId, List<UUID> roleIds) {
        requireTenant(tenantId);
        requireTenantUser(tenantId, userId);
        ensureSeeded(tenantId);
        List<UUID> requested = roleIds != null ? roleIds : List.of();
        for (UUID roleId : requested) {
            TenantRole role = requireRole(tenantId, roleId);
            if (role.isSystemRole()) {
                throw new IllegalArgumentException("System tenant roles cannot be assigned from the roster.");
            }
        }
        rbac.replaceUserRoles(userId, requested);
        return listUserRoleAssignments(tenantId, userId);
    }

    private UserAccount requireTenantUser(UUID tenantId, UUID userId) {
        UserAccount user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!user.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("User not found in this tenant.");
        }
        return user;
    }

    private void ensureSeeded(UUID tenantId) {
        if (permissions.findByTenantIdOrderByKeyAsc(tenantId).isEmpty()) {
            bootstrap.seedForTenant(tenantId);
        }
    }

    private void syncAssignments(
            UUID tenantId, TenantRole role, List<UUID> permissionIds, List<UUID> applicationIds) {
        List<UUID> perms = permissionIds != null ? permissionIds : List.of();
        for (UUID permissionId : perms) {
            TenantPermission permission = permissions
                    .findById(permissionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + permissionId));
            if (!permission.getTenantId().equals(tenantId)) {
                throw new IllegalArgumentException("Permission does not belong to this tenant.");
            }
        }
        List<UUID> apps = applicationIds != null ? applicationIds : List.of();
        for (UUID applicationId : apps) {
            Application app = applications
                    .findById(applicationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
            if (!app.getTenantId().equals(tenantId)) {
                throw new IllegalArgumentException("Application does not belong to this tenant.");
            }
        }
        rbac.replacePermissions(role.getId(), perms);
        rbac.replaceApplications(role.getId(), apps);
    }

    private TenantRole requireRole(UUID tenantId, UUID roleId) {
        TenantRole role = roles.findById(roleId).orElseThrow(() -> new ResourceNotFoundException("Role not found"));
        if (!role.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Role not found in this tenant.");
        }
        return role;
    }

    private void requireTenant(UUID tenantId) {
        if (!tenants.existsById(tenantId)) {
            throw new ResourceNotFoundException("Tenant not found: " + tenantId);
        }
    }

    private TenantPermissionResponse toPermissionResponse(TenantPermission permission) {
        return new TenantPermissionResponse(
                permission.getId(), permission.getTenantId(), permission.getKey(), permission.getDescription());
    }

    private TenantRoleSummaryResponse toSummary(TenantRole role) {
        return new TenantRoleSummaryResponse(
                role.getId(),
                role.getTenantId(),
                role.getName(),
                role.getDescription(),
                role.isSystemRole(),
                rbac.countUsersByRoleId(role.getId()),
                rbac.countPermissionsByRoleId(role.getId()),
                rbac.findApplicationIdsByRoleId(role.getId()).size());
    }

    private TenantRoleDetailResponse toDetail(TenantRole role) {
        List<UUID> permissionIds = rbac.findPermissionIdsByRoleId(role.getId());
        List<UUID> applicationIds = rbac.findApplicationIdsByRoleId(role.getId());
        List<TenantPermissionResponse> catalog =
                permissions.findByTenantIdOrderByKeyAsc(role.getTenantId()).stream()
                        .map(this::toPermissionResponse)
                        .toList();
        return new TenantRoleDetailResponse(
                role.getId(),
                role.getTenantId(),
                role.getName(),
                role.getDescription(),
                role.isSystemRole(),
                permissionIds,
                applicationIds,
                catalog,
                rbac.countUsersByRoleId(role.getId()));
    }
}
