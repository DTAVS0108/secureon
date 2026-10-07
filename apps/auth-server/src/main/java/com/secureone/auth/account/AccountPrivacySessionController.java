package com.secureone.auth.account;

import com.secureone.auth.authn.SessionUserResolver;
import com.secureone.auth.user.UserAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account privacy (session)", description = "GDPR export and deletion via browser session cookie")
@RestController
@RequestMapping("/api/v1/account/privacy/session")
public class AccountPrivacySessionController {

    private final SessionUserResolver users;
    private final AccountPrivacyService privacy;

    public AccountPrivacySessionController(SessionUserResolver users, AccountPrivacyService privacy) {
        this.users = users;
        this.privacy = privacy;
    }

    @Operation(summary = "Export personal data", description = "Session-authenticated GDPR export for hosted privacy portal.")
    @GetMapping("/export")
    public Map<String, Object> export(Authentication authentication) {
        UserAccount user = users.requireTenantUser(authentication);
        return privacy.exportForUser(user);
    }

    @Operation(summary = "Request account deletion", description = "Disables the account and records a deletion request.")
    @PostMapping("/delete-request")
    public Map<String, String> deleteRequest(Authentication authentication) {
        UserAccount user = users.requireTenantUser(authentication);
        return privacy.requestDeletionForUser(user);
    }
}
