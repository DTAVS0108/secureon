package com.secureone.auth.authn;

import com.secureone.auth.tenant.Tenant;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import com.secureone.auth.user.UserCredentialRepository;
import java.util.Locale;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Username format: {@code tenantSlug:email} */
@Service
@Transactional(readOnly = true)
public class TenantUserDetailsService implements UserDetailsService {

    private final UserAccountRepository users;
    private final TenantRepository tenants;
    private final UserCredentialRepository credentials;
    private final AccountLockoutService lockout;

    public TenantUserDetailsService(
            UserAccountRepository users,
            TenantRepository tenants,
            UserCredentialRepository credentials,
            AccountLockoutService lockout) {
        this.users = users;
        this.tenants = tenants;
        this.credentials = credentials;
        this.lockout = lockout;
    }

    public UserDetails buildSessionPrincipal(String tenantSlug, UserAccount account) {
        tenants.findById(account.getTenantId()).ifPresent(TenantUserDetailsService::assertTenantActive);
        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw new UsernameNotFoundException("Account is not active");
        }
        lockout.assertNotLocked(account);
        String username = tenantSlug + ":" + account.getEmail();
        boolean hasPassword =
                credentials.findFirstByUserIdAndCurrentTrueOrderByCreatedAtDescIdDesc(account.getId()).isPresent();
        return User.withUsername(username)
                .password(hasPassword ? "{noop}placeholder" : "")
                .roles("USER")
                .build();
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        int sep = username.indexOf(':');
        if (sep <= 0) {
            throw new UsernameNotFoundException("Use tenantSlug:email as username");
        }
        String tenantSlug = username.substring(0, sep).trim().toLowerCase(Locale.ROOT);
        String email = username.substring(sep + 1).trim().toLowerCase(Locale.ROOT);
        Tenant tenant = tenants
                .findBySlug(tenantSlug)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown tenant: " + tenantSlug));
        assertTenantActive(tenant);
        UserAccount account = users
                .findByTenantIdAndEmail(tenant.getId(), email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw new UsernameNotFoundException("Account is not active");
        }
        lockout.assertNotLocked(account);
        if (!account.isEmailVerified()) {
            throw new UsernameNotFoundException(
                    "Email address is not verified. Open the link from your inbox or ask an administrator to verify your account.");
        }
        boolean hasPassword =
                credentials.findFirstByUserIdAndCurrentTrueOrderByCreatedAtDescIdDesc(account.getId()).isPresent();
        return User.withUsername(username)
                .password(hasPassword ? "{noop}placeholder" : "")
                .roles("USER")
                .build();
    }

    /** A suspended (or otherwise inactive) organization blocks every sign-in for its users. */
    private static void assertTenantActive(Tenant tenant) {
        if (!"ACTIVE".equalsIgnoreCase(tenant.getStatus())) {
            throw new DisabledException("This organization is suspended. Contact your administrator.");
        }
    }

    public UserAccount resolveAccount(String tenantSlug, String email) {
        Tenant tenant = tenants
                .findBySlug(tenantSlug.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown tenant"));
        return users
                .findByTenantIdAndEmail(tenant.getId(), email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
