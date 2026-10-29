---
title: Swagger UI and OpenAPI
summary: Interactive API docs, OpenAPI files and categories
order: 1
audience: public
---

# Swagger UI and OpenAPI

SecureOne documents its HTTP API three ways. They describe the same endpoints:

| Where | Best for |
|-------|----------|
| **[API reference](index.md)** (these docs) | Reading section by section. Every endpoint with authentication, fields, responses and a **curl** command. |
| **Swagger UI** (`/swagger-ui/index.html`, or `/docs`) | Trying calls from the browser. |
| **OpenAPI JSON** (`/v3/api-docs/all`) | Generating SDKs, importing into Postman, contract tests. |
| **Application console › Settings › Integration › API reference** | The endpoints one app needs (sign-in, tokens, profile), filled in with its application ID and client ID, each with curl. Shown when *API reference* is on in Platform settings › Feature flags. |

## API reference sections

| Section |
|---------|
| [Getting started](getting-started.md): shell setup, credentials, **admin session token**, errors |
| [Public endpoints and first-run setup](public-and-setup.md) |
| [Application sign-in and sign-up (native UI)](application-sign-in.md) |
| [OAuth 2.1 and OpenID Connect](oauth-oidc.md) |
| [Account self-service](account.md) |
| [Machine-to-machine and SCIM provisioning](machine-and-scim.md) |
| [Admin API: sign-in, context and operator profile](admin-sign-in.md) |
| [Admin API: tenants](admin-tenants.md) |
| [Admin API: users](admin-users.md) |
| [Admin API: applications, OAuth clients and change approvals](admin-applications-and-clients.md) |
| [Admin API: application console](admin-application-console.md) |
| [Admin API: security and governance](admin-security-and-governance.md) |
| [Admin API: platform settings and configuration as code](admin-platform-settings.md) |
| [Data models](data-models.md) |

The section pages are generated from the OpenAPI document (`scripts/api-docs/generate_api_reference.py`), so they
match the running code. See [Keeping this reference current](index.md#keeping-this-reference-current).

## Turn on Swagger UI and the OpenAPI document

They follow `SECUREONE_ENV`: **on** in development (from source, or the image with `SECUREONE_ENV=development`) and
**off** in production, which is the image's default. Turn them on in production with one setting:

```dotenv
SECUREONE_SWAGGER_ENABLED=true
```

Add it to the container's env file (see [deploy/README.md](../../deploy/README.md#31-save-the-containers-settings-once))
and replace the container. Check:

```bash
curl -s -o /dev/null -w "%{http_code}\n" $SECUREONE_URL/v3/api-docs/all   # 200 = on, 404 = off
curl -s $SECUREONE_URL/api/info                                            # "features":{"swagger":true,…}
```

The documents describe the API only (no data, no secrets), and every call still needs its credential. On an
Internet-facing production server you may prefer to leave them off and use this reference.

| Resource | Path |
|----------|------|
| Swagger UI | `/swagger-ui/index.html`, or `/docs` (redirect) |
| OpenAPI JSON, everything | `/v3/api-docs/all` |
| OpenAPI JSON by category | `/v3/api-docs/{definition}`, see below |
| Service discovery | `GET /api/info` → `openapi`, `swaggerUi`, `docs` |

### Categories

Swagger UI opens **All APIs**; **Select a definition** (top right) switches to one category, numbered in reading
order:

| # | Definition (`/v3/api-docs/…`) | What it holds | Who calls it |
|---|-------------------------------|---------------|--------------|
| 1 | **Public** (`public`) | Server info, first-run setup, documentation discovery, domain checks, CAPTCHA settings | Anyone, no credential |
| 2 | **Authentication** (`auth`) | Application manifest, sign-in, sign-up, one-time codes, MFA, passkeys, external providers, device sign-in | Apps with their own sign-in screens |
| 3 | **OAuth 2.1 / OIDC and SAML** (`oauth-oidc`) | Discovery, authorize, token, revoke, introspect, JWKS, UserInfo, consent, dynamic client registration, SAML IdP | OIDC / SAML libraries |
| 4 | **Account** (`account`) | Profile, password, email, MFA, sessions, connected apps, privacy | A signed-in user (access token) |
| 5 | **Machine-to-machine and SCIM** (`machine`) | API keys for services, SCIM 2.0 provisioning | Other systems |
| 6 | **Admin — sign-in and operator** (`admin-sign-in`) | Console sign-in, context, operator profile and MFA | Console / automation |
| 7 | **Admin — tenants and users** (`admin-tenants`) | Tenants, console roles and access, SCIM, domains, users | Operators (session token) |
| 8 | **Admin — applications and clients** (`admin-applications`) | Applications, OAuth clients, approvals, each application's console | Operators (session token) |
| 9 | **Admin — security and governance** (`admin-security`) | Overview, platform operators, access review, identity providers, service accounts, webhooks, audit, sessions | Operators (session token) |
| 10 | **Admin — platform settings** (`admin-settings`) | Platform settings, attack protection, log streaming, configuration as code, signing keys | Platform operators |

**All APIs** (`all`, the default) holds everything, its sections in the same order. The categories are defined once,
in `ApiCategory` (auth-server); a unit test fails when a controller uses a tag that no category lists.

The [API reference](index.md) follows the same order: one page per category for the first five, and the admin
categories split over several pages.

## Authentication in Swagger UI

| Calls | How |
|-------|-----|
| Public (discovery, sign-in, sign-up, reset) | Nothing. Leave **Authorize** empty. |
| Admin APIs (`/api/admin/v1/…`) | Run `POST /api/admin/v1/auth/login`. It returns `sessionToken` at once, or `mfaToken` when console two-step sign-in is on (default in production): then run `POST /api/admin/v1/auth/mfa/verify` with an authenticator code. Copy `sessionToken`, click **Authorize**, and under **adminHttpBasic** enter `SecureOne-Admin-Session <sessionToken>`. Passwords alone (HTTP Basic) and console cookies are not accepted. |
| UserInfo, account profile and privacy | Get an access token from `POST /oauth2/token`, then **Authorize › bearerAuth**. |
| Token, revoke, introspect (confidential client) | **Authorize › oauthClientBasic** with the client ID and secret. |

The same steps with curl: [Getting started › Admin API](getting-started.md#admin-api-get-a-console-session-token).

## Troubleshooting

| Symptom | Cause and fix |
|---------|---------------|
| `/docs` or `/v3/api-docs/all` returns **404** | Swagger is off (production): set `SECUREONE_SWAGGER_ENABLED=true` (above) and restart. |
| Swagger UI: *No API definition provided* | Open `/docs` or `/swagger-ui/index.html?url=/v3/api-docs/all`. |
| Swagger UI: *Failed to fetch* | Open Swagger on the same host as the API (`localhost` vs `127.0.0.1` matters) and choose the server **Current host** (`/`). Check `GET /api/info` says `UP`. |
| Admin call returns **401** | No token, or it expired (30 minutes idle, 8 hours at most) or was signed out. Sign in again. |
| Admin call returns **403** | The account's role does not allow it (for example a tenant admin calling platform settings). |
| `DELETE /api/admin/v1/oauth-clients/{id}` returns **202** and the client is still there | Expected: deleting a client needs another admin's approval (Access review › Pending approvals). |

## Exporting the spec

```bash
curl -s $SECUREONE_URL/v3/api-docs/all -o secureone-openapi.json
```

## Configuration

- `apps/auth-server/src/main/resources/application.yml` → `springdoc.*`, `secureone.swagger-enabled`
  (default from `SecureOneEnvironmentDefaults`: on unless `SECUREONE_ENV=production`)
- The document's title, version (the build version, e.g. `0.1.3`) and link to SecureOne Documentation:
  `OpenApiConfig`
- Java: `com.secureone.auth.config.openapi.ApiCategory` (the categories and their tags), `OpenApiConfig`
  (definitions, security schemes),
  `OAuth2OpenApiDocumentation` (protocol endpoints), `SwaggerAccessFilter`
- Endpoint texts: `@Tag`, `@Operation` and `@Parameter` on the controllers

## SecureOne Documentation APIs (admin console)

The documentation you are reading is served by the admin console and is public (no sign-in).

| API | Purpose |
|-----|---------|
| `GET /api/v1/documentation` (auth-server) | Catalog by category, with links to the Documentation UI and content API |
| `GET /admin/api/documentation` | Full catalog JSON |
| `GET /admin/api/documentation/{slug}` | One page: markdown and metadata |
| `GET /admin/api/documentation/assets/{path}` | Files next to the docs (`.drawio`, images) |

UI: `/admin/documentation`.
