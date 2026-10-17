package com.secureone.auth.platform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Merges feature flag lists by {@code key} (not {@code id}). */
public final class FeatureFlagMerge {

    private FeatureFlagMerge() {}

    public static List<Map<String, Object>> merge(List<Map<String, Object>> platform, Object override) {
        Set<String> allowed = FeatureFlagDefaults.catalogKeys();
        if (!(override instanceof List<?> overrideList) || overrideList.isEmpty()) {
            return withScope(filterCatalog(copy(platform), allowed));
        }
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        for (Map<String, Object> item : platform) {
            String key = flagKey(item);
            if (key != null && allowed.contains(key)) {
                byKey.put(key, new HashMap<>(item));
            }
        }
        for (Object raw : overrideList) {
            if (!(raw instanceof Map<?, ?> row)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> patch = (Map<String, Object>) row;
            String key = flagKey(patch);
            if (key == null || !allowed.contains(key)) {
                continue;
            }
            if (byKey.containsKey(key)) {
                byKey.get(key).putAll(patch);
            } else {
                byKey.put(key, new HashMap<>(patch));
            }
        }
        return withScope(new ArrayList<>(byKey.values()));
    }

    /** Runtime + manifest: application flag is ON only when platform and app are both ON. */
    public static List<Map<String, Object>> resolveEffective(List<Map<String, Object>> platform, Object override) {
        return applyPlatformGate(merge(platform, applicationOverrides(override)), platform);
    }

    /** Admin API: effective {@code enabled} plus {@code platformEnabled} for UI gating. */
    public static List<Map<String, Object>> forApplicationAdmin(
            List<Map<String, Object>> platform, Object override) {
        List<Map<String, Object>> merged = merge(platform, applicationOverrides(override));
        Map<String, Boolean> platformOn = enabledByKey(platform);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> flag : merged) {
            Map<String, Object> row = new HashMap<>(flag);
            String key = flagKey(row);
            boolean platformEnabled = key != null && Boolean.TRUE.equals(platformOn.get(key));
            row.put("platformEnabled", platformEnabled);
            row.put("enabled", platformEnabled && Boolean.TRUE.equals(row.get("enabled")));
            out.add(row);
        }
        return out;
    }

    /** Application overrides may only hold application-scoped flags (console controls are platform-only). */
    public static List<Map<String, Object>> applicationScopedOnly(List<Map<String, Object>> appFlags) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : appFlags) {
            String key = flagKey(row);
            if (key != null && FeatureFlagDefaults.isApplicationScoped(key)) {
                out.add(row);
            }
        }
        return out;
    }

    /**
     * An application's saved overrides, without console controls: those are platform decisions, so an
     * application value (saved before scopes existed, or sent anyway) never applies.
     */
    @SuppressWarnings("unchecked")
    private static Object applicationOverrides(Object override) {
        if (!(override instanceof List<?> list)) {
            return override;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object raw : list) {
            if (raw instanceof Map<?, ?> row) {
                rows.add((Map<String, Object>) row);
            }
        }
        return applicationScopedOnly(rows);
    }

    private static List<Map<String, Object>> withScope(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            String key = flagKey(row);
            if (key != null) {
                row.put("scope", FeatureFlagDefaults.scopeOf(key));
            }
        }
        return rows;
    }

    /** Prevent persisting app ON when platform has the flag OFF. */
    public static List<Map<String, Object>> clampAppOverridesToPlatform(
            List<Map<String, Object>> platform, List<Map<String, Object>> appFlags) {
        Map<String, Boolean> platformOn = enabledByKey(platform);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : appFlags) {
            Map<String, Object> copy = new HashMap<>(row);
            String key = flagKey(copy);
            if (key != null && !Boolean.TRUE.equals(platformOn.get(key))) {
                copy.put("enabled", false);
            }
            out.add(copy);
        }
        return out;
    }

    private static List<Map<String, Object>> applyPlatformGate(
            List<Map<String, Object>> merged, List<Map<String, Object>> platform) {
        Map<String, Boolean> platformOn = enabledByKey(platform);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> flag : merged) {
            Map<String, Object> row = new HashMap<>(flag);
            String key = flagKey(row);
            boolean platformEnabled = key != null && Boolean.TRUE.equals(platformOn.get(key));
            row.put("enabled", platformEnabled && Boolean.TRUE.equals(row.get("enabled")));
            out.add(row);
        }
        return out;
    }

    private static Map<String, Boolean> enabledByKey(List<Map<String, Object>> flags) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (Map<String, Object> flag : flags) {
            String key = flagKey(flag);
            if (key != null) {
                out.put(key, Boolean.TRUE.equals(flag.get("enabled")));
            }
        }
        return out;
    }

    private static List<Map<String, Object>> filterCatalog(List<Map<String, Object>> source, Set<String> allowed) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> item : source) {
            String key = flagKey(item);
            if (key != null && allowed.contains(key)) {
                out.add(item);
            }
        }
        return out;
    }

    private static String flagKey(Map<String, Object> item) {
        Object key = item.get("key");
        if (key == null) {
            return null;
        }
        String s = String.valueOf(key);
        return s.isBlank() ? null : s;
    }

    private static List<Map<String, Object>> copy(List<Map<String, Object>> source) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> item : source) {
            out.add(new HashMap<>(item));
        }
        return out;
    }
}
