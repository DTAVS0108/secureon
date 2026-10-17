package com.secureone.auth.platform;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Platform-only switches for console features (stored as platform setting {@code platform_features}).
 * Unlike feature flags, applications cannot override these.
 */
@Service
public class PlatformFeatures {

    static final String SETTING_KEY = "platform_features";
    /** Configuration as code: the console page and /api/admin/v1/config. Off until a platform operator turns it on. */
    public static final String CONFIG_AS_CODE = "configAsCode";
    /** Connect an app (guided wizard): on until a platform operator turns it off. */
    public static final String CONNECT_APP = "connectApp";
    /** Setup code on each OAuth client's page (Platform admin → OAuth clients): on until turned off. */
    public static final String CLIENT_SETUP_CODE = "clientSetupCode";

    private final PlatformSettingsService settings;

    public PlatformFeatures(PlatformSettingsService settings) {
        this.settings = settings;
    }

    public boolean configAsCodeEnabled() {
        return Boolean.TRUE.equals(get().get(CONFIG_AS_CODE));
    }

    public boolean connectAppEnabled() {
        return Boolean.TRUE.equals(get().get(CONNECT_APP));
    }

    public boolean clientSetupCodeEnabled() {
        return Boolean.TRUE.equals(get().get(CLIENT_SETUP_CODE));
    }

    /** Every known switch with its current value; an unset switch has its default. */
    public Map<String, Object> get() {
        Map<String, Object> stored = settings.get(SETTING_KEY);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(CONFIG_AS_CODE, Boolean.TRUE.equals(stored.get(CONFIG_AS_CODE))); // default off
        result.put(CONNECT_APP, !Boolean.FALSE.equals(stored.get(CONNECT_APP))); // default on
        result.put(CLIENT_SETUP_CODE, !Boolean.FALSE.equals(stored.get(CLIENT_SETUP_CODE))); // default on
        return result;
    }

    /** Saves the known switches present in {@code changes}; other keys are ignored. */
    public Map<String, Object> update(Map<String, Object> changes) {
        Map<String, Object> next = get();
        if (changes != null) {
            for (String key : next.keySet()) {
                if (changes.get(key) instanceof Boolean value) {
                    next.put(key, value);
                }
            }
        }
        settings.saveMap(SETTING_KEY, next);
        return next;
    }
}
