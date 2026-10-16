package com.secureone.auth.application;

import java.util.LinkedHashMap;
import java.util.Map;

/** Defaults for per-application public manifest (unauthenticated client API). */
public final class PublicManifestDefaults {

    private PublicManifestDefaults() {}

    public static Map<String, Object> platformDefaults() {
        Map<String, Object> sections = new LinkedHashMap<>();
        sections.put("application", true);
        sections.put("authMethods", true);
        sections.put("featureFlags", true);
        sections.put("passwordPolicy", true);
        sections.put("verificationPolicy", true);
        sections.put("appearance", false);
        sections.put("federation", true);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("enabled", false);
        root.put("sections", sections);
        root.put("authMethodsOnlyEnabled", true);
        return root;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> merge(Map<String, Object> platform, Map<String, Object> appOverride) {
        // Code defaults go underneath the stored platform value, so a section added after the platform
        // row was first saved resolves to its default rather than to "missing" (treated as off).
        Map<String, Object> merged = platformDefaults();
        overlay(merged, platform);
        overlay(merged, appOverride);
        return merged;
    }

    @SuppressWarnings("unchecked")
    private static void overlay(Map<String, Object> merged, Map<String, Object> patchMap) {
        if (patchMap == null || patchMap.isEmpty()) {
            return;
        }
        patchMap.forEach((k, v) -> {
            if (v == null) return;
            if ("sections".equals(k) && v instanceof Map<?, ?> patch && merged.get("sections") instanceof Map<?, ?> base) {
                Map<String, Object> sections = (Map<String, Object>) merged.get("sections");
                patch.forEach((sk, sv) -> sections.put(sk.toString(), sv));
            } else {
                merged.put(k, v);
            }
        });
    }
}
