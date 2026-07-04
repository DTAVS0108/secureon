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
        Map<String, Object> merged = deepCopy(platform != null ? platform : platformDefaults());
        if (appOverride == null || appOverride.isEmpty()) {
            return merged;
        }
        appOverride.forEach((k, v) -> {
            if (v == null) return;
            if ("sections".equals(k) && v instanceof Map<?, ?> patch && merged.get("sections") instanceof Map<?, ?> base) {
                Map<String, Object> sections = (Map<String, Object>) merged.get("sections");
                patch.forEach((sk, sv) -> sections.put(sk.toString(), sv));
            } else {
                merged.put(k, v);
            }
        });
        return merged;
    }

    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((k, v) -> {
            if (v instanceof Map<?, ?> map) {
                Map<String, Object> nested = new LinkedHashMap<>();
                map.forEach((nk, nv) -> nested.put(nk.toString(), nv));
                copy.put(k, nested);
            } else {
                copy.put(k, v);
            }
        });
        return copy;
    }
}
