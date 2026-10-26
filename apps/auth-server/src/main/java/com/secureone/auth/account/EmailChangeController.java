package com.secureone.auth.account;

import com.secureone.auth.authn.SessionUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account email change", description = "Users change their sign-in email; the new address must be confirmed")
@RestController
public class EmailChangeController {

    private final EmailChangeService emailChange;
    private final SessionUserResolver sessionUsers;
    private final AccountProfileService profiles;

    public EmailChangeController(EmailChangeService emailChange, SessionUserResolver sessionUsers, AccountProfileService profiles) {
        this.emailChange = emailChange;
        this.sessionUsers = sessionUsers;
        this.profiles = profiles;
    }

    public record ChangeEmailRequest(String newEmail, String currentPassword, UUID applicationId) {}

    public record ConfirmRequest(String token) {}

    @Operation(summary = "Change email (integrated apps, Bearer token)")
    @PostMapping("/api/v1/account/email/change")
    public Map<String, Object> changeWithToken(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Application-Id", required = false) UUID applicationId,
            @RequestBody ChangeEmailRequest body) {
        UUID userId = profiles.userFor(jwt).getId();
        UUID app = applicationId != null ? applicationId : body.applicationId();
        // The transaction starts inside the platform scope (the scope applies when a connection is taken).
        // An access token does not prove a recent sign-in: accounts without a password use the Account security page.
        return com.secureone.auth.tenant.RlsScope.asPlatform(
                () -> emailChange.request(userId, body.newEmail(), body.currentPassword(), app, null));
    }

    @Operation(summary = "Change email (hosted Account security page, browser session)")
    @PostMapping("/api/v1/account/email/change/session")
    public Map<String, Object> changeWithSession(
            Authentication authentication,
            @RequestBody ChangeEmailRequest body,
            jakarta.servlet.http.HttpServletRequest request) {
        UUID userId = sessionUsers.requireTenantUser(authentication).getId();
        java.time.Instant signedInAt = com.secureone.auth.authn.AuthContextAttributes.authTime(request);
        return com.secureone.auth.tenant.RlsScope.asPlatform(() -> emailChange.request(
                userId, body.newEmail(), body.currentPassword(), body.applicationId(), signedInAt));
    }

    @Operation(summary = "Confirm the new email from the emailed link")
    @PostMapping("/api/v1/account/email/change/confirm")
    public Map<String, Object> confirm(@RequestBody ConfirmRequest body) {
        String token = body != null ? body.token() : null;
        // The transaction must start inside the platform scope (the scope is applied when a connection is taken).
        return com.secureone.auth.tenant.RlsScope.asPlatform(() -> emailChange.confirm(token));
    }
}
