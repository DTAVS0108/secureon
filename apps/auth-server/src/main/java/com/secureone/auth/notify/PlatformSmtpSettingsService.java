package com.secureone.auth.notify;

import com.secureone.auth.platform.PlatformSettingsService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Platform SMTP server: the one saved under Platform settings → Notifications, or, until one is saved there,
 * the one given by environment ({@code SECUREONE_SMTP_HOST}, {@code _PORT}, {@code _USERNAME},
 * {@code _PASSWORD}, {@code _AUTH}, {@code _STARTTLS}), so a container can send mail from its first start.
 */
@Service
public class PlatformSmtpSettingsService {

    public static final String SETTINGS_KEY = "smtp";
    private static final String SOURCE = "source";

    private final PlatformSettingsService platformSettings;
    private final SecretProtector secrets;
    private final Map<String, Object> environmentSmtp;
    private final String environmentPassword;

    public PlatformSmtpSettingsService(
            PlatformSettingsService platformSettings,
            SecretProtector secrets,
            @Value("${spring.mail.host:}") String envHost,
            @Value("${spring.mail.port:1025}") int envPort,
            @Value("${spring.mail.username:}") String envUsername,
            @Value("${spring.mail.password:}") String envPassword,
            @Value("${spring.mail.properties.mail.smtp.auth:false}") boolean envAuth,
            @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}") boolean envStartTls) {
        this.platformSettings = platformSettings;
        this.secrets = secrets;
        this.environmentPassword = envPassword == null ? "" : envPassword;
        if (envHost == null || envHost.isBlank()) {
            this.environmentSmtp = null;
        } else {
            Map<String, Object> env = new HashMap<>();
            env.put("host", envHost.trim());
            env.put("port", envPort);
            env.put("security", envStartTls ? "starttls" : envPort == 465 ? "ssl" : "none");
            env.put("username", envUsername == null ? "" : envUsername);
            env.put("authEnabled", envAuth);
            this.environmentSmtp = Map.copyOf(env);
        }
    }

    /** Stored settings once a host is saved; otherwise the environment's SMTP server, if any. */
    private boolean usesEnvironment(Map<String, Object> stored) {
        return string(stored, "host", "").isBlank() && environmentSmtp != null;
    }

    public boolean isConfigured() {
        Map<String, Object> smtp = effectiveMap();
        return !string(smtp, "host", "").isBlank();
    }

    public Map<String, Object> getPublicSettings() {
        Map<String, Object> smtp = new HashMap<>(effectiveMap());
        boolean fromEnvironment = "environment".equals(smtp.get(SOURCE));
        boolean passwordConfigured = fromEnvironment ? !environmentPassword.isBlank() : hasPassword(smtp);
        smtp.remove("passwordEncrypted");
        smtp.put("passwordConfigured", passwordConfigured);
        smtp.put("smtpConfigured", isConfigured());
        smtp.putIfAbsent(SOURCE, "settings");
        return smtp;
    }

    public Map<String, Object> save(Map<String, Object> body) {
        Map<String, Object> current = new HashMap<>(loadMap());
        current.remove(SOURCE);
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
        SmtpSettingsService.requireEncryptionUnlessLocal(string(current, "host", ""), string(current, "security", "ssl"));
        platformSettings.save(SETTINGS_KEY, current);
        return getPublicSettings();
    }

    SmtpSettingsService.SmtpConnectionConfig loadConnectionConfig() {
        Map<String, Object> smtp = effectiveMap();
        String host = string(smtp, "host", "");
        if (host.isBlank()) {
            return null;
        }
        String password;
        if ("environment".equals(smtp.get(SOURCE))) {
            password = environmentPassword;
        } else {
            String encrypted = string(smtp, "passwordEncrypted", "");
            password = encrypted.isBlank() ? "" : secrets.decrypt(encrypted);
        }
        return new SmtpSettingsService.SmtpConnectionConfig(
                host,
                intValue(smtp.get("port"), 465),
                normalizeSecurity(string(smtp, "security", "ssl")),
                string(smtp, "username", ""),
                password,
                Boolean.TRUE.equals(smtp.get("authEnabled")));
    }

    /** Settings in force: the stored ones, or the environment's (marked source=environment) while none are saved. */
    /** What is stored (not the environment fallback), without the password; for configuration files. */
    public Map<String, Object> storedPublicSettings() {
        Map<String, Object> smtp = new HashMap<>(loadMap());
        smtp.remove(SOURCE);
        smtp.remove("passwordEncrypted");
        return smtp;
    }

    /** Whether the stored SMTP password is this value (configuration files). */
    public boolean passwordMatches(String plain) {
        String encrypted = string(loadMap(), "passwordEncrypted", "");
        return plain != null && !encrypted.isBlank() && plain.equals(secrets.decrypt(encrypted));
    }

    private Map<String, Object> effectiveMap() {
        Map<String, Object> stored = loadMap();
        if (!usesEnvironment(stored)) {
            return stored;
        }
        Map<String, Object> env = new HashMap<>(environmentSmtp);
        env.put(SOURCE, "environment");
        return env;
    }

    private Map<String, Object> loadMap() {
        Map<String, Object> stored = platformSettings.get(SETTINGS_KEY);
        if (stored == null || stored.isEmpty()) {
            return defaultSmtp();
        }
        return new HashMap<>(stored);
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
        return !string(smtp, "passwordEncrypted", "").isBlank();
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
}
