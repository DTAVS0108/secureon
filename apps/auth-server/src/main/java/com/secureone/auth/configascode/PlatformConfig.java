package com.secureone.auth.configascode;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Platform-wide settings as a file (docs/26): the default SMTP server and SMS provider for every
 * application, and SCIM. Credentials come from environment variables named in the file.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"apiVersion", "kind", "smtp", "sms", "scim"})
public record PlatformConfig(
        String apiVersion,
        String kind,
        TenantConfig.Smtp smtp,
        TenantConfig.Sms sms,
        Scim scim) {

    public static final String KIND = "PlatformConfiguration";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"enabled", "userNameDomain", "bearerTokenEnv"})
    public record Scim(Boolean enabled, String userNameDomain, String bearerTokenEnv) {}
}
