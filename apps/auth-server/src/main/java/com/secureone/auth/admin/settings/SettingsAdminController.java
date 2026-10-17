package com.secureone.auth.admin.settings;

import com.secureone.auth.application.TokenPolicyDefaults;
import com.secureone.auth.mfa.MfaPolicyDefaults;
import com.secureone.auth.notify.EmailNotificationService;
import com.secureone.auth.notify.PlatformMailSenderProvider;
import com.secureone.auth.notify.PlatformSmsSettingsService;
import com.secureone.auth.notify.PlatformSmtpSettingsService;
import com.secureone.auth.platform.AuthSettingsService;
import com.secureone.auth.platform.PlatformFeatures;
import com.secureone.auth.platform.PlatformSettingsService;
import com.secureone.auth.platform.SettingsExposureService;
import com.secureone.auth.platform.VerificationPolicyService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin — platform settings", description = "Platform defaults, exposure to applications, SMTP")
@SecurityRequirement(name = "adminHttpBasic")
@RestController
@RequestMapping("/api/admin/v1/settings")
public class SettingsAdminController {

    private final EmailNotificationService emailService;
    private final PlatformSettingsService platformSettings;
    private final PlatformSmtpSettingsService platformSmtp;
    private final PlatformMailSenderProvider mailSenderProvider;
    private final AuthSettingsService authSettings;
    private final SettingsExposureService exposure;
    private final VerificationPolicyService verificationPolicy;
    private final PlatformSmsSettingsService smsSettings;
    private final PlatformFeatures platformFeatures;

    public SettingsAdminController(
            EmailNotificationService emailService,
            PlatformSettingsService platformSettings,
            PlatformSmtpSettingsService platformSmtp,
            PlatformMailSenderProvider mailSenderProvider,
            AuthSettingsService authSettings,
            SettingsExposureService exposure,
            VerificationPolicyService verificationPolicy,
            PlatformSmsSettingsService smsSettings,
            PlatformFeatures platformFeatures) {
        this.emailService = emailService;
        this.platformSettings = platformSettings;
        this.platformSmtp = platformSmtp;
        this.mailSenderProvider = mailSenderProvider;
        this.authSettings = authSettings;
        this.exposure = exposure;
        this.verificationPolicy = verificationPolicy;
        this.smsSettings = smsSettings;
        this.platformFeatures = platformFeatures;
    }

    /** Which setting sections applications may customize (platform super admin). */
    @GetMapping("/app-exposure")
    public Map<String, Boolean> getAppExposure() {
        return exposure.getExposure();
    }

    @PutMapping("/app-exposure")
    public Map<String, Boolean> updateAppExposure(@RequestBody Map<String, Boolean> body) {
        return exposure.saveExposure(body);
    }

    @GetMapping("/notifications")
    public Map<String, Object> getNotifications() {
        Map<String, Object> body = new HashMap<>(emailService.getNotificationSettings());
        body.put("smtpConfigured", platformSmtp.isConfigured());
        return body;
    }

    @PutMapping("/notifications")
    public Map<String, Object> updateNotifications(@RequestBody Map<String, Object> body) {
        Map<String, Object> saved = emailService.saveNotificationSettings(body);
        saved.put("smtpConfigured", platformSmtp.isConfigured());
        return saved;
    }

    @GetMapping("/email")
    public Map<String, Object> getEmail() {
        Map<String, Object> body = new HashMap<>(emailService.getEmailSettings());
        body.put("smtpConfigured", platformSmtp.isConfigured());
        return body;
    }

    @PutMapping("/email")
    public Map<String, Object> updateEmail(@RequestBody Map<String, Object> body) {
        Map<String, Object> saved = emailService.saveEmailSettings(body);
        saved.put("smtpConfigured", platformSmtp.isConfigured());
        return saved;
    }

    @GetMapping("/smtp")
    public Map<String, Object> getSmtp() {
        return platformSmtp.getPublicSettings();
    }

    @PutMapping("/smtp")
    public Map<String, Object> updateSmtp(@RequestBody Map<String, Object> body) {
        Map<String, Object> saved = platformSmtp.save(body);
        mailSenderProvider.invalidatePlatform();
        return saved;
    }

    /** Admin UI theme / appearance (stored in platform_setting.appearance). */
    @GetMapping("/appearance")
    public Map<String, Object> getAppearance() {
        return new HashMap<>(platformSettings.get("appearance"));
    }

    @PutMapping("/appearance")
    public Map<String, Object> updateAppearance(@RequestBody Map<String, Object> body) {
        return platformSettings.saveMap("appearance", body);
    }

    @GetMapping("/auth-methods")
    public List<Map<String, Object>> getAuthMethods() {
        return authSettings.getAuthMethods();
    }

    @PutMapping("/auth-methods")
    public List<Map<String, Object>> updateAuthMethods(@RequestBody List<Map<String, Object>> body) {
        return authSettings.saveAuthMethods(body);
    }

    @GetMapping("/password-policy")
    public Map<String, Object> getPasswordPolicy() {
        return authSettings.getPasswordPolicy();
    }

    @PutMapping("/password-policy")
    public Map<String, Object> updatePasswordPolicy(@RequestBody Map<String, Object> body) {
        return authSettings.savePasswordPolicy(body);
    }

    /** Platform-only console features, e.g. configuration as code (applications cannot override). */
    @GetMapping("/platform-features")
    public Map<String, Object> getPlatformFeatures() {
        return platformFeatures.get();
    }

    @PutMapping("/platform-features")
    public Map<String, Object> updatePlatformFeatures(@RequestBody Map<String, Object> body) {
        return platformFeatures.update(body);
    }

    @GetMapping("/feature-flags")
    public List<Map<String, Object>> getFeatureFlags() {
        return authSettings.getFeatureFlags();
    }

    @PutMapping("/feature-flags")
    public List<Map<String, Object>> updateFeatureFlags(@RequestBody List<Map<String, Object>> body) {
        return authSettings.saveFeatureFlags(body);
    }

    @GetMapping("/token-policy")
    public Map<String, Object> getTokenPolicy() {
        Map<String, Object> stored = platformSettings.get("token_policy");
        if (stored == null || stored.isEmpty()) {
            return TokenPolicyDefaults.platformDefaults();
        }
        return new HashMap<>(stored);
    }

    @PutMapping("/token-policy")
    public Map<String, Object> updateTokenPolicy(@RequestBody Map<String, Object> body) {
        return platformSettings.saveMap("token_policy", TokenPolicyDefaults.sanitizeForSave(body));
    }

    @GetMapping("/verification-policy")
    public Map<String, Object> getVerificationPolicy() {
        return verificationPolicy.getPlatformPolicy();
    }

    @PutMapping("/verification-policy")
    public Map<String, Object> updateVerificationPolicy(@RequestBody Map<String, Object> body) {
        return verificationPolicy.savePlatformPolicy(body);
    }

    @GetMapping("/sms")
    public Map<String, Object> getSms() {
        return smsSettings.getPublicSettings();
    }

    @PutMapping("/sms")
    public Map<String, Object> updateSms(@RequestBody Map<String, Object> body) {
        return smsSettings.save(body);
    }

    @GetMapping("/mfa-policy")
    public Map<String, Object> getMfaPolicy() {
        Map<String, Object> stored = platformSettings.get("mfa_policy");
        if (stored == null || stored.isEmpty()) {
            return MfaPolicyDefaults.platformDefaults();
        }
        // Fill fields added after the policy was saved, so GET and the effective policy agree.
        return MfaPolicyDefaults.sanitizeForSave(stored);
    }

    @PutMapping("/mfa-policy")
    public Map<String, Object> updateMfaPolicy(@RequestBody Map<String, Object> body) {
        return platformSettings.saveMap("mfa_policy", MfaPolicyDefaults.sanitizeForSave(body));
    }

    public record TestSmsRequest(String to) {}

    @PostMapping("/sms/test")
    public Map<String, String> sendTestSms(@RequestBody TestSmsRequest request) {
        if (request.to() == null || request.to().isBlank()) {
            throw new IllegalArgumentException("to is required");
        }
        smsSettings.resolveProvider().send(request.to(), "SecureOne platform SMS test message.");
        return Map.of("status", "sent", "to", request.to());
    }
}
