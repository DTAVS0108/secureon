package com.secureone.auth.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String PACKAGE = "com.secureone.auth";

    @Bean
    OpenAPI secureOneOpenAPI(
            @Value("${secureone.public-base-url:http://localhost:9000}") String publicBaseUrl,
            @Value("${secureone.admin-web-url:}") String adminWebUrl,
            ObjectProvider<BuildProperties> build) {
        String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
        String console = adminWebUrl.isBlank() ? base + "/admin" : adminWebUrl.replaceAll("/+$", "");
        BuildProperties properties = build.getIfAvailable();
        String version = properties != null ? properties.getVersion() : "development";

        return new OpenAPI()
                .info(new Info()
                        .title("SecureOne API")
                        .version(version)
                        .description(
                                """
                                Identity and access management for multi-tenant applications.

                                **API surfaces**
                                - **Public** — server info, sign-in method catalog, application manifest, sign-up, account recovery
                                - **Account** — self-service for a signed-in user (Bearer access token)
                                - **Admin** — platform and per-application management (console session token)
                                - **OAuth 2.1 / OIDC** — authorization server protocol endpoints

                                **Try admin APIs here:** sign in with `POST /api/admin/v1/auth/login` (then `POST /api/admin/v1/auth/mfa/verify` when the answer asks for a second step), and choose **Authorize** with `SecureOne-Admin-Session <sessionToken>`.

                                **Integrated apps:** use `GET/POST /api/v1/applications/{applicationId}/signup` when Self Registration is enabled.

                                API docs are on by default in development and off in production (`SECUREONE_SWAGGER_ENABLED` overrides).""")
                        .contact(new Contact().name("SecureOne")))
                .externalDocs(new ExternalDocumentation()
                        .description("SecureOne Documentation (in the console)")
                        .url(console + "/documentation"))
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

    /**
     * Default Swagger UI definition: every endpoint, its tags ordered by {@link ApiCategory} and grouped with the
     * {@code x-tagGroups} extension (one group per category).
     */
    @Bean
    GroupedOpenApi allApis() {
        return GroupedOpenApi.builder()
                .group("all")
                .displayName("All APIs")
                .pathsToMatch("/**")
                .packagesToScan(PACKAGE)
                .addOpenApiCustomizer(commonCustomizer())
                .addOpenApiCustomizer(openApi -> ApiCategories.orderTags(openApi, null))
                .build();
    }

    // One Swagger UI definition per category (/v3/api-docs/{id}), in reading order.

    @Bean
    GroupedOpenApi publicApis() {
        return category(ApiCategory.PUBLIC);
    }

    @Bean
    GroupedOpenApi authenticationApis() {
        return category(ApiCategory.AUTHENTICATION);
    }

    @Bean
    GroupedOpenApi oauthOidcApis() {
        return category(ApiCategory.OAUTH_OIDC);
    }

    @Bean
    GroupedOpenApi accountApis() {
        return category(ApiCategory.ACCOUNT);
    }

    @Bean
    GroupedOpenApi machineApis() {
        return category(ApiCategory.MACHINE);
    }

    @Bean
    GroupedOpenApi adminSignInApis() {
        return category(ApiCategory.ADMIN_SIGN_IN);
    }

    @Bean
    GroupedOpenApi adminTenantApis() {
        return category(ApiCategory.ADMIN_TENANTS);
    }

    @Bean
    GroupedOpenApi adminApplicationApis() {
        return category(ApiCategory.ADMIN_APPLICATIONS);
    }

    @Bean
    GroupedOpenApi adminSecurityApis() {
        return category(ApiCategory.ADMIN_SECURITY);
    }

    @Bean
    GroupedOpenApi adminSettingsApis() {
        return category(ApiCategory.ADMIN_SETTINGS);
    }

    /**
     * Swagger UI's "Select a definition" list. springdoc 3 does not fill it from the GroupedOpenApi beans, and it
     * always sorts it by display name, so the numbered names keep the reading order; "All APIs" is the default.
     */
    @Bean
    SmartInitializingSingleton swaggerUiDefinitions(
            ObjectProvider<SwaggerUiConfigProperties> swaggerUi,
            ObjectProvider<SpringDocConfigProperties> springDoc,
            List<GroupedOpenApi> groups) {
        // Neither exists when the API docs are off (production default).
        return () -> swaggerUi.ifAvailable(ui -> springDoc.ifAvailable(properties -> {
            String docs = properties.getApiDocs().getPath();
            Set<AbstractSwaggerUiConfigProperties.SwaggerUrl> urls = new LinkedHashSet<>();
            groups.forEach(group -> urls.add(new AbstractSwaggerUiConfigProperties.SwaggerUrl(
                    group.getGroup(), docs + "/" + group.getGroup(), group.getDisplayName())));
            ui.setUrls(urls);
        }));
    }

    private static GroupedOpenApi category(ApiCategory category) {
        return GroupedOpenApi.builder()
                .group(category.id())
                .displayName(String.format("%02d · %s", category.ordinal() + 1, category.title()))
                .pathsToMatch("/**")
                .packagesToScan(PACKAGE)
                .addOpenApiCustomizer(commonCustomizer())
                .addOpenApiCustomizer(openApi -> ApiCategories.keepOnly(openApi, category))
                .build();
    }

    /** Protocol endpoints (not MVC controllers), shared error responses and the admin session requirement. */
    private static OpenApiCustomizer commonCustomizer() {
        OpenApiCustomizer adminSecurity = adminSecurityCustomizer();
        return openApi -> {
            OAuth2OpenApiDocumentation.apply(openApi);
            applyGlobalErrorResponses(openApi);
            adminSecurity.customise(openApi);
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
                                + "`POST /api/admin/v1/auth/login` (then `/auth/mfa/verify` with an authenticator code when "
                                + "console two-step sign-in is on). "
                                + "HTTP Basic and sign-in cookies are not accepted on admin APIs.");
    }
}
