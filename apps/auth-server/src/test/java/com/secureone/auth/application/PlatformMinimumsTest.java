package com.secureone.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlatformMinimumsTest {

    @Test
    void weakerPasswordPolicyIsRaisedToThePlatform() {
        Map<String, Object> platform = PasswordPolicySanitizer.platformDefaults();
        platform.put("expiryDays", 90);
        platform.put("requireSymbol", true);
        Map<String, Object> app = new HashMap<>(platform);
        app.put("minLength", 8);
        app.put("requireSymbol", false);
        app.put("maxFailedAttempts", 10);
        app.put("expiryDays", 0);

        PlatformMinimums.Result result = PlatformMinimums.password(platform, app);

        assertThat(result.value()).containsEntry("minLength", 12).containsEntry("requireSymbol", true)
                .containsEntry("maxFailedAttempts", 5).containsEntry("expiryDays", 90);
        assertThat(result.raised()).hasSize(4);
        assertThatThrownBy(result::requireNothingRaised)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Minimum length must be at least 12");
    }

    @Test
    void stricterPasswordPolicyIsKept() {
        Map<String, Object> platform = PasswordPolicySanitizer.platformDefaults();
        Map<String, Object> app = new HashMap<>(platform);
        app.put("minLength", 16);
        app.put("breachCheck", true);
        app.put("maxFailedAttempts", 3);
        app.put("expiryDays", 30);

        PlatformMinimums.Result result = PlatformMinimums.password(platform, app);

        assertThat(result.raised()).isEmpty();
        assertThat(result.value()).containsEntry("minLength", 16).containsEntry("expiryDays", 30);
    }

    @Test
    void tokensCannotOutliveThePlatformOrTurnRefreshBackOn() {
        Map<String, Object> platform = TokenPolicyDefaults.platformDefaults();
        platform.put("refreshTokensEnabled", false);
        Map<String, Object> app = new HashMap<>(platform);
        app.put("accessTokenTtlSeconds", 86_400);
        app.put("idTokenTtlSeconds", 600);
        app.put("refreshTokensEnabled", true);

        PlatformMinimums.Result result = PlatformMinimums.token(platform, app);

        assertThat(result.value()).containsEntry("accessTokenTtlSeconds", 3600)
                .containsEntry("idTokenTtlSeconds", 600).containsEntry("refreshTokensEnabled", false);
        assertThat(result.raised()).hasSize(2);
    }

    @Test
    void verificationKeepsRequiredChannelsAndOnlyAllowedModes() {
        Map<String, Object> platform = com.secureone.auth.platform.VerificationPolicyDefaults.platformDefaults();
        @SuppressWarnings("unchecked")
        Map<String, Object> platformLogin = new HashMap<>((Map<String, Object>) platform.get("login"));
        platformLogin.put("passwordlessModes", List.of("email_otp"));
        platform.put("login", platformLogin);

        Map<String, Object> app = new HashMap<>();
        app.put("signup", Map.of("requiredChannels", List.of(), "otpLength", 4));
        app.put("login", Map.of("passwordlessModes", List.of("email_otp", "magic_link")));
        app.put("recovery", Map.of("allowedModes", List.of("otp"), "passwordResetMode", "otp",
                "requireVerifiedChannel", false));
        Map<String, Object> platformRecovery = new HashMap<>((Map<String, Object>) platform.get("recovery"));
        platformRecovery.put("allowedModes", List.of("link"));
        platform.put("recovery", platformRecovery);

        PlatformMinimums.Result result = PlatformMinimums.verification(platform, app);

        @SuppressWarnings("unchecked")
        Map<String, Object> signup = (Map<String, Object>) result.value().get("signup");
        @SuppressWarnings("unchecked")
        Map<String, Object> login = (Map<String, Object>) result.value().get("login");
        @SuppressWarnings("unchecked")
        Map<String, Object> recovery = (Map<String, Object>) result.value().get("recovery");
        assertThat(signup).containsEntry("requiredChannels", List.of("email")).containsEntry("otpLength", 6);
        assertThat(login).containsEntry("passwordlessModes", List.of("email_otp"));
        assertThat(recovery).containsEntry("allowedModes", List.of("link"))
                .containsEntry("passwordResetMode", "link").containsEntry("requireVerifiedChannel", true);
    }

    @Test
    void noPasswordlessModeLeftStaysNone() {
        Map<String, Object> platform = Map.of("login", Map.of("passwordlessModes", List.of("email_otp")));
        Map<String, Object> app = Map.of("login", Map.of("passwordlessModes", List.of("magic_link")));

        Map<?, ?> login = (Map<?, ?>) PlatformMinimums.verification(platform, app).value().get("login");

        assertThat(login.get("passwordlessModes")).isEqualTo(List.of());
    }
}
