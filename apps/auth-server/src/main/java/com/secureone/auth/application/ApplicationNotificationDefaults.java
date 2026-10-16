package com.secureone.auth.application;

import com.secureone.auth.notify.SmtpSettingsService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Per-application notification defaults — not inherited from platform settings. */
public final class ApplicationNotificationDefaults {

    /** Platform default sender name; an application that keeps it sends under its own name instead. */
    public static final String DEFAULT_FROM_NAME = "SecureOne";


    private ApplicationNotificationDefaults() {}

    public static Map<String, Object> notificationDefaults() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("emailEnabled", true);
        out.put("userEmailEnabled", true);
        out.put("pushEnabled", false);
        out.put("auditAlertsEnabled", true);
        out.put("securityAlertsEnabled", true);
        out.put("adminRecipients", new ArrayList<String>());
        Map<String, Object> groups = new LinkedHashMap<>();
        groups.put("security", new ArrayList<String>());
        groups.put("operations", new ArrayList<String>());
        out.put("recipientGroups", groups);
        return out;
    }

    public static Map<String, Object> emailDefaults() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fromName", DEFAULT_FROM_NAME);
        out.put("fromAddress", "noreply@secureone.local");
        out.put("replyTo", "support@secureone.local");
        return out;
    }

    public static Map<String, Object> mergeNotifications(Map<String, Object> appOverride) {
        Map<String, Object> merged = new HashMap<>(notificationDefaults());
        if (appOverride != null) {
            merged.putAll(appOverride);
        }
        return merged;
    }

    public static Map<String, Object> mergeEmail(Map<String, Object> appOverride) {
        Map<String, Object> merged = new HashMap<>(emailDefaults());
        if (appOverride != null) {
            merged.putAll(appOverride);
        }
        return merged;
    }

    public static Map<String, Object> sanitizeNotifications(Map<String, Object> body) {
        Map<String, Object> defaults = notificationDefaults();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("emailEnabled", bool(body, "emailEnabled", defaults));
        out.put("userEmailEnabled", bool(body, "userEmailEnabled", defaults));
        out.put("pushEnabled", bool(body, "pushEnabled", defaults));
        out.put("auditAlertsEnabled", bool(body, "auditAlertsEnabled", defaults));
        out.put("securityAlertsEnabled", bool(body, "securityAlertsEnabled", defaults));
        out.put("adminRecipients", SmtpSettingsService.parseEmailList(body != null ? body.get("adminRecipients") : null));
        if (body != null && body.get("recipientGroups") instanceof Map<?, ?> groups) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (var entry : groups.entrySet()) {
                sanitized.put(
                        entry.getKey().toString(),
                        SmtpSettingsService.parseEmailList(entry.getValue()));
            }
            out.put("recipientGroups", sanitized);
        } else {
            out.put("recipientGroups", defaults.get("recipientGroups"));
        }
        return out;
    }

    public static Map<String, Object> sanitizeEmail(Map<String, Object> body) {
        Map<String, Object> defaults = emailDefaults();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fromName", string(body, "fromName", defaults));
        out.put("fromAddress", string(body, "fromAddress", defaults));
        out.put("replyTo", string(body, "replyTo", defaults));
        return out;
    }

    private static String string(Map<String, Object> body, String key, Map<String, Object> defaults) {
        Object value = body != null ? body.get(key) : null;
        if (value != null && !value.toString().isBlank()) {
            return value.toString().trim();
        }
        Object fallback = defaults.get(key);
        return fallback != null ? fallback.toString() : "";
    }

    private static boolean bool(Map<String, Object> body, String key, Map<String, Object> defaults) {
        Object value = body != null ? body.get(key) : null;
        if (value instanceof Boolean b) {
            return b;
        }
        Object fallback = defaults.get(key);
        return fallback instanceof Boolean fb ? fb : false;
    }
}
