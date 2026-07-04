package com.secureone.auth.authn;

import com.secureone.auth.user.UserAccount;
import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class SessionUserResolver {

    private final TenantUserDetailsService userDetailsService;

    public SessionUserResolver(TenantUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    public UserAccount requireTenantUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UsernameNotFoundException("Not signed in.");
        }
        String principal = authentication.getName();
        int sep = principal.indexOf(':');
        if (sep <= 0) {
            throw new UsernameNotFoundException("Platform operators cannot manage MFA here.");
        }
        String tenantSlug = principal.substring(0, sep).trim().toLowerCase(Locale.ROOT);
        String email = principal.substring(sep + 1).trim().toLowerCase(Locale.ROOT);
        return userDetailsService.resolveAccount(tenantSlug, email);
    }
}
