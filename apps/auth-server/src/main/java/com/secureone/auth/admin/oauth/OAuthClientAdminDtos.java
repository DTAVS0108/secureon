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
            String setupFramework) {}

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
            @Size(max = 64) String setupFramework) {}

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
            @Size(max = 64) String setupFramework) {}

    public record OAuthClientCreateResult(OAuthClientResponse client, String clientSecret) {}

    public record OAuthClientSecretResponse(String clientSecret) {}
}
