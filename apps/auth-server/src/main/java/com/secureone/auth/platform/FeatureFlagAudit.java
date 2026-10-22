package com.secureone.auth.platform;

import com.secureone.auth.audit.AuditService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Audit entries for feature flags switched on or off (platform settings or one application). */
@Component
public class FeatureFlagAudit {

    public static final String ACTION = "settings.feature_flag_changed";

    private final AuditService audit;

    public FeatureFlagAudit(AuditService audit) {
        this.audit = audit;
    }

    /** One entry per flag whose effective value differs; {@code applicationId} null = platform settings. */
    public void recordChanges(UUID applicationId, List<Map<String, Object>> before, List<Map<String, Object>> after) {
        for (String label : changes(applicationId, before, after)) {
            audit.record(null, applicationId, "admin", ACTION, "feature_flag", null, label, true);
        }
    }

    /** E.g. {@code "2FA: Try another way (mfa_try_another_way): on → off · platform"}. */
    static List<String> changes(UUID applicationId, List<Map<String, Object>> before, List<Map<String, Object>> after) {
        Map<String, Boolean> was = new HashMap<>();
        for (Map<String, Object> row : before) {
            was.put(String.valueOf(row.get("key")), Boolean.TRUE.equals(row.get("enabled")));
        }
        List<String> out = new ArrayList<>();
        for (Map<String, Object> row : after) {
            String key = String.valueOf(row.get("key"));
            boolean on = Boolean.TRUE.equals(row.get("enabled"));
            Boolean old = was.get(key);
            if (old != null && old == on) {
                continue;
            }
            Object name = row.get("name");
            out.add((name != null ? name + " (" + key + ")" : key)
                    + ": " + (old == null ? "new" : old ? "on" : "off") + " → " + (on ? "on" : "off")
                    + (applicationId == null ? " · platform" : ""));
        }
        return out;
    }
}
