package com.secureone.auth.config;

import java.util.List;
import java.util.Map;

/**
 * What discovery (OpenID Provider metadata and RFC 8414 server metadata) advertises, limited to what a SecureOne
 * client can actually be set up for. Spring lists every method it could support (client_secret_jwt, mTLS);
 * SecureOne clients use a client secret (basic or post), a private key JWT, or none (public clients), so
 * advertising more would send integrations down a path that fails.
 */
final class DiscoveryMetadata {

    static final List<String> SCOPES = List.of("openid", "profile", "email", "offline_access");

    static final List<String> CLAIMS = List.of(
            "sub", "iss", "aud", "exp", "iat", "auth_time", "nonce", "acr", "amr", "sid", "azp",
            "email", "email_verified", "name", "given_name", "family_name", "preferred_username",
            "user_id", "tenant_id", "roles", "permissions");

    static final List<String> CLIENT_AUTH = List.of("client_secret_basic", "client_secret_post", "private_key_jwt", "none");

    /** Revocation and introspection: confidential clients only. */
    static final List<String> SECRET_AUTH = List.of("client_secret_basic", "client_secret_post", "private_key_jwt");

    private DiscoveryMetadata() {}

    /** RFC 8414 metadata. */
    static void apply(Map<String, Object> claims) {
        claims.put("scopes_supported", SCOPES);
        claims.put("token_endpoint_auth_methods_supported", CLIENT_AUTH);
        claims.put("revocation_endpoint_auth_methods_supported", SECRET_AUTH);
        claims.put("introspection_endpoint_auth_methods_supported", SECRET_AUTH);
        List<String> algorithms = com.secureone.auth.config.oauth.ClientAssertionDecoderFactory.ALGORITHM_NAMES;
        claims.put("token_endpoint_auth_signing_alg_values_supported", algorithms);
        claims.put("revocation_endpoint_auth_signing_alg_values_supported", algorithms);
        claims.put("introspection_endpoint_auth_signing_alg_values_supported", algorithms);
        // Certificate-bound tokens need mTLS client authentication, which clients cannot be set to.
        claims.remove("tls_client_certificate_bound_access_tokens");
    }

    /**
     * registration_endpoint, when this issuer takes registrations: a tenant issuer when one of the tenant's
     * applications does, the shared issuer when any application does (with an initial access token).
     */
    static void applyRegistration(Map<String, Object> claims, com.secureone.auth.oauth.registration.ClientRegistrationService registrations) {
        var context = org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder.getContext();
        if (registrations == null || context == null) {
            return;
        }
        java.util.UUID tenant = null;
        if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() instanceof
                org.springframework.web.context.request.ServletRequestAttributes attributes
                && attributes.getRequest().getAttribute(com.secureone.auth.tenant.TenantIssuers.ATTRIBUTE)
                        instanceof com.secureone.auth.tenant.TenantIssuers.Target target) {
            tenant = target.tenantId();
        }
        if (registrations.anyEnabled(tenant)) {
            claims.put("registration_endpoint", context.getIssuer() + "/oauth2/register");
        }
    }

    /** OpenID Provider metadata: the same, plus the claims tokens and userinfo can carry. */
    static void applyOidc(Map<String, Object> claims) {
        apply(claims);
        claims.put("claims_supported", CLAIMS);
    }
}
