package com.secureone.auth.platform;

import com.secureone.auth.application.PasswordPolicySanitizer;
import com.secureone.auth.application.PublicManifestDefaults;
import com.secureone.auth.config.SecureOneEnvironmentProperties;
import com.secureone.auth.application.TokenPolicyDefaults;
import com.secureone.auth.application.UserDirectoryDefaults;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Re-seed platform_setting rows removed manually (e.g. TRUNCATE) without re-running Flyway. */
@Component
public class PlatformSettingsBootstrap {

    private final PlatformSettingRepository settings;
    private final SecureOneEnvironmentProperties environment;

    public PlatformSettingsBootstrap(
            PlatformSettingRepository settings, SecureOneEnvironmentProperties environment) {
        this.settings = settings;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureDefaults() {
        ensure("notifications", PlatformNotificationDefaults.defaults());
        ensure("email", PlatformNotificationDefaults.emailDefaults());
        ensure("smtp", defaultSmtp());
        ensure("email_templates", Map.of());
        ensure("auth_methods", defaultAuthMethods());
        ensure("password_policy", defaultPasswordPolicy());
        ensure("feature_flags", FeatureFlagDefaults.platformCatalog());
        ensure("user_directory", UserDirectoryDefaults.platformDefaults());
        ensure("public_manifest_defaults", PublicManifestDefaults.platformDefaults());
        ensure(
                "token_policy",
                environment.isProduction()
                        ? TokenPolicyDefaults.productionDefaults()
                        : TokenPolicyDefaults.platformDefaults());
        ensure("verification_policy", VerificationPolicyDefaults.platformDefaults());
        ensure("mfa_policy", defaultMfaPolicy());
        ensure("sms", defaultSms());
        ensure("appearance", Map.of());
        ensure("app_settings_exposure", defaultExposure());
    }

    private void ensure(String key, Object value) {
        if (settings.existsById(key)) {
            return;
        }
        PlatformSetting row = new PlatformSetting();
        row.setKey(key);
        row.setValue(value);
        settings.save(row);
    }

    private static Map<String, Object> defaultSmtp() {
        Map<String, Object> smtp = new LinkedHashMap<>();
        smtp.put("host", "");
        smtp.put("port", 465);
        smtp.put("security", "ssl");
        smtp.put("username", "");
        smtp.put("authEnabled", true);
        return smtp;
    }

    private static Map<String, Object> defaultPasswordPolicy() {
        return new LinkedHashMap<>(PasswordPolicySanitizer.platformDefaults());
    }

    private static List<Map<String, Object>> defaultAuthMethods() {
        return List.of(
                authMethod("m_password", "Password", "Username + password", true, "primary", true),
                authMethod("m_passkey", "Passkeys (WebAuthn)", "Phishing-resistant sign-in", true, "primary", false),
                authMethod("m_magic", "Magic Link", "Email passwordless login", true, "primary", true),
                authMethod("m_totp", "TOTP Authenticator", "Authenticator app codes", true, "mfa", true),
                authMethod("m_sms", "SMS OTP", "SMS one-time codes", false, "mfa", true),
                authMethod("m_email_otp", "Email OTP", "Email one-time codes at login", true, "mfa", true),
                authMethod("m_push", "Push Notification", "Approve on device", false, "mfa", false),
                authMethod("m_google", "Google", "Google OIDC", true, "federation", false),
                authMethod("m_github", "GitHub", "GitHub OAuth", true, "federation", false),
                authMethod("m_saml", "SAML 2.0", "Enterprise SSO", false, "federation", false),
                authMethod("m_oidc", "External OIDC", "OIDC federation", false, "federation", false));
    }

    private static Map<String, Object> authMethod(
            String id, String name, String description, boolean enabled, String category, boolean implemented) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        row.put("description", description);
        row.put("enabled", enabled);
        row.put("category", category);
        row.put("implemented", implemented);
        return row;
    }

    private static Map<String, Object> defaultSms() {
        Map<String, Object> sms = new LinkedHashMap<>();
        sms.put("provider", "log");
        sms.put("enabled", false);
        sms.put("twilioAccountSid", "");
        sms.put("twilioAuthToken", "");
        sms.put("twilioFromNumber", "");
        return sms;
    }

    private static Map<String, Object> defaultMfaPolicy() {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("stepUpAfterPassword", true);
        policy.put("recoveryCodesPerEnrollment", 10);
        policy.put("challengeTtlSeconds", 300);
        policy.put("maxChallengeAttempts", 5);
        return policy;
    }

    private static Map<String, Object> defaultExposure() {
        Map<String, Object> exposure = new LinkedHashMap<>();
        for (String key : SettingsExposureService.SECTION_KEYS) {
            exposure.put(key, !"appearance".equals(key));
        }
        return exposure;
    }
}
