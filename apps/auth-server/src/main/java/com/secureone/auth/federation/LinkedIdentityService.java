package com.secureone.auth.federation;

import com.secureone.auth.account.UserPasswordService;
import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Google, Microsoft, SAML… accounts linked to a SecureOne account, which the user and their administrators can
 * see and unlink. An account keeps at least one way to sign in: its last link goes only when it has a password.
 */
@Service
@Transactional
public class LinkedIdentityService {

    private final UserIdentityRepository identities;
    private final IdentityProviderRepository providers;
    private final UserAccountRepository users;
    private final UserPasswordService passwords;
    private final AuditService audit;

    public LinkedIdentityService(
            UserIdentityRepository identities,
            IdentityProviderRepository providers,
            UserAccountRepository users,
            UserPasswordService passwords,
            AuditService audit) {
        this.identities = identities;
        this.providers = providers;
        this.users = users;
        this.passwords = passwords;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (UserIdentity link : identities.findByUserId(userId)) {
            var provider = providers.findById(link.getProviderId()).orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", link.getId());
            row.put("providerId", link.getProviderId());
            row.put("provider", provider != null ? provider.getDisplayName() : "Removed provider");
            row.put("type", provider != null ? provider.getType() : null);
            row.put("email", link.getEmail());
            row.put("linkedAt", link.getLinkedAt());
            out.add(row);
        }
        return out;
    }

    /** {@code by}: "admin", or the user's own email. Refused (409) when it is the account's only way to sign in. */
    public void unlink(UUID userId, UUID identityId, String by) {
        UserAccount user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        UserIdentity link = identities.findById(identityId)
                .filter(i -> userId.equals(i.getUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("Linked account not found: " + identityId));
        if (!passwords.hasPassword(userId) && identities.findByUserId(userId).size() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "admin".equals(by)
                    ? "This is the only way this user signs in. Send them a set-password email first."
                    : "This is the only way you sign in. Set a password first (Forgot password), then unlink it.");
        }
        String provider = providers.findById(link.getProviderId()).map(IdentityProvider::getDisplayName).orElse("provider");
        identities.delete(link);
        audit.record(user.getTenantId(), by, "user.identity_unlinked", "user_account", userId,
                user.getEmail() + ": " + provider + " (" + link.getEmail() + ")", true);
    }
}
