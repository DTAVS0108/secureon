package com.secureone.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.secureone.auth.platform.VerificationPolicyDefaults;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Application settings kept within the standards (0.3.29 audit). */
class SettingsStandardsTest {

    private static final Set<String> TRUSTED = ClientUrlPolicy.origins(List.of(
            "https://app.example.com/callback", "http://localhost:3000/callback"));

    @Test
    void tokenPagesMustBeThisApplicationsHttpsPages() {
        assertThat(ClientUrlPolicy.problem("https://app.example.com/reset", TRUSTED)).isNull();
        assertThat(ClientUrlPolicy.problem("http://localhost:3000/reset", TRUSTED)).isNull();
        assertThat(ClientUrlPolicy.problem("https://evil.example.net/reset", TRUSTED)).contains("redirect to");
        assertThat(ClientUrlPolicy.problem("http://app.example.com/reset", TRUSTED)).contains("https");
        assertThat(ClientUrlPolicy.problem("javascript:alert(1)", TRUSTED)).isNotNull();
        assertThat(ClientUrlPolicy.problem("https://user@app.example.com/reset", TRUSTED)).isNotNull();
        assertThat(ClientUrlPolicy.problem("/reset", TRUSTED)).contains("full address");
        assertThat(ClientUrlPolicy.problem("https://app.example.com/reset", Set.of())).contains("add an OAuth client");
    }

    @Test
    void appearanceAcceptsOnlyPlainValues() {
        assertThat(ClientAppearanceDefaults.problem("primaryColor", "#4f46e5")).isNull();
        assertThat(ClientAppearanceDefaults.problem("dialogOverlay", "#00000066")).isNull();
        assertThat(ClientAppearanceDefaults.problem("primaryColor", "red;background:url(https://x)")).isNotNull();
        assertThat(ClientAppearanceDefaults.problem("logoUrl", "https://cdn.example.com/logo.png")).isNull();
        assertThat(ClientAppearanceDefaults.problem("logoUrl", "javascript:alert(1)")).isNotNull();
        assertThat(ClientAppearanceDefaults.problem("logoUrl", "http://cdn.example.com/logo.png")).isNotNull();
        assertThat(ClientAppearanceDefaults.problem("appName", "<b>Shop</b>")).isNotNull();
        assertThat(ClientAppearanceDefaults.problem("fontScale", 9)).isNotNull();
        assertThatThrownBy(() -> ClientAppearanceDefaults.requireValid(Map.of("textColor", "expression(x)")))
                .hasMessageContaining("textColor");
        // A bad stored value keeps the default instead of reaching client apps.
        Map<String, Object> merged = ClientAppearanceDefaults.merge(null, Map.of("primaryColor", "url(x)"));
        assertThat(merged.get("primaryColor")).isEqualTo("#4f46e5");
    }

    @Test
    void codesHaveAtLeastSixDigitsAndExpireWithinTenMinutes() {
        Map<String, Object> body = new HashMap<>(VerificationPolicyDefaults.platformDefaults());
        body.put("login", new HashMap<>(Map.of("otpLength", 4, "otpTtlSeconds", 3600)));
        Map<?, ?> login = (Map<?, ?>) VerificationPolicyDefaults.sanitizeForSave(body).get("login");
        assertThat(login.get("otpLength")).isEqualTo(6);
        assertThat(login.get("otpTtlSeconds")).isEqualTo(600);
    }

    @Test
    void tokenLifetimesStayShort() {
        Map<String, Object> body = new HashMap<>(TokenPolicyDefaults.platformDefaults());
        body.put("accessTokenTtlSeconds", 86_400);
        body.put("authorizationCodeTtlSeconds", 3600);
        Map<String, Object> out = TokenPolicyDefaults.sanitizeForSave(body);
        assertThat(out).containsEntry("accessTokenTtlSeconds", 3600).containsEntry("authorizationCodeTtlSeconds", 600)
                .containsEntry("refreshTokenAbsoluteSeconds", 2_592_000).containsEntry("signInMaxAgeSeconds", 0);
    }

    @Test
    void passwordDefaultsFollowNist() {
        Map<String, Object> defaults = PasswordPolicySanitizer.platformDefaults();
        assertThat(defaults).containsEntry("breachCheck", true).containsEntry("requireSymbol", false)
                .containsEntry("hashAlgorithm", "argon2id");
        assertThat(PasswordPolicySanitizer.normalizeHashAlgorithm("bcrypt")).isEqualTo("argon2id");
    }
}
