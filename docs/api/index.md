---
title: API reference
summary: Every endpoint, section by section, with curl
order: 5
audience: public
---

# API reference

Every SecureOne HTTP endpoint, section by section, each with a ready-to-run **curl** command.

- **New here?** Start with [Getting started](getting-started.md): base URL, the four kinds of credentials,
  getting an admin token, errors.
- **Integrating an app?** The first five sections, from [Public endpoints and setup](public-and-setup.md) to
  [Machine-to-machine and SCIM](machine-and-scim.md). An OIDC library usually covers
  [OAuth 2.1, OpenID Connect and SAML](oauth-oidc.md) for you.
- **Automating the console?** The *Admin API* sections. Everything the console does, the admin API
  does too.
- **Trying calls in the browser?** Swagger UI at `/swagger-ui/index.html` (or `/docs`), when
  `SECUREONE_SWAGGER_ENABLED=true` (see [Getting started › Swagger UI](getting-started.md#swagger-ui-and-the-openapi-document)).

## Sections

<!-- sections:start -->
| Section | Endpoints | What it covers |
|---|---|---|
| [Public endpoints and setup](public-and-setup.md) | 8 | Server information, the one-time setup of a new installation, documentation discovery, custom-domain checks and the CAPTCHA settings the sign-in pages use. |
| [Application sign-in and sign-up](application-sign-in.md) | 44 | For apps that draw their own sign-in screens instead of redirecting to the hosted page: application manifest, password and one-time-code sign-in, sign-up, MFA, passkeys, federation and device sign-in. |
| [OAuth 2.1, OpenID Connect and SAML](oauth-oidc.md) | 23 | The authorization server protocol: discovery, authorize, token, revoke, introspect, JWKS, UserInfo, device flow and OAuth extensions. |
| [Account self-service](account.md) | 55 | What a signed-in user can do for themselves: password reset and change, email verification, profile, MFA factors, sessions, email change, connected apps and privacy (export, delete request). |
| [Machine-to-machine and SCIM](machine-and-scim.md) | 16 | Calls made by other systems: service accounts with API keys, and SCIM 2.0 user and group provisioning from an identity provider. |
| [Admin API: sign-in and operator](admin-sign-in.md) | 27 | Getting a console session token (sign-in plus MFA), the admin context (who am I, what can I open) and the signed-in operator's own profile and authenticators. |
| [Admin API: tenants](admin-tenants.md) | 57 | Tenants and everything inside one: the tenant workspace (roster, application access), tenant roles, console roles and access, access requests and the tenant's SCIM token. |
| [Admin API: users](admin-users.md) | 29 | User accounts across tenants: create, edit, suspend, delete, passwords, email verification, MFA reset, unlock and impersonation. |
| [Admin API: applications and OAuth clients](admin-applications-and-clients.md) | 21 | Application products, their OAuth clients, and changes that wait for a second admin (deleting an OAuth client returns 202 with a change request; another admin approves it). |
| [Admin API: application console](admin-application-console.md) | 127 | Everything under /api/admin/v1/applications/{applicationId}/…: the application's users, roles, permissions, groups, settings and logs. |
| [Admin API: security and governance](admin-security-and-governance.md) | 49 | Platform operators, access review, platform roles, sign-in federation providers, service accounts, webhooks, the audit log (search and CSV export), sessions and the console overview. |
| [Admin API: platform settings](admin-platform-settings.md) | 40 | Platform-wide settings (sign-in methods, password, MFA and token policies, email, SMS, appearance, feature flags, attack protection, log retention and streaming) and configuration files (export, preview, apply). |
| [Data models](data-models.md) | 134 | Every request and response type with its fields. |
<!-- sections:end -->

## How each endpoint is described

Every endpoint has the same parts, in this order:

| Part | What it tells you |
|------|-------------------|
| Title and `METHOD /path` | What the call does and where it is. `{name}` in a path is a value you fill in. |
| Description | Extra rules, when the server has them. |
| **Authentication** | Which credential to send: none, an access token, a client secret, an API key, a SCIM token or a console session token. |
| **Parameters** | Path and query values. |
| **Request body** | The JSON (or form) fields, with a link to the full type in [Data models](data-models.md). |
| **Responses** | The success status and its body type. The common errors (400, 401, 403, 404, 409) are the same everywhere and described once in [Getting started › Errors](getting-started.md#errors). |
| `curl` | A command to copy. It uses the shell variables from [Getting started](getting-started.md#set-up-your-shell); replace `{placeholders}` with real values. Example values in request bodies (`Tenant A Shop`, `user@example.com`) are only examples. |

<!-- developer -->
## Keeping this reference current

The section pages are **generated** from the server's OpenAPI document, so they always match the code:

```bash
# On a server running with SECUREONE_SWAGGER_ENABLED=true
curl -s $SECUREONE_URL/v3/api-docs/all -o openapi.json
python scripts/api-docs/generate_api_reference.py openapi.json docs/api-reference
```

Change an endpoint's text in its controller (`@Tag`, `@Operation`, `@Parameter`) and regenerate; do not edit the
generated pages by hand. This page and [Getting started](getting-started.md) are written by hand (the section
table above is filled in by the generator).

Related: [API documentation overview](swagger-and-openapi.md) ·
[Application integration guide](../integrate/integration-guide.md) ·
[User guide](../guide/index.md)
<!-- /developer -->
