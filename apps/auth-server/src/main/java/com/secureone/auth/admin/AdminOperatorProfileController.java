package com.secureone.auth.admin;

import com.secureone.auth.account.UserPasswordService;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import com.secureone.auth.user.UserAvatarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Admin — operator profile", description = "Signed-in admin console user profile and password")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1/me")
public class AdminOperatorProfileController {

    private final AdminAccessService access;
    private final AdminOperatorService operators;
    private final UserPasswordService passwords;
    private final UserAccountRepository users;
    private final com.secureone.auth.tenant.TenantRepository tenants;
    private final UserAvatarService avatars;

    public AdminOperatorProfileController(
            AdminAccessService access,
            AdminOperatorService operators,
            UserPasswordService passwords,
            UserAccountRepository users,
            com.secureone.auth.tenant.TenantRepository tenants,
            UserAvatarService avatars) {
        this.access = access;
        this.operators = operators;
        this.passwords = passwords;
        this.users = users;
        this.tenants = tenants;
        this.avatars = avatars;
    }

    public record OperatorProfileResponse(
            String operatorTier,
            boolean platformSuperAdmin,
            String principal,
            UUID userId,
            UUID tenantId,
            String tenantSlug,
            String tenantName,
            String email,
            String username,
            String displayName,
            boolean emailVerified,
            boolean hasPassword,
            String status,
            Instant createdAt,
            /** The configured emergency account: no user record, profile or MFA to manage. */
            boolean breakGlass,
            /** When the profile picture last changed (epoch ms), for GET /me/avatar?v=…; null without one. */
            Long avatarVersion) {}

    public record OperatorProfileUpdateRequest(
            @Size(max = 150) String username,
            @Size(max = 150) String displayName) {}

    public record OperatorPasswordChangeRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 128) String newPassword) {}

    @GetMapping
    public OperatorProfileResponse profile(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail) {
        boolean superAdmin = access.canAccessPlatformSettings(authentication, actAsEmail);
        String tier = access.resolveOperatorTier(authentication, actAsEmail);
        if (superAdmin && access.isBreakGlass(authentication)) {
            return new OperatorProfileResponse(
                    tier,
                    true,
                    authentication != null ? authentication.getName() : "admin",
                    null,
                    null,
                    null,
                    null,
                    null,
                    authentication != null ? authentication.getName() : "admin",
                    authentication != null ? authentication.getName() : "Platform admin",
                    true,
                    true,
                    "ACTIVE",
                    null,
                    true,
                    null);
        }
        UserAccount user = operators
                .resolveTenantUser(authentication, actAsEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Operator account not found"));
        var tenant = tenants.findById(user.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        return new OperatorProfileResponse(
                tier,
                superAdmin,
                authentication.getName(),
                user.getId(),
                tenant.getId(),
                tenant.getSlug(),
                tenant.getName(),
                user.getEmail(),
                user.getUsername(),
                user.getDisplayName(),
                user.isEmailVerified(),
                passwords.hasPassword(user.getId()),
                user.getStatus(),
                user.getCreatedAt(),
                false,
                avatars.version(user.getId()).map(Instant::toEpochMilli).orElse(null));
    }

    @Operation(summary = "My profile picture", description = "PNG, JPEG or WebP; 404 when none is set.")
    @GetMapping("/avatar")
    public ResponseEntity<byte[]> avatar(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail) {
        UserAccount user = operators
                .resolveTenantUser(authentication, actAsEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Operator account not found"));
        UserAvatarService.Avatar avatar = avatars.find(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No profile picture"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                // The console adds ?v=<avatarVersion>, so a changed picture has a new URL.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(avatar.image());
    }

    @Operation(summary = "Set my profile picture",
            description = "Multipart field `file`: PNG, JPEG or WebP, at most 512 KB (checked by content).")
    @PutMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OperatorProfileResponse uploadAvatar(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @RequestParam("file") MultipartFile file) throws IOException {
        UserAccount user = profileOwner(authentication, actAsEmail);
        avatars.save(user, file.getBytes());
        return profile(authentication, actAsEmail);
    }

    @Operation(summary = "Remove my profile picture")
    @DeleteMapping("/avatar")
    public OperatorProfileResponse removeAvatar(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail) {
        UserAccount user = profileOwner(authentication, actAsEmail);
        avatars.delete(user.getId());
        return profile(authentication, actAsEmail);
    }

    private UserAccount profileOwner(Authentication authentication, String actAsEmail) {
        if (access.isBreakGlass(authentication) && actAsEmail == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "The emergency account has no profile to change.");
        }
        return operators
                .resolveTenantUser(authentication, actAsEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Operator account not found"));
    }

    @PatchMapping
    public OperatorProfileResponse updateProfile(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @RequestBody OperatorProfileUpdateRequest body) {
        if (access.isBreakGlass(authentication) && actAsEmail == null) {
            return profile(authentication, actAsEmail);
        }
        UserAccount user = operators
                .resolveTenantUser(authentication, actAsEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Operator account not found"));
        if (body.username() != null && !body.username().isBlank()) {
            user.setUsername(body.username().trim());
        }
        if (body.displayName() != null && !body.displayName().isBlank()) {
            user.setDisplayName(body.displayName().trim());
        }
        users.save(user);
        return profile(authentication, actAsEmail);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            Authentication authentication,
            @RequestHeader(value = "X-Act-As-Email", required = false) String actAsEmail,
            @RequestBody OperatorPasswordChangeRequest body) {
        if (access.isBreakGlass(authentication) && actAsEmail == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The emergency account's password is set in server configuration (SECUREONE_DEV_PASSWORD).");
        }
        UserAccount user = operators
                .resolveTenantUser(authentication, actAsEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Operator account not found"));
        // Platform operators belong to no application: their password follows the platform policy.
        UUID appId = access.canAccessPlatformSettings(authentication, actAsEmail)
                ? null
                : access.accessibleApplications(authentication, actAsEmail).stream()
                        .findFirst()
                        .map(AdminAccessService.ApplicationSummary::id)
                        .orElse(null);
        passwords.changePassword(user.getId(), body.currentPassword(), body.newPassword(), appId);
    }
}
