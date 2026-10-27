package com.secureone.auth.account;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account profile", description = "Authenticated end-user profile and password change (Bearer access token)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/account")
public class AccountProfileController {

    private final AccountProfileService profiles;

    public AccountProfileController(AccountProfileService profiles) {
        this.profiles = profiles;
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 128) String newPassword) {}

    @GetMapping("/profile")
    public Map<String, Object> profile(@AuthenticationPrincipal Jwt jwt) {
        return profiles.profile(jwt);
    }

    /** null keeps a name, "" clears it. */
    public record UpdateProfileRequest(
            @Size(max = 150) String givenName, @Size(max = 150) String familyName, @Size(max = 150) String displayName) {}

    @PatchMapping("/profile")
    public Map<String, Object> updateProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        return profiles.updateNames(profiles.userFor(jwt), request.givenName(), request.familyName(), request.displayName());
    }

    @PostMapping("/password/change")
    public Map<String, String> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Application-Id", required = false) UUID applicationId,
            @Valid @RequestBody ChangePasswordRequest request) {
        profiles.changePassword(jwt, request.currentPassword(), request.newPassword(), applicationId);
        return Map.of("message", "Password updated successfully.");
    }
}
