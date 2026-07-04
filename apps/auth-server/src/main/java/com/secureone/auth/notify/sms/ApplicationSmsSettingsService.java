package com.secureone.auth.notify.sms;

import com.secureone.auth.application.ApplicationSetting;
import com.secureone.auth.application.ApplicationSettingRepository;
import com.secureone.auth.notify.SecretProtector;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ApplicationSmsSettingsService {

    private final ApplicationSettingRepository appSettings;
    private final SecretProtector secrets;

    public ApplicationSmsSettingsService(ApplicationSettingRepository appSettings, SecretProtector secrets) {
        this.appSettings = appSettings;
        this.secrets = secrets;
    }

    public boolean hasOverride(UUID applicationId) {
        return applicationId != null
                && appSettings.findByApplicationIdAndKey(applicationId, SmsSettingsMapper.SETTINGS_KEY).isPresent();
    }

    public boolean isConfigured(UUID applicationId) {
        return SmsSettingsMapper.isConfigured(resolveCredentials(applicationId));
    }

    public Map<String, Object> getPublicSettings(UUID applicationId) {
        Map<String, Object> view = SmsSettingsMapper.toPublicView(loadMap(applicationId));
        view.put("scope", "application");
        view.put("inheritsPlatformDefaults", !hasOverride(applicationId));
        return view;
    }

    public Map<String, Object> save(UUID applicationId, Map<String, Object> body) {
        Map<String, Object> merged = SmsSettingsMapper.mergeSave(loadMap(applicationId), body, secrets);
        persist(applicationId, merged);
        return getPublicSettings(applicationId);
    }

    public SmsResolvedCredentials resolveCredentials(UUID applicationId) {
        return SmsSettingsMapper.resolveCredentials(loadMap(applicationId), secrets);
    }

    public Map<String, Object> platformSnapshotForOverride() {
        return new HashMap<>(SmsSettingsMapper.defaultSettings());
    }

    private Map<String, Object> loadMap(UUID applicationId) {
        return appSettings
                .findByApplicationIdAndKey(applicationId, SmsSettingsMapper.SETTINGS_KEY)
                .map(ApplicationSetting::getValue)
                .filter(Map.class::isInstance)
                .map(value -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) value;
                    return new HashMap<>(map);
                })
                .orElseGet(() -> new HashMap<>(SmsSettingsMapper.defaultSettings()));
    }

    private void persist(UUID applicationId, Map<String, Object> value) {
        ApplicationSetting row = appSettings
                .findByApplicationIdAndKey(applicationId, SmsSettingsMapper.SETTINGS_KEY)
                .orElseGet(() -> {
                    ApplicationSetting created = new ApplicationSetting();
                    created.setApplicationId(applicationId);
                    created.setKey(SmsSettingsMapper.SETTINGS_KEY);
                    return created;
                });
        row.setValue(value);
        appSettings.save(row);
    }
}
