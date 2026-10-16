package com.secureone.auth.application;

import com.secureone.auth.platform.AuthSettingsService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves effective auth policy for an application (app override + platform default). */
@Service
@Transactional(readOnly = true)
public class ApplicationEffectiveSettingsService {

    private final AuthSettingsService platformAuth;
    private final ApplicationSettingsService applicationSettings;

    public ApplicationEffectiveSettingsService(
            AuthSettingsService platformAuth, ApplicationSettingsService applicationSettings) {
        this.platformAuth = platformAuth;
        this.applicationSettings = applicationSettings;
    }

    public boolean isAuthMethodEnabled(UUID applicationId, String methodId) {
        List<Map<String, Object>> methods = applicationId != null
                ? applicationSettings.resolveAuthMethods(applicationId)
                : platformAuth.getAuthMethods();
        return methodEnabled(methods, methodId);
    }

    public boolean isAuthMethodImplemented(UUID applicationId, String methodId) {
        List<Map<String, Object>> methods = applicationId != null
                ? applicationSettings.resolveAuthMethods(applicationId)
                : platformAuth.getAuthMethods();
        return methodImplemented(methods, methodId);
    }

    /** Enabled and implemented for this application (platform defaults when {@code applicationId} is null). */
    public boolean isMethodAvailable(UUID applicationId, String methodId) {
        return isAuthMethodEnabled(applicationId, methodId) && isAuthMethodImplemented(applicationId, methodId);
    }

    /** Same, for the application the current request is for (see ApplicationSchemaContext). */
    public boolean isMethodAvailableForCurrentApplication(String methodId) {
        return isMethodAvailable(ApplicationSchemaContext.getApplicationId(), methodId);
    }

    public boolean isFeatureEnabled(UUID applicationId, String key) {
        if (applicationId != null && !platformAuth.isFeatureEnabled(key)) {
            return false;
        }
        List<Map<String, Object>> flags = applicationId != null
                ? applicationSettings.resolveFeatureFlags(applicationId)
                : platformAuth.getFeatureFlags();
        return featureEnabled(flags, key);
    }

    public Map<String, Object> passwordPolicy(UUID applicationId) {
        if (applicationId != null) {
            return applicationSettings.resolvePasswordPolicy(applicationId);
        }
        return platformAuth.getPasswordPolicy();
    }

    private static boolean methodEnabled(List<Map<String, Object>> methods, String methodId) {
        return methods.stream()
                .filter(m -> methodId.equals(String.valueOf(m.get("id"))))
                .findFirst()
                .map(m -> Boolean.TRUE.equals(m.get("enabled")))
                .orElse(false);
    }

    private static boolean methodImplemented(List<Map<String, Object>> methods, String methodId) {
        return methods.stream()
                .filter(m -> methodId.equals(String.valueOf(m.get("id"))))
                .findFirst()
                .map(m -> !Boolean.FALSE.equals(m.get("implemented")))
                .orElse(false);
    }

    private static boolean featureEnabled(List<Map<String, Object>> flags, String key) {
        return flags.stream()
                .filter(f -> key.equals(String.valueOf(f.get("key"))))
                .findFirst()
                .map(f -> Boolean.TRUE.equals(f.get("enabled")))
                .orElse(false);
    }
}
