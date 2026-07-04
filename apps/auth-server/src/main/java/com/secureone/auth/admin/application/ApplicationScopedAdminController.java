package com.secureone.auth.admin.application;

import com.secureone.auth.admin.user.UserAdminDtos.UserAuthMethodsUpdateRequest;
import com.secureone.auth.admin.user.UserAdminDtos.UserResponse;
import com.secureone.auth.admin.user.UserAdminService;
import com.secureone.auth.admin.user.UserImportExportService;
import com.secureone.auth.application.ApplicationPublicManifestService;
import com.secureone.auth.application.ApplicationSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.multipart.MultipartFile;

/**
 * Per-application admin: users and settings scoped to one OAuth client / relying party.
 * Unset app settings inherit platform defaults.
 */
@Tag(name = "Admin — application scope", description = "Users, settings, import/export for one application")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1/applications/{applicationId}")
public class ApplicationScopedAdminController {

    private final ApplicationUserAdminService users;
    private final ApplicationSettingsService settings;
    private final UserAdminService userAdmin;
    private final UserImportExportService userImportExport;
    private final ApplicationPublicManifestService publicManifest;

    public ApplicationScopedAdminController(
            ApplicationUserAdminService users,
            ApplicationSettingsService settings,
            UserAdminService userAdmin,
            UserImportExportService userImportExport,
            ApplicationPublicManifestService publicManifest) {
        this.users = users;
        this.settings = settings;
        this.userAdmin = userAdmin;
        this.userImportExport = userImportExport;
        this.publicManifest = publicManifest;
    }

    @GetMapping("/users")
    public List<UserResponse> listUsers(@PathVariable UUID applicationId) {
        return users.listUsers(applicationId);
    }

    @PostMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grantUser(@PathVariable UUID applicationId, @PathVariable UUID userId) {
        users.grantAccess(applicationId, userId);
    }

    @DeleteMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeUser(@PathVariable UUID applicationId, @PathVariable UUID userId) {
        users.revokeAccess(applicationId, userId);
    }

    @GetMapping("/users/{userId}")
    public UserResponse getUser(@PathVariable UUID applicationId, @PathVariable UUID userId) {
        return userAdmin.get(userId, applicationId);
    }

    @PutMapping("/users/{userId}/auth-methods")
    public UserResponse updateUserAuthMethods(
            @PathVariable UUID applicationId,
            @PathVariable UUID userId,
            @Valid @RequestBody UserAuthMethodsUpdateRequest body) {
        return userAdmin.updateAuthMethods(userId, applicationId, body.methods());
    }

    @DeleteMapping("/users/{userId}/mfa/factors/{factorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMfaFactor(
            @PathVariable UUID applicationId, @PathVariable UUID userId, @PathVariable UUID factorId) {
        userAdmin.deleteMfaFactor(userId, factorId);
    }

    @PostMapping("/users/{userId}/mfa/methods/{methodId}/reset")
    public UserResponse resetMfaForMethod(
            @PathVariable UUID applicationId,
            @PathVariable UUID userId,
            @PathVariable String methodId) {
        return userAdmin.resetMfaForMethod(userId, applicationId, methodId);
    }

    @PostMapping("/users/{userId}/password/set")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void adminSetPassword(
            @PathVariable UUID applicationId,
            @PathVariable UUID userId,
            @Valid @RequestBody com.secureone.auth.admin.user.UserAdminDtos.AdminSetPasswordRequest request) {
        userAdmin.adminSetPassword(userId, request.password(), applicationId);
    }

    @PostMapping("/users/{userId}/password/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void adminRemovePassword(@PathVariable UUID applicationId, @PathVariable UUID userId) {
        userAdmin.adminRemovePassword(userId, applicationId);
    }

    @PostMapping("/users/{userId}/password/set-password-email")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> sendSetPasswordInviteEmail(
            @PathVariable UUID applicationId, @PathVariable UUID userId) {
        userAdmin.sendSetPasswordInviteEmail(userId, applicationId);
        return Map.of("status", "sent", "message", "Set-password email sent to the user.");
    }

    @PostMapping("/users/{userId}/password/reset-email")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> sendPasswordResetEmail(
            @PathVariable UUID applicationId, @PathVariable UUID userId) {
        userAdmin.sendPasswordResetEmail(userId, applicationId);
        return Map.of("status", "sent", "message", "Password reset email sent to the user.");
    }

    @PostMapping("/users/{userId}/email/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> resendVerification(
            @PathVariable UUID applicationId, @PathVariable UUID userId) {
        userAdmin.resendVerificationEmail(userId, applicationId);
        return Map.of("status", "sent", "message", "Verification email sent to the user.");
    }

    @PostMapping("/users/{userId}/email/verify")
    public UserResponse markEmailVerified(@PathVariable UUID applicationId, @PathVariable UUID userId) {
        return userAdmin.markEmailVerified(userId, applicationId);
    }

    @PatchMapping("/users/{userId}/email-verification")
    public UserResponse updateEmailVerification(
            @PathVariable UUID applicationId,
            @PathVariable UUID userId,
            @RequestBody Map<String, Boolean> body) {
        boolean verified = body != null && Boolean.TRUE.equals(body.get("verified"));
        return userAdmin.setEmailVerified(userId, verified, applicationId);
    }

    @GetMapping(value = "/users/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportUsers(
            @PathVariable UUID applicationId, @RequestParam(defaultValue = "csv") String format) {
        byte[] body = userImportExport.exportUsers(applicationId, format);
        String filename = "users-" + applicationId + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }

    @PostMapping(value = "/users/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importUsers(
            @PathVariable UUID applicationId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "csv") String source) {
        return userImportExport.importFromFile(applicationId, file, source);
    }

    @PostMapping("/users/import/ldap/preview")
    public Map<String, Object> previewLdapImport(@PathVariable UUID applicationId) {
        return userImportExport.previewLdap(applicationId);
    }

    @PostMapping("/users/import/ldap")
    public Map<String, Object> importFromLdap(@PathVariable UUID applicationId) {
        return userImportExport.importFromLdap(applicationId);
    }

    @GetMapping("/settings/workspace")
    public Map<String, Object> getSettingsWorkspace(@PathVariable UUID applicationId) {
        return settings.getWorkspace(applicationId);
    }

    @GetMapping("/settings/exposure")
    public Map<String, Boolean> getExposure(@PathVariable UUID applicationId) {
        return settings.getExposureForApplication(applicationId);
    }

    @GetMapping("/settings/policy-sources")
    public Map<String, String> getPolicySources(@PathVariable UUID applicationId) {
        return settings.getPolicySources(applicationId);
    }

    @GetMapping("/settings/policy-source/{exposureKey}")
    public Map<String, String> getPolicySource(
            @PathVariable UUID applicationId, @PathVariable String exposureKey) {
        return Map.of("exposureKey", exposureKey, "policySource", settings.getPolicySource(applicationId, exposureKey));
    }

    @PutMapping("/settings/policy-source/{exposureKey}")
    public Map<String, String> setPolicySource(
            @PathVariable UUID applicationId,
            @PathVariable String exposureKey,
            @RequestBody Map<String, String> body) {
        String source = body != null ? body.get("source") : null;
        String policySource = settings.setPolicySource(applicationId, exposureKey, source);
        return Map.of("exposureKey", exposureKey, "policySource", policySource);
    }

    @GetMapping("/settings/notifications")
    public Map<String, Object> getNotifications(@PathVariable UUID applicationId) {
        return settings.getNotifications(applicationId);
    }

    @PutMapping("/settings/notifications")
    public Map<String, Object> updateNotifications(
            @PathVariable UUID applicationId, @RequestBody Map<String, Object> body) {
        return settings.saveNotifications(applicationId, body);
    }

    @DeleteMapping("/settings/notifications")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetNotifications(@PathVariable UUID applicationId) {
        settings.clearOverride(applicationId, "notifications");
    }

    @GetMapping("/settings/email")
    public Map<String, Object> getEmail(@PathVariable UUID applicationId) {
        return settings.getEmail(applicationId);
    }

    @PutMapping("/settings/email")
    public Map<String, Object> updateEmail(
            @PathVariable UUID applicationId, @RequestBody Map<String, Object> body) {
        return settings.saveEmail(applicationId, body);
    }

    @DeleteMapping("/settings/email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetEmail(@PathVariable UUID applicationId) {
        settings.clearOverride(applicationId, "email");
    }

    @GetMapping("/settings/smtp")
    public Map<String, Object> getSmtp(@PathVariable UUID applicationId) {
        return settings.getSmtp(applicationId);
    }

    @PutMapping("/settings/smtp")
    public Map<String, Object> updateSmtp(
            @PathVariable UUID applicationId, @RequestBody Map<String, Object> body) {
        return settings.saveSmtp(applicationId, body);
    }

    @GetMapping("/settings/email-templates")
    public Map<String, Object> getEmailTemplates(@PathVariable UUID applicationId) {
        return settings.getEmailTemplates(applicationId);
    }

    @PutMapping("/settings/email-templates")
    public Map<String, Object> updateEmailTemplates(
            @PathVariable UUID applicationId, @RequestBody Map<String, Object> body) {
        return settings.saveEmailTemplates(applicationId, body);
    }

    @GetMapping("/settings/email-templates/defaults")
    public Map<String, Object> getEmailTemplateDefaults(@PathVariable UUID applicationId) {
        return settings.getEmailTemplateDefaults();
    }

    @DeleteMapping("/settings/email-templates/{templateKey}")
    public Map<String, Object> resetEmailTemplate(
            @PathVariable UUID applicationId, @PathVariable String templateKey) {
        return settings.resetEmailTemplate(applicationId, templateKey);
    }

    public record TestEmailRequest(
            @NotBlank @Email String to,
            List<String> cc,
            List<String> bcc,
            String templateKey,
            Map<String, String> customData) {}

    @PostMapping("/settings/email/test")
    public Map<String, String> sendTestEmail(
            @PathVariable UUID applicationId, @RequestBody TestEmailRequest request) {
        settings.sendTestEmail(
                applicationId,
                request.to(),
                request.cc(),
                request.bcc(),
                request.templateKey(),
                request.customData());
        return Map.of("status", "sent", "to", request.to());
    }

    @GetMapping("/settings/auth-methods")
    public List<Map<String, Object>> getAuthMethods(@PathVariable UUID applicationId) {
        return settings.getAuthMethods(applicationId);
    }

    @PutMapping("/settings/auth-methods")
    public List<Map<String, Object>> updateAuthMethods(
            @PathVariable UUID applicationId, @RequestBody List<Map<String, Object>> body) {
        return settings.saveAuthMethods(applicationId, body);
    }

    @DeleteMapping("/settings/auth-methods")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetAuthMethods(@PathVariable UUID applicationId) {
        settings.clearOverride(applicationId, "auth_methods");
    }

    @GetMapping("/settings/mfa-tab/tab-state")
    public Map<String, Object> getMfaTabState(@PathVariable UUID applicationId) {
        return settings.getMfaTabState(applicationId);
    }

    @PutMapping("/settings/mfa-tab/tab-enabled")
    public Map<String, Object> setMfaTabEnabled(
            @PathVariable UUID applicationId, @RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body != null ? body.get("enabled") : null);
        return settings.saveMfaTabEnabled(applicationId, enabled);
    }

    @GetMapping("/settings/password-policy")
    public Map<String, Object> getPasswordPolicy(@PathVariable UUID applicationId) {
        return settings.getPasswordPolicy(applicationId);
    }

    @PutMapping("/settings/password-policy")
    public Map<String, Object> updatePasswordPolicy(
            @PathVariable UUID applicationId, @RequestBody Map<String, Object> body) {
        return settings.savePasswordPolicy(applicationId, body);
    }

    @DeleteMapping("/settings/password-policy")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPasswordPolicy(@PathVariable UUID applicationId) {
        settings.clearOverride(applicationId, "password_policy");
    }

    @GetMapping("/settings/verification-policy")
    public Map<String, Object> getVerificationPolicy(@PathVariable UUID applicationId) {
        return settings.getVerificationPolicy(applicationId);
    }

    @PutMapping("/settings/verification-policy")
    public Map<String, Object> updateVerificationPolicy(
            @PathVariable UUID applicationId, @RequestBody Map<String, Object> body) {
        return settings.saveVerificationPolicy(applicationId, body);
    }

    @DeleteMapping("/settings/verification-policy")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetVerificationPolicy(@PathVariable UUID applicationId) {
        settings.clearOverride(applicationId, "verification_policy");
    }

    @GetMapping("/settings/mfa-policy")
    public Map<String, Object> getMfaPolicy(@PathVariable UUID applicationId) {
        return settings.getMfaPolicy(applicationId);
    }

    @PutMapping("/settings/mfa-policy")
    public Map<String, Object> updateMfaPolicy(
            @PathVariable UUID applicationId, @RequestBody Map<String, Object> body) {
        return settings.saveMfaPolicy(applicationId, body);
    }

    @DeleteMapping("/settings/mfa-policy")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetMfaPolicy(@PathVariable UUID applicationId) {
        settings.clearOverride(applicationId, "mfa_policy");
    }

    @GetMapping("/settings/sms")
    public Map<String, Object> getSms(@PathVariable UUID applicationId) {
        return settings.getSms(applicationId);
    }

    @PutMapping("/settings/sms")
    public Map<String, Object> updateSms(
            @PathVariable UUID applicationId, @RequestBody Map<String, Object> body) {
        return settings.saveSms(applicationId, body);
    }

    @DeleteMapping("/settings/sms")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetSms(@PathVariable UUID applicationId) {
        settings.clearOverride(applicationId, "sms");
    }

    public record TestSmsRequest(@NotBlank String to) {}

    @PostMapping("/settings/sms/test")
    public Map<String, String> sendTestSms(
            @PathVariable UUID applicationId, @RequestBody TestSmsRequest request) {
        settings.sendTestSms(applicationId, request.to());
        return Map.of("status", "sent", "to", request.to());
    }

    @GetMapping("/settings/feature-flags")
    public List<Map<String, Object>> getFeatureFlags(@PathVariable UUID applicationId) {
        return settings.getFeatureFlags(applicationId);
    }

    @PutMapping("/settings/feature-flags")
    public List<Map<String, Object>> updateFeatureFlags(
            @PathVariable UUID applicationId, @RequestBody List<Map<String, Object>> body) {
        return settings.saveFeatureFlags(applicationId, body);
    }

    @DeleteMapping("/settings/feature-flags")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetFeatureFlags(@PathVariable UUID applicationId) {
        settings.clearOverride(applicationId, "feature_flags");
    }

    @GetMapping("/settings/user-directory")
    public Map<String, Object> getUserDirectory(@PathVariable UUID applicationId) {
        return settings.getUserDirectory(applicationId);
    }

    @PutMapping("/settings/user-directory")
    public Map<String, Object> updateUserDirectory(
            @PathVariable UUID applicationId, @RequestBody Map<String, Object> body) {
        return settings.saveUserDirectory(applicationId, body);
    }

    @DeleteMapping("/settings/user-directory")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetUserDirectory(@PathVariable UUID applicationId) {
        settings.clearOverride(applicationId, "user_directory");
    }

    @GetMapping("/settings/appearance")
    public Map<String, Object> getAppearance(@PathVariable UUID applicationId) {
        return settings.getAppearance(applicationId);
    }

    @PutMapping("/settings/appearance")
    public Map<String, Object> updateAppearance(
            @PathVariable UUID applicationId, @RequestBody Map<String, Object> body) {
        return settings.saveAppearance(applicationId, body);
    }

    @DeleteMapping("/settings/appearance")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetAppearance(@PathVariable UUID applicationId) {
        settings.clearOverride(applicationId, "appearance");
    }

    @GetMapping("/settings/public-manifest")
    public Map<String, Object> getPublicManifestSettings(@PathVariable UUID applicationId) {
        return publicManifest.getManifestConfig(applicationId);
    }

    @PutMapping("/settings/public-manifest")
    public Map<String, Object> updatePublicManifestSettings(
            @PathVariable UUID applicationId, @RequestBody Map<String, Object> body) {
        return publicManifest.saveManifestConfig(applicationId, body);
    }

    @DeleteMapping("/settings/public-manifest")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPublicManifestSettings(@PathVariable UUID applicationId) {
        publicManifest.clearManifestConfig(applicationId);
    }

    @GetMapping("/settings/token-policy/tab-state")
    public Map<String, Object> getTokenTabState(@PathVariable UUID applicationId) {
        return settings.getTokenTabState(applicationId);
    }

    @PutMapping("/settings/token-policy/tab-enabled")
    public Map<String, Object> setTokenTabEnabled(
            @PathVariable UUID applicationId, @RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body != null ? body.get("enabled") : null);
        return settings.saveTokenTabEnabled(applicationId, enabled);
    }

    @GetMapping("/settings/token-policy")
    public Map<String, Object> getTokenPolicy(@PathVariable UUID applicationId) {
        return settings.getTokenPolicy(applicationId);
    }

    @PutMapping("/settings/token-policy")
    public Map<String, Object> updateTokenPolicy(
            @PathVariable UUID applicationId, @RequestBody Map<String, Object> body) {
        return settings.saveTokenPolicy(applicationId, body);
    }

    @DeleteMapping("/settings/token-policy")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetTokenPolicy(@PathVariable UUID applicationId) {
        settings.clearOverride(applicationId, "token_policy");
    }

    @GetMapping("/settings/client-integration")
    public Map<String, Object> getClientIntegration(@PathVariable UUID applicationId) {
        return settings.getClientIntegration(applicationId);
    }

    @PutMapping("/settings/client-integration")
    public Map<String, Object> updateClientIntegration(
            @PathVariable UUID applicationId, @RequestBody Map<String, Object> body) {
        return settings.saveClientIntegration(applicationId, body);
    }
}
