package com.secureone.auth.account;

import com.secureone.auth.authn.TenantUserDetailsService;
import com.secureone.auth.tenant.Tenant;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountProfileService {

    private final UserAccountRepository users;
    private final TenantRepository tenants;
    private final TenantUserDetailsService userDetails;
    private final UserPasswordService passwords;

    public AccountProfileService(
            UserAccountRepository users,
            TenantRepository tenants,
            TenantUserDetailsService userDetails,
            UserPasswordService passwords) {
        this.users = users;
        this.tenants = tenants;
        this.userDetails = userDetails;
        this.passwords = passwords;
    }

    public Map<String, Object> profile(Jwt jwt) {
        UserAccount user = resolveUser(jwt);
        Tenant tenant = tenants.findById(user.getTenantId()).orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", user.getId());
        out.put("sub", jwt.getSubject());
        out.put("email", user.getEmail());
        out.put("emailVerified", user.isEmailVerified());
        out.put("username", user.getUsername());
        out.put("displayName", user.getDisplayName());
        out.put("status", user.getStatus());
        out.put("type", user.getType());
        out.put("hasPassword", passwords.hasPassword(user.getId()));
        out.put("lastLoginAt", user.getLastLoginAt());
        out.put("createdAt", user.getCreatedAt());
        out.put("updatedAt", user.getUpdatedAt());
        if (tenant != null) {
            out.put("tenantId", tenant.getId());
            out.put("tenantSlug", tenant.getSlug());
            out.put("tenantName", tenant.getName());
        }
        String principal = jwt.getSubject();
        if (principal != null && principal.contains(":")) {
            out.put("loginUsername", principal);
        } else {
            out.put("loginUsername", tenant != null ? tenant.getSlug() + ":" + user.getEmail() : user.getEmail());
        }
        if (jwt.getClaimAsString("scope") != null) {
            out.put("scopes", jwt.getClaimAsString("scope"));
        }
        if (jwt.getClaimAsString("azp") != null) {
            out.put("clientId", jwt.getClaimAsString("azp"));
        }
        return out;
    }

    public void changePassword(Jwt jwt, String currentPassword, String newPassword, UUID applicationId) {
        UserAccount user = resolveUser(jwt);
        passwords.changePassword(user.getId(), currentPassword, newPassword, applicationId);
    }

    /** The user an access token belongs to. */
    public UserAccount userFor(Jwt jwt) {
        return resolveUser(jwt);
    }

    private UserAccount resolveUser(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new BadCredentialsException("Token subject is missing.");
        }
        try {
            UUID id = UUID.fromString(subject);
            return users.findById(id).orElseThrow(() -> new BadCredentialsException("User not found."));
        } catch (IllegalArgumentException ignored) {
            // Fall through — subject is tenantSlug:email
        }
        int sep = subject.indexOf(':');
        if (sep <= 0) {
            throw new BadCredentialsException("Unrecognized token subject.");
        }
        String tenantSlug = subject.substring(0, sep).trim().toLowerCase(Locale.ROOT);
        String email = subject.substring(sep + 1).trim().toLowerCase(Locale.ROOT);
        return userDetails.resolveAccount(tenantSlug, email);
    }
}
