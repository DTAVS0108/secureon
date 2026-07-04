package com.secureone.auth.mfa;

import com.secureone.auth.authn.SessionUserResolver;
import com.secureone.auth.user.UserAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account MFA", description = "Self-service MFA overview (session-authenticated)")
@RestController
@RequestMapping("/api/v1/account/mfa")
public class AccountMfaOverviewController {

    private final SessionUserResolver users;
    private final MfaFactorRepository factors;

    public AccountMfaOverviewController(SessionUserResolver users, MfaFactorRepository factors) {
        this.users = users;
        this.factors = factors;
    }

    @Operation(summary = "List enrolled MFA factors")
    @GetMapping("/factors")
    public List<Map<String, Object>> listFactors(Authentication authentication) {
        UserAccount user = users.requireTenantUser(authentication);
        return MfaFactorViews.listForUser(factors, user.getId());
    }
}
