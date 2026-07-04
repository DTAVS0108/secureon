package com.secureone.auth.federation;

public enum FederationProviderType {
    GOOGLE,
    GITHUB,
    OIDC,
    SAML;

    public static FederationProviderType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Provider type is required.");
        }
        return FederationProviderType.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
