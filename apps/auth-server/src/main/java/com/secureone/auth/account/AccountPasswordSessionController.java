package com.secureone.auth.account;

import com.secureone.auth.authn.SessionUserResolver;
import com.secureone.auth.tenant.RlsScope;
import com.secureone.auth.user.UserAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Account password change", description = "Users change their password on the hosted Account security page")
@RestController
public class AccountPasswordSessionController {

    private final SessionUserResolver sessionUsers;
    private final AccountProfileService profiles;
    private final AccountSessionService sessions;

    public AccountPasswordSessionController(
            SessionUserResolver sessionUsers, AccountProfileService profiles, AccountSessionService sessions) {
        this.sessionUsers = sessionUsers;
        this.profiles = profiles;
        this.sessions = sessions;
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 128) String newPassword, UUID applicationId) {}

    @Operation(
            summary = "Change password (hosted Account security page, browser session)",
            description = "Needs the current password. This browser stays signed in; the user's other browser sessions end.")
    @PostMapping("/api/v1/account/password/change/session")
    public Map<String, Object> change(
            Authentication authentication, @Valid @RequestBody ChangePasswordRequest body, HttpServletRequest request) {
        UserAccount user = sessionUsers.requireTenantUser(authentication);
        try {
            // The transaction starts inside the platform scope (the scope applies when a connection is taken).
            RlsScope.runAsPlatform(() -> profiles.changePassword(
                    user, body.currentPassword(), body.newPassword(), body.applicationId()));
        } catch (BadCredentialsException e) {
            // Not 401: the browser is signed in, only the password typed is wrong.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        int ended = sessions.revokeOthers(authentication.getName(), AccountSessionService.currentSessionId(request));
        return Map.of(
                "message", ended > 0 ? "Password updated. Your other devices were signed out." : "Password updated.",
                "otherSessionsEnded", ended);
    }

    public record UpdateNamesRequest(
            @Size(max = 150) String givenName, @Size(max = 150) String familyName, @Size(max = 150) String displayName) {}

    @Operation(summary = "Your names (hosted Account security page, browser session)")
    @GetMapping("/api/v1/account/profile/session")
    public Map<String, Object> names(Authentication authentication) {
        UserAccount user = sessionUsers.requireTenantUser(authentication);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("email", user.getEmail());
        out.put("givenName", user.getGivenName());
        out.put("familyName", user.getFamilyName());
        out.put("displayName", user.getDisplayName());
        return out;
    }

    @Operation(
            summary = "Change your names (hosted Account security page, browser session)",
            description = "null keeps a name, an empty string clears it. Apps see the new names in the next tokens they get.")
    @PatchMapping("/api/v1/account/profile/session")
    public Map<String, Object> updateNames(Authentication authentication, @Valid @RequestBody UpdateNamesRequest body) {
        UserAccount user = sessionUsers.requireTenantUser(authentication);
        try {
            return RlsScope.asPlatform(() -> profiles.updateNames(user, body.givenName(), body.familyName(), body.displayName()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
