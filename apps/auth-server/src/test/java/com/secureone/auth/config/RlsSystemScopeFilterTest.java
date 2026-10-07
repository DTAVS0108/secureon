package com.secureone.auth.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RlsSystemScopeFilterTest {

    @Test
    void signInAndProtocolEndpointsRunWithSystemScope() {
        assertTrue(RlsSystemScopeFilter.isSystemPath("/oauth2/token"));
        assertTrue(RlsSystemScopeFilter.isSystemPath("/userinfo"));
        assertTrue(RlsSystemScopeFilter.isSystemPath("/login"));
        assertTrue(RlsSystemScopeFilter.isSystemPath("/api/admin/v1/auth/login"));
        assertTrue(RlsSystemScopeFilter.isSystemPath("/api/v1/account/password/forgot"));
        assertTrue(RlsSystemScopeFilter.isSystemPath("/scim/v2/Users"));
    }

    @Test
    void managementAndSelfServiceApisDoNot() {
        assertFalse(RlsSystemScopeFilter.isSystemPath("/api/admin/v1/users"));
        assertFalse(RlsSystemScopeFilter.isSystemPath("/api/admin/v1/tenants/x/console-access"));
        assertFalse(RlsSystemScopeFilter.isSystemPath("/api/v1/account/sessions"));
        assertFalse(RlsSystemScopeFilter.isSystemPath("/login.html.evil"));
        assertFalse(RlsSystemScopeFilter.isSystemPath("/loginx"));
        assertFalse(RlsSystemScopeFilter.isSystemPath(null));
    }
}
