package com.secureone.auth.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Public account API paths scoped to an application (tenant is resolved server-side). */
public final class ApplicationAccountEndpoints {

    private ApplicationAccountEndpoints() {}

    public static String basePath(UUID applicationId) {
        return "/api/v1/applications/" + applicationId;
    }

    public static Map<String, Object> manifestBlock(UUID applicationId) {
        return manifestBlock(applicationId, Map.of());
    }

    public static Map<String, Object> manifestBlock(UUID applicationId, Map<String, String> clientUrls) {
        String base = basePath(applicationId);
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("forgotPasswordEndpoint", base + "/account/password/forgot");
        account.put("passwordResetOtpEndpoint", base + "/account/password/reset/otp");
        account.put("passwordResetEndpoint", "/api/v1/account/password/reset");
        account.put("resendVerificationEndpoint", base + "/account/email/resend-verification");
        account.put("verifyOtpConfirmEndpoint", base + "/account/verify/otp/confirm");
        account.put("magicLinkRequestEndpoint", base + "/account/magic-link/request");
        account.put("sessionLoginEndpoint", base + "/auth/session/login");
        account.put("otpLoginRequestEndpoint", base + "/auth/otp/request");
        account.put("otpLoginVerifyEndpoint", base + "/auth/otp/verify");
        account.put("passkeyOptionsEndpoint", "/api/v1/auth/passkey/options");
        account.put("passkeyVerifyEndpoint", "/api/v1/auth/passkey/verify");
        account.put("federationProvidersEndpoint", "/api/v1/auth/federation/providers");
        account.put("setPasswordEndpoint", "/api/v1/account/set-password");
        account.put("signupOptionsEndpoint", base + "/signup");
        account.put("loginIdentifier", "email or phone (E.164)");
        account.put("loginEmailHint", "user@example.com");
        account.put("hostedLoginPage", "/login.html?applicationId=" + applicationId);
        account.put("hostedSignupPage", "/account/signup.html?applicationId=" + applicationId);
        account.put("hostedForgotPasswordPage", "/account/forgot-password.html?applicationId=" + applicationId);
        account.put("hostedMagicLinkPage", "/account/magic-link.html?applicationId=" + applicationId);
        account.put("hostedSecurityPage", "/account/security.html?applicationId=" + applicationId);
        if (clientUrls != null && !clientUrls.isEmpty()) {
            account.put("clientUrls", clientUrls);
        }
        return account;
    }
}
