package com.secureone.auth.federation;

import java.util.LinkedHashMap;
import java.util.Map;

/** Well-known OAuth/OIDC endpoints for built-in social providers. */
public final class FederationPresets {

    private FederationPresets() {}

    public static Map<String, Object> defaultsFor(FederationProviderType type) {
        return switch (type) {
            case GOOGLE -> googleDefaults();
            case GITHUB -> githubDefaults();
            case OIDC -> oidcDefaults();
            case SAML -> samlDefaults();
        };
    }

    public static String authMethodId(FederationProviderType type) {
        return switch (type) {
            case GOOGLE -> "m_google";
            case GITHUB -> "m_github";
            case SAML -> "m_saml";
            case OIDC -> "m_oidc";
        };
    }

    private static Map<String, Object> googleDefaults() {
        Map<String, Object> out = commonDefaults();
        out.put("authorizationUri", "https://accounts.google.com/o/oauth2/v2/auth");
        out.put("tokenUri", "https://oauth2.googleapis.com/token");
        out.put("userInfoUri", "https://openidconnect.googleapis.com/v1/userinfo");
        out.put("scopes", "openid email profile");
        return out;
    }

    private static Map<String, Object> githubDefaults() {
        Map<String, Object> out = commonDefaults();
        out.put("authorizationUri", "https://github.com/login/oauth/authorize");
        out.put("tokenUri", "https://github.com/login/oauth/access_token");
        out.put("userInfoUri", "https://api.github.com/user");
        out.put("emailUri", "https://api.github.com/user/emails");
        out.put("scopes", "read:user user:email");
        return out;
    }

    private static Map<String, Object> oidcDefaults() {
        Map<String, Object> out = commonDefaults();
        out.put("authorizationUri", "");
        out.put("tokenUri", "");
        out.put("userInfoUri", "");
        out.put("scopes", "openid email profile");
        return out;
    }

    private static Map<String, Object> samlDefaults() {
        Map<String, Object> out = commonDefaults();
        out.put("metadataUrl", "");
        out.put("entityId", "");
        return out;
    }

    private static Map<String, Object> commonDefaults() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("clientId", "");
        out.put("clientSecretEncrypted", "");
        out.put("jitProvisioning", true);
        out.put("linkByEmail", true);
        return out;
    }
}
