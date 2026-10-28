package com.secureone.auth.platform;

import com.secureone.auth.notify.SmtpSettingsService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Platform operator alerts only — independent from application notification settings. */
public final class PlatformNotificationDefaults {

    private PlatformNotificationDefaults() {}

    public static Map<String, Object> defaults() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("emailEnabled", true);
        out.put("auditAlertsEnabled", true);
        out.put("securityAlertsEnabled", true);
        out.put("adminRecipients", new ArrayList<>());
        return out;
    }

    public static Map<String, Object> emailDefaults() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fromName", "SecureOne");
        out.put("fromAddress", "noreply@secureone.local");
        out.put("replyTo", "support@secureone.local");
        return out;
    }

    public static Map<String, Object> mergeNotifications(Map<String, Object> stored) {
        Map<String, Object> merged = new HashMap<>(defaults());
        if (stored != null) {
            merged.putAll(stored);
        }
        return sanitizeNotifications(merged);
    }

    public static Map<String, Object> mergeEmail(Map<String, Object> stored) {
        Map<String, Object> merged = new HashMap<>(emailDefaults());
        if (stored != null) {
            merged.putAll(stored);
        }
        return sanitizeEmail(merged);
    }

    public static Map<String, Object> sanitizeNotifications(Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("emailEnabled", bool(body, "emailEnabled", true));
        out.put("auditAlertsEnabled", bool(body, "auditAlertsEnabled", true));
        out.put("securityAlertsEnabled", bool(body, "securityAlertsEnabled", true));
        out.put("adminRecipients", SmtpSettingsService.parseEmailList(body.get("adminRecipients")));
        return out;
    }

    public static Map<String, Object> sanitizeEmail(Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fromName", string(body, "fromName", "SecureOne"));
        out.put("fromAddress", string(body, "fromAddress", "noreply@secureone.local"));
        out.put("replyTo", string(body, "replyTo", "support@secureone.local"));
        return out;
    }

    private static boolean bool(Map<String, Object> body, String key, boolean fallback) {
        Object value = body != null ? body.get(key) : null;
        return value instanceof Boolean b ? b : fallback;
    }

    private static String string(Map<String, Object> body, String key, String fallback) {
        if (body == null) {
            return fallback;
        }
        Object value = body.get(key);
        return value != null ? value.toString().trim() : fallback;
    }
}
