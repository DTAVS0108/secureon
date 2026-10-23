package com.secureone.auth.application;

import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.notify.EmailNotificationService;
import com.secureone.auth.notify.EmailTemplateService;
import com.secureone.auth.notify.PlatformMailSenderProvider;
import com.secureone.auth.notify.PlatformSmsSettingsService;
import com.secureone.auth.notify.SmsNotificationService;
import com.secureone.auth.notify.SmtpSettingsService;
import com.secureone.auth.notify.sms.ApplicationSmsSettingsService;
import com.secureone.auth.mfa.MfaPolicyDefaults;
import com.secureone.auth.platform.AuthSettingsService;
import com.secureone.auth.platform.FeatureFlagMerge;
import com.secureone.auth.platform.PlatformSettingsService;
import com.secureone.auth.platform.SettingsExposureService;
import com.secureone.auth.platform.VerificationPolicyDefaults;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves settings for an application: app override, else platform default. */
@Service
@Transactional
public class ApplicationSettingsService {

    private final ApplicationRepository applications;
    private final ApplicationSettingRepository appSettings;
    private final PlatformSettingsService platformSettings;
    private final AuthSettingsService authSettings;
    private final EmailNotificationService emailService;
    private final SmtpSettingsService smtpSettings;
    private final ApplicationSmsSettingsService applicationSmsSettings;
    private final PlatformSmsSettingsService platformSmsSettings;
    private final SmsNotificationService smsNotification;
    private final EmailTemplateService emailTemplates;
    private final PlatformMailSenderProvider mailSenderProvider;
    private final SettingsExposureService exposure;
    private final com.secureone.auth.twostep.TwoStepPolicyResolver twoStepPolicy;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.secureone.auth.oauth.OAuthClientRepository> oauthClients;

    public ApplicationSettingsService(
            ApplicationRepository applications,
            ApplicationSettingRepository appSettings,
            PlatformSettingsService platformSettings,
            AuthSettingsService authSettings,
            EmailNotificationService emailService,
            SmtpSettingsService smtpSettings,
            ApplicationSmsSettingsService applicationSmsSettings,
            PlatformSmsSettingsService platformSmsSettings,
            SmsNotificationService smsNotification,
            EmailTemplateService emailTemplates,
            PlatformMailSenderProvider mailSenderProvider,
            SettingsExposureService exposure,
            com.secureone.auth.twostep.TwoStepPolicyResolver twoStepPolicy) {
        this.applications = applications;
        this.appSettings = appSettings;
        this.platformSettings = platformSettings;
        this.authSettings = authSettings;
        this.emailService = emailService;
        this.smtpSettings = smtpSettings;
        this.applicationSmsSettings = applicationSmsSettings;
        this.platformSmsSettings = platformSmsSettings;
        this.smsNotification = smsNotification;
        this.emailTemplates = emailTemplates;
        this.mailSenderProvider = mailSenderProvider;
        this.exposure = exposure;
        this.twoStepPolicy = twoStepPolicy;
    }

    public void requireApplication(UUID applicationId) {
        if (!applications.existsById(applicationId)) {
            throw new ResourceNotFoundException("Application not found: " + applicationId);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> getExposureForApplication(UUID applicationId) {
        requireApplication(applicationId);
        return exposure.getExposure();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getWorkspace(UUID applicationId) {
        requireApplication(applicationId);
        Map<String, Object> workspace = new LinkedHashMap<>();
        workspace.put("exposure", exposure.getExposure());
        Map<String, Boolean> exposed = exposure.getExposure();
        if (Boolean.TRUE.equals(exposed.get("notifications"))) {
            workspace.put("notifications", getNotifications(applicationId));
        }
        if (Boolean.TRUE.equals(exposed.get("email"))) {
            workspace.put("email", getEmail(applicationId));
        }
        if (Boolean.TRUE.equals(exposed.get("auth-methods"))) {
            workspace.put("authMethods", getAuthMethods(applicationId));
        }
        if (Boolean.TRUE.equals(exposed.get("password-policy"))) {
            workspace.put("passwordPolicy", getPasswordPolicy(applicationId));
        }
        if (Boolean.TRUE.equals(exposed.get("feature-flags"))) {
            workspace.put("featureFlags", getFeatureFlags(applicationId));
        }
        if (Boolean.TRUE.equals(exposed.get("appearance"))) {
            workspace.put("appearance", getAppearance(applicationId));
        }
        if (Boolean.TRUE.equals(exposed.get("user-directory"))) {
            workspace.put("userDirectory", getUserDirectory(applicationId));
        }
        if (Boolean.TRUE.equals(exposed.get("public-manifest"))) {
            workspace.put("publicManifest", getPublicManifestConfigSummary(applicationId));
        }
        if (Boolean.TRUE.equals(exposed.get("token-policy"))) {
            workspace.put("tokenPolicy", getTokenPolicy(applicationId));
        }
        return workspace;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTokenPolicy(UUID applicationId) {
        requireExposed("token-policy");
        requireApplication(applicationId);
        Map<String, Object> merged = resolveTokenPolicy(applicationId);
        boolean own = hasOverride(applicationId, "token_policy");
        merged.put("tabEnabled", own);
        merged.put("scope", "application");
        merged.put("inheritsPlatformDefaults", !own);
        return merged;
    }

    /** The OAuth tokens switch is on exactly when this application has its own token policy. */
    @Transactional(readOnly = true)
    public Map<String, Object> getTokenTabState(UUID applicationId) {
        requireExposed("token-policy");
        requireApplication(applicationId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("platformExposed", true);
        out.put("tabEnabled", hasOverride(applicationId, "token_policy"));
        return out;
    }

    /** On: start this application's own token policy from the platform's. Off: back to the platform's. */
    public Map<String, Object> saveTokenTabEnabled(UUID applicationId, boolean enabled) {
        requireExposed("token-policy");
        requireApplication(applicationId);
        if (enabled) {
            snapshotEffectiveAsOverride(applicationId, "token_policy");
            syncTokenPolicyToApplicationConfig(applicationId);
        } else {
            clearOverride(applicationId, "token_policy");
        }
        return getTokenTabState(applicationId);
    }

    public Map<String, Object> saveTokenPolicy(UUID applicationId, Map<String, Object> body) {
        requireExposed("token-policy");
        requireApplication(applicationId);
        Map<String, Object> sanitized = TokenPolicyDefaults.sanitizeForSave(body);
        PlatformMinimums.token(platformTokenPolicy(), sanitized).requireNothingRaised();
        saveAppMap(applicationId, "token_policy", sanitized);
        syncTokenPolicyToApplicationConfig(applicationId);
        return getTokenPolicy(applicationId);
    }

    /** The platform's token policy, or this application's own (never weaker than the platform's). */
    @Transactional(readOnly = true)
    public Map<String, Object> resolveTokenPolicy(UUID applicationId) {
        requireApplication(applicationId);
        Map<String, Object> platform = platformTokenPolicy();
        Map<String, Object> own = getAppMap(applicationId, "token_policy").orElse(null);
        if (own == null) {
            return new LinkedHashMap<>(platform);
        }
        Map<String, Object> merged = TokenPolicyDefaults.merge(platform, own);
        merged.remove("tabEnabled");
        return new LinkedHashMap<>(PlatformMinimums.token(platform, merged).value());
    }

    private Map<String, Object> platformTokenPolicy() {
        Map<String, Object> platform = platformSettings.get("token_policy");
        if (platform == null || platform.isEmpty()) {
            return TokenPolicyDefaults.platformDefaults();
        }
        return TokenPolicyDefaults.sanitizeForSave(platform);
    }

    private void syncTokenPolicyToApplicationConfig(UUID applicationId) {
        applications.findById(applicationId).ifPresent(app -> {
            Map<String, Object> config = new HashMap<>(app.getConfig() != null ? app.getConfig() : Map.of());
            if (hasOverride(applicationId, "token_policy")) {
                config.put("tokenPolicy", resolveTokenPolicy(applicationId));
            } else {
                config.remove("tokenPolicy");
            }
            app.setConfig(config);
        });
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPublicManifestConfigSummary(UUID applicationId) {
        requireExposed("public-manifest");
        requireApplication(applicationId);
        Map<String, Object> platform = platformSettings.get("public_manifest_defaults");
        if (platform == null || platform.isEmpty()) {
            platform = PublicManifestDefaults.platformDefaults();
        }
        Map<String, Object> merged =
                PublicManifestDefaults.merge(platform, getAppMap(applicationId, "public_manifest").orElse(Map.of()));
        merged.put("scope", "application");
        merged.put("inheritsPlatformDefaults", !hasOverride(applicationId, "public_manifest"));
        merged.put("publicEndpoint", "/api/v1/applications/" + applicationId);
        return merged;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getUserDirectory(UUID applicationId) {
        requireExposed("user-directory");
        requireApplication(applicationId);
        Map<String, Object> platform = platformSettings.get("user_directory");
        if (platform == null || platform.isEmpty()) {
            platform = UserDirectoryDefaults.platformDefaults();
        }
        Map<String, Object> merged = UserDirectoryDefaults.merge(platform, getAppMap(applicationId, "user_directory").orElse(Map.of()));
        applyLdapFeatureGate(applicationId, merged);
        merged.put("scope", "application");
        merged.put("inheritsPlatformDefaults", !hasOverride(applicationId, "user_directory"));
        return merged;
    }

    public Map<String, Object> saveUserDirectory(UUID applicationId, Map<String, Object> body) {
        requireExposed("user-directory");
        requireApplication(applicationId);
        Map<String, Object> sanitized = new LinkedHashMap<>(body);
        applyLdapFeatureGate(applicationId, sanitized);
        saveAppMap(applicationId, "user_directory", sanitized);
        return getUserDirectory(applicationId);
    }

    /** LDAP import is not available yet: the LDAP source always reads as off. */
    private void applyLdapFeatureGate(UUID applicationId, Map<String, Object> config) {
        Object sourcesObj = config.get("sources");
        if (!(sourcesObj instanceof Map<?, ?> sources)) {
            return;
        }
        Object ldapObj = sources.get("ldap");
        if (!(ldapObj instanceof Map<?, ?> ldap)) {
            return;
        }
        Map<String, Object> ldapSource = new LinkedHashMap<>();
        ldap.forEach((k, v) -> ldapSource.put(k.toString(), v));
        ldapSource.put("enabled", false);
        @SuppressWarnings("unchecked")
        Map<String, Object> sourcesMap = (Map<String, Object>) sourcesObj;
        sourcesMap.put("ldap", ldapSource);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getClientIntegration(UUID applicationId) {
        requireApplication(applicationId);
        return mergeClientIntegration(clientIntegrationDefaults(), getAppMap(applicationId, "client_integration").orElse(Map.of()));
    }

    public Map<String, Object> saveClientIntegration(UUID applicationId, Map<String, Object> body) {
        requireApplication(applicationId);
        Map<String, Object> sanitized = sanitizeClientIntegration(body);
        // Pages that receive reset and sign-in tokens must be this application's own HTTPS pages.
        Set<String> trusted = trustedClientOrigins(applicationId);
        @SuppressWarnings("unchecked")
        Map<String, String> urls = (Map<String, String>) sanitized.get("clientUrls");
        for (var e : urls.entrySet()) {
            if (!e.getValue().isBlank()) {
                String problem = ClientUrlPolicy.problem(e.getValue(), trusted);
                if (problem != null) {
                    throw new IllegalArgumentException(clientUrlLabel(e.getKey()) + " page " + problem + ".");
                }
            }
        }
        saveAppMap(applicationId, "client_integration", sanitized);
        return getClientIntegration(applicationId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getNotifications(UUID applicationId) {
        requireExposed("notifications");
        requireApplication(applicationId);
        Map<String, Object> merged = ApplicationNotificationDefaults.mergeNotifications(
                getAppMap(applicationId, "notifications").orElse(null));
        merged.put("scope", "application");
        merged.put("smtpConfigured", smtpSettings.isConfigured(applicationId));
        return merged;
    }

    public Map<String, Object> saveNotifications(UUID applicationId, Map<String, Object> body) {
        requireExposed("notifications");
        requireApplication(applicationId);
        saveAppMap(applicationId, "notifications", ApplicationNotificationDefaults.sanitizeNotifications(body));
        return getNotifications(applicationId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getEmail(UUID applicationId) {
        requireExposed("email");
        requireApplication(applicationId);
        Map<String, Object> merged =
                ApplicationNotificationDefaults.mergeEmail(getAppMap(applicationId, "email").orElse(null));
        merged.put("scope", "application");
        merged.put("smtpConfigured", smtpSettings.isConfigured(applicationId));
        return merged;
    }

    public Map<String, Object> saveEmail(UUID applicationId, Map<String, Object> body) {
        requireExposed("email");
        requireApplication(applicationId);
        saveAppMap(applicationId, "email", ApplicationNotificationDefaults.sanitizeEmail(body));
        return getEmail(applicationId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSmtp(UUID applicationId) {
        requireExposed("notifications");
        requireApplication(applicationId);
        return smtpSettings.getPublicSettings(applicationId);
    }

    public Map<String, Object> saveSmtp(UUID applicationId, Map<String, Object> body) {
        requireExposed("notifications");
        requireApplication(applicationId);
        Map<String, Object> saved = smtpSettings.save(applicationId, body);
        mailSenderProvider.invalidate(applicationId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getEmailTemplates(UUID applicationId) {
        requireExposed("notifications");
        requireApplication(applicationId);
        return emailTemplates.getTemplates(applicationId);
    }

    public Map<String, Object> saveEmailTemplates(UUID applicationId, Map<String, Object> body) {
        requireExposed("notifications");
        requireApplication(applicationId);
        return emailTemplates.saveTemplates(applicationId, body);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getEmailTemplateDefaults() {
        requireExposed("notifications");
        return emailTemplates.getPlatformDefaults();
    }

    public Map<String, Object> resetEmailTemplate(UUID applicationId, String templateKey) {
        requireExposed("notifications");
        requireApplication(applicationId);
        return emailTemplates.resetTemplate(applicationId, templateKey);
    }

    public void sendTestEmail(
            UUID applicationId,
            String to,
            List<String> cc,
            List<String> bcc,
            String templateKey,
            Map<String, String> customData) {
        requireExposed("notifications");
        requireApplication(applicationId);
        emailService.sendTestEmail(applicationId, to, cc, bcc, templateKey, customData);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSms(UUID applicationId) {
        requireExposed("sms");
        requireApplication(applicationId);
        if (hasOverride(applicationId, "sms")) {
            return applicationSmsSettings.getPublicSettings(applicationId);
        }
        Map<String, Object> platform = new HashMap<>(platformSmsSettings.getPublicSettings());
        platform.put("scope", "application");
        platform.put("inheritsPlatformDefaults", true);
        return platform;
    }

    public Map<String, Object> saveSms(UUID applicationId, Map<String, Object> body) {
        requireExposed("sms");
        requireApplication(applicationId);
        return applicationSmsSettings.save(applicationId, body);
    }

    public void sendTestSms(UUID applicationId, String toE164) {
        requireExposed("sms");
        requireApplication(applicationId);
        smsNotification.sendTest(applicationId, toE164);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAuthMethods(UUID applicationId) {
        requireExposed("auth-methods");
        requireApplication(applicationId);
        List<Map<String, Object>> merged = resolveAuthMethods(applicationId);
        merged.forEach(m -> m.put("inheritsPlatformDefaults", !hasOverride(applicationId, "auth_methods")));
        return merged;
    }

    public List<Map<String, Object>> saveAuthMethods(UUID applicationId, List<Map<String, Object>> body) {
        requireExposed("auth-methods");
        requireApplication(applicationId);
        saveAppRaw(applicationId, "auth_methods", withinPlatformMethods(stripMeta(copyList(body))));
        return getAuthMethods(applicationId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMfaTabState(UUID applicationId) {
        requireExposed(com.secureone.auth.twostep.TwoStepPolicyResolver.OWN_SETTINGS_EXPOSURE);
        requireApplication(applicationId);
        Map<String, Object> appRow = getAppMap(applicationId, AuthMfaTabDefaults.SETTING_KEY).orElse(Map.of());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("platformExposed", true);
        out.put("tabEnabled", AuthMfaTabDefaults.tabEnabled(appRow));
        return out;
    }

    public Map<String, Object> saveMfaTabEnabled(UUID applicationId, boolean enabled) {
        requireExposed(com.secureone.auth.twostep.TwoStepPolicyResolver.OWN_SETTINGS_EXPOSURE);
        requireApplication(applicationId);
        Map<String, Object> next = new HashMap<>(AuthMfaTabDefaults.platformDefaults());
        next.putAll(getAppMap(applicationId, AuthMfaTabDefaults.SETTING_KEY).orElse(Map.of()));
        next.put("tabEnabled", enabled);
        saveAppMap(applicationId, AuthMfaTabDefaults.SETTING_KEY, next);
        return getMfaTabState(applicationId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPasswordPolicy(UUID applicationId) {
        requireExposed("password-policy");
        requireApplication(applicationId);
        boolean inherits = !hasOverride(applicationId, "password_policy");
        Map<String, Object> merged = new HashMap<>(resolvePasswordPolicy(applicationId));
        merged.put("scope", "application");
        merged.put("inheritsPlatformDefaults", inherits);
        // With two-factor off the password is the only factor: NIST SP 800-63B then asks for at least 15 characters.
        merged.put("twoFactorMode", String.valueOf(twoStepPolicy.resolve(applicationId).policy().get("mode")));
        return merged;
    }

    public Map<String, Object> savePasswordPolicy(UUID applicationId, Map<String, Object> body) {
        requireExposed("password-policy");
        requireApplication(applicationId);
        Map<String, Object> sanitized = PasswordPolicySanitizer.sanitize(body);
        PlatformMinimums.password(authSettings.getPasswordPolicy(), sanitized).requireNothingRaised();
        saveAppMap(applicationId, "password_policy", sanitized);
        return getPasswordPolicy(applicationId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMfaPolicy(UUID applicationId) {
        requireApplication(applicationId);
        Map<String, Object> merged = new java.util.LinkedHashMap<>(resolveMfaPolicy(applicationId));
        merged.put("scope", "application");
        // False: the "Two Factor Authentication" switch is off (or Two Factor Authentication is not open to
        // applications); any values saved here wait unused.
        merged.put("ownSettings", twoStepPolicy.hasOwnSettings(applicationId));
        // True: the switch is off, so two-factor is off for this application unless the platform or tenant locks it.
        merged.put("switchedOff", twoStepPolicy.switchedOff(applicationId));
        merged.put("inheritsPlatformDefaults", !hasOverride(applicationId, "mfa_policy"));
        // Which values this app sets itself, and what it inherits (platform, then tenant), for the settings page.
        merged.put("overrides", getAppMap(applicationId, "mfa_policy").map(m -> List.copyOf(m.keySet())).orElse(List.of()));
        merged.put("inherited", inheritedMfaPolicy(applicationId));
        return merged;
    }

    /**
     * Saves the application's two-step overrides: only values that differ from what it inherits (platform, then
     * tenant), so later platform or tenant changes still apply. Values the levels above lock are refused.
     */
    public Map<String, Object> saveMfaPolicy(UUID applicationId, Map<String, Object> body) {
        requireExposed(com.secureone.auth.twostep.TwoStepPolicyResolver.OWN_SETTINGS_EXPOSURE);
        requireApplication(applicationId);
        if (!twoStepPolicy.hasOwnSettings(applicationId)) {
            throw new IllegalArgumentException(
                    "Turn on Two Factor Authentication under this application's Settings → Feature flags first.");
        }
        Map<String, Object> overrides = com.secureone.auth.mfa.MfaPolicyDefaults.sanitizeOverride(body);
        overrides.remove(com.secureone.auth.mfa.MfaPolicyDefaults.LOCKS);
        twoStepPolicy.requireAllowedForApplication(applicationId, overrides);
        Map<String, Object> inherited = inheritedMfaPolicy(applicationId);
        overrides.entrySet().removeIf(e -> e.getValue().equals(inherited.get(e.getKey())));
        if (overrides.isEmpty()) {
            appSettings.findByApplicationIdAndKey(applicationId, "mfa_policy").ifPresent(appSettings::delete);
        } else {
            saveAppMap(applicationId, "mfa_policy", overrides);
        }
        return getMfaPolicy(applicationId);
    }

    /** The policy the application gets without its own overrides (platform, then its tenant's defaults). */
    private Map<String, Object> inheritedMfaPolicy(UUID applicationId) {
        UUID tenantId = twoStepPolicy.tenantOf(applicationId);
        return tenantId != null ? twoStepPolicy.resolveTenant(tenantId).policy() : twoStepPolicy.platform();
    }

    /** Effective two-step policy: platform, then tenant defaults, then the application, under lock modes. */
    @Transactional(readOnly = true)
    public Map<String, Object> resolveMfaPolicy(UUID applicationId) {
        requireApplication(applicationId);
        return twoStepPolicy.resolve(applicationId).policy();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getVerificationPolicy(UUID applicationId) {
        requireExposed("verification-policy");
        requireApplication(applicationId);
        boolean inherits = !hasOverride(applicationId, "verification_policy");
        // What sign-in enforces: the app's override, otherwise the platform policy (not the code defaults).
        Map<String, Object> merged = new HashMap<>(resolveVerificationPolicy(applicationId));
        merged.put("scope", "application");
        merged.put("inheritsPlatformDefaults", inherits);
        return merged;
    }

    public Map<String, Object> saveVerificationPolicy(UUID applicationId, Map<String, Object> body) {
        requireExposed("verification-policy");
        requireApplication(applicationId);
        Map<String, Object> sanitized = VerificationPolicyDefaults.sanitizeForSave(body);
        PlatformMinimums.verification(platformVerificationPolicy(), sanitized).requireNothingRaised();
        saveAppMap(applicationId, "verification_policy", sanitized);
        return getVerificationPolicy(applicationId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getFeatureFlags(UUID applicationId) {
        requireExposed("feature-flags");
        requireApplication(applicationId);
        List<Map<String, Object>> platform = authSettings.getFeatureFlags();
        return FeatureFlagMerge.forApplicationAdmin(platform, getAppRaw(applicationId, "feature_flags"));
    }

    public List<Map<String, Object>> saveFeatureFlags(UUID applicationId, List<Map<String, Object>> body) {
        requireExposed("feature-flags");
        requireApplication(applicationId);
        List<Map<String, Object>> platform = authSettings.getFeatureFlags();
        List<Map<String, Object>> sanitized = FeatureFlagSanitizer.sanitize(stripMeta(copyList(body)));
        // Only application features can be overridden; console controls stay platform decisions.
        sanitized = FeatureFlagMerge.applicationScopedOnly(sanitized);
        sanitized = FeatureFlagMerge.clampAppOverridesToPlatform(platform, sanitized);
        saveAppRaw(applicationId, "feature_flags", sanitized);
        return getFeatureFlags(applicationId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAppearance(UUID applicationId) {
        requireExposed("appearance");
        requireApplication(applicationId);
        Map<String, Object> merged =
                ClientAppearanceDefaults.merge(
                        ClientAppearanceDefaults.platformDefaults(),
                        getAppMap(applicationId, "appearance").orElse(Map.of()));
        merged.put("scope", "application");
        merged.put("inheritsPlatformDefaults", !hasOverride(applicationId, "appearance"));
        return merged;
    }

    public Map<String, Object> saveAppearance(UUID applicationId, Map<String, Object> body) {
        requireExposed("appearance");
        requireApplication(applicationId);
        Map<String, Object> appearance = ClientAppearanceDefaults.stripMeta(body);
        appearance.keySet().retainAll(ClientAppearanceDefaults.PUBLIC_MANIFEST_KEYS);
        ClientAppearanceDefaults.requireValid(appearance);
        saveAppMap(applicationId, "appearance", appearance);
        return getAppearance(applicationId);
    }

    @Transactional(readOnly = true)
    public Map<String, String> getPolicySources(UUID applicationId) {
        requireApplication(applicationId);
        Map<String, String> sources = new LinkedHashMap<>();
        Map<String, String> sections = new LinkedHashMap<>();
        sections.put("auth-methods", "auth_methods");
        sections.put("two-factor", "mfa_policy");
        sections.put("password-policy", "password_policy");
        sections.put("feature-flags", "feature_flags");
        sections.put("appearance", "appearance");
        sections.put("user-directory", "user_directory");
        sections.put("public-manifest", "public_manifest");
        sections.put("token-policy", "token_policy");
        sections.put("notifications", "notifications");
        sections.put("email", "email");
        sections.put("sms", "sms");
        for (var entry : sections.entrySet()) {
            if (exposure.isExposed(entry.getKey())) {
                sources.put(entry.getKey(), policySource(applicationId, entry.getValue()));
            }
        }
        return sources;
    }

    public String getPolicySource(UUID applicationId, String exposureKey) {
        requireApplication(applicationId);
        String settingKey = SettingsSectionKeys.settingKeyForExposure(exposureKey);
        if (settingKey == null) {
            throw new IllegalArgumentException("Unknown settings section: " + exposureKey);
        }
        requireExposed(exposureKey);
        return policySource(applicationId, settingKey);
    }

    public String setPolicySource(UUID applicationId, String exposureKey, String source) {
        requireApplication(applicationId);
        String settingKey = SettingsSectionKeys.settingKeyForExposure(exposureKey);
        if (settingKey == null) {
            throw new IllegalArgumentException("Unknown settings section: " + exposureKey);
        }
        requireExposed(exposureKey);
        if ("platform".equalsIgnoreCase(source)) {
            clearOverride(applicationId, settingKey);
            return "platform";
        }
        if ("application".equalsIgnoreCase(source)) {
            snapshotEffectiveAsOverride(applicationId, settingKey);
            return "application";
        }
        throw new IllegalArgumentException("source must be 'platform' or 'application'");
    }

    public void clearOverride(UUID applicationId, String key) {
        requireApplication(applicationId);
        String exposureKey = SettingsSectionKeys.exposureForSettingKey(key);
        if (exposureKey != null) {
            requireExposed(exposureKey);
        }
        appSettings
                .findByApplicationIdAndKey(applicationId, key)
                .ifPresent(row -> {
                    appSettings.delete(row);
                    if ("token_policy".equals(key)) {
                        syncTokenPolicyToApplicationConfig(applicationId);
                    }
                    if (SmtpSettingsService.SETTINGS_KEY.equals(key)) {
                        mailSenderProvider.invalidate(applicationId);
                    }
                });
    }

    /**
     * Settings sections that tenant configuration files carry. SMTP and SMS are left out: they hold
     * provider credentials, which belong in the console or the environment, not in a file.
     */
    public static final List<String> PORTABLE_SETTING_KEYS = List.of(
            "auth_methods", "password_policy", "mfa_policy", "verification_policy", "token_policy",
            "feature_flags", "appearance", "user_directory", "notifications", "email", "client_integration");

    private static final java.util.Set<String> LIST_SETTING_KEYS = java.util.Set.of("auth_methods", "feature_flags");
    private static final java.util.Set<String> CREDENTIAL_FIELDS =
            java.util.Set.of("bindpassword", "password", "secret", "clientsecret", "apikey", "authtoken");

    public static boolean isListSetting(String key) {
        return LIST_SETTING_KEYS.contains(key);
    }

    /** The application's own overrides (not the inherited platform values), with credentials blanked. */
    @Transactional(readOnly = true)
    public Map<String, Object> exportOverrides(UUID applicationId) {
        requireApplication(applicationId);
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : PORTABLE_SETTING_KEYS) {
            Object raw = getAppRaw(applicationId, key);
            if (raw != null) {
                out.put(key, redactCredentials(raw));
            }
        }
        return out;
    }

    /** Saves one section through its usual save path; a blank credential keeps the stored one. */
    @SuppressWarnings("unchecked")
    public void importOverride(UUID applicationId, String key, Object value) {
        Object merged = keepStoredCredentials(value, getAppRaw(applicationId, key));
        switch (key) {
            case "auth_methods" -> saveAuthMethods(applicationId, (List<Map<String, Object>>) merged);
            case "feature_flags" -> saveFeatureFlags(applicationId, (List<Map<String, Object>>) merged);
            case "password_policy" -> savePasswordPolicy(applicationId, (Map<String, Object>) merged);
            case "mfa_policy" -> {
                // A file carrying the application's own two-factor settings means it uses them.
                saveMfaTabEnabled(applicationId, true);
                saveMfaPolicy(applicationId, (Map<String, Object>) merged);
            }
            case "verification_policy" -> saveVerificationPolicy(applicationId, (Map<String, Object>) merged);
            case "token_policy" -> saveTokenPolicy(applicationId, (Map<String, Object>) merged);
            case "appearance" -> saveAppearance(applicationId, (Map<String, Object>) merged);
            case "user_directory" -> saveUserDirectory(applicationId, (Map<String, Object>) merged);
            case "notifications" -> saveNotifications(applicationId, (Map<String, Object>) merged);
            case "email" -> saveEmail(applicationId, (Map<String, Object>) merged);
            case "client_integration" -> saveClientIntegration(applicationId, (Map<String, Object>) merged);
            default -> throw new IllegalArgumentException("Unknown settings section: " + key);
        }
    }

    public static Object redactCredentials(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k),
                    isCredentialField(String.valueOf(k)) && v instanceof String ? "" : redactCredentials(v)));
            return out;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(ApplicationSettingsService::redactCredentials).toList();
        }
        return value;
    }

    private static Object keepStoredCredentials(Object incoming, Object stored) {
        if (incoming instanceof Map<?, ?> in) {
            Map<?, ?> old = stored instanceof Map<?, ?> m ? m : Map.of();
            Map<String, Object> out = new LinkedHashMap<>();
            in.forEach((k, v) -> {
                String name = String.valueOf(k);
                Object previous = old.get(name);
                if (isCredentialField(name) && (v == null || "".equals(v)) && previous instanceof String) {
                    out.put(name, previous);
                } else {
                    out.put(name, keepStoredCredentials(v, previous));
                }
            });
            return out;
        }
        return incoming;
    }

    private static boolean isCredentialField(String name) {
        return CREDENTIAL_FIELDS.contains(name.toLowerCase(java.util.Locale.ROOT));
    }

    private String policySource(UUID applicationId, String settingKey) {
        return hasOverride(applicationId, settingKey) ? "application" : "platform";
    }

    private void snapshotEffectiveAsOverride(UUID applicationId, String settingKey) {
        if (hasOverride(applicationId, settingKey)) {
            return;
        }
        switch (settingKey) {
            case "auth_methods" -> saveAppRaw(
                    applicationId, settingKey, stripMeta(copyList(authSettings.getAuthMethods())));
            case "password_policy" -> saveAppMap(
                    applicationId, settingKey, PasswordPolicySanitizer.sanitize(authSettings.getPasswordPolicy()));
            case "feature_flags" -> saveAppRaw(
                    applicationId, settingKey, stripMeta(copyList(authSettings.getFeatureFlags())));
            // The platform's stored "appearance" is the console's own look; an application's appearance is its
            // sign-in pages, which start from the built-in defaults.
            case "appearance" -> saveAppMap(
                    applicationId, settingKey, new HashMap<>(ClientAppearanceDefaults.platformDefaults()));
            case "verification_policy" -> saveAppMap(applicationId, settingKey, new HashMap<>(platformVerificationPolicy()));
            case "user_directory" -> saveAppMap(
                    applicationId,
                    settingKey,
                    UserDirectoryDefaults.merge(
                            UserDirectoryDefaults.platformDefaults(),
                            platformSettings.get("user_directory") != null
                                    ? platformSettings.get("user_directory")
                                    : Map.of()));
            case "public_manifest" -> {
                Map<String, Object> platform = platformSettings.get("public_manifest_defaults");
                if (platform == null || platform.isEmpty()) {
                    platform = PublicManifestDefaults.platformDefaults();
                }
                saveAppRaw(applicationId, settingKey, new LinkedHashMap<>(platform));
            }
            case "token_policy" -> {
                saveAppMap(applicationId, settingKey, new HashMap<>(platformTokenPolicy()));
                syncTokenPolicyToApplicationConfig(applicationId);
            }
            case "notifications" -> {
                Map<String, Object> effective = new HashMap<>(getNotifications(applicationId));
                effective.remove("scope");
                effective.remove("smtpConfigured");
                saveAppMap(
                        applicationId,
                        settingKey,
                        ApplicationNotificationDefaults.sanitizeNotifications(effective));
            }
            case "email" -> {
                Map<String, Object> effective = new HashMap<>(getEmail(applicationId));
                effective.remove("scope");
                effective.remove("smtpConfigured");
                saveAppMap(
                        applicationId,
                        settingKey,
                        ApplicationNotificationDefaults.sanitizeEmail(effective));
            }
            case "sms" -> saveAppRaw(
                    applicationId, settingKey, new HashMap<>(platformSmsSettings.snapshotForApplicationOverride()));
            // Overrides are stored as differences, so "own policy" starts empty and inherits until changed.
            case "mfa_policy" -> saveAppMap(applicationId, settingKey, new HashMap<>());
            default -> throw new IllegalArgumentException("Unsupported setting key: " + settingKey);
        }
    }

    private void requireExposed(String sectionKey) {
        if (!exposure.isExposed(sectionKey)) {
            throw new IllegalArgumentException(
                    "This setting section is not enabled for applications. Enable it under Platform settings → For applications.");
        }
    }

    private boolean hasOverride(UUID applicationId, String key) {
        return appSettings.findByApplicationIdAndKey(applicationId, key).isPresent();
    }

    private void saveAppMap(UUID applicationId, String key, Map<String, Object> value) {
        Map<String, Object> cleaned = new HashMap<>(value != null ? value : Map.of());
        cleaned.remove("scope");
        cleaned.remove("inheritsPlatformDefaults");
        cleaned.remove("smtpConfigured");
        cleaned.remove("smsConfigured");
        cleaned.remove("configured");
        cleaned.remove("availableProviders");
        cleaned.remove("platformExposed");
        saveAppRaw(applicationId, key, cleaned);
    }

    private void saveAppRaw(UUID applicationId, String key, Object value) {
        ApplicationSetting row = appSettings
                .findByApplicationIdAndKey(applicationId, key)
                .orElseGet(() -> {
                    ApplicationSetting created = new ApplicationSetting();
                    created.setApplicationId(applicationId);
                    created.setKey(key);
                    return created;
                });
        row.setValue(value);
        appSettings.save(row);
    }

    @SuppressWarnings("unchecked")
    private java.util.Optional<Map<String, Object>> getAppMap(UUID applicationId, String key) {
        Object raw = getAppRaw(applicationId, key);
        if (raw instanceof Map<?, ?> map) {
            return java.util.Optional.of((Map<String, Object>) map);
        }
        return java.util.Optional.empty();
    }

    private Object getAppRaw(UUID applicationId, String key) {
        return appSettings.findByApplicationIdAndKey(applicationId, key).map(ApplicationSetting::getValue).orElse(null);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mergeList(List<Map<String, Object>> platform, Object appOverride) {
        if (!(appOverride instanceof List<?> appList) || appList.isEmpty()) {
            return copyList(platform);
        }
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> item : platform) {
            String id = String.valueOf(item.get("id"));
            if (id == null || "null".equals(id)) {
                continue;
            }
            byId.put(id, new HashMap<>(item));
        }
        for (Object raw : appList) {
            if (raw instanceof Map<?, ?> row) {
                String id = String.valueOf(row.get("id"));
                Map<String, Object> patch = (Map<String, Object>) row;
                if (byId.containsKey(id)) {
                    // "implemented" is what the platform can do, not an app choice: an override saved
                    // while the catalog said otherwise must not switch a working method off.
                    Object implemented = byId.get(id).get("implemented");
                    byId.get(id).putAll(patch);
                    if (implemented != null) {
                        byId.get(id).put("implemented", implemented);
                    }
                } else {
                    byId.put(id, new HashMap<>(patch));
                }
            }
        }
        return new ArrayList<>(byId.values());
    }

    private static List<Map<String, Object>> copyList(List<Map<String, Object>> source) {
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> item : source) {
            copy.add(new HashMap<>(item));
        }
        return copy;
    }

    private static List<Map<String, Object>> stripMeta(List<Map<String, Object>> body) {
        for (Map<String, Object> row : body) {
            row.remove("inheritsPlatformDefaults");
            row.remove("scope");
            row.remove("platformEnabled");
        }
        return body;
    }

    /** Resolved settings for public manifest (no admin exposure gate). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> resolveAuthMethods(UUID applicationId) {
        requireApplication(applicationId);
        List<Map<String, Object>> platform = authSettings.getAuthMethods();
        List<Map<String, Object>> merged = mergeList(platform, getAppRaw(applicationId, "auth_methods"));
        // A method the platform turns off is off for every application (an application only narrows the list).
        java.util.Set<String> platformOn = new java.util.HashSet<>();
        for (Map<String, Object> m : platform) {
            if (Boolean.TRUE.equals(m.get("enabled"))) {
                platformOn.add(String.valueOf(m.get("id")));
            }
        }
        for (Map<String, Object> m : merged) {
            boolean allowed = platformOn.contains(String.valueOf(m.get("id")));
            m.put("platformEnabled", allowed);
            if (!allowed) {
                m.put("enabled", false);
            }
        }
        return merged;
    }

    /** Drops "on" for methods the platform has off, so a saved list never claims what it cannot do. */
    private List<Map<String, Object>> withinPlatformMethods(List<Map<String, Object>> body) {
        java.util.Set<String> platformOn = new java.util.HashSet<>();
        for (Map<String, Object> m : authSettings.getAuthMethods()) {
            if (Boolean.TRUE.equals(m.get("enabled"))) {
                platformOn.add(String.valueOf(m.get("id")));
            }
        }
        for (Map<String, Object> m : body) {
            if (!platformOn.contains(String.valueOf(m.get("id")))) {
                m.put("enabled", false);
            }
        }
        return body;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> resolveFeatureFlags(UUID applicationId) {
        requireApplication(applicationId);
        return FeatureFlagMerge.resolveEffective(
                authSettings.getFeatureFlags(), getAppRaw(applicationId, "feature_flags"));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resolvePasswordPolicy(UUID applicationId) {
        requireApplication(applicationId);
        Map<String, Object> platform = authSettings.getPasswordPolicy();
        if (hasOverride(applicationId, "password_policy")) {
            // Never weaker than the platform, including after the platform tightens.
            return PlatformMinimums.password(platform, PasswordPolicySanitizer.sanitize(
                    getAppMap(applicationId, "password_policy").orElse(Map.of()))).value();
        }
        return platform;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resolveVerificationPolicy(UUID applicationId) {
        requireApplication(applicationId);
        if (hasOverride(applicationId, "verification_policy")) {
            return PlatformMinimums.verification(platformVerificationPolicy(), VerificationPolicyDefaults.sanitizeForSave(
                    getAppMap(applicationId, "verification_policy").orElse(Map.of()))).value();
        }
        return platformVerificationPolicy();
    }

    private Map<String, Object> platformVerificationPolicy() {
        Map<String, Object> platform = platformSettings.get("verification_policy");
        if (platform == null || platform.isEmpty()) {
            return VerificationPolicyDefaults.platformDefaults();
        }
        return VerificationPolicyDefaults.sanitizeForSave(platform);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resolveAppearance(UUID applicationId) {
        requireApplication(applicationId);
        return ClientAppearanceDefaults.merge(
                ClientAppearanceDefaults.platformDefaults(),
                getAppMap(applicationId, "appearance").orElse(Map.of()));
    }

    private Map<String, Object> clientIntegrationDefaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("authUiMode", "hosted");
        defaults.put("clientUrls", defaultClientUrls());
        defaults.put("checklist", defaultIntegrationChecklist());
        defaults.put("notes", "");
        return defaults;
    }

    private static Map<String, String> defaultClientUrls() {
        Map<String, String> urls = new LinkedHashMap<>();
        urls.put("forgotPassword", "");
        urls.put("passwordReset", "");
        urls.put("magicLink", "");
        return urls;
    }

    private Map<String, Boolean> defaultIntegrationChecklist() {
        Map<String, Boolean> checklist = new LinkedHashMap<>();
        checklist.put("redirectUris", false);
        checklist.put("publicManifest", false);
        checklist.put("smtpConfigured", false);
        checklist.put("smsConfigured", false);
        checklist.put("authMethodsReviewed", false);
        checklist.put("signupReviewed", false);
        checklist.put("passwordResetTested", false);
        checklist.put("recoveryOtpTested", false);
        checklist.put("loginFlowTested", false);
        checklist.put("oauthPkceTested", false);
        checklist.put("mfaStepUpTested", false);
        checklist.put("sessionRevokeTested", false);
        checklist.put("magicLinkTested", false);
        checklist.put("federationTested", false);
        checklist.put("passkeyTested", false);
        return checklist;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeClientIntegration(Map<String, Object> defaults, Map<String, Object> stored) {
        Map<String, Object> out = new LinkedHashMap<>(defaults);
        if (stored.containsKey("authUiMode")) {
            String mode = String.valueOf(stored.get("authUiMode")).toLowerCase(java.util.Locale.ROOT);
            out.put("authUiMode", "native".equals(mode) ? "native" : "hosted");
        }
        if (stored.containsKey("notes")) {
            out.put("notes", String.valueOf(stored.get("notes")));
        }
        out.put("clientUrls", mergeClientUrls(stored.get("clientUrls")));
        Map<String, Boolean> mergedChecklist = new LinkedHashMap<>(defaultIntegrationChecklist());
        if (stored.get("checklist") instanceof Map<?, ?> raw) {
            for (String key : mergedChecklist.keySet()) {
                Object val = raw.get(key);
                if (val instanceof Boolean b) {
                    mergedChecklist.put(key, b);
                }
            }
        }
        out.put("checklist", mergedChecklist);
        return out;
    }

    private Map<String, Object> sanitizeClientIntegration(Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>();
        String mode = body != null && body.get("authUiMode") != null
                ? String.valueOf(body.get("authUiMode")).toLowerCase(java.util.Locale.ROOT)
                : "hosted";
        out.put("authUiMode", "native".equals(mode) ? "native" : "hosted");
        out.put("notes", body != null && body.get("notes") != null ? String.valueOf(body.get("notes")) : "");
        out.put("clientUrls", mergeClientUrls(body != null ? body.get("clientUrls") : null));
        Map<String, Boolean> checklist = defaultIntegrationChecklist();
        if (body != null && body.get("checklist") instanceof Map<?, ?> raw) {
            for (String key : checklist.keySet()) {
                Object val = raw.get(key);
                if (val instanceof Boolean b) {
                    checklist.put(key, b);
                }
            }
        }
        out.put("checklist", checklist);
        return out;
    }

    private static Map<String, String> mergeClientUrls(Object raw) {
        Map<String, String> merged = defaultClientUrls();
        if (!(raw instanceof Map<?, ?> map)) {
            return merged;
        }
        for (String key : merged.keySet()) {
            Object value = map.get(key);
            if (value != null) {
                merged.put(key, String.valueOf(value).trim());
            }
        }
        return merged;
    }

    /** Non-empty client URL overrides exposed on the public manifest. */
    public Map<String, String> publicClientUrls(UUID applicationId) {
        Map<String, String> urls = mergeClientUrls(getClientIntegration(applicationId).get("clientUrls"));
        // A page saved before these rules, or whose OAuth client was removed since, is skipped: links then use the
        // hosted pages rather than send a token somewhere this application does not control.
        Set<String> trusted = trustedClientOrigins(applicationId);
        urls.entrySet().removeIf(e -> e.getValue() == null || e.getValue().isBlank()
                || ClientUrlPolicy.problem(e.getValue(), trusted) != null);
        return urls;
    }

    /** Origins of this application's OAuth client redirect and post-logout addresses. */
    private Set<String> trustedClientOrigins(UUID applicationId) {
        List<String> uris = new ArrayList<>();
        for (var client : oauthClients.getObject().findByApplicationIdOrderByCreatedAtDesc(applicationId)) {
            uris.addAll(client.getRedirectUris());
            uris.addAll(client.getPostLogoutRedirectUris());
        }
        return ClientUrlPolicy.origins(uris);
    }

    private static String clientUrlLabel(String key) {
        return switch (key) {
            case "forgotPassword" -> "Forgot password";
            case "passwordReset" -> "Password reset";
            case "magicLink" -> "Magic link";
            default -> key;
        };
    }
}
