package com.secureone.auth.authn;

import com.secureone.auth.account.PhoneNormalizer;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class IdentifierResolver {

    private final UserAccountRepository users;

    public IdentifierResolver(UserAccountRepository users) {
        this.users = users;
    }

    public Optional<UserAccount> resolve(UUID tenantId, String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String trimmed = identifier.trim();
        if (trimmed.contains("@")) {
            return users.findByTenantIdAndEmail(tenantId, trimmed.toLowerCase(Locale.ROOT));
        }
        String phone = PhoneNormalizer.toE164(trimmed, "US");
        if (phone != null) {
            return users.findByTenantIdAndPhone(tenantId, phone);
        }
        return Optional.empty();
    }

    public boolean looksLikeEmail(String identifier) {
        return identifier != null && identifier.contains("@");
    }
}
