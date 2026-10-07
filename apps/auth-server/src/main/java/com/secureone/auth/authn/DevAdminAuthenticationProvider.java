package com.secureone.auth.authn;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * The configured platform account ({@code spring.security.user.name}; {@code admin:admin} in local
 * dev). Usernames without {@code :} are this account, not tenant users.
 *
 * <p>Day-to-day platform administration uses named operators in the {@code platform} tenant. In
 * production this account exists only as a break-glass login, and only while
 * {@code secureone.break-glass.enabled} is true.
 */
@Component
@ConditionalOnExpression("${secureone.dev-auth-enabled:true} or ${secureone.break-glass.enabled:false}")
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

    /** Rebuilds the platform admin principal for an already-issued admin session (no password check). */
    public Optional<Authentication> restoreSession(String username) {
        if (!adminUsername.equals(username)) {
            return Optional.empty();
        }
        return Optional.of(UsernamePasswordAuthenticationToken.authenticated(
                username,
                null,
                AuthenticationAuthorities.withPasswordFactor(AuthorityUtils.createAuthorityList("ROLE_ADMIN"))));
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
