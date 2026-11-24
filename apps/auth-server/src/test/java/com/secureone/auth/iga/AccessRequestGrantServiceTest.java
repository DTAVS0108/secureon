package com.secureone.auth.iga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationMembershipService;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.application.ApplicationSchemaService;
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
import org.springframework.beans.factory.ObjectProvider;
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
    @Mock
    private ApplicationRepository applications;
    @Mock
    private ObjectProvider<ApplicationSchemaService> schemas;
    @Mock
    private ApplicationSchemaService schema;

    private AccessRequestGrantService service;

    @BeforeEach
    void setUp() {
        service = new AccessRequestGrantService(
                users, tenantRoles, tenantRbac, applicationRoles, userRoles, memberships, auditService, applications, schemas);
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
        stubApplications(tenantId, applicationId);
        when(schemas.getIfAvailable()).thenReturn(schema);
        when(applicationRoles.findById(roleId)).thenReturn(Optional.of(role));
        when(userRoles.findByUserId(userId)).thenReturn(List.of());

        Map<String, Object> result = service.applyGrant(request, UUID.randomUUID());

        assertTrue((Boolean) result.get("grantApplied"));
        assertEquals("application_role", result.get("grantType"));
        verify(memberships).ensureMember(applicationId, userId);
        verify(userRoles).saveAndFlush(any(UserRole.class));
        // Looked up and granted in the application's own schema, then back to the platform schema
        verify(schema, org.mockito.Mockito.atLeastOnce()).applySearchPathForApplicationId(applicationId);
        verify(schema).applySearchPathForApplicationId(null);
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
        when(applications.findByTenantIdOrderByCreatedAtDesc(tenantId))
                .thenReturn(List.of(app(tenantId, a.getApplicationId()), app(tenantId, b.getApplicationId())));
        when(applicationRoles.findByApplicationIdAndName(a.getApplicationId(), "Member")).thenReturn(Optional.of(a));
        when(applicationRoles.findByApplicationIdAndName(b.getApplicationId(), "Member")).thenReturn(Optional.of(b));

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
        when(applications.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(app(tenantId, applicationId)));
        when(applicationRoles.findById(roleId)).thenReturn(Optional.of(role));
        when(userRoles.findByUserId(userId)).thenReturn(List.of(existing));

        service.applyGrant(request, UUID.randomUUID());

        verify(userRoles, never()).saveAndFlush(any());
        verify(memberships).ensureMember(applicationId, userId);
    }

    @Test
    void approveFindsApplicationRoleByNameInTheApplicationSchema() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        Role role = applicationRole(tenantId, applicationId, UUID.randomUUID(), "Invoice submitter");

        AccessRequest request = baseRequest(tenantId, userId);
        request.setResourceType("application_role");
        request.setResourceId("Invoice submitter");
        request.setApplicationId(applicationId);

        stubTenantUser(tenantId, userId);
        stubApplications(tenantId, applicationId);
        when(applicationRoles.findByApplicationIdAndName(applicationId, "Invoice submitter")).thenReturn(Optional.of(role));
        when(userRoles.findByUserId(userId)).thenReturn(List.of());

        Map<String, Object> result = service.applyGrant(request, UUID.randomUUID());

        assertEquals(role.getId().toString(), result.get("grantedRoleId"));
        verify(userRoles).saveAndFlush(any(UserRole.class));
    }

    @Test
    void approveRefusesRoleOfAnotherTenantsApplication() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID otherApp = UUID.randomUUID();
        AccessRequest request = baseRequest(tenantId, userId);
        request.setResourceType("application_role");
        request.setResourceId(UUID.randomUUID().toString());
        request.setApplicationId(otherApp);

        stubTenantUser(tenantId, userId);
        when(applications.findById(otherApp)).thenReturn(Optional.of(app(UUID.randomUUID(), otherApp)));

        assertThrows(com.secureone.auth.admin.ResourceNotFoundException.class, () -> service.applyGrant(request, UUID.randomUUID()));
    }

    private void stubApplications(UUID tenantId, UUID applicationId) {
        when(applications.findById(applicationId)).thenReturn(Optional.of(app(tenantId, applicationId)));
    }

    private static Application app(UUID tenantId, UUID applicationId) {
        Application app = new Application();
        app.setId(applicationId);
        app.setTenantId(tenantId);
        return app;
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
