package com.secureone.auth.application;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The name end users know an application by: Appearance → display name when set, otherwise the
 * application's name. Used wherever SecureOne speaks to an app's users (email sender and text, the
 * passkey prompt) so they see the app's own name (e.g. "Demo Store"), not the identity platform.
 */
@Service
public class ApplicationDisplayName {

    public static final String PLATFORM_NAME = "SecureOne";

    private final ApplicationRepository applications;
    private final ApplicationSettingRepository appSettings;

    public ApplicationDisplayName(ApplicationRepository applications, ApplicationSettingRepository appSettings) {
        this.applications = applications;
        this.appSettings = appSettings;
    }

    public String of(UUID applicationId) {
        if (applicationId == null) {
            return PLATFORM_NAME;
        }
        return applications.findById(applicationId).map(this::of).orElse(PLATFORM_NAME);
    }

    public String of(Application app) {
        return appearanceName(app.getId()).orElse(app.getName());
    }

    private Optional<String> appearanceName(UUID applicationId) {
        return appSettings
                .findByApplicationIdAndKey(applicationId, "appearance")
                .map(ApplicationSetting::getValue)
                .filter(Map.class::isInstance)
                .map(v -> ((Map<?, ?>) v).get("appName"))
                .map(String::valueOf)
                .map(String::trim)
                .filter(n -> !n.isEmpty() && !"null".equals(n));
    }
}
