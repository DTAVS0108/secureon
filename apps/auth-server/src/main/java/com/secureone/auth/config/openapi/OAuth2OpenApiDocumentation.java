package com.secureone.auth.config.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Manual OpenAPI paths for Spring Authorization Server endpoints (not Spring MVC controllers). */
public final class OAuth2OpenApiDocumentation {

    private OAuth2OpenApiDocumentation() {}

    public static void apply(OpenAPI openApi) {
        Paths paths = openApi.getPaths();
        if (paths == null) {
            paths = new Paths();
            openApi.setPaths(paths);
        }

        paths.addPathItem(
                "/.well-known/openid-configuration",
                new PathItem()
                        .get(new Operation()
                                .operationId("openidConfiguration")
                                .summary("OpenID Provider metadata")
                                .description("OIDC discovery document (issuer, endpoints, supported scopes and grant types).")
                                .tags(List.of("OAuth 2.1 / OIDC"))
                                .responses(responses("200", okJson("OpenID configuration JSON")))));

        paths.addPathItem(
                "/.well-known/oauth-authorization-server",
                new PathItem()
                        .get(new Operation()
                                .operationId("oauthAuthorizationServerMetadata")
                                .summary("Authorization server metadata (RFC 8414)")
                                .tags(List.of("OAuth 2.1 / OIDC"))
                                .responses(responses("200", okJson("Authorization server metadata JSON")))));

        paths.addPathItem(
                "/oauth2/authorize",
                new PathItem()
                        .get(new Operation()
                                .operationId("oauth2Authorize")
                                .summary("Authorization endpoint (authorization code + PKCE)")
                                .description(
                                        "Browser redirect flow. Requires an authenticated end-user session. "
                                                + "Use `response_type=code`, `client_id`, `redirect_uri`, `scope`, and PKCE `code_challenge` / `code_challenge_method=S256`.")
                                .tags(List.of("OAuth 2.1 / OIDC"))
                                .parameters(List.of(
                                        queryParam("response_type", "code", true),
                                        queryParam("client_id", "your-client-id", true),
                                        queryParam(
                                                "redirect_uri",
                                                "http://127.0.0.1:3000/login/oauth2/code/secureone",
                                                true),
                                        queryParam("scope", "openid profile", false),
                                        queryParam("state", "opaque-state", false),
                                        queryParam("code_challenge", "PKCE challenge", false),
                                        queryParam("code_challenge_method", "S256", false)))
                                .responses(responses(
                                        "302",
                                        new ApiResponse()
                                                .description("Redirect to client with `code` or error"),
                                        "401",
                                        new ApiResponse().description("Login required")))));

        Schema<?> tokenResponse = new ObjectSchema()
                .addProperty("access_token", new StringSchema())
                .addProperty("token_type", new StringSchema().example("Bearer"))
                .addProperty("expires_in", new Schema<>().type("integer"))
                .addProperty("refresh_token", new StringSchema())
                .addProperty("scope", new StringSchema())
                .addProperty("id_token", new StringSchema());

        paths.addPathItem(
                "/oauth2/token",
                new PathItem()
                        .post(new Operation()
                                .operationId("oauth2Token")
                                .summary("Token endpoint")
                                .description(
                                        "Exchange credentials for tokens. Supports `authorization_code`, `refresh_token`, "
                                                + "`client_credentials`, `urn:ietf:params:oauth:grant-type:device_code` (RFC 8628 polling: "
                                                + "`authorization_pending`, `slow_down`, `access_denied`, `expired_token`) and token exchange. "
                                                + "Client auth: HTTP Basic (`client_id` / `client_secret`) or `client_secret_post`.")
                                .tags(List.of("OAuth 2.1 / OIDC"))
                                .requestBody(formUrlEncoded(
                                        "grant_type",
                                        "authorization_code | refresh_token | client_credentials | urn:ietf:params:oauth:grant-type:device_code",
                                        "code",
                                        "Authorization code (authorization_code grant)",
                                        "redirect_uri",
                                        "Must match authorize request",
                                        "code_verifier",
                                        "PKCE verifier",
                                        "refresh_token",
                                        "Refresh token (refresh_token grant)",
                                        "scope",
                                        "Requested scopes"))
                                .responses(responses(
                                        "200",
                                        new ApiResponse()
                                                .description("Token response")
                                                .content(jsonContent(tokenResponse)),
                                        "400",
                                        new ApiResponse().description("Invalid grant or request"),
                                        "401",
                                        new ApiResponse().description("Invalid client")))));

        paths.addPathItem(
                "/oauth2/revoke",
                new PathItem()
                        .post(new Operation()
                                .operationId("oauth2Revoke")
                                .summary("Token revocation (RFC 7009)")
                                .tags(List.of("OAuth 2.1 / OIDC"))
                                .requestBody(formUrlEncoded("token", "Access or refresh token", "token_type_hint", "access_token | refresh_token"))
                                .responses(responses(
                                        "200",
                                        new ApiResponse().description("Revoked (or unknown token)")))));

        paths.addPathItem(
                "/oauth2/introspect",
                new PathItem()
                        .post(new Operation()
                                .operationId("oauth2Introspect")
                                .summary("Token introspection (RFC 7662)")
                                .tags(List.of("OAuth 2.1 / OIDC"))
                                .requestBody(formUrlEncoded("token", "Token to introspect"))
                                .responses(responses("200", okJson("Active flag and token metadata")))));

        paths.addPathItem(
                "/oauth2/device_authorization",
                new PathItem()
                        .post(new Operation()
                                .operationId("oauth2DeviceAuthorization")
                                .summary("Device authorization (RFC 8628)")
                                .description("For TVs / CLIs: returns device_code, user_code, verification_uri "
                                        + "(/account/device.html), verification_uri_complete, expires_in and interval. "
                                        + "Public device clients send client_id only. The openid scope is not allowed.")
                                .tags(List.of("OAuth 2.1 / OIDC"))
                                .requestBody(formUrlEncoded("client_id", "Client with the device_code grant", "scope", "e.g. profile email"))
                                .responses(responses(
                                        "200", okJson("Device and user codes"),
                                        "401", new ApiResponse().description("Unknown client or client without the device grant")))));

        paths.addPathItem(
                "/.well-known/oauth-protected-resource",
                new PathItem()
                        .get(new Operation()
                                .operationId("oauthProtectedResourceMetadata")
                                .summary("Protected resource metadata (RFC 9728)")
                                .tags(List.of("OAuth 2.1 / OIDC"))
                                .responses(responses("200", okJson("Authorization servers, JWKS URI, bearer methods")))));

        paths.addPathItem(
                "/oauth2/jwks",
                new PathItem()
                        .get(new Operation()
                                .operationId("oauth2Jwks")
                                .summary("JSON Web Key Set")
                                .tags(List.of("OAuth 2.1 / OIDC"))
                                .responses(responses("200", okJson("JWKS document")))));

        paths.addPathItem(
                "/userinfo",
                new PathItem()
                        .get(new Operation()
                                .operationId("oidcUserInfo")
                                .summary("OIDC UserInfo")
                                .tags(List.of("OAuth 2.1 / OIDC"))
                                .security(List.of(new SecurityRequirement().addList("bearerAuth")))
                                .responses(responses(
                                        "200",
                                        new ApiResponse().description("Claims about the authenticated subject"),
                                        "401",
                                        new ApiResponse().description("Invalid or missing bearer token")))));
    }

    private static ApiResponses responses(String code, ApiResponse response) {
        return new ApiResponses().addApiResponse(code, response);
    }

    private static ApiResponses responses(String code1, ApiResponse r1, String code2, ApiResponse r2) {
        return new ApiResponses().addApiResponse(code1, r1).addApiResponse(code2, r2);
    }

    private static ApiResponses responses(
            String code1, ApiResponse r1, String code2, ApiResponse r2, String code3, ApiResponse r3) {
        return new ApiResponses()
                .addApiResponse(code1, r1)
                .addApiResponse(code2, r2)
                .addApiResponse(code3, r3);
    }

    public static SecurityScheme bearerAuthScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("OAuth 2.0 access token from `/oauth2/token`");
    }

    public static SecurityScheme clientBasicScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("basic")
                .description("OAuth client credentials (`client_id` / `client_secret`)");
    }

    private static Parameter queryParam(String name, String example, boolean required) {
        return new Parameter()
                .in("query")
                .name(name)
                .required(required)
                .schema(new StringSchema().example(example));
    }

    private static ApiResponse okJson(String description) {
        return new ApiResponse()
                .description(description)
                .content(jsonContent(new ObjectSchema().description("JSON object")));
    }

    private static Content jsonContent(Schema<?> schema) {
        return new Content().addMediaType("application/json", new MediaType().schema(schema));
    }

    private static RequestBody formUrlEncoded(String... nameDescriptionPairs) {
        Map<String, Schema<?>> props = new LinkedHashMap<>();
        for (int i = 0; i < nameDescriptionPairs.length; i += 2) {
            props.put(
                    nameDescriptionPairs[i],
                    new StringSchema().description(nameDescriptionPairs[i + 1]));
        }
        ObjectSchema schema = new ObjectSchema();
        props.forEach(schema::addProperty);
        return new RequestBody()
                .required(true)
                .content(new Content()
                        .addMediaType(
                                "application/x-www-form-urlencoded",
                                new MediaType().schema(schema)));
    }
}
