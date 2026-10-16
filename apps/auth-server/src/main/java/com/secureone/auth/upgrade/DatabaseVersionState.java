package com.secureone.auth.upgrade;

import java.util.LinkedHashMap;
import java.util.Map;

/** The schema versions found at start-up, for the management info endpoint. */
public class DatabaseVersionState {

    private volatile Map<String, Object> details = Map.of();

    void record(String platformVersion, int applicationSchemaVersion, int applicationSchemas, String mode) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("platformSchemaVersion", platformVersion);
        out.put("applicationSchemaVersion", applicationSchemaVersion);
        out.put("applicationSchemas", applicationSchemas);
        out.put("migrateMode", mode);
        details = Map.copyOf(out);
    }

    public Map<String, Object> details() {
        return details;
    }
}
