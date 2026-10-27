package com.secureone.auth.account;

import com.secureone.auth.authn.SessionUserResolver;
import com.secureone.auth.federation.LinkedIdentityService;
import com.secureone.auth.tenant.RlsScope;
import com.secureone.auth.user.UserAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account linked sign-in", description = "The Google, SAML… accounts a user signs in with (hosted Account security page)")
@RestController
public class AccountIdentityController {

    private final SessionUserResolver sessionUsers;
    private final LinkedIdentityService identities;

    public AccountIdentityController(SessionUserResolver sessionUsers, LinkedIdentityService identities) {
        this.sessionUsers = sessionUsers;
        this.identities = identities;
    }

    @Operation(summary = "My linked sign-in accounts")
    @GetMapping("/api/v1/account/identities")
    public List<Map<String, Object>> list(Authentication authentication) {
        UserAccount user = sessionUsers.requireTenantUser(authentication);
        return RlsScope.asPlatform(() -> identities.list(user.getId()));
    }

    @Operation(summary = "Unlink one", description = "Refused (409) when it is the only way the account signs in.")
    @DeleteMapping("/api/v1/account/identities/{identityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(Authentication authentication, @PathVariable UUID identityId) {
        UserAccount user = sessionUsers.requireTenantUser(authentication);
        RlsScope.runAsPlatform(() -> identities.unlink(user.getId(), identityId, user.getEmail()));
    }
}
