package com.secureone.auth.application;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Defaults for per-application client appearance (public manifest / embedded apps).
 * Separate from platform admin console theme ({@code platform_setting.appearance}).
 */
public final class ClientAppearanceDefaults {

    private ClientAppearanceDefaults() {}

    /** Keys exposed on the unauthenticated public manifest appearance block. */
    public static final List<String> PUBLIC_MANIFEST_KEYS = Collections.unmodifiableList(
            Arrays.asList(
                    "mode",
                    "primaryColor",
                    "buttonTextColor",
                    "accentColor",
                    "backgroundColor",
                    "surfaceColor",
                    "textColor",
                    "darkBackgroundColor",
                    "darkSurfaceColor",
                    "darkTextColor",
                    "gradientPreset",
                    "gradientFrom",
                    "gradientTo",
                    "gradientAngle",
                    "gradientScope",
                    "borderRadius",
                    "fontScale",
                    "notifyInfoBackground",
                    "notifyInfoText",
                    "notifyInfoBorder",
                    "notifySuccessBackground",
                    "notifySuccessText",
                    "notifySuccessBorder",
                    "notifyErrorBackground",
                    "notifyErrorText",
                    "notifyErrorBorder",
                    "appName",
                    "logoUrl",
                    "dialogBackground",
                    "dialogText",
                    "dialogBorder",
                    "dialogOverlay",
                    "inputBackground",
                    "inputText",
                    "inputBorder",
                    "inputPlaceholder",
                    "mutedText",
                    "dividerColor",
                    "language"));

    /** Languages the hosted sign-in pages are translated into ("auto": the person's browser language). */
    public static final java.util.Set<String> LANGUAGES = java.util.Set.of("auto", "en", "es", "fr", "de", "pt", "hi");

    public static Map<String, Object> platformDefaults() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("mode", "system");
        root.put("primaryColor", "#4f46e5");
        root.put("buttonTextColor", "#ffffff");
        root.put("accentColor", "#06b6d4");
        root.put("backgroundColor", "#f7f7f8");
        root.put("surfaceColor", "#ffffff");
        root.put("textColor", "#171717");
        root.put("darkBackgroundColor", "#121216");
        root.put("darkSurfaceColor", "#1c1c24");
        root.put("darkTextColor", "#ededed");
        root.put("gradientPreset", "none");
        root.put("gradientFrom", "#4f46e5");
        root.put("gradientTo", "#06b6d4");
        root.put("gradientAngle", 135);
        root.put("gradientScope", "both");
        root.put("borderRadius", 12);
        root.put("fontScale", 1);
        root.put("notifyInfoBackground", "#ffffff");
        root.put("notifyInfoText", "#171717");
        root.put("notifyInfoBorder", "#4f46e533");
        root.put("notifySuccessBackground", "#ecfdf5");
        root.put("notifySuccessText", "#065f46");
        root.put("notifySuccessBorder", "#10b98166");
        root.put("notifyErrorBackground", "#fef2f2");
        root.put("notifyErrorText", "#991b1b");
        root.put("notifyErrorBorder", "#ef444466");
        root.put("appName", "");
        root.put("logoUrl", "");
        root.put("dialogBackground", "#ffffff");
        root.put("dialogText", "#171717");
        root.put("dialogBorder", "#e5e7eb");
        root.put("dialogOverlay", "#00000066");
        root.put("inputBackground", "#ffffff");
        root.put("inputText", "#171717");
        root.put("inputBorder", "#d1d5db");
        root.put("inputPlaceholder", "#9ca3af");
        root.put("mutedText", "#6b7280");
        root.put("dividerColor", "#e5e7eb");
        root.put("language", "auto");
        return root;
    }

    public static Map<String, Object> merge(Map<String, Object> defaults, Map<String, Object> appOverride) {
        Map<String, Object> merged = new LinkedHashMap<>(defaults != null ? defaults : platformDefaults());
        if (appOverride == null || appOverride.isEmpty()) {
            return merged;
        }
        appOverride.forEach((k, v) -> {
            if (v == null || isMetaKey(k)) return;
            // Values saved before validation existed and are not valid now keep the default.
            if (PUBLIC_MANIFEST_KEYS.contains(k) && problem(k, v) == null) {
                merged.put(k, v);
            }
        });
        return merged;
    }

    private static final java.util.regex.Pattern HEX_COLOR =
            java.util.regex.Pattern.compile("^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");
    private static final java.util.Set<String> MODES = java.util.Set.of("light", "dark", "system");
    private static final java.util.Set<String> GRADIENT_PRESETS =
            java.util.Set.of("none", "brand", "sunset", "ocean", "violet", "custom");
    private static final java.util.Set<String> GRADIENT_SCOPES = java.util.Set.of("page", "brand", "both");

    /**
     * Why a value cannot be used, or null. Client apps put these values into their pages and styles, so only
     * plain colours, known choices, numbers in range and an https logo are accepted (no CSS or script can ride in).
     */
    public static String problem(String key, Object value) {
        return switch (key) {
            case "mode" -> MODES.contains(String.valueOf(value)) ? null : "must be light, dark or system";
            case "language" -> LANGUAGES.contains(String.valueOf(value)) ? null : "must be one of " + String.join(", ", new java.util.TreeSet<>(LANGUAGES));
            case "gradientPreset" -> GRADIENT_PRESETS.contains(String.valueOf(value)) ? null : "is not a known gradient";
            case "gradientScope" -> GRADIENT_SCOPES.contains(String.valueOf(value)) ? null : "must be page, brand or both";
            case "gradientAngle" -> inRange(value, 0, 360) ? null : "must be 0 to 360";
            case "borderRadius" -> inRange(value, 0, 48) ? null : "must be 0 to 48";
            case "fontScale" -> inRange(value, 0.75, 1.5) ? null : "must be 0.75 to 1.5";
            case "appName" -> value instanceof String s && s.length() <= 80 && s.chars().noneMatch(c -> c < 32 || c == '<' || c == '>')
                    ? null : "must be up to 80 characters, without < or >";
            case "logoUrl" -> value instanceof String s && (s.isBlank() || isHttpsUrl(s)) ? null : "must be an https:// address";
            default -> value instanceof String s && HEX_COLOR.matcher(s.trim()).matches()
                    ? null : "must be a colour such as #4f46e5";
        };
    }

    /** Refuses the first unusable value with a clear message. */
    public static void requireValid(Map<String, Object> body) {
        for (var e : body.entrySet()) {
            if (e.getValue() != null && PUBLIC_MANIFEST_KEYS.contains(e.getKey())) {
                String problem = problem(e.getKey(), e.getValue());
                if (problem != null) {
                    throw new IllegalArgumentException("Appearance: " + e.getKey() + " " + problem + ".");
                }
            }
        }
    }

    private static boolean inRange(Object value, double min, double max) {
        return value instanceof Number n && n.doubleValue() >= min && n.doubleValue() <= max;
    }

    private static boolean isHttpsUrl(String raw) {
        try {
            java.net.URI uri = new java.net.URI(raw.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (Exception e) {
            return false;
        }
    }

    public static Map<String, Object> sanitizeForPublicManifest(Map<String, Object> appearance) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (appearance == null) {
            return out;
        }
        for (String key : PUBLIC_MANIFEST_KEYS) {
            if (appearance.containsKey(key) && appearance.get(key) != null) {
                out.put(key, appearance.get(key));
            }
        }
        return out;
    }

    private static boolean isMetaKey(String key) {
        return "scope".equals(key) || "inheritsPlatformDefaults".equals(key);
    }

    /** Strip admin-only metadata before persisting application overrides. */
    public static Map<String, Object> stripMeta(Map<String, Object> body) {
        Map<String, Object> out = new HashMap<>(body);
        out.remove("scope");
        out.remove("inheritsPlatformDefaults");
        return out;
    }
}
