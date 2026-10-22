# API documentation (reference, curl, OpenAPI and Swagger UI)

SecureOne documents its HTTP API three ways. They describe the same endpoints:

| Where | Best for |
|-------|----------|
| **[API reference](api-reference/README.md)** (these docs) | Reading section by section. Every endpoint with authentication, fields, responses and a **curl** command. |
| **Swagger UI** (`/swagger-ui/index.html`, or `/docs`) | Trying calls from the browser. |
| **OpenAPI JSON** (`/v3/api-docs/all`) | Generating SDKs, importing into Postman, contract tests. |
| **Application console › Settings › Integration › API reference** | The endpoints one app needs (sign-in, tokens, profile), filled in with its application ID and client ID, each with curl. Shown when *API reference* is on in Platform settings › Feature flags. |

## API reference sections

| # | Section |
|---|---------|
| 0 | [Getting started](api-reference/00-getting-started.md): shell setup, credentials, **admin session token**, errors |
| 1 | [OAuth 2.1 and OpenID Connect](api-reference/01-oauth-oidc.md) |
| 2 | [Public endpoints and first-run setup](api-reference/02-public-and-setup.md) |
| 3 | [Application sign-in and sign-up (native UI)](api-reference/03-application-sign-in.md) |
| 4 | [Account self-service](api-reference/04-account.md) |
| 5 | [Machine-to-machine and SCIM provisioning](api-reference/05-machine-and-scim.md) |
| 6 | [Admin API: sign-in, context and operator profile](api-reference/06-admin-sign-in.md) |
| 7 | [Admin API: tenants](api-reference/07-admin-tenants.md) |
| 8 | [Admin API: users](api-reference/08-admin-users.md) |
| 9 | [Admin API: applications, OAuth clients and change approvals](api-reference/09-admin-applications-and-clients.md) |
| 10 | [Admin API: application console](api-reference/10-admin-application-console.md) |
| 11 | [Admin API: security and governance](api-reference/11-admin-security-and-governance.md) |
| 12 | [Admin API: platform settings and configuration as code](api-reference/12-admin-platform-settings.md) |
| 13 | [Data models](api-reference/13-data-models.md) |

Sections 1 to 13 are generated from the OpenAPI document (`scripts/api-docs/generate_api_reference.py`), so they
match the running code. See [Keeping this reference current](api-reference/README.md#keeping-this-reference-current).

## Turn on Swagger UI and the OpenAPI document

They are **on** when the server runs from source (`application.yml`) and **off** in the production profile, which
the Docker image uses. Turn them on with one setting:

```dotenv
SECUREONE_SWAGGER_ENABLED=true
```

Add it to the container's env file (see [deploy/README.md](../deploy/README.md#31-save-the-containers-settings-once))
and replace the container. Check:

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:9000/v3/api-docs/all   # 200 = on, 404 = off
curl -s http://localhost:9000/api/info                                            # "features":{"swagger":true,…}
```

The documents describe the API only (no data, no secrets), and every call still needs its credential. On an
Internet-facing production server you may prefer to leave them off and use this reference.

| Resource | Path |
|----------|------|
| Swagger UI | `/swagger-ui/index.html`, or `/docs` (redirect) |
| OpenAPI JSON, everything | `/v3/api-docs/all` |
| OpenAPI JSON by area | `/v3/api-docs/public`, `/account`, `/applications`, `/admin-platform`, `/admin-applications`, `/oauth-oidc` |
| Service discovery | `GET /api/info` → `openapi`, `swaggerUi`, `docs` |

Swagger UI opens **All APIs**; the **Select a definition** list switches to one area. **Admin — platform** holds every
admin API except the per-application console, which is in **Admin — per application**.

## Authentication in Swagger UI

| Calls | How |
|-------|-----|
| Public (discovery, sign-in, sign-up, reset) | Nothing. Leave **Authorize** empty. |
| Admin APIs (`/api/admin/v1/…`) | Run `POST /api/admin/v1/auth/login`, then `POST /api/admin/v1/auth/mfa/verify` with an authenticator code. Copy `sessionToken`, click **Authorize**, and under **adminHttpBasic** enter `SecureOne-Admin-Session <sessionToken>`. Passwords alone (HTTP Basic) and console cookies are not accepted. |
| UserInfo, account profile and privacy | Get an access token from `POST /oauth2/token`, then **Authorize › bearerAuth**. |
| Token, revoke, introspect (confidential client) | **Authorize › oauthClientBasic** with the client ID and secret. |

The same steps with curl: [Getting started › Admin API](api-reference/00-getting-started.md#admin-api-get-a-console-session-token).

## Troubleshooting

| Symptom | Cause and fix |
|---------|---------------|
| `/docs` or `/v3/api-docs/all` returns **404** | Swagger is off: set `SECUREONE_SWAGGER_ENABLED=true` (above) and restart. |
| Swagger UI: *No API definition provided* | Open `/docs` or `/swagger-ui/index.html?url=/v3/api-docs/all`. |
| Swagger UI: *Failed to fetch* | Open Swagger on the same host as the API (`localhost` vs `127.0.0.1` matters) and choose the server **Current host** (`/`). Check `GET /api/info` says `UP`. |
| Admin call returns **401** | No token, or it expired (30 minutes idle, 8 hours at most) or was signed out. Sign in again. |
| Admin call returns **403** | The account's role does not allow it (for example a tenant admin calling platform settings). |
| `DELETE /api/admin/v1/oauth-clients/{id}` returns **202** and the client is still there | Expected: deleting a client needs another admin's approval (Access review › Pending approvals). |

## Exporting the spec

```bash
curl -s http://localhost:9000/v3/api-docs/all -o secureone-openapi.json
```

## Configuration

- `apps/auth-server/src/main/resources/application.yml` and `application-prod.yml` → `springdoc.*`,
  `secureone.swagger-enabled`
- Java: `com.secureone.auth.config.openapi.OpenApiConfig` (groups, security schemes),
  `OAuth2OpenApiDocumentation` (protocol endpoints), `SwaggerAccessFilter`
- Endpoint texts: `@Tag`, `@Operation` and `@Parameter` on the controllers

## SecureOne Confluence APIs (admin console)

The documentation you are reading is served by the admin console and is public (no sign-in).

| API | Purpose |
|-----|---------|
| `GET /api/v1/confluence` (auth-server) | Catalog by category, with links to the Confluence UI and content API |
| `GET /admin/api/confluence` | Full catalog JSON |
| `GET /admin/api/confluence/{slug}` | One page: markdown and metadata |
| `GET /admin/api/confluence/assets/{path}` | Files next to the docs (`.drawio`, images) |

UI: `http://localhost:9000/admin/confluence`.
