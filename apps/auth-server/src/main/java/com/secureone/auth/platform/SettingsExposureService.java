package com.secureone.auth.platform;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Controls which platform setting sections appear in per-application settings: a platform default per section,
 * and exceptions for single applications ("open for this app" / "closed for this app"). Two Factor Authentication
 * has no exceptions: it has its own per-application switch, and closing it needs another operator's approval.
 */
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

    public static final String OVERRIDES_KEY = "app_settings_exposure_overrides";
    /** Sections that cannot differ per application. */
    public static final java.util.Set<String> NO_EXCEPTIONS = java.util.Set.of("two-factor");

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

    /** For the application of the current request (its exceptions apply), else the platform default. */
    public boolean isExposed(String sectionKey) {
        return isExposed(sectionKey, com.secureone.auth.application.ApplicationSchemaContext.getApplicationId());
    }

    public boolean isExposed(String sectionKey, UUID applicationId) {
        return Boolean.TRUE.equals(getExposureFor(applicationId).get(sectionKey));
    }

    /** The platform defaults with this application's exceptions applied (null: the defaults). */
    public Map<String, Boolean> getExposureFor(UUID applicationId) {
        Map<String, Boolean> result = getExposure();
        if (applicationId == null) {
            return result;
        }
        Object raw = getOverrides().get(applicationId.toString());
        if (raw instanceof Map<?, ?> exceptions) {
            exceptions.forEach((k, v) -> {
                String key = String.valueOf(k);
                if (SECTION_KEYS.contains(key) && !NO_EXCEPTIONS.contains(key) && v instanceof Boolean b) {
                    result.put(key, b);
                }
            });
        }
        return result;
    }

    /** applicationId → { section → open (true) / closed (false) }. */
    public Map<String, Object> getOverrides() {
        Map<String, Object> stored = platformSettings.get(OVERRIDES_KEY);
        return stored != null ? stored : Map.of();
    }

    /** Replaces one application's exceptions; a section missing from {@code exceptions} follows the default. */
    public Map<String, Object> saveOverrides(UUID applicationId, Map<String, Boolean> exceptions) {
        Map<String, Object> all = new LinkedHashMap<>(getOverrides());
        Map<String, Boolean> clean = new LinkedHashMap<>();
        if (exceptions != null) {
            exceptions.forEach((k, v) -> {
                if (SECTION_KEYS.contains(k) && !NO_EXCEPTIONS.contains(k) && v != null) {
                    clean.put(k, v);
                }
            });
        }
        if (clean.isEmpty()) {
            all.remove(applicationId.toString());
        } else {
            all.put(applicationId.toString(), clean);
        }
        platformSettings.save(OVERRIDES_KEY, all);
        return getOverrides();
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
