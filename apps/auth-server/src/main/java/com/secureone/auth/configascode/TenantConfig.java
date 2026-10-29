package com.secureone.auth.configascode;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.Map;

/**
 * A tenant's configuration as a file (docs/install/configuration-and-upgrades.md): the tenant, its applications, their OAuth clients,
 * permissions, roles, groups and settings. Users, group members, secrets and provider credentials are
 * never part of it. A field left out (null) means "leave as it is"; a list given means "exactly this".
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"apiVersion", "kind", "prune", "tenant", "scim", "identityProviders", "webhooks", "serviceAccounts",
        "applications"})
public record TenantConfig(
        String apiVersion,
        String kind,
        /** Delete what the file's lists leave out (and clear settings the file's settings leave out). */
        Boolean prune,
        Tenant tenant,
        /** The tenant's SCIM token (directory provisioning). */
        Scim scim,
        /** Sign-in providers for every application of the tenant. */
        List<IdentityProvider> identityProviders,
        List<Webhook> webhooks,
        List<ServiceAccount> serviceAccounts,
        List<Application> applications) {

    public static final String API_VERSION = "secureone/v1";
    public static final String KIND = "TenantConfiguration";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"slug", "name", "plan", "status"})
    public record Tenant(String slug, String name, String plan, String status) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"slug", "name", "description", "status", "clients", "permissions", "roles", "groups",
            "identityProviders", "settings"})
    public record Application(
            String slug,
            String name,
            String description,
            String status,
            List<Client> clients,
            List<Permission> permissions,
            List<Role> roles,
            List<Group> groups,
            /** Sign-in providers for this application only. */
            List<IdentityProvider> identityProviders,
            /** Settings overrides; also smtp and sms (see {@link Smtp}, {@link Sms}). */
            Map<String, Object> settings) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"clientId", "type", "status", "grantTypes", "scopes", "redirectUris", "postLogoutRedirectUris",
            "pkceRequired", "tokenEndpointAuthMethod", "subjectType", "backchannelLogoutUri", "consentRequired", "clientSecretEnv",
            "jwksUri", "jwks"})
    public record Client(
            String clientId,
            String type,
            String status,
            List<String> grantTypes,
            List<String> scopes,
            List<String> redirectUris,
            List<String> postLogoutRedirectUris,
            Boolean pkceRequired,
            String tokenEndpointAuthMethod,
            String subjectType,
            /** OIDC Back-Channel Logout URL (optional). */
            String backchannelLogoutUri,
            /** Third-party client: users see a consent screen (optional, default false). */
            Boolean consentRequired,
            /** Name of an environment variable on the server that holds this confidential client's secret. */
            String clientSecretEnv,
            /** private_key_jwt: where the client serves its public keys. */
            String jwksUri,
            /** private_key_jwt: the client's public keys (JWK set), when it has no jwksUri. */
            Map<String, Object> jwks) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"key", "description"})
    public record Permission(String key, String description) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"name", "description", "composite", "permissions", "includes"})
    public record Role(
            String name,
            String description,
            /** Optional; a role that includes other roles is composite. */
            Boolean composite,
            /** Permission keys. */
            List<String> permissions,
            /** Names of the roles this role includes. */
            List<String> includes) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"name", "description", "roles"})
    public record Group(String name, String description, List<String> roles) {}

    /** enabled: false revokes the token. Without tokenEnv a token is generated (shown once) if none exists. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"enabled", "tokenEnv"})
    public record Scim(Boolean enabled, String tokenEnv) {}

    /** config: the provider's settings (clientId, endpoints, SAML metadata, jitProvisioning, linkByEmail, ...). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"alias", "type", "displayName", "enabled", "config", "clientSecretEnv"})
    public record IdentityProvider(
            String alias,
            String type,
            String displayName,
            Boolean enabled,
            Map<String, Object> config,
            String clientSecretEnv) {}

    /** Matched by url (and application). Without secretEnv a new endpoint gets a generated signing secret. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"url", "application", "events", "enabled", "secretEnv"})
    public record Webhook(String url, String application, List<String> events, Boolean enabled, String secretEnv) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"name", "description", "status", "apiKeys"})
    public record ServiceAccount(String name, String description, String status, List<ApiKey> apiKeys) {}

    /** Matched by label. keyEnv: the key's value (sk_live_...); changing it rotates the key. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"label", "expiresAt", "keyEnv"})
    public record ApiKey(String label, String expiresAt, String keyEnv) {}

    /** settings.smtp of an application, or smtp of the platform file. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"host", "port", "security", "username", "authEnabled", "passwordEnv"})
    public record Smtp(String host, Integer port, String security, String username, Boolean authEnabled, String passwordEnv) {}

    /** settings.sms of an application, or sms of the platform file. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"enabled", "provider", "twilio", "aws_sns", "msg91"})
    public record Sms(
            Boolean enabled,
            String provider,
            Twilio twilio,
            @com.fasterxml.jackson.annotation.JsonProperty("aws_sns") AwsSns awsSns,
            Msg91 msg91) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"accountSid", "fromNumber", "authTokenEnv"})
    public record Twilio(String accountSid, String fromNumber, String authTokenEnv) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"region", "accessKeyId", "senderId", "secretAccessKeyEnv"})
    public record AwsSns(String region, String accessKeyId, String senderId, String secretAccessKeyEnv) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"senderId", "templateId", "apiKeyEnv"})
    public record Msg91(String senderId, String templateId, String apiKeyEnv) {}
}
