package com.secureone.auth.platform;

import com.secureone.auth.cache.ConfigCache;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PlatformSettingsService {

    private final PlatformSettingRepository settings;
    private final ConfigCache cache;

    /** No row for that key (cached too). */
    private static final Object ABSENT = new Object();

    public PlatformSettingsService(PlatformSettingRepository settings, ConfigCache cache) {
        this.settings = settings;
        this.cache = cache;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> get(String key) {
        Object value = getRaw(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new HashMap<>();
    }

    /** The stored value; a copy the caller may change (read on most requests, so cached, see {@link ConfigCache}). */
    public Object getRaw(String key) {
        Object value = cache.get("platform-setting", key, () -> {
            Object stored = settings.findById(key).map(PlatformSetting::getValue).orElse(null);
            return stored == null ? ABSENT : copy(stored);
        });
        return value == ABSENT ? null : copy(value);
    }

    /** Deep copy of a JSON-shaped value (maps, lists, scalars), so the cached value is never changed. */
    public static Object copy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), copy(v)));
            return out;
        }
        if (value instanceof java.util.Collection<?> list) {
            java.util.List<Object> out = new java.util.ArrayList<>(list.size());
            list.forEach(v -> out.add(copy(v)));
            return out;
        }
        return value;
    }

    public Object save(String key, Object value) {
        cache.changedAfterCommit();
        PlatformSetting row = settings.findById(key).orElseGet(() -> {
            PlatformSetting created = new PlatformSetting();
            created.setKey(key);
            return created;
        });
        row.setValue(value);
        settings.save(row);
        return row.getValue();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> saveMap(String key, Map<String, Object> value) {
        return (Map<String, Object>) save(key, value != null ? new HashMap<>(value) : new HashMap<>());
    }
}
