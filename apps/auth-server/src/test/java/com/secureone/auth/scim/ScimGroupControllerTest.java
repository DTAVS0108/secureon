package com.secureone.auth.scim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ScimGroupControllerTest {

    @Mock
    private ScimGroupService groups;

    @InjectMocks
    private ScimGroupController controller;

    @Test
    void listRequiresTenantId() {
        assertThrows(ResponseStatusException.class, () -> controller.list(new org.springframework.mock.web.MockHttpServletRequest(), null));
    }

    @Test
    void listReturnsResources() {
        UUID tenantId = UUID.randomUUID();
        when(groups.list(tenantId))
                .thenReturn(List.of(Map.of("id", UUID.randomUUID().toString(), "displayName", "Admins")));

        Map<String, Object> out = controller.list(new org.springframework.mock.web.MockHttpServletRequest(), tenantId);

        assertEquals(1, out.get("totalResults"));
        assertEquals(1, ((List<?>) out.get("Resources")).size());
    }

    @Test
    void tenantTokenListsItsOwnTenantWithoutTenantId() {
        UUID tenantId = UUID.randomUUID();
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setAttribute(ScimScope.TENANT_ATTRIBUTE, tenantId);
        when(groups.list(tenantId)).thenReturn(List.of());

        assertEquals(0, controller.list(request, null).get("totalResults"));
        assertThrows(ResponseStatusException.class, () -> controller.list(request, UUID.randomUUID()));
    }

    @Test
    void tenantTokenCannotReachAnotherTenantsGroup() {
        UUID groupId = UUID.randomUUID();
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setAttribute(ScimScope.TENANT_ATTRIBUTE, UUID.randomUUID());
        when(groups.tenantOf(groupId)).thenReturn(UUID.randomUUID());

        assertThrows(ResponseStatusException.class, () -> controller.delete(request, groupId));
    }
}
