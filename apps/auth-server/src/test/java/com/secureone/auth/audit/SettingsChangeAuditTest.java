package com.secureone.auth.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettingsChangeAuditTest {

    @Test
    void platformAndApplicationSettingsChangesAreAudited() {
        assertThat(SettingsChangeAudit.target("PUT", "/api/admin/v1/settings/password-policy"))
                .isEqualTo(new SettingsChangeAudit.Target("password-policy", null));
        UUID app = UUID.randomUUID();
        assertThat(SettingsChangeAudit.target("PUT", "/api/admin/v1/applications/" + app + "/settings/smtp"))
                .isEqualTo(new SettingsChangeAudit.Target("smtp", app));
    }

    @Test
    void readsTestsAndSectionsWithTheirOwnEntryAreSkipped() {
        assertThat(SettingsChangeAudit.target("GET", "/api/admin/v1/settings/password-policy")).isNull();
        assertThat(SettingsChangeAudit.target("POST", "/api/admin/v1/settings/sms/test")).isNull();
        assertThat(SettingsChangeAudit.target("PUT", "/api/admin/v1/settings/feature-flags")).isNull();
        assertThat(SettingsChangeAudit.target("POST", "/api/admin/v1/settings/signing-keys/rotate")).isNull();
        assertThat(SettingsChangeAudit.target("PUT", "/api/admin/v1/settings/app-exposure/exceptions/x")).isNull();
        assertThat(SettingsChangeAudit.target("PUT", "/api/admin/v1/users/1")).isNull();
    }

    @Test
    void theEntrySaysWhatWasSavedWithSecretsMasked() {
        String label = SettingsChangeAudit.describe("smtp", Map.of("host", "mail.example.com", "password", "hunter2"));
        assertThat(label).startsWith("smtp: ").contains("host=mail.example.com").contains("password=***")
                .doesNotContain("hunter2");
        assertThat(SettingsChangeAudit.describe("auth-methods", List.of(Map.of("id", "password", "enabled", true))))
                .contains("[0].id=password");
        assertThat(SettingsChangeAudit.describe("x", Map.of("v", "a".repeat(5000)))).hasSizeLessThanOrEqualTo(1000);
    }

    @Test
    void withoutARequestTheActorIsTheSystem() {
        assertThat(AuditService.actorType(null)).isEqualTo("SYSTEM");
    }
}
