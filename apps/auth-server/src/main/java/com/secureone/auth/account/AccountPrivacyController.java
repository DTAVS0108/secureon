package com.secureone.auth.account;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account privacy", description = "GDPR-oriented self-service export and deletion request (Bearer access token)")
@SecurityRequirement(name = "oauth2Bearer")
@RestController
@RequestMapping("/api/v1/account/privacy")
public class AccountPrivacyController {

    private final AccountPrivacyService privacy;

    public AccountPrivacyController(AccountPrivacyService privacy) {
        this.privacy = privacy;
    }

    @GetMapping("/export")
    public Map<String, Object> export(@AuthenticationPrincipal Jwt jwt) {
        return privacy.export(jwt);
    }

    @PostMapping("/delete-request")
    public Map<String, String> deleteRequest(@AuthenticationPrincipal Jwt jwt) {
        return privacy.requestDeletion(jwt);
    }
}
