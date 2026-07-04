package com.secureone.auth.iga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AccessRequestGrantServiceTest {

    @Mock
    private UserAccountRepository users;
    @Mock
    private TenantRoleRepository tenantRoles;
    @Mock
    private TenantRbacRepository tenantRbac;
    @Mock
    private RoleRepository applicationRoles;
    @Mock
    private UserRoleRepository userRoles;
    @Mock
    private ApplicationMembershipService memberships;
    @Mock
    private AuditService auditService;

    private AccessRequestGrantService service;

    @BeforeEach
    void setUp() {
        service = new AccessRequestGrantService(
                users, tenantRoles, tenantRbac, applicationRoles, userRoles, memberships, auditService);
    }

    @Test
    void approveGrantsTenantRoleByName() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        TenantRole role = tenantRole(tenantId, "Support Agent", false);

        AccessRequest request = baseRequest(tenantId, userId);
        request.setResourceType("role");
        request.setResourceId("Support Agent");

        stubTenantUser(tenantId, userId);
        when(tenantRoles.findByTenantIdAndName(tenantId, "Support Agent")).thenReturn(Optional.of(role));

        Map<String, Object> result = service.applyGrant(request, reviewerId);

        assertTrue((Boolean) result.get("grantApplied"));
        assertEquals("tenant_role", result.get("grantType"));
        verify(tenantRbac).grantUserRole(userId, role.getId());
        verify(userRoles, never()).save(any());
    }

    @Test
    void approveGrantsApplicationRoleByUuid() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Role role = applicationRole(tenantId, applicationId, roleId, "Editor");

        AccessRequest request = baseRequest(tenantId, userId);
        request.setResourceType("application_role");
        request.setResourceId(roleId.toString());
        request.setApplicationId(applicationId);

        stubTenantUser(tenantId, userId);
        when(applicationRoles.findById(roleId)).thenReturn(Optional.of(role));
        when(userRoles.findByUserId(userId)).thenReturn(List.of());

        Map<String, Object> result = service.applyGrant(request, UUID.randomUUID());

        assertTrue((Boolean) result.get("grantApplied"));
        assertEquals("application_role", result.get("grantType"));
        verify(memberships).ensureMember(applicationId, userId);
        verify(userRoles).save(any(UserRole.class));
    }

    @Test
    void approveSkipsGrantForNonRoleResourceType() {
        AccessRequest request = baseRequest(UUID.randomUUID(), UUID.randomUUID());
        request.setResourceType("group");
        request.setResourceId("engineering");

        Map<String, Object> result = service.applyGrant(request, UUID.randomUUID());

        assertFalse((Boolean) result.get("grantApplied"));
        verify(tenantRbac, never()).grantUserRole(any(), any());
    }

    @Test
    void approveRejectsSystemTenantRole() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TenantRole role = tenantRole(tenantId, "Tenant Admin", true);

        AccessRequest request = baseRequest(tenantId, userId);
        request.setResourceType("tenant_role");
        request.setResourceId(role.getId().toString());

        stubTenantUser(tenantId, userId);
        when(tenantRoles.findById(role.getId())).thenReturn(Optional.of(role));

        assertThrows(ResponseStatusException.class, () -> service.applyGrant(request, UUID.randomUUID()));
    }

    @Test
    void approveRejectsAmbiguousApplicationRoleName() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Role a = applicationRole(tenantId, UUID.randomUUID(), UUID.randomUUID(), "Member");
        Role b = applicationRole(tenantId, UUID.randomUUID(), UUID.randomUUID(), "Member");

        AccessRequest request = baseRequest(tenantId, userId);
        request.setResourceType("application_role");
        request.setResourceId("Member");

        stubTenantUser(tenantId, userId);
        when(applicationRoles.findByTenantIdOrderByNameAsc(tenantId)).thenReturn(List.of(a, b));

        assertThrows(ResponseStatusException.class, () -> service.applyGrant(request, UUID.randomUUID()));
    }

    @Test
    void approveUsesTargetUserWhenPresent() {
        UUID tenantId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        TenantRole role = tenantRole(tenantId, "Viewer", false);

        AccessRequest request = baseRequest(tenantId, requesterId);
        request.setTargetUserId(targetId);
        request.setResourceType("tenant_role");
        request.setResourceId("Viewer");

        stubTenantUser(tenantId, targetId);
        when(tenantRoles.findByTenantIdAndName(tenantId, "Viewer")).thenReturn(Optional.of(role));

        Map<String, Object> result = service.applyGrant(request, UUID.randomUUID());

        assertEquals(targetId.toString(), result.get("grantedUserId"));
        verify(tenantRbac).grantUserRole(targetId, role.getId());
    }

    @Test
    void approveDoesNotDuplicateExistingApplicationRole() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Role role = applicationRole(tenantId, applicationId, roleId, "Editor");

        AccessRequest request = baseRequest(tenantId, userId);
        request.setResourceType("app_role");
        request.setResourceId(roleId.toString());

        UserRole existing = new UserRole();
        existing.setUserId(userId);
        existing.setRoleId(roleId);

        stubTenantUser(tenantId, userId);
        when(applicationRoles.findById(roleId)).thenReturn(Optional.of(role));
        when(userRoles.findByUserId(userId)).thenReturn(List.of(existing));

        service.applyGrant(request, UUID.randomUUID());

        verify(userRoles, never()).save(any());
        verify(memberships).ensureMember(applicationId, userId);
    }

    private void stubTenantUser(UUID tenantId, UUID userId) {
        UserAccount user = new UserAccount();
        user.setId(userId);
        user.setTenantId(tenantId);
        when(users.findById(userId)).thenReturn(Optional.of(user));
    }

    private static AccessRequest baseRequest(UUID tenantId, UUID requesterUserId) {
        AccessRequest request = new AccessRequest();
        request.setTenantId(tenantId);
        request.setRequesterUserId(requesterUserId);
        request.setStatus("PENDING");
        return request;
    }

    private static TenantRole tenantRole(UUID tenantId, String name, boolean system) {
        TenantRole role = new TenantRole();
        role.setId(UUID.randomUUID());
        role.setTenantId(tenantId);
        role.setName(name);
        role.setSystemRole(system);
        return role;
    }

    private static Role applicationRole(UUID tenantId, UUID applicationId, UUID roleId, String name) {
        Role role = new Role();
        role.setId(roleId);
        role.setTenantId(tenantId);
        role.setApplicationId(applicationId);
        role.setName(name);
        return role;
    }
}
