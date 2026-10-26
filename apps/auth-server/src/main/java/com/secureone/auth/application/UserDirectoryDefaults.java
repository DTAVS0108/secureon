package com.secureone.auth.application;

import java.util.LinkedHashMap;
import java.util.Map;

/** Platform defaults for per-application user import/export (CSV and Excel files). */
public final class UserDirectoryDefaults {

    private UserDirectoryDefaults() {}

    public static Map<String, Object> platformDefaults() {
        Map<String, Object> csv = new LinkedHashMap<>();
        csv.put("enabled", true);
        Map<String, Object> excel = new LinkedHashMap<>();
        excel.put("enabled", true);

        Map<String, Object> sources = new LinkedHashMap<>();
        sources.put("csv", csv);
        sources.put("excel", excel);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("importEnabled", false);
        root.put("exportEnabled", false);
        root.put("sources", sources);
        return root;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> merge(Map<String, Object> platform, Map<String, Object> appOverride) {
        Map<String, Object> merged = deepCopy(platform != null ? platform : platformDefaults());
        if (appOverride != null && !appOverride.isEmpty()) {
            appOverride.forEach((k, v) -> {
                if (v == null) return;
                if ("sources".equals(k) && v instanceof Map<?, ?> srcOverride && merged.get("sources") instanceof Map<?, ?> base) {
                    Map<String, Object> sources = (Map<String, Object>) merged.get("sources");
                    srcOverride.forEach((sk, sv) -> {
                        if (sv instanceof Map<?, ?> patch && sources.get(sk.toString()) instanceof Map<?, ?> existing) {
                            Map<String, Object> target = (Map<String, Object>) sources.get(sk.toString());
                            patch.forEach((pk, pv) -> target.put(pk.toString(), pv));
                        }
                    });
                } else if ("importEnabled".equals(k) || "exportEnabled".equals(k)) {
                    merged.put(k, v);
                }
            });
        }
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
