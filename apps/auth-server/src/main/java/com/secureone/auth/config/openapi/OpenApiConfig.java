package com.secureone.auth.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI secureOneOpenAPI(@Value("${secureone.public-base-url:http://localhost:9000}") String publicBaseUrl) {
        String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;

        return new OpenAPI()
                .info(new Info()
                        .title("SecureOne Auth Server API")
                        .version("0.0.1-SNAPSHOT")
                        .description(
                                """
                                Centralized IAM for multi-tenant applications.

                                **API surfaces**
                                - **Public** — unauthenticated manifest, sign-up, auth method catalog, account recovery
                                - **Admin** — platform and application-scoped management (console session token after MFA)
                                - **OAuth 2.1 / OIDC** — authorization server protocol endpoints

                                **Try admin APIs in Swagger UI:** sign in with `POST /api/admin/v1/auth/login` (and `/auth/mfa/verify`), then Authorize with `SecureOne-Admin-Session <sessionToken>`.

                                **Integrated apps (e.g. e-commerce):** use `GET/POST /api/v1/applications/{applicationId}/signup` when Self Registration is enabled.""")
                        .contact(new Contact().name("SecureOne").email("support@secureone.local"))
                        .license(new License().name("Proprietary").url("https://github.com/secureone")))
                .externalDocs(new ExternalDocumentation()
                        .description("Architecture & auth standards")
                        .url("https://github.com/secureone/secureone/blob/develop/docs/05-auth-standards.md"))
                .servers(List.of(
                        new Server().url("/").description("Current host (use this in Swagger UI)"),
                        new Server().url(base).description("Configured public base URL")))
                .components(new Components()
                        .addSecuritySchemes("adminHttpBasic", adminBasicScheme())
                        .addSecuritySchemes("bearerAuth", OAuth2OpenApiDocumentation.bearerAuthScheme())
                        .addSecuritySchemes("oauthClientBasic", OAuth2OpenApiDocumentation.clientBasicScheme())
                        .addResponses("ProblemBadRequest", problemResponse("Bad Request", "400"))
                        .addResponses("ProblemUnauthorized", problemResponse("Unauthorized", "401"))
                        .addResponses("ProblemForbidden", problemResponse("Forbidden", "403"))
                        .addResponses("ProblemNotFound", problemResponse("Not Found", "404"))
                        .addResponses("ProblemConflict", problemResponse("Conflict", "409")));
    }

    /** Default Swagger UI group — every REST controller plus OAuth/OIDC protocol paths. */
    @Bean
    GroupedOpenApi allApis() {
        return GroupedOpenApi.builder()
                .group("all")
                .displayName("All APIs")
                .pathsToMatch("/**")
                .packagesToScan("com.secureone.auth")
                .addOpenApiCustomizer(oauthAndErrorsCustomizer())
                .addOpenApiCustomizer(adminSecurityCustomizer())
                .build();
    }

    @Bean
    GroupedOpenApi publicApis() {
        return GroupedOpenApi.builder()
                .group("public")
                .displayName("Public — auth & info")
                .pathsToMatch("/api/info", "/api/v1/auth/**")
                .build();
    }

    @Bean
    GroupedOpenApi accountApis() {
        return GroupedOpenApi.builder()
                .group("account")
                .displayName("Public — account flows")
                .pathsToMatch("/api/v1/account/**")
                .build();
    }

    @Bean
    GroupedOpenApi applicationPublicApis() {
        return GroupedOpenApi.builder()
                .group("applications")
                .displayName("Public — application manifest & signup")
                .pathsToMatch("/api/v1/applications/**")
                .build();
    }

    @Bean
    GroupedOpenApi adminPlatformApis() {
        return GroupedOpenApi.builder()
                .group("admin-platform")
                .displayName("Admin — platform")
                // Every admin API except the per-application console (next group): tenants, users, OAuth
                // clients, change approvals, access review, platform operators, settings, audit, sessions…
                .pathsToMatch("/api/admin/v1/**")
                .pathsToExclude("/api/admin/v1/applications/*/**")
                .addOpenApiCustomizer(adminSecurityCustomizer())
                .build();
    }

    @Bean
    GroupedOpenApi adminApplicationApis() {
        return GroupedOpenApi.builder()
                .group("admin-applications")
                .displayName("Admin — per application")
                .pathsToMatch("/api/admin/v1/applications/{applicationId}/**", "/api/admin/v1/applications/**")
                .addOpenApiCustomizer(adminSecurityCustomizer())
                .build();
    }

    @Bean
    GroupedOpenApi oauthOidcApis() {
        return GroupedOpenApi.builder()
                .group(OAuth2OpenApiDocumentation.GROUP)
                .displayName("OAuth 2.1 / OIDC")
                .pathsToMatch("/oauth2/**", "/.well-known/**", "/userinfo")
                .addOpenApiCustomizer(openApi -> OAuth2OpenApiDocumentation.apply(openApi))
                .build();
    }

    private static OpenApiCustomizer oauthAndErrorsCustomizer() {
        return openApi -> {
            OAuth2OpenApiDocumentation.apply(openApi);
            applyGlobalErrorResponses(openApi);
        };
    }

    private static OpenApiCustomizer adminSecurityCustomizer() {
        // Admin APIs need a console session token, except signing in itself (/api/admin/v1/auth/login, /mfa/…).
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) -> {
                boolean signIn = path.startsWith("/api/admin/v1/auth/") && !path.endsWith("/logout");
                if (!path.startsWith("/api/admin/") || signIn) {
                    return;
                }
                pathItem.readOperations().forEach(operation -> {
                    if (operation.getSecurity() == null || operation.getSecurity().isEmpty()) {
                        operation.addSecurityItem(new SecurityRequirement().addList("adminHttpBasic"));
                    }
                });
            });
        };
    }

    private static void applyGlobalErrorResponses(io.swagger.v3.oas.models.OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            return;
        }
        openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(operation -> {
            operation.getResponses().addApiResponse("400", ref("ProblemBadRequest"));
            operation.getResponses().addApiResponse("401", ref("ProblemUnauthorized"));
            operation.getResponses().addApiResponse("403", ref("ProblemForbidden"));
            operation.getResponses().addApiResponse("404", ref("ProblemNotFound"));
            operation.getResponses().addApiResponse("409", ref("ProblemConflict"));
        }));
    }

    private static ApiResponse ref(String componentKey) {
        return new ApiResponse().$ref("#/components/responses/" + componentKey);
    }

    private static ApiResponse problemResponse(String title, String status) {
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("type", new StringSchema().example("about:blank"));
        schema.addProperty("title", new StringSchema().example(title));
        schema.addProperty("status", new StringSchema().example(status));
        schema.addProperty("detail", new StringSchema());
        schema.addProperty("instance", new StringSchema());
        return new ApiResponse()
                .description("RFC 9457 Problem Details (`application/problem+json`)")
                .content(new Content()
                        .addMediaType("application/problem+json", new MediaType().schema(schema)));
    }

    private static SecurityScheme adminBasicScheme() {
        // Key kept as "adminHttpBasic" because controllers reference it; the credential is a session token.
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
                .description(
                        "Console session token: `SecureOne-Admin-Session <sessionToken>`. Get one from "
                                + "`POST /api/admin/v1/auth/login` (then `/auth/mfa/verify` with an authenticator code). "
                                + "HTTP Basic and sign-in cookies are not accepted on admin APIs.");
    }
}
