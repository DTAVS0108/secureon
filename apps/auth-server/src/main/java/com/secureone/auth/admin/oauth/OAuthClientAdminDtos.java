package com.secureone.auth.admin.oauth;

import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OAuthClientAdminDtos {

    private OAuthClientAdminDtos() {}

    public record OAuthEndpoints(
            String issuer,
            String authorizationEndpoint,
            String tokenEndpoint,
            String jwksUri) {}

    public record OAuthClientResponse(
            UUID id,
            UUID applicationId,
            UUID tenantId,
            String applicationName,
            String clientId,
            String type,
            String status,
            List<String> grantTypes,
            List<String> scopes,
            List<String> redirectUris,
            List<String> postLogoutRedirectUris,
            boolean confidential,
            boolean pkceRequired,
            String tokenEndpointAuthMethod,
            boolean clientSecretConfigured,
            Instant createdAt,
            Instant updatedAt,
            OAuthEndpoints oAuthEndpoints,
            String subjectType,
            /** Framework chosen in Connect an app (e.g. "electron"); null when not set. */
            String setupFramework,
            /** OIDC Back-Channel Logout URL; null when not set. */
            String backchannelLogoutUri,
            /** Third-party client: users see a consent screen. */
            boolean consentRequired,
            /** private_key_jwt: the client's JWKS URL; null when not set. */
            String jwksUri,
            /** private_key_jwt: the client's public keys; null when not set. */
            java.util.Map<String, Object> jwks) {}

    public record OAuthClientCreateRequest(
            UUID applicationId,
            @Size(max = 255) String applicationName,
            UUID tenantId,
            @Size(max = 100) String clientId,
            @Size(max = 32) String type,
            @Size(max = 32) String status,
            List<String> grantTypes,
            List<String> scopes,
            List<String> redirectUris,
            List<String> postLogoutRedirectUris,
            Boolean pkceRequired,
            String tokenEndpointAuthMethod,
            String subjectType,
            @Size(max = 64) String setupFramework,
            @Size(max = 1024) String backchannelLogoutUri,
            Boolean consentRequired,
            /** private_key_jwt: the client's JWKS URL (https). */
            @Size(max = 1024) String jwksUri,
            /** private_key_jwt: the client's public keys, a JWK set, when it serves none at a URL. */
            java.util.Map<String, Object> jwks) {

        /** The request without private_key_jwt keys (callers that predate them). */
        public OAuthClientCreateRequest(
                UUID applicationId, String applicationName, UUID tenantId, String clientId, String type, String status,
                List<String> grantTypes, List<String> scopes, List<String> redirectUris, List<String> postLogoutRedirectUris,
                Boolean pkceRequired, String tokenEndpointAuthMethod, String subjectType, String setupFramework,
                String backchannelLogoutUri, Boolean consentRequired) {
            this(applicationId, applicationName, tenantId, clientId, type, status, grantTypes, scopes, redirectUris,
                    postLogoutRedirectUris, pkceRequired, tokenEndpointAuthMethod, subjectType, setupFramework,
                    backchannelLogoutUri, consentRequired, null, null);
        }
    }

    public record OAuthClientUpdateRequest(
            @Size(max = 32) String type,
            @Size(max = 32) String status,
            List<String> grantTypes,
            List<String> scopes,
            List<String> redirectUris,
            List<String> postLogoutRedirectUris,
            Boolean pkceRequired,
            String tokenEndpointAuthMethod,
            String subjectType,
            /** null keeps the current value; "" clears it. */
            @Size(max = 64) String setupFramework,
            /** null keeps the current value; "" clears it. */
            @Size(max = 1024) String backchannelLogoutUri,
            /** null keeps the current value. */
            Boolean consentRequired,
            /** null keeps the current value; "" clears it. */
            @Size(max = 1024) String jwksUri,
            /** null keeps the current value; {} clears it. */
            java.util.Map<String, Object> jwks) {

        /** The request without private_key_jwt keys (callers that predate them). */
        public OAuthClientUpdateRequest(
                String type, String status, List<String> grantTypes, List<String> scopes, List<String> redirectUris,
                List<String> postLogoutRedirectUris, Boolean pkceRequired, String tokenEndpointAuthMethod,
                String subjectType, String setupFramework, String backchannelLogoutUri, Boolean consentRequired) {
            this(type, status, grantTypes, scopes, redirectUris, postLogoutRedirectUris, pkceRequired,
                    tokenEndpointAuthMethod, subjectType, setupFramework, backchannelLogoutUri, consentRequired, null, null);
        }
    }

    public record OAuthClientCreateResult(OAuthClientResponse client, String clientSecret) {}

    public record OAuthClientSecretResponse(String clientSecret) {}
}
