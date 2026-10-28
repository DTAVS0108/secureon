package com.secureone.auth.config.openapi;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The API by audience, in the order a reader meets it. Each category is one Swagger UI definition
 * ({@code /v3/api-docs/{id}}) and one tag group in "All APIs"; its tags are the {@code @Tag} names of the
 * controllers, in display order. Every tag must belong to exactly one category (ApiCategoryTest).
 */
public enum ApiCategory {

    PUBLIC("public", "Public",
            "No authentication: server information, first-run setup, documentation discovery, domain checks.",
            "System", "First-run setup", "Documentation", "Custom domains", "Bot protection"),
    AUTHENTICATION("auth", "Authentication",
            "Sign-in and sign-up for apps that draw their own screens: application manifest, password and one-time"
                    + " codes, MFA, passkeys, external identity providers, device sign-in.",
            "Application public API", "Public auth", "Public auth — federation", "Public auth — device sign-in",
            "Public auth — impersonation"),
    OAUTH_OIDC("oauth-oidc", "OAuth 2.1 / OIDC and SAML",
            "Standard protocols: discovery, authorize, token, revoke, introspect, JWKS, UserInfo, consent, dynamic"
                    + " client registration, and the SAML identity provider.",
            "OAuth 2.1 / OIDC", "OAuth 2.1 / OIDC — consent", "Client registration", "SAML identity provider"),
    ACCOUNT("account", "Account",
            "Self-service for a signed-in user (Bearer access token): profile, password, email, MFA, sessions,"
                    + " connected apps, privacy.",
            "Account", "Account profile", "Account password change", "Account email change", "Account MFA",
            "Account linked sign-in", "Account sessions", "Account connected apps", "Account privacy",
            "Account privacy (session)"),
    MACHINE("machine", "Machine-to-machine and SCIM",
            "Service accounts with API keys, and SCIM 2.0 provisioning from a company directory.",
            "Machine-to-machine (API keys)", "SCIM 2.0 provisioning"),
    ADMIN_SIGN_IN("admin-sign-in", "Admin — sign-in and operator",
            "Console sign-in (session token), the operator's context, profile and MFA.",
            "Admin — auth", "Admin — context", "Admin — operator profile", "Admin — operator MFA"),
    ADMIN_TENANTS("admin-tenants", "Admin — tenants and users",
            "Tenants, their console roles and access, SCIM, domains, and users.",
            "Admin — tenants", "Admin — tenant workspace", "Admin — tenant RBAC", "Admin — tenant console roles",
            "Admin — console access", "Admin — console capabilities", "Admin — access requests",
            "Admin — tenant SCIM", "Admin — SCIM", "Admin — deletion requests", "Admin — tenant issuer and domains",
            "Admin — users"),
    ADMIN_APPLICATIONS("admin-applications", "Admin — applications and clients",
            "Applications, OAuth clients, change approvals, and each application's console: roles, groups, logs,"
                    + " webhooks, APIs, SAML apps, client registration.",
            "Admin — applications", "Admin — OAuth clients", "Admin — change approvals", "Admin — application scope",
            "Admin — application RBAC", "Admin — application logs", "Admin — application webhooks",
            "Admin — application APIs", "Admin — application SAML apps", "Admin — application client registration"),
    ADMIN_SECURITY("admin-security", "Admin — security and governance",
            "Overview, platform operators, access review, identity providers, service accounts, webhooks, audit,"
                    + " sessions.",
            "Admin — overview", "Admin — platform operators", "Admin — access review", "Admin — platform RBAC",
            "Admin — federation", "Admin — service accounts", "Admin — webhooks", "Admin — audit", "Admin — sessions"),
    ADMIN_SETTINGS("admin-settings", "Admin — platform settings",
            "Platform settings, attack protection, log streaming, configuration as code, signing keys.",
            "Admin — platform settings", "Admin — attack protection", "Admin — log streams",
            "Admin — configuration as code", "Admin — signing keys");

    private final String id;
    private final String title;
    private final String description;
    private final List<String> tags;

    ApiCategory(String id, String title, String description, String... tags) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.tags = List.of(tags);
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public List<String> tags() {
        return tags;
    }

    public static Optional<ApiCategory> of(String tag) {
        return Arrays.stream(values()).filter(c -> c.tags.contains(tag)).findFirst();
    }

    /** Position of a tag in reading order (categories, then their tags); unknown tags last. */
    public static int order(String tag) {
        int offset = 0;
        for (ApiCategory category : values()) {
            int index = category.tags.indexOf(tag);
            if (index >= 0) {
                return offset + index;
            }
            offset += category.tags.size();
        }
        return Integer.MAX_VALUE;
    }
}
