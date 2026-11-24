package com.secureone.auth.iga;

import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.application.ApplicationMembershipService;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.rbac.Role;
import com.secureone.auth.rbac.RoleRepository;
import com.secureone.auth.rbac.UserRole;
import com.secureone.auth.rbac.UserRoleRepository;
import com.secureone.auth.tenant.rbac.TenantRbacRepository;
import com.secureone.auth.tenant.rbac.TenantRole;
import com.secureone.auth.tenant.rbac.TenantRoleRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccessRequestGrantService {

    private final UserAccountRepository users;
    private final TenantRoleRepository tenantRoles;
    private final TenantRbacRepository tenantRbac;
    private final RoleRepository applicationRoles;
    private final UserRoleRepository userRoles;
    private final ApplicationMembershipService memberships;
    private final AuditService auditService;
    private final com.secureone.auth.application.ApplicationRepository applications;
    private final org.springframework.beans.factory.ObjectProvider<com.secureone.auth.application.ApplicationSchemaService> schemas;

    public AccessRequestGrantService(
            UserAccountRepository users,
            TenantRoleRepository tenantRoles,
            TenantRbacRepository tenantRbac,
            RoleRepository applicationRoles,
            UserRoleRepository userRoles,
            ApplicationMembershipService memberships,
            AuditService auditService,
            com.secureone.auth.application.ApplicationRepository applications,
            org.springframework.beans.factory.ObjectProvider<com.secureone.auth.application.ApplicationSchemaService> schemas) {
        this.users = users;
        this.tenantRoles = tenantRoles;
        this.tenantRbac = tenantRbac;
        this.applicationRoles = applicationRoles;
        this.userRoles = userRoles;
        this.memberships = memberships;
        this.auditService = auditService;
        this.applications = applications;
        this.schemas = schemas;
    }

    /**
     * Applies access grants when an IGA request is approved. Returns metadata merged into the API response.
     */
    public Map<String, Object> applyGrant(AccessRequest request, UUID reviewerId) {
        String resourceType = normalizeResourceType(request.getResourceType());
        if (!isRoleResourceType(resourceType)) {
            return Map.of("grantApplied", false);
        }

        UUID beneficiaryId = request.getTargetUserId() != null ? request.getTargetUserId() : request.getRequesterUserId();
        requireTenantUser(request.getTenantId(), beneficiaryId);

        if ("tenant_role".equals(resourceType) || "role".equals(resourceType)) {
            Optional<TenantRole> tenantRole = resolveTenantRole(request);
            if (tenantRole.isPresent()) {
                return grantTenantRole(request, tenantRole.get(), beneficiaryId, reviewerId);
            }
            if ("tenant_role".equals(resourceType)) {
                throw roleNotFound(request.getResourceId());
            }
        }

        // Application roles, memberships and role grants live in each application's own schema: look the role up
        // there (in the named application, or in each of the tenant's applications) and grant it in that schema.
        UUID previous = com.secureone.auth.application.ApplicationSchemaContext.getApplicationId();
        try {
            Role applicationRole = resolveApplicationRole(request);
            return grantApplicationRole(request, applicationRole, beneficiaryId, reviewerId);
        } finally {
            enterSchema(previous);
        }
    }

    /** Routes this transaction (and repository calls) to the application's schema; null: the platform schema. */
    private void enterSchema(UUID applicationId) {
        com.secureone.auth.application.ApplicationSchemaService schema = schemas.getIfAvailable();
        if (schema != null) {
            schema.applySearchPathForApplicationId(applicationId);
        }
        com.secureone.auth.application.ApplicationSchemaContext.setApplicationId(applicationId);
    }

    /** The applications whose schema may hold the requested role: the request's, or every application of the tenant. */
    private List<UUID> candidateApplications(AccessRequest request) {
        if (request.getApplicationId() != null) {
            return applications.findById(request.getApplicationId())
                    .filter(app -> request.getTenantId().equals(app.getTenantId()))
                    .map(app -> List.of(app.getId()))
                    .orElse(List.of());
        }
        return applications.findByTenantIdOrderByCreatedAtDesc(request.getTenantId()).stream()
                .map(com.secureone.auth.application.Application::getId)
                .toList();
    }

    private Map<String, Object> grantTenantRole(
            AccessRequest request, TenantRole role, UUID beneficiaryId, UUID reviewerId) {
        if (role.isSystemRole()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "System tenant roles cannot be granted via access requests.");
        }
        tenantRbac.grantUserRole(beneficiaryId, role.getId());
        auditService.record(
                request.getTenantId(),
                reviewerId != null ? reviewerId.toString() : "admin",
                "iga.tenant_role_granted",
                "user_account",
                beneficiaryId,
                role.getName(),
                true);
        return grantResult("tenant_role", role.getId(), beneficiaryId, role.getName());
    }

    private Map<String, Object> grantApplicationRole(
            AccessRequest request, Role role, UUID beneficiaryId, UUID reviewerId) {
        memberships.ensureMember(role.getApplicationId(), beneficiaryId);
        boolean alreadyGranted = userRoles.findByUserId(beneficiaryId).stream()
                .anyMatch(grant -> grant.getRoleId().equals(role.getId()));
        if (!alreadyGranted) {
            UserRole grant = new UserRole();
            grant.setUserId(beneficiaryId);
            grant.setRoleId(role.getId());
            userRoles.saveAndFlush(grant); // written before the transaction leaves the application's schema
        }
        auditService.record(
                request.getTenantId(),
                role.getApplicationId(),
                reviewerId != null ? reviewerId.toString() : "admin",
                "iga.application_role_granted",
                "user_account",
                beneficiaryId,
                role.getName(),
                true);
        return grantResult("application_role", role.getId(), beneficiaryId, role.getName());
    }

    private Optional<TenantRole> resolveTenantRole(AccessRequest request) {
        UUID roleId = parseUuid(request.getResourceId());
        if (roleId != null) {
            return tenantRoles.findById(roleId)
                    .filter(role -> request.getTenantId().equals(role.getTenantId()));
        }
        return tenantRoles.findByTenantIdAndName(request.getTenantId(), request.getResourceId().trim());
    }

    /**
     * Finds the role in the candidate applications' schemas and leaves the transaction in the schema of the
     * application that has it (where the grant is then written).
     */
    private Role resolveApplicationRole(AccessRequest request) {
        UUID roleId = parseUuid(request.getResourceId());
        String roleName = request.getResourceId().trim();
        List<Role> matches = new java.util.ArrayList<>();
        for (UUID applicationId : candidateApplications(request)) {
            enterSchema(applicationId);
            Optional<Role> found = roleId != null
                    ? applicationRoles.findById(roleId)
                    : applicationRoles.findByApplicationIdAndName(applicationId, roleName);
            found.filter(role -> applicationId.equals(role.getApplicationId()))
                    .filter(role -> request.getTenantId().equals(role.getTenantId()))
                    .ifPresent(matches::add);
            if (roleId != null && !matches.isEmpty()) {
                break;
            }
        }
        if (matches.isEmpty()) {
            throw roleNotFound(request.getResourceId());
        }
        if (matches.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Multiple application roles named '"
                            + roleName
                            + "' exist. Set applicationId on the request or use the role UUID.");
        }
        Role role = matches.getFirst();
        enterSchema(role.getApplicationId());
        return role;
    }

    private UserAccount requireTenantUser(UUID tenantId, UUID userId) {
        UserAccount user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (!tenantId.equals(user.getTenantId())) {
            throw new ResourceNotFoundException("User not found in this tenant.");
        }
        return user;
    }

    private static Map<String, Object> grantResult(String grantType, UUID roleId, UUID userId, String roleName) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("grantApplied", true);
        out.put("grantType", grantType);
        out.put("grantedRoleId", roleId.toString());
        out.put("grantedRoleName", roleName);
        out.put("grantedUserId", userId.toString());
        return out;
    }

    private static ResourceNotFoundException roleNotFound(String resourceId) {
        return new ResourceNotFoundException("Role not found: " + resourceId);
    }

    private static boolean isRoleResourceType(String resourceType) {
        return "role".equals(resourceType)
                || "tenant_role".equals(resourceType)
                || "application_role".equals(resourceType)
                || "app_role".equals(resourceType);
    }

    private static String normalizeResourceType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
