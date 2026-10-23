package com.secureone.auth.application;

import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.account.AccountVerificationService;
import com.secureone.auth.federation.IdentityProviderService;
import com.secureone.auth.platform.SettingsExposureService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds unauthenticated application manifest responses for client apps. */
@Service
@Transactional
public class ApplicationPublicManifestService {

    private final ApplicationRepository applications;
    private final ApplicationSettingRepository appSettings;
    private final ApplicationSettingsService settings;
    private final com.secureone.auth.platform.PlatformSettingsService platformSettings;
    private final SettingsExposureService exposure;
    private final ApplicationTenantResolver tenantResolver;
    private final AccountVerificationService accountVerification;
    private final IdentityProviderService federationProviders;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<ApplicationEffectiveSettingsService> effectiveSettings;

    public ApplicationPublicManifestService(
            ApplicationRepository applications,
            ApplicationSettingRepository appSettings,
            ApplicationSettingsService settings,
            com.secureone.auth.platform.PlatformSettingsService platformSettings,
            SettingsExposureService exposure,
            ApplicationTenantResolver tenantResolver,
            AccountVerificationService accountVerification,
            IdentityProviderService federationProviders) {
        this.applications = applications;
        this.appSettings = appSettings;
        this.settings = settings;
        this.platformSettings = platformSettings;
        this.exposure = exposure;
        this.tenantResolver = tenantResolver;
        this.accountVerification = accountVerification;
        this.federationProviders = federationProviders;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getManifestConfig(UUID applicationId) {
        requirePublicManifestExposed();
        settings.requireApplication(applicationId);
        Map<String, Object> out = configFor(applicationId);
        out.put("scope", "application");
        out.put("inheritsPlatformDefaults", !hasOverride(applicationId));
        out.put("publicEndpoint", "/api/v1/applications/" + applicationId);
        return out;
    }

    public Map<String, Object> saveManifestConfig(UUID applicationId, Map<String, Object> body) {
        requirePublicManifestExposed();
        settings.requireApplication(applicationId);
        Map<String, Object> current = new LinkedHashMap<>(configFor(applicationId));
        current.remove("scope");
        current.remove("inheritsPlatformDefaults");
        current.remove("publicEndpoint");
        if (body.containsKey("enabled")) {
            current.put("enabled", Boolean.TRUE.equals(body.get("enabled")));
        }
        if (body.get("sections") instanceof Map<?, ?> sections) {
            @SuppressWarnings("unchecked")
            Map<String, Object> existing = (Map<String, Object>) current.getOrDefault("sections", new LinkedHashMap<>());
            Map<String, Object> sec = new LinkedHashMap<>(existing);
            sections.forEach((k, v) -> sec.put(k.toString(), Boolean.TRUE.equals(v)));
            current.put("sections", sec);
        }
        if (body.containsKey("authMethodsOnlyEnabled")) {
            current.put("authMethodsOnlyEnabled", Boolean.TRUE.equals(body.get("authMethodsOnlyEnabled")));
        }
        saveConfig(applicationId, current);
        return getManifestConfig(applicationId);
    }

    public void clearManifestConfig(UUID applicationId) {
        requirePublicManifestExposed();
        settings.requireApplication(applicationId);
        appSettings
                .findByApplicationIdAndKey(applicationId, "public_manifest")
                .ifPresent(appSettings::delete);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPublicManifest(UUID applicationId) {
        requirePublicManifestExposed();
        Application app = applications
                .findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        if (!"ACTIVE".equalsIgnoreCase(app.getStatus())) {
            throw new ResourceNotFoundException("Application not found: " + applicationId);
        }
        Map<String, Object> config = configFor(applicationId);
        if (!Boolean.TRUE.equals(config.get("enabled"))) {
            throw new ResourceNotFoundException("Public manifest is disabled for this application");
        }
        @SuppressWarnings("unchecked")
        Map<String, Boolean> sections =
                (Map<String, Boolean>) config.getOrDefault("sections", Map.of());
        boolean onlyEnabledAuth = !Boolean.FALSE.equals(config.get("authMethodsOnlyEnabled"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("applicationId", app.getId().toString());
        response.put("manifestEnabled", true);

        if (Boolean.TRUE.equals(sections.get("application"))) {
            response.put(
                    "application",
                    Map.of(
                            "id", app.getId().toString(),
                            "name", app.getName(),
                            "slug", app.getSlug(),
                            "status", app.getStatus().toLowerCase(Locale.ROOT)));
        }
        List<Map<String, Object>> providers = federationProviders.listPublic(app.getTenantId(), applicationId);
        if (Boolean.TRUE.equals(sections.get("authMethods"))) {
            java.util.Set<Object> configuredFederation = new java.util.HashSet<>();
            providers.forEach(p -> configuredFederation.add(p.get("authMethodId")));
            // One-time-code sign-in follows Verification & OTP › Passwordless modes (and an SMS provider for texts).
            List<Map<String, Object>> methods = new ArrayList<>();
            for (Map<String, Object> m : settings.resolveAuthMethods(applicationId)) {
                Map<String, Object> row = new LinkedHashMap<>(m);
                String id = String.valueOf(m.get("id"));
                if (ApplicationEffectiveSettingsService.CODE_SIGN_IN_METHODS.containsKey(id)) {
                    row.put("enabled", effectiveSettings.getObject().isAuthMethodEnabled(applicationId, id));
                    row.put("implemented", true);
                }
                methods.add(row);
            }
            response.put("authMethods", sanitizeAuthMethods(methods, onlyEnabledAuth, configuredFederation));
        }
        if (Boolean.TRUE.equals(sections.get("featureFlags"))) {
            response.put("featureFlags", sanitizeFeatureFlags(settings.resolveFeatureFlags(applicationId)));
        }
        if (Boolean.TRUE.equals(sections.get("passwordPolicy"))) {
            response.put(
                    "passwordPolicy",
                    com.secureone.auth.account.PasswordPolicyRules.publicPolicy(
                            settings.resolvePasswordPolicy(applicationId)));
        }
        if (Boolean.TRUE.equals(sections.get("verificationPolicy"))) {
            response.put("verification", accountVerification.verificationOptions(applicationId));
        }
        if (Boolean.TRUE.equals(sections.get("appearance"))) {
            response.put("appearance", sanitizeAppearance(settings.resolveAppearance(applicationId)));
        }
        if (Boolean.TRUE.equals(sections.get("federation"))) {
            response.put("federation", buildFederationBlock(applicationId, providers));
        }
        // The operator's preferred sign-in UI, so a client can pick hosted redirect vs its own screens
        // without a build-time flag. Advisory only: both flows stay available.
        response.put("authUiMode", settings.getClientIntegration(applicationId).get("authUiMode"));
        response.put("signup", buildSignupBlock(applicationId, app));
        response.put(
                "account",
                ApplicationAccountEndpoints.manifestBlock(applicationId, settings.publicClientUrls(applicationId)));
        return response;
    }

    private Map<String, Object> buildFederationBlock(UUID applicationId, List<Map<String, Object>> providers) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put(
                "providersEndpoint",
                "/api/v1/auth/federation/providers?applicationId=" + applicationId);
        block.put("providers", providers);
        return block;
    }

    private Map<String, Object> buildSignupBlock(UUID applicationId, Application app) {
        boolean enabled = settings.resolveFeatureFlags(applicationId).stream()
                .filter(f -> "self_registration".equals(String.valueOf(f.get("key"))))
                .findFirst()
                .map(f -> Boolean.TRUE.equals(f.get("enabled")))
                .orElse(false);
        Map<String, Object> signup = new LinkedHashMap<>();
        signup.put("enabled", enabled);
        signup.put("endpoint", "/api/v1/applications/" + applicationId + "/signup");
        signup.put("optionsEndpoint", "/api/v1/applications/" + applicationId + "/signup");
        signup.put("hostedPagePath", "/account/signup.html?applicationId=" + applicationId);
        return signup;
    }

    private Map<String, Object> configFor(UUID applicationId) {
        Map<String, Object> platform = platformSettings.get("public_manifest_defaults");
        if (platform == null || platform.isEmpty()) {
            platform = PublicManifestDefaults.platformDefaults();
        }
        Map<String, Object> merged =
                PublicManifestDefaults.merge(platform, getAppMap(applicationId).orElse(Map.of()));
        return merged;
    }

    private void saveConfig(UUID applicationId, Map<String, Object> value) {
        var row = appSettings
                .findByApplicationIdAndKey(applicationId, "public_manifest")
                .orElseGet(() -> {
                    ApplicationSetting s = new ApplicationSetting();
                    s.setApplicationId(applicationId);
                    s.setKey("public_manifest");
                    return s;
                });
        row.setValue(value);
        appSettings.save(row);
    }

    private boolean hasOverride(UUID applicationId) {
        return appSettings.findByApplicationIdAndKey(applicationId, "public_manifest").isPresent();
    }

    private java.util.Optional<Map<String, Object>> getAppMap(UUID applicationId) {
        return appSettings
                .findByApplicationIdAndKey(applicationId, "public_manifest")
                .map(ApplicationSetting::getValue)
                .filter(v -> v instanceof Map<?, ?>)
                .map(v -> (Map<String, Object>) v);
    }

    /**
     * {@code available} tells a client whether to offer the method. A federation method (Google,
     * GitHub, external OIDC) also needs an enabled provider in the tenant; without one its button
     * would lead nowhere.
     */
    private static List<Map<String, Object>> sanitizeAuthMethods(
            List<Map<String, Object>> methods, boolean onlyEnabled, java.util.Set<Object> configuredFederation) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> m : methods) {
            boolean enabled = Boolean.TRUE.equals(m.get("enabled"));
            if (onlyEnabled && !enabled) {
                continue;
            }
            boolean implemented = !Boolean.FALSE.equals(m.get("implemented"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.get("id"));
            row.put("name", m.get("name"));
            row.put("description", m.get("description"));
            row.put("category", m.get("category"));
            row.put("enabled", enabled);
            row.put("implemented", implemented);
            boolean needsProvider = "federation".equals(m.get("category"));
            boolean available = enabled && implemented && (!needsProvider || configuredFederation.contains(m.get("id")));
            row.put("available", available);
            if (enabled && implemented && !available) {
                row.put("unavailableReason", "No sign-in provider is configured for this organization.");
            }
            out.add(row);
        }
        return out;
    }

    private static List<Map<String, Object>> sanitizeFeatureFlags(List<Map<String, Object>> flags) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> f : flags) {
            // Client apps only need product capabilities, not the console's own controls.
            if (!com.secureone.auth.platform.FeatureFlagDefaults.isApplicationScoped(String.valueOf(f.get("key")))) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", f.get("key"));
            row.put("name", f.get("name"));
            row.put("description", f.get("description"));
            row.put("enabled", Boolean.TRUE.equals(f.get("enabled")));
            Object category = f.get("category");
            if (category != null) {
                row.put("category", String.valueOf(category));
            }
            out.add(row);
        }
        return out;
    }

    private static Map<String, Object> sanitizeAppearance(Map<String, Object> appearance) {
        return ClientAppearanceDefaults.sanitizeForPublicManifest(appearance);
    }

    private static void copyIfPresent(Map<String, Object> from, Map<String, Object> to, String key) {
        if (from.containsKey(key) && from.get(key) != null) {
            to.put(key, from.get(key));
        }
    }

    private void requirePublicManifestExposed() {
        if (!exposure.isExposed("public-manifest")) {
            throw new IllegalArgumentException(
                    "Public API settings are not enabled for applications. Enable under Platform settings → For applications.");
        }
    }
}
