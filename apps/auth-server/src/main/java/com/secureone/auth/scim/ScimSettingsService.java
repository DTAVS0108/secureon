package com.secureone.auth.scim;

import com.secureone.auth.notify.SecretProtector;
import com.secureone.auth.platform.PlatformSettingRepository;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ScimSettingsService {

    private final PlatformSettingRepository settings;
    private final SecretProtector secrets;

    public ScimSettingsService(PlatformSettingRepository settings, SecretProtector secrets) {
        this.settings = settings;
        this.secrets = secrets;
    }

    public boolean enabled() {
        return Boolean.TRUE.equals(config().get("enabled"));
    }

    public String bearerToken() {
        String encrypted = string(config().get("bearerTokenEncrypted"));
        return encrypted.isBlank() ? "" : secrets.decrypt(encrypted);
    }

    public Map<String, Object> getConfig() {
        Map<String, Object> out = new HashMap<>(config());
        out.remove("bearerTokenEncrypted");
        out.put("bearerTokenConfigured", !string(config().get("bearerTokenEncrypted")).isBlank());
        return out;
    }

    public Map<String, Object> saveConfig(Map<String, Object> body) {
        Map<String, Object> current = config();
        Map<String, Object> next = new HashMap<>(current);
        if (body.containsKey("enabled")) {
            next.put("enabled", Boolean.TRUE.equals(body.get("enabled")));
        }
        if (body.get("userNameDomain") != null) {
            next.put("userNameDomain", String.valueOf(body.get("userNameDomain")).trim());
        }
        if (body.get("bearerToken") instanceof String token && !token.isBlank()) {
            next.put("bearerTokenEncrypted", secrets.encrypt(token.trim()));
        }
        persist(next);
        return getConfig();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> config() {
        return settings.findById("scim")
                .map(row -> row.getValue() instanceof Map<?, ?> map ? (Map<String, Object>) map : defaults())
                .orElse(defaults());
    }

    private void persist(Map<String, Object> value) {
        var row = settings.findById("scim").orElseGet(() -> {
            var created = new com.secureone.auth.platform.PlatformSetting();
            created.setKey("scim");
            return created;
        });
        row.setValue(value);
        settings.save(row);
    }

    private static Map<String, Object> defaults() {
        Map<String, Object> out = new HashMap<>();
        out.put("enabled", false);
        out.put("bearerTokenEncrypted", "");
        out.put("userNameDomain", "secureone.local");
        return out;
    }

    private static String string(Object raw) {
        return raw != null ? raw.toString().trim() : "";
    }
}
