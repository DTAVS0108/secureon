package com.secureone.auth.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.secureone.auth.admin.console.AdminConsoleCapabilityService;
import com.secureone.auth.admin.role.RoleApplicationLookup;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.platform.PlatformFeatures;
import com.secureone.auth.user.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

/** Configuration as code API: platform operators only, and only while the platform switch is on. */
@ExtendWith(MockitoExtension.class)
class AdminAccessInterceptorConfigAsCodeTest {

    @Mock AdminAccessService access;
    @Mock AdminPermissionService permissions;
    @Mock AdminConsoleCapabilityService consoleCapabilities;
    @Mock RoleApplicationLookup rbacLookup;
    @Mock UserAccountRepository users;
    @Mock ApplicationRepository applications;
    @Mock PlatformFeatures platformFeatures;

    AdminAccessInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AdminAccessInterceptor(
                access, permissions, consoleCapabilities, rbacLookup, users, applications, platformFeatures);
    }

    private static MockHttpServletRequest consoleRequest(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setAttribute(AdminCredentialAuthenticationFilter.CONSOLE_SESSION_ATTR, Boolean.TRUE);
        return request;
    }

    @Test
    void refusedWithNotFoundWhileTheSwitchIsOff() throws Exception {
        when(platformFeatures.configAsCodeEnabled()).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(
                consoleRequest("/api/admin/v1/config/platform/export"), response, new Object());

        assertFalse(proceed);
        assertEquals(404, response.getStatus());
        assertTrue(response.getContentAsString().contains("Configuration as code is turned off"));
    }

    @Test
    void allowedWhileTheSwitchIsOn() throws Exception {
        when(platformFeatures.configAsCodeEnabled()).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(
                consoleRequest("/api/admin/v1/config/tenants/tenant-a/export"), response, new Object());

        assertTrue(proceed);
        assertEquals(200, response.getStatus());
    }

    @Test
    void stillRequiresAPlatformOperatorWhenOn() {
        doThrow(new AccessDeniedException("Platform settings require the platform operator account"))
                .when(access).requirePlatformSettingsAccess(any(), any());

        assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preHandle(
                        consoleRequest("/api/admin/v1/config/import"), new MockHttpServletResponse(), new Object()));
    }
}
