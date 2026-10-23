package com.secureone.auth.application;

import com.secureone.auth.platform.FeatureFlagDefaults;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FeatureFlagSanitizer {

    /** Stored fields of a flag (a flag is on or off: no "rollout" percentage). */
    private static final Set<String> FIELDS = Set.of("key", "name", "description", "enabled", "category");

    private FeatureFlagSanitizer() {}

    public static List<Map<String, Object>> sanitize(List<Map<String, Object>> body) {
        if (body == null || body.isEmpty()) {
            return List.of();
        }
        Set<String> allowed = FeatureFlagDefaults.catalogKeys();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : body) {
            if (row == null) {
                continue;
            }
            Object keyObj = row.get("key");
            if (keyObj == null || String.valueOf(keyObj).isBlank()) {
                continue;
            }
            String key = String.valueOf(keyObj).trim();
            if (!allowed.contains(key)) {
                continue;
            }
            Map<String, Object> clean = new LinkedHashMap<>();
            FIELDS.forEach(k -> {
                if (row.containsKey(k) && row.get(k) != null) {
                    clean.put(k, row.get(k));
                }
            });
            clean.put("key", key);
            if (!clean.containsKey("enabled")) {
                clean.put("enabled", false);
            }
            out.add(clean);
        }
        return out;
    }
}
