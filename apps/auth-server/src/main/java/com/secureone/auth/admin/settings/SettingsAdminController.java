package com.secureone.auth.admin.settings;

import com.secureone.auth.application.TokenPolicyDefaults;
import com.secureone.auth.mfa.MfaPolicyDefaults;
import com.secureone.auth.notify.EmailNotificationService;
import com.secureone.auth.notify.PlatformMailSenderProvider;
import com.secureone.auth.notify.PlatformSmsSettingsService;
import com.secureone.auth.notify.PlatformSmtpSettingsService;
import com.secureone.auth.platform.AuthSettingsService;
import com.secureone.auth.platform.FeatureFlagAudit;
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
    private final FeatureFlagAudit flagAudit;

    /** Two Factor Authentication changes that weaken protection wait for another platform operator. */
    @org.springframework.beans.factory.annotation.Autowired
    private com.secureone.auth.admin.approval.TwoFactorChangeRequests twoFactorChanges;

    @org.springframework.beans.factory.annotation.Autowired
    private com.secureone.auth.admin.approval.ChangeRequestViews changeViews;

    public SettingsAdminController(
            EmailNotificationService emailService,
            PlatformSettingsService platformSettings,
            PlatformSmtpSettingsService platformSmtp,
            PlatformMailSenderProvider mailSenderProvider,
            AuthSettingsService authSettings,
            SettingsExposureService exposure,
            VerificationPolicyService verificationPolicy,
            PlatformSmsSettingsService smsSettings,
            PlatformFeatures platformFeatures,
            FeatureFlagAudit flagAudit) {
        this.flagAudit = flagAudit;
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

    /** Applications whose sections differ from the defaults above: applicationId → { section → open/closed }. */
    @GetMapping("/app-exposure/exceptions")
    public Map<String, Object> getAppExposureExceptions() {
        return exposure.getOverrides();
    }

    /** Replaces one application's exceptions (a section left out follows the default). */
    @PutMapping("/app-exposure/exceptions/{applicationId}")
    public Map<String, Object> updateAppExposureExceptions(
            @org.springframework.web.bind.annotation.PathVariable java.util.UUID applicationId,
            @RequestBody Map<String, Boolean> body) {
        Map<String, Object> saved = exposure.saveOverrides(applicationId, body);
        exposureAudit.ifAvailable(a -> a.record(null, applicationId, "admin", "settings.app_exposure_exceptions_changed",
                "application", applicationId, String.valueOf(body), true));
        return saved;
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.secureone.auth.audit.AuditService> exposureAudit;

    /**
     * Closing Two Factor Authentication to applications waits for another platform operator (every application
     * then follows the platform and tenant settings); the other sections save now.
     */
    @PutMapping("/app-exposure")
    public Map<String, Boolean> updateAppExposure(
            jakarta.servlet.http.HttpServletRequest request,
            org.springframework.security.core.Authentication authentication,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Act-As-Email", required = false) String actAs,
            @RequestBody Map<String, Boolean> body) {
        String key = com.secureone.auth.twostep.TwoStepPolicyResolver.OWN_SETTINGS_EXPOSURE;
        boolean closing = body != null && Boolean.FALSE.equals(body.get(key)) && exposure.isExposed(key);
        if (!closing) {
            return exposure.saveExposure(body);
        }
        Map<String, Boolean> rest = new java.util.LinkedHashMap<>(body);
        rest.put(key, true);
        exposure.saveExposure(rest);
        twoFactorChanges.closeToApplications(Map.of(key, false), changeViews.email(authentication, actAs), null, request);
        return exposure.getExposure();
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
        List<Map<String, Object>> before = authSettings.getFeatureFlags();
        List<Map<String, Object>> saved = authSettings.saveFeatureFlags(body);
        flagAudit.recordChanges(null, before, saved);
        return saved;
    }

    @GetMapping("/token-policy")
    public Map<String, Object> getTokenPolicy() {
        Map<String, Object> stored = platformSettings.get("token_policy");
        if (stored == null || stored.isEmpty()) {
            return TokenPolicyDefaults.platformDefaults();
        }
        // What applies: values stored before the current limits are shown (and used) within them.
        return TokenPolicyDefaults.sanitizeForSave(stored);
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
    public Map<String, Object> getMfaPolicy(
            org.springframework.security.core.Authentication authentication,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Act-As-Email", required = false) String actAs) {
        Map<String, Object> stored = platformSettings.get("mfa_policy");
        // Fill fields added after the policy was saved, so GET and the effective policy agree.
        Map<String, Object> out = new java.util.LinkedHashMap<>(stored == null || stored.isEmpty()
                ? MfaPolicyDefaults.platformDefaults()
                : MfaPolicyDefaults.sanitizeForSave(stored));
        out.put("pendingChange", twoFactorChanges.pending(
                        com.secureone.auth.admin.approval.TwoFactorChangeRequests.PLATFORM_POLICY, null)
                .map(row -> changeViews.view(row, authentication, actAs)).orElse(null));
        return out;
    }

    /** Tightening saves now; a weaker policy or a removed lock waits for another platform operator. */
    @PutMapping("/mfa-policy")
    public Map<String, Object> updateMfaPolicy(
            jakarta.servlet.http.HttpServletRequest request,
            org.springframework.security.core.Authentication authentication,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Act-As-Email", required = false) String actAs,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> proposed = MfaPolicyDefaults.sanitizeForSave(body);
        Map<String, Object> current = MfaPolicyDefaults.sanitizeForSave(platformSettings.get("mfa_policy"));
        java.util.List<String> weakenings = MfaPolicyDefaults.weakenings(current, proposed);
        if (weakenings.isEmpty()) {
            platformSettings.saveMap("mfa_policy", proposed);
        } else {
            twoFactorChanges.platformPolicy(proposed, weakenings, changeViews.email(authentication, actAs),
                    body.get("reason") != null ? String.valueOf(body.get("reason")) : null, request);
        }
        return getMfaPolicy(authentication, actAs);
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
