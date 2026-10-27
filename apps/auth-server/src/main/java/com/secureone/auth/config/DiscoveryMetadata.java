package com.secureone.auth.config;

import java.util.List;
import java.util.Map;

/**
 * What discovery (OpenID Provider metadata and RFC 8414 server metadata) advertises, limited to what a SecureOne
 * client can actually be set up for. Spring lists every method it could support (client_secret_jwt,
 * private_key_jwt, mTLS); SecureOne clients use a client secret (basic or post) or none (public clients), so
 * advertising more would send integrations down a path that fails.
 */
final class DiscoveryMetadata {

    static final List<String> SCOPES = List.of("openid", "profile", "email", "offline_access");

    static final List<String> CLAIMS = List.of(
            "sub", "iss", "aud", "exp", "iat", "auth_time", "nonce", "acr", "amr", "sid", "azp",
            "email", "email_verified", "name", "preferred_username",
            "user_id", "tenant_id", "roles", "permissions");

    static final List<String> CLIENT_AUTH = List.of("client_secret_basic", "client_secret_post", "none");

    static final List<String> SECRET_AUTH = List.of("client_secret_basic", "client_secret_post");

    private DiscoveryMetadata() {}

    /** RFC 8414 metadata. */
    static void apply(Map<String, Object> claims) {
        claims.put("scopes_supported", SCOPES);
        claims.put("token_endpoint_auth_methods_supported", CLIENT_AUTH);
        claims.put("revocation_endpoint_auth_methods_supported", SECRET_AUTH);
        claims.put("introspection_endpoint_auth_methods_supported", SECRET_AUTH);
        // Certificate-bound tokens need mTLS client authentication, which clients cannot be set to.
        claims.remove("tls_client_certificate_bound_access_tokens");
    }

    /** OpenID Provider metadata: the same, plus the claims tokens and userinfo can carry. */
    static void applyOidc(Map<String, Object> claims) {
        apply(claims);
        claims.put("claims_supported", CLAIMS);
    }
}
