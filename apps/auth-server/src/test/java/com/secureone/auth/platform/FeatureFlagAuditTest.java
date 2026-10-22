package com.secureone.auth.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** One audit entry per flag actually switched, naming the flag and both values. */
class FeatureFlagAuditTest {

    private static Map<String, Object> row(String key, String name, boolean enabled) {
        Map<String, Object> row = new HashMap<>();
        row.put("key", key);
        row.put("name", name);
        row.put("enabled", enabled);
        return row;
    }

    @Test
    void onlySwitchedFlagsAreRecorded() {
        List<Map<String, Object>> before = List.of(
                row("mfa_try_another_way", "2FA: Try another way", true), row("ldap", "LDAP / AD", false));
        List<Map<String, Object>> after = List.of(
                row("mfa_try_another_way", "2FA: Try another way", false), row("ldap", "LDAP / AD", false));

        assertEquals(List.of("2FA: Try another way (mfa_try_another_way): on → off · platform"),
                FeatureFlagAudit.changes(null, before, after));
    }

    @Test
    void applicationChangeIsNotMarkedPlatform() {
        List<String> changes = FeatureFlagAudit.changes(UUID.randomUUID(),
                List.of(row("mfa_try_another_way", "2FA: Try another way", false)),
                List.of(row("mfa_try_another_way", "2FA: Try another way", true)));

        assertEquals(List.of("2FA: Try another way (mfa_try_another_way): off → on"), changes);
    }

    @Test
    void nothingChangedRecordsNothing() {
        List<Map<String, Object>> flags = List.of(row("mfa_try_another_way", "2FA: Try another way", true));

        assertEquals(List.of(), FeatureFlagAudit.changes(null, flags, flags));
    }
}
