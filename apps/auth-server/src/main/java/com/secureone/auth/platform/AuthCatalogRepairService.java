package com.secureone.auth.platform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Patches platform catalog rows when new features ship (without re-running Flyway). */
@Component
public class AuthCatalogRepairService {

    private final PlatformSettingRepository settings;

    public AuthCatalogRepairService(PlatformSettingRepository settings) {
        this.settings = settings;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void repairOnStartup() {
        repairAuthMethodImplemented("m_email_otp", true);
        repairAuthMethodImplemented("m_sms", true);
        repairAuthMethodImplemented("m_totp", true);
        repairAuthMethodImplemented("m_google", true);
        repairAuthMethodImplemented("m_github", true);
        repairAuthMethodImplemented("m_passkey", true);
        repairAppExposure("verification-policy", true);
        repairAppExposure("sms", true);
    }

    private void repairAuthMethodImplemented(String methodId, boolean implemented) {
        settings.findById("auth_methods").ifPresent(row -> {
            Object raw = row.getValue();
            if (!(raw instanceof List<?> list)) {
                return;
            }
            List<Map<String, Object>> patched = new ArrayList<>();
            boolean changed = false;
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                Map<String, Object> copy = new HashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    copy.put(String.valueOf(e.getKey()), e.getValue());
                }
                if (methodId.equals(String.valueOf(copy.get("id")))) {
                    Boolean current = copy.get("implemented") instanceof Boolean b ? b : null;
                    if (current == null || current != implemented) {
                        copy.put("implemented", implemented);
                        changed = true;
                    }
                }
                patched.add(copy);
            }
            if (changed) {
                row.setValue(patched);
                settings.save(row);
            }
        });
    }

    private void repairAppExposure(String key, boolean enabled) {
        settings.findById("app_settings_exposure").ifPresent(row -> {
            Object raw = row.getValue();
            if (!(raw instanceof Map<?, ?> map)) {
                return;
            }
            Map<String, Object> copy = new HashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                copy.put(String.valueOf(e.getKey()), e.getValue());
            }
            if (!copy.containsKey(key) || !Boolean.TRUE.equals(copy.get(key))) {
                copy.put(key, enabled);
                row.setValue(copy);
                settings.save(row);
            }
        });
    }
}
