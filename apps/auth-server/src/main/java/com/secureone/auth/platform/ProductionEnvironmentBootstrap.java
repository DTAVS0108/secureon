package com.secureone.auth.platform;

import com.secureone.auth.config.SecureOneEnvironmentProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Applies production-safe defaults to platform settings on startup. */
@Component
public class ProductionEnvironmentBootstrap {

    private final SecureOneEnvironmentProperties environment;
    private final PlatformSettingRepository settings;

    public ProductionEnvironmentBootstrap(
            SecureOneEnvironmentProperties environment, PlatformSettingRepository settings) {
        this.environment = environment;
        this.settings = settings;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void applyProductionDefaults() {
        if (!environment.isProduction()) {
            return;
        }
        patchSmsDefaults();
        patchSelfRegistrationFlag();
        patchPasswordPolicyBreachCheck();
    }

    private void patchSmsDefaults() {
        settings.findById("sms").ifPresent(row -> {
            if (!(row.getValue() instanceof Map<?, ?> map)) {
                return;
            }
            Map<String, Object> copy = new HashMap<>();
            map.forEach((k, v) -> copy.put(String.valueOf(k), v));
            if ("log".equalsIgnoreCase(String.valueOf(copy.get("provider")))) {
                copy.put("provider", "");
                copy.put("enabled", false);
                row.setValue(copy);
                settings.save(row);
            }
        });
        settings.findById("auth_methods").ifPresent(row -> {
            if (!(row.getValue() instanceof List<?> list)) {
                return;
            }
            List<Map<String, Object>> patched = new ArrayList<>();
            boolean changed = false;
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                Map<String, Object> copy = new HashMap<>();
                map.forEach((k, v) -> copy.put(String.valueOf(k), v));
                if ("m_sms".equals(String.valueOf(copy.get("id"))) && Boolean.TRUE.equals(copy.get("enabled"))) {
                    copy.put("enabled", false);
                    changed = true;
                }
                patched.add(copy);
            }
            if (changed) {
                row.setValue(patched);
                settings.save(row);
            }
        });
    }

    private void patchSelfRegistrationFlag() {
        settings.findById("feature_flags").ifPresent(row -> {
            if (!(row.getValue() instanceof List<?> list)) {
                return;
            }
            List<Map<String, Object>> patched = new ArrayList<>();
            boolean changed = false;
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                Map<String, Object> copy = new HashMap<>();
                map.forEach((k, v) -> copy.put(String.valueOf(k), v));
                if ("self_registration".equals(String.valueOf(copy.get("key")))
                        && Boolean.TRUE.equals(copy.get("enabled"))) {
                    copy.put("enabled", false);
                    changed = true;
                }
                patched.add(copy);
            }
            if (changed) {
                row.setValue(patched);
                settings.save(row);
            }
        });
    }

    private void patchPasswordPolicyBreachCheck() {
        settings.findById("password_policy").ifPresent(row -> {
            if (!(row.getValue() instanceof Map<?, ?> map)) {
                return;
            }
            Map<String, Object> copy = new HashMap<>();
            map.forEach((k, v) -> copy.put(String.valueOf(k), v));
            if (!Boolean.TRUE.equals(copy.get("breachCheck"))) {
                copy.put("breachCheck", true);
                row.setValue(copy);
                settings.save(row);
            }
        });
    }
}
