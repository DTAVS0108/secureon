package com.secureone.auth.authn;

import com.secureone.auth.admin.platform.PlatformOperators;
import com.secureone.auth.tenant.RlsScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Rebuilds the authenticated principal behind an admin console session token. Tenant operators are
 * reloaded from the user store on every request, so a disabled, locked or deleted account loses
 * console access immediately.
 */
@Component
public class AdminSessionPrincipals {

    private final TenantUserDetailsService tenantUsers;
    private final ObjectProvider<DevAdminAuthenticationProvider> devAdmin;
    private final PlatformOperators platformOperators;

    public AdminSessionPrincipals(
            TenantUserDetailsService tenantUsers,
            ObjectProvider<DevAdminAuthenticationProvider> devAdmin,
            PlatformOperators platformOperators) {
        this.tenantUsers = tenantUsers;
        this.devAdmin = devAdmin;
        this.platformOperators = platformOperators;
    }

    public Optional<Authentication> restore(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        if (!username.contains(":")) {
            // Platform admin exists only while dev auth is enabled.
            DevAdminAuthenticationProvider provider = devAdmin.getIfAvailable();
            return provider != null ? provider.restoreSession(username) : Optional.empty();
        }
        try {
            // Runs before the request's tenant scope is known (see RlsScope).
            UserDetails user = RlsScope.asPlatform(() -> tenantUsers.loadUserByUsername(username));
            List<GrantedAuthority> roles = new ArrayList<>(user.getAuthorities());
            if (isNamedPlatformOperator(username)) {
                // Platform-wide requests run with platform RLS scope, like the break-glass account.
                roles.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }
            return Optional.of(UsernamePasswordAuthenticationToken.authenticated(
                    user, null, AuthenticationAuthorities.withPasswordFactor(roles)));
        } catch (AuthenticationException ex) {
            return Optional.empty();
        }
    }

    private boolean isNamedPlatformOperator(String username) {
        int sep = username.indexOf(':');
        if (!PlatformOperators.PLATFORM_TENANT_SLUG.equals(username.substring(0, sep))) {
            return false;
        }
        return RlsScope.asPlatform(() -> {
            UUID userId = tenantUsers.resolveAccount(username.substring(0, sep), username.substring(sep + 1)).getId();
            return platformOperators.isOperator(userId);
        });
    }
}
