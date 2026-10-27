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
        out.put("givenName", user.getGivenName());
        out.put("familyName", user.getFamilyName());
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
        changePassword(resolveUser(jwt), currentPassword, newPassword, applicationId);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.secureone.auth.audit.AuditService> audit;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.secureone.auth.notify.EmailNotificationService> email;

    /**
     * The user changes their own password (an app with their access token, or the Account security page): audited,
     * and the "password changed" security notice is emailed, as for a reset.
     */
    public void changePassword(UserAccount user, String currentPassword, String newPassword, UUID applicationId) {
        passwords.changePassword(user.getId(), currentPassword, newPassword, applicationId);
        if (audit != null) audit.ifAvailable(a -> a.recordIndependently(
                user.getTenantId(), null, user.getEmail(), "user.password_changed", "user_account", user.getId(),
                user.getEmail(), true));
        if (email != null) email.ifAvailable(e -> {
            try {
                e.sendPasswordChanged(applicationId, user);
            } catch (RuntimeException ignored) {
                // The notice never undoes the change.
            }
        });
    }

    /** Longest name a user may set (the columns hold 150 characters). */
    static final int MAX_NAME = 150;

    /**
     * The user sets their own names; a null field keeps its value, blank clears it. A display name left out follows
     * the given and family name when they change. Audited as user.profile_updated.
     */
    public Map<String, Object> updateNames(UserAccount user, String givenName, String familyName, String displayName) {
        for (String value : new String[] {givenName, familyName, displayName}) {
            if (value != null && (value.length() > MAX_NAME || value.chars().anyMatch(Character::isISOControl))) {
                throw new IllegalArgumentException("Names are at most " + MAX_NAME + " characters, without control characters.");
            }
        }
        UserAccount stored = users.findById(user.getId()).orElseThrow(() -> new BadCredentialsException("User not found."));
        String given = givenName != null ? givenName : stored.getGivenName();
        String family = familyName != null ? familyName : stored.getFamilyName();
        stored.setNames(given, family);
        if (displayName != null && !displayName.isBlank()) {
            stored.setDisplayName(displayName.trim());
        } else if (givenName != null || familyName != null) {
            String joined = ((stored.getGivenName() != null ? stored.getGivenName() : "") + " "
                    + (stored.getFamilyName() != null ? stored.getFamilyName() : "")).trim();
            stored.setDisplayName(joined.isEmpty() ? stored.getEmail() : joined);
        }
        users.save(stored);
        if (audit != null) audit.ifAvailable(a -> a.recordIndependently(
                stored.getTenantId(), null, stored.getEmail(), "user.profile_updated", "user_account", stored.getId(),
                stored.getEmail(), true));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("givenName", stored.getGivenName());
        out.put("familyName", stored.getFamilyName());
        out.put("displayName", stored.getDisplayName());
        return out;
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
