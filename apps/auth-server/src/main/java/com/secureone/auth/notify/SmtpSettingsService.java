package com.secureone.auth.notify;

import com.secureone.auth.application.ApplicationSetting;
import com.secureone.auth.application.ApplicationSettingRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SmtpSettingsService {

    public static final String SETTINGS_KEY = "smtp";

    private final ApplicationSettingRepository appSettings;
    private final SecretProtector secrets;

    public SmtpSettingsService(ApplicationSettingRepository appSettings, SecretProtector secrets) {
        this.appSettings = appSettings;
        this.secrets = secrets;
    }

    public boolean isConfigured(UUID applicationId) {
        if (applicationId == null) {
            return false;
        }
        Map<String, Object> smtp = loadMap(applicationId);
        return !string(smtp, "host", "").isBlank();
    }

    public Map<String, Object> getPublicSettings(UUID applicationId) {
        Map<String, Object> smtp = new HashMap<>(loadMap(applicationId));
        boolean passwordConfigured = hasPassword(smtp);
        smtp.remove("passwordEncrypted");
        smtp.put("passwordConfigured", passwordConfigured);
        smtp.put("smtpConfigured", isConfigured(applicationId));
        return smtp;
    }

    public Map<String, Object> save(UUID applicationId, Map<String, Object> body) {
        Map<String, Object> current = new HashMap<>(loadMap(applicationId));
        if (body.containsKey("host")) {
            current.put("host", string(body, "host", ""));
        }
        if (body.containsKey("port")) {
            current.put("port", parsePort(body.get("port"), intValue(current.get("port"), 465)));
        }
        if (body.containsKey("security")) {
            current.put("security", normalizeSecurity(string(body, "security", "ssl")));
        }
        if (body.containsKey("username")) {
            current.put("username", string(body, "username", ""));
        }
        if (body.containsKey("authEnabled")) {
            current.put("authEnabled", Boolean.TRUE.equals(body.get("authEnabled")));
        }
        Object password = body.get("password");
        if (password != null) {
            String plain = password.toString();
            if (!plain.isBlank()) {
                current.put("passwordEncrypted", secrets.encrypt(plain));
            }
        }
        if (!current.containsKey("port")) {
            current.put("port", 465);
        }
        if (!current.containsKey("security")) {
            current.put("security", "ssl");
        }
        if (!current.containsKey("authEnabled")) {
            current.put("authEnabled", true);
        }
        persist(applicationId, current);
        return getPublicSettings(applicationId);
    }

    /** Whether the application's stored SMTP password is this value (configuration files). */
    public boolean passwordMatches(UUID applicationId, String plain) {
        String encrypted = string(loadMap(applicationId), "passwordEncrypted", "");
        return plain != null && !encrypted.isBlank() && plain.equals(secrets.decrypt(encrypted));
    }

    /** Whether the application has its own SMTP server (not the platform's). */
    public boolean hasOverride(UUID applicationId) {
        return appSettings.findByApplicationIdAndKey(applicationId, SETTINGS_KEY).isPresent();
    }

    SmtpConnectionConfig loadConnectionConfig(UUID applicationId) {
        if (applicationId == null) {
            return null;
        }
        Map<String, Object> smtp = loadMap(applicationId);
        String host = string(smtp, "host", "");
        if (host.isBlank()) {
            return null;
        }
        String encrypted = string(smtp, "passwordEncrypted", "");
        String password = encrypted.isBlank() ? "" : secrets.decrypt(encrypted);
        return new SmtpConnectionConfig(
                host,
                intValue(smtp.get("port"), 465),
                normalizeSecurity(string(smtp, "security", "ssl")),
                string(smtp, "username", ""),
                password,
                Boolean.TRUE.equals(smtp.get("authEnabled")));
    }

    private Map<String, Object> loadMap(UUID applicationId) {
        return appSettings
                .findByApplicationIdAndKey(applicationId, SETTINGS_KEY)
                .map(ApplicationSetting::getValue)
                .filter(Map.class::isInstance)
                .map(value -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) value;
                    return new HashMap<>(map);
                })
                .orElseGet(() -> new HashMap<>(defaultSmtp()));
    }

    private void persist(UUID applicationId, Map<String, Object> value) {
        ApplicationSetting row = appSettings
                .findByApplicationIdAndKey(applicationId, SETTINGS_KEY)
                .orElseGet(() -> {
                    ApplicationSetting created = new ApplicationSetting();
                    created.setApplicationId(applicationId);
                    created.setKey(SETTINGS_KEY);
                    return created;
                });
        row.setValue(new HashMap<>(value));
        appSettings.save(row);
    }

    private static Map<String, Object> defaultSmtp() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("host", "");
        defaults.put("port", 465);
        defaults.put("security", "ssl");
        defaults.put("username", "");
        defaults.put("authEnabled", true);
        return defaults;
    }

    private static boolean hasPassword(Map<String, Object> smtp) {
        String encrypted = string(smtp, "passwordEncrypted", "");
        return !encrypted.isBlank();
    }

    private static String normalizeSecurity(String security) {
        String normalized = security == null ? "ssl" : security.trim().toLowerCase();
        return switch (normalized) {
            case "ssl", "starttls", "none" -> normalized;
            default -> "ssl";
        };
    }

    private static int parsePort(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }

    private static String string(Map<String, Object> map, String key, String defaultValue) {
        Object v = map.get(key);
        return v != null ? v.toString().trim() : defaultValue;
    }

    record SmtpConnectionConfig(
            String host, int port, String security, String username, String password, boolean authEnabled) {

        String cacheKey(UUID applicationId) {
            return applicationId + ":" + host + ":" + port + ":" + security + ":" + username + ":" + password.length();
        }
    }

    public static List<String> parseEmailList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        List<String> emails = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    emails.add(item.toString().trim().toLowerCase());
                }
            }
        } else if (raw instanceof String text) {
            for (String part : text.split("[,;\\s]+")) {
                if (!part.isBlank()) {
                    emails.add(part.trim().toLowerCase());
                }
            }
        }
        return emails;
    }
}
