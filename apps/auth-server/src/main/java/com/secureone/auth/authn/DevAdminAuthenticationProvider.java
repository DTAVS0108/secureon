package com.secureone.auth.authn;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Dev console HTTP Basic auth ({@code admin:admin} by default). Usernames without {@code :} are
 * treated as platform admin, not tenant users.
 */
@Component
@ConditionalOnProperty(prefix = "secureone", name = "dev-auth-enabled", havingValue = "true", matchIfMissing = true)
public class DevAdminAuthenticationProvider implements AuthenticationProvider {

    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public DevAdminAuthenticationProvider(
            PasswordEncoder passwordEncoder,
            @Value("${spring.security.user.name:admin}") String adminUsername,
            @Value("${spring.security.user.password:admin}") String adminPassword) {
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = stripNoopPrefix(adminPassword);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        if (username == null || username.contains(":")) {
            return null;
        }
        if (!adminUsername.equals(username)) {
            throw new BadCredentialsException("Invalid credentials");
        }
        String presented = authentication.getCredentials().toString();
        if (!matchesAdminPassword(presented)) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return new UsernamePasswordAuthenticationToken(
                username,
                presented,
                AuthenticationAuthorities.withPasswordFactor(AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private boolean matchesAdminPassword(String presented) {
        if (adminPassword.startsWith("{bcrypt}") || adminPassword.startsWith("$2")) {
            return passwordEncoder.matches(presented, adminPassword.replace("{bcrypt}", ""));
        }
        return adminPassword.equals(presented);
    }

    private static String stripNoopPrefix(String value) {
        if (value != null && value.startsWith("{noop}")) {
            return value.substring("{noop}".length());
        }
        return value;
    }
}
