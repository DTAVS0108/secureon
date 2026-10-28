package com.secureone.auth.configascode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.secureone.auth.application.ApplicationSettingsService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TenantConfigFormatTest {

    private static final String FILE = """
            apiVersion: secureone/v1
            kind: TenantConfiguration
            tenant:
              slug: tenant-a
              name: Tenant A
            applications:
              - slug: shop
                name: Shop
                clients:
                  - clientId: shop-web
                    type: web
                    redirectUris: [https://shop.example.com/callback]
                    clientSecretEnv: SHOP_WEB_SECRET
                roles:
                  - name: manager
                    permissions: [orders:read]
                    includes: [viewer]
                settings:
                  password_policy:
                    minLength: 12
            """;

    @Test
    void readsYamlAndWritesItBack() {
        TenantConfig config = TenantConfigFormat.parse(FILE);
        assertEquals("tenant-a", config.tenant().slug());
        TenantConfig.Client client = config.applications().getFirst().clients().getFirst();
        assertEquals("SHOP_WEB_SECRET", client.clientSecretEnv());
        assertEquals(List.of("viewer"), config.applications().getFirst().roles().getFirst().includes());

        String yaml = TenantConfigFormat.yaml(config);
        assertFalse(yaml.contains("null"), yaml);
        assertEquals(config, TenantConfigFormat.parse(yaml));
        assertEquals(config, TenantConfigFormat.parse(TenantConfigFormat.json(config)));
    }

    @Test
    void aTypoNamesTheFieldWhereItIsAndWhatIsAllowed() {
        var error = assertThrows(IllegalArgumentException.class, () -> TenantConfigFormat.parse(
                FILE.replace("redirectUris:", "redirectUri:")));
        assertTrue(error.getMessage().startsWith("Unknown field 'redirectUri' at applications[0].clients[0]"), error.getMessage());
        assertTrue(error.getMessage().contains("redirectUris"), error.getMessage());
    }

    @Test
    void aWrongTypeSaysWhatWasExpected() {
        var error = assertThrows(IllegalArgumentException.class, () -> TenantConfigFormat.parse(
                FILE.replace("permissions: [orders:read]", "permissions: {a: b}")));
        assertTrue(error.getMessage().contains("applications[0].roles[0].permissions") && error.getMessage().contains("a list"),
                error.getMessage());
        assertThrows(IllegalArgumentException.class, () -> TenantConfigFormat.parse("  "));
        assertThrows(IllegalArgumentException.class, () -> TenantConfigFormat.parse("tenant: [unclosed"));
    }

    @Test
    void settingsCompareByWhatTheFileSets() {
        Map<String, Object> stored = Map.of("minLength", 12, "requireDigit", true, "history", List.of(1, 2));
        assertTrue(ConfigPlan.contains(stored, Map.of("minLength", 12)));
        assertTrue(ConfigPlan.contains(stored, Map.of("minLength", 12.0)));
        assertFalse(ConfigPlan.contains(stored, Map.of("minLength", 10)));
        assertFalse(ConfigPlan.contains(stored, Map.of("history", List.of(1))));
        assertFalse(ConfigPlan.contains(stored, Map.of("missing", true)));
    }

    @Test
    void platformFilesAreRecognisedByKind() {
        Object parsed = TenantConfigFormat.parseAny("""
                apiVersion: secureone/v1
                kind: PlatformConfiguration
                smtp:
                  host: smtp.example.com
                  port: 587
                  passwordEnv: SMTP_PASSWORD
                sms:
                  provider: twilio
                  twilio: {accountSid: AC1, authTokenEnv: TWILIO_TOKEN}
                scim: {enabled: true, bearerTokenEnv: SCIM_TOKEN}
                """);
        PlatformConfig platform = (PlatformConfig) parsed;
        assertEquals("SMTP_PASSWORD", platform.smtp().passwordEnv());
        assertEquals("TWILIO_TOKEN", platform.sms().twilio().authTokenEnv());
        var error = assertThrows(IllegalArgumentException.class, () -> TenantConfigFormat.parse("""
                apiVersion: secureone/v1
                kind: PlatformConfiguration
                """));
        assertTrue(error.getMessage().contains("TenantConfiguration was expected"), error.getMessage());
    }

    @Test
    void credentialsInTheFileAreRefusedWithTheFieldToUse() {
        ConfigPlan plan = new ConfigPlan(false, name -> null);
        assertEquals(null, MessagingConfig.smtp(plan, "shop: settings.smtp", Map.of("host", "smtp.x", "password", "hunter2")));
        assertTrue(plan.errors.getFirst().contains("name it with passwordEnv"), plan.errors.toString());

        plan = new ConfigPlan(false, name -> null);
        MessagingConfig.sms(plan, "platform.sms", Map.of("twilio", Map.of("authToken", "abc")));
        assertTrue(plan.errors.getFirst().contains("authTokenEnv"), plan.errors.toString());

        plan = new ConfigPlan(false, name -> null);
        MessagingConfig.smtp(plan, "shop: settings.smtp", Map.of("hostname", "smtp.x"));
        assertTrue(plan.errors.getFirst().contains("unknown field 'hostname'"), plan.errors.toString());
    }

    @Test
    void secretVariablesAreChecked() {
        ConfigPlan plan = new ConfigPlan(false, Map.of("OK", "scim_" + "x".repeat(40), "SHORT", "scim_x", "WRONG", "y".repeat(50))::get);
        assertEquals("scim_" + "x".repeat(40), plan.secret("OK", "scim", "scim_", 40));
        assertEquals(null, plan.secret("MISSING", "scim", "scim_", 40));
        assertEquals(null, plan.secret("SHORT", "scim", "scim_", 40));
        assertEquals(null, plan.secret("WRONG", "scim", "scim_", 40));
        assertEquals(null, plan.secret(null, "scim", "scim_", 40));
        assertEquals(3, plan.errors.size(), plan.errors.toString());
        assertTrue(plan.errors.getFirst().contains("MISSING is not set"));
    }

    @Test
    void credentialsAreBlankedInExports() {
        Object redacted = ApplicationSettingsService.redactCredentials(Map.of(
                "sources", Map.of("ldap", Map.of("url", "ldaps://dir", "bindPassword", "hunter2"))));
        assertEquals(Map.of("sources", Map.of("ldap", Map.of("url", "ldaps://dir", "bindPassword", ""))), redacted);
    }
}
