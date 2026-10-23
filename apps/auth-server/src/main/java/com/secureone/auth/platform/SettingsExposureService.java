package com.secureone.auth.platform;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Controls which platform setting sections appear in per-application settings. */
@Service
public class SettingsExposureService {

    public static final List<String> SECTION_KEYS = List.of(
            "notifications",
            "email",
            "auth-methods",
            // Two Factor Authentication: apps may have their own two-factor settings (separate from sign-in methods).
            "two-factor",
            "password-policy",
            "feature-flags",
            "appearance",
            "user-directory",
            "public-manifest",
            "token-policy",
            "verification-policy",
            "sms");

    private final PlatformSettingsService platformSettings;

    public SettingsExposureService(PlatformSettingsService platformSettings) {
        this.platformSettings = platformSettings;
    }

    public Map<String, Boolean> getExposure() {
        Map<String, Object> stored = platformSettings.get("app_settings_exposure");
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String key : SECTION_KEYS) {
            Object val = stored.get(key);
            result.put(key, readBoolean(val, key));
        }
        return result;
    }

    public Map<String, Boolean> saveExposure(Map<String, Boolean> body) {
        Map<String, Object> existing = platformSettings.get("app_settings_exposure");
        Map<String, Object> toSave = new LinkedHashMap<>();
        for (String key : SECTION_KEYS) {
            if (body != null && body.containsKey(key)) {
                toSave.put(key, Boolean.TRUE.equals(body.get(key)));
            } else {
                toSave.put(key, readBoolean(existing.get(key), key));
            }
        }
        platformSettings.save("app_settings_exposure", toSave);
        return getExposure();
    }

    public boolean isExposed(String sectionKey) {
        return Boolean.TRUE.equals(getExposure().get(sectionKey));
    }

    private static boolean defaultExposure(String key) {
        return !"appearance".equals(key);
    }

    private static boolean readBoolean(Object val, String key) {
        if (val instanceof Boolean b) {
            return b;
        }
        if (val instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return defaultExposure(key);
    }
}
