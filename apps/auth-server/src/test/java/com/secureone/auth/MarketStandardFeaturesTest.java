package com.secureone.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.secureone.auth.admin.audit.AuditQueryServiceAccess;
import com.secureone.auth.application.TokenClaimMappings;
import com.secureone.auth.oauth.BackchannelLogoutAccess;
import com.secureone.auth.oauth.BackchannelLogoutService;
import com.secureone.auth.oauth.OAuthClient;
import com.secureone.auth.protection.AttackProtectionFilterAccess;
import com.secureone.auth.session.NewDeviceAlertServiceAccess;
import com.secureone.auth.webhook.WebhookEvents;
import com.secureone.auth.webhook.WebhookEventsAccess;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The rules behind attack protection, audit export, webhooks, token claims, back-channel logout and alerts. */
class MarketStandardFeaturesTest {

    @Test
    void tokenClaimsRefuseReservedDuplicateAndEmptyNames() {
        assertThatThrownBy(() -> TokenClaimMappings.sanitize(List.of(Map.of("name", "sub", "source", "static", "value", "x"))))
                .hasMessageContaining("already set by SecureOne");
        assertThatThrownBy(() -> TokenClaimMappings.sanitize(List.of(
                Map.of("name", "plan", "source", "static", "value", "gold"),
                Map.of("name", "plan", "source", "static", "value", "silver"))))
                .hasMessageContaining("twice");
        assertThatThrownBy(() -> TokenClaimMappings.sanitize(List.of(Map.of("name", "dept", "source", "attribute"))))
                .hasMessageContaining("user attribute name");
        List<Map<String, Object>> ok = TokenClaimMappings.sanitize(List.of(
                Map.of("name", "https://example.com/plan", "source", "attribute", "value", "plan", "idToken", true)));
        assertThat(ok.get(0)).containsEntry("accessToken", true).containsEntry("idToken", true);
    }

    @Test
    void internalUserAttributesNeverGoInTokens() {
        assertThatThrownBy(() -> TokenClaimMappings.sanitize(List.of(
                Map.of("name", "next_email", "source", "attribute", "value", "pendingEmail"))))
                .hasMessageContaining("cannot go in tokens");
        com.secureone.auth.user.UserAccount user = new com.secureone.auth.user.UserAccount();
        user.setAttributes(Map.of("pendingEmail", "new@x.com", "plan", "gold"));
        TokenClaimMappings.Claim stored = TokenClaimMappings.parse(List.of(
                Map.of("name", "next_email", "source", "attribute", "value", "pendingEmail"))).get(0);
        TokenClaimMappings.Claim plan = TokenClaimMappings.parse(List.of(
                Map.of("name", "plan", "source", "attribute", "value", "plan"))).get(0);
        assertThat(TokenClaimMappings.valueFor(stored, user, "shop")).isNull();
        assertThat(TokenClaimMappings.valueFor(plan, user, "shop")).isEqualTo("gold");
    }

    @Test
    void onlyLocalhostLogoutUrlsSkipThePrivateAddressCheck() {
        assertThat(BackchannelLogoutAccess.isLocalDevelopment("http://localhost:3000/bcl")).isTrue();
        assertThat(BackchannelLogoutAccess.isLocalDevelopment("https://10.0.0.5/bcl")).isFalse();
        assertThat(BackchannelLogoutAccess.isLocalDevelopment("https://app.example.com/bcl")).isFalse();
    }

    @Test
    void everyAuditDrivenWebhookIsInTheCatalog() {
        assertThat(WebhookEvents.CATALOG.keySet()).containsAll(WebhookEventsAccess.fromAuditValues());
        assertThat(WebhookEvents.subscribable()).contains("*", "user.login.failed", "session.revoked");
    }

    @Test
    void csvCellsCannotRunAsSpreadsheetFormulas() {
        assertThat(AuditQueryServiceAccess.csv("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(AuditQueryServiceAccess.csv("plain")).isEqualTo("\"plain\"");
        assertThat(AuditQueryServiceAccess.csv(null)).isEmpty();
    }

    @Test
    void captchaCoversTheRequestsBotsAbuse() {
        assertThat(AttackProtectionFilterAccess.action("/api/v1/applications/" + UUID.randomUUID() + "/signup")).isEqualTo("signUp");
        assertThat(AttackProtectionFilterAccess.action("/api/v1/applications/x/account/password/forgot")).isEqualTo("passwordReset");
        assertThat(AttackProtectionFilterAccess.action("/api/v1/applications/x/account/magic-link/request")).isEqualTo("magicLink");
        assertThat(AttackProtectionFilterAccess.action("/api/v1/applications/x/auth/otp/request")).isEqualTo("oneTimeCodes");
        assertThat(AttackProtectionFilterAccess.action("/api/v1/applications/x")).isNull();
    }

    @Test
    void backchannelLogoutUrlsMustBeHttpsAndSubjectsMatchTheClient() {
        assertThat(BackchannelLogoutService.validateUri("https://app.example.com/bcl")).isEqualTo("https://app.example.com/bcl");
        assertThat(BackchannelLogoutService.validateUri("http://localhost:3000/bcl")).isEqualTo("http://localhost:3000/bcl");
        assertThat(BackchannelLogoutService.validateUri("  ")).isNull();
        assertThatThrownBy(() -> BackchannelLogoutService.validateUri("http://app.example.com/bcl")).hasMessageContaining("https");
        OAuthClient byId = new OAuthClient();
        byId.setSubjectType(OAuthClient.SUBJECT_USER_ID);
        UUID id = UUID.randomUUID();
        assertThat(BackchannelLogoutAccess.subjectFor(byId, "shop:a@x.com", Optional.of(id))).isEqualTo(id.toString());
        OAuthClient legacy = new OAuthClient();
        legacy.setSubjectType(OAuthClient.SUBJECT_LEGACY);
        assertThat(BackchannelLogoutAccess.subjectFor(legacy, "shop:a@x.com", Optional.of(id))).isEqualTo("shop:a@x.com");
    }

    @Test
    void devicesAreComparedByBrowserAndSystem() {
        String chromeWin = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";
        String chromeWinNewer = chromeWin.replace("130.0", "131.0");
        String safariIphone = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1";
        assertThat(NewDeviceAlertServiceAccess.describe(chromeWin)).isEqualTo("Chrome on Windows");
        assertThat(NewDeviceAlertServiceAccess.describe(chromeWinNewer)).isEqualTo(NewDeviceAlertServiceAccess.describe(chromeWin));
        assertThat(NewDeviceAlertServiceAccess.describe(safariIphone)).isEqualTo("Safari on iOS");
    }
}
