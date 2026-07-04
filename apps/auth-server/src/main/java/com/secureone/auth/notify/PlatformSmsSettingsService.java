package com.secureone.auth.notify;

import com.secureone.auth.notify.SmsProvider;
import com.secureone.auth.notify.sms.SmsProviderFactory;
import com.secureone.auth.notify.sms.SmsProviderIds;
import com.secureone.auth.notify.sms.SmsResolvedCredentials;
import com.secureone.auth.notify.sms.SmsSettingsMapper;
import com.secureone.auth.platform.PlatformSettingsService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PlatformSmsSettingsService {

    private final PlatformSettingsService platformSettings;
    private final SecretProtector secrets;
    private final SmsProviderFactory factory;

    public PlatformSmsSettingsService(
            PlatformSettingsService platformSettings,
            SecretProtector secrets,
            SmsProviderFactory factory) {
        this.platformSettings = platformSettings;
        this.secrets = secrets;
        this.factory = factory;
    }

    public Map<String, Object> getPublicSettings() {
        Map<String, Object> view = SmsSettingsMapper.toPublicView(loadMap());
        view.put("scope", "platform");
        return view;
    }

    public Map<String, Object> save(Map<String, Object> body) {
        Map<String, Object> merged = SmsSettingsMapper.mergeSave(loadMap(), body, secrets);
        platformSettings.saveMap(SmsSettingsMapper.SETTINGS_KEY, merged);
        return getPublicSettings();
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(loadMap().get("enabled"));
    }

    public boolean isConfigured() {
        return factory.isConfigured(resolveCredentials());
    }

    public SmsResolvedCredentials resolveCredentials() {
        return SmsSettingsMapper.resolveCredentials(loadMap(), secrets);
    }

    public SmsProvider resolveProvider() {
        SmsResolvedCredentials credentials = resolveCredentials();
        if (!credentials.enabled()) {
            return factory.create(SmsResolvedCredentials.logOnly());
        }
        return factory.create(credentials);
    }

    /** @deprecated use {@link #resolveProvider()} via ApplicationSmsResolver */
    @Deprecated
    public SmsProvider resolveProviderLegacy() {
        return resolveProvider();
    }

    public Map<String, Object> snapshotForApplicationOverride() {
        return new HashMap<>(loadMap());
    }

    private Map<String, Object> loadMap() {
        Map<String, Object> stored = platformSettings.get(SmsSettingsMapper.SETTINGS_KEY);
        if (stored == null || stored.isEmpty()) {
            Map<String, Object> defaults = SmsSettingsMapper.defaultSettings();
            defaults.put("provider", SmsProviderIds.LOG);
            return defaults;
        }
        return new HashMap<>(stored);
    }
}
