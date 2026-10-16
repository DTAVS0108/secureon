# API documentation (OpenAPI & Swagger UI)

The **auth-server** publishes interactive API documentation via [springdoc-openapi](https://springdoc.org/) and **Swagger UI**.

## URLs (default `http://localhost:9000`)

| Resource | Path |
|----------|------|
| Swagger UI | `/swagger-ui/index.html` or `/docs` (redirect) |
| OpenAPI JSON (all) | `/v3/api-docs/all` |
| OpenAPI JSON (default) | `/v3/api-docs` |
| Service discovery | `GET /api/info` → `swaggerUi`, `openapi`, `docs` |

## API groups

Swagger UI opens **All APIs** by default (every endpoint). Use the **Select a definition** dropdown to filter:

> If you only see two endpoints, the UI is on **Public — auth & info**. Switch to **All APIs**.

| Group | Contents |
|-------|----------|
| **all** | Every REST endpoint plus OAuth/OIDC protocol paths |
| **public** | `GET /api/info`, `GET /api/v1/auth/methods` |
| **account** | Password reset, email verification (link + OTP), magic link (session on verify) |
| **applications** | Public manifest, sign-up, OTP login, session login |
| **admin-platform** | Tenants, platform settings, audit, sessions, platform RBAC |
| **admin-applications** | Application products, OAuth clients, per-application users/settings/RBAC, **isolate** |

### Application & OAuth admin endpoints (super-admin)

Documented in [15 — Applications & OAuth clients](15-applications-and-oauth-clients.md):

- `GET/POST /api/admin/v1/applications` — application products (`schemaName`, `oauthClientCount`)
- `POST /api/admin/v1/applications/{id}/isolate` — migrate legacy IAM to dedicated schema
- `GET/POST /api/admin/v1/oauth-clients` — OAuth client registry (separate from application product)

Per-application console APIs: `/api/admin/v1/applications/{applicationId}/…` (users, roles, permissions, groups, settings).
| **oauth-oidc** | Authorization server: authorize, token, revoke, introspect, JWKS, OIDC discovery, UserInfo |

## Authentication in Swagger UI

### Admin APIs

1. Click **Authorize**.
2. Choose **adminHttpBasic** and enter platform credentials (dev default: `admin` / `admin`), **or**
3. Open `/login.html` in the same browser, sign in, then use **Try it out** (session cookie).

Tenant-scoped admins use `tenantSlug:email` as the Basic username (see login form).

### OAuth token endpoint

Use **oauthClientBasic** with `demo-client` / `demo-secret` (dev), or send `client_id` / `client_secret` in the form body.

### Bearer-protected OIDC UserInfo

Obtain an access token from `POST /oauth2/token`, then **Authorize** → **bearerAuth**.

## Integrated applications (e.g. e-commerce)

Public flows do not require Swagger authorization:

- `GET /api/v1/applications/{applicationId}` — client manifest (auth methods, feature flags, password policy, **verification** policy, signup/account endpoints)
- `GET/POST /api/v1/applications/{applicationId}/signup` — self-registration when **Self Registration** is enabled
- `POST /api/v1/applications/{applicationId}/auth/otp/request` + `…/auth/otp/verify` — email/SMS OTP login
- `POST /api/v1/applications/{applicationId}/account/verify/otp/confirm` — signup verification (OTP mode)
- `POST /api/v1/applications/{applicationId}/account/password/forgot` — password reset link or OTP
- `POST /api/v1/applications/{applicationId}/account/password/reset/otp` — complete recovery with OTP

See [19 — Verification & OTP](19-verification-and-otp.md) and [Authentication UI integration](13-auth-ui-integration.md).

## Privacy & GDPR (account)

Session or bearer token required for export/deletion:

| Endpoint | Auth | Purpose |
|----------|------|---------|
| `GET /api/v1/account/privacy/export` | Bearer | JSON export of user data |
| `POST /api/v1/account/privacy/delete-request` | Bearer | Request account deletion |
| `GET /api/v1/account/privacy/session/export` | Session cookie | Same export for logged-in browser users |
| `POST /api/v1/account/privacy/session/delete-request` | Session cookie | Deletion request via session |

Hosted UI: `/account/privacy.html` (linked from `/account/security.html`). See [13 — Auth UI integration](13-auth-ui-integration.md#hosted-account-pages).

## Machine-to-machine (API keys)

Service account API keys authenticate to `/api/v1/m2m/**` via `Authorization: Bearer {api_key}` or `X-Api-Key` header:

| Endpoint | Purpose |
|----------|---------|
| `GET /api/v1/m2m/health` | Liveness check for the M2M route |
| `GET /api/v1/m2m/whoami` | Service account id, tenant, apiKeyId, scopes |
| `GET /api/v1/m2m/tenant` | Tenant metadata for the key's service account |

Per-key rate limit: 120 requests/minute (Redis). Keys and scopes are created in **Application → Service accounts** (admin-web).

## OAuth extensions

| Endpoint | Purpose |
|----------|---------|
| `POST /oauth2/par` | Store authorization params; returns `request_uri` (single-use, Redis TTL) |
| `GET /oauth2/authorize?request_uri=…` | Standard authorize with merged pushed params |
| `POST /oauth2/token` (`grant_type=urn:ietf:params:oauth:grant-type:token-exchange`) | RFC 8693 token exchange — `subject_token`, `requested_token_type`, optional `scope` |
| `POST /oauth2/backchannel-logout` | Back-channel logout notification (ack stub) |
| `POST /oauth2/device_authorization` | RFC 8628 device authorization: `device_code`, `user_code`, `verification_uri(_complete)`, `expires_in`, `interval` |
| `POST /oauth2/token` (`grant_type=urn:ietf:params:oauth:grant-type:device_code`) | Device polling: `authorization_pending`, `slow_down`, `access_denied`, `expired_token`, or tokens |
| `GET/POST /oauth2/device_verification` | User approval of a device code (hosted page `/account/device.html` drives it; explicit Allow/Deny) |
| `POST /api/v1/auth/device/lookup` | Device page: `{ userCode }` → app name and scopes of a pending code (rate-limited) |
| `GET /.well-known/oauth-protected-resource` | RFC 9728 metadata for SecureOne's bearer APIs |

## Federation (OIDC / SAML 2.0)

| Endpoint | Purpose |
|----------|---------|
| `GET /api/v1/auth/federation/providers?applicationId=…` | Public list of enabled providers (`alias`, `type`, `displayName`, `startUrl`) |
| `GET /api/v1/auth/federation/{alias}/start?applicationId=…&continue=…` | Start sign-in (OAuth redirect, or SAML AuthnRequest via Redirect / auto-POST) |
| `GET /api/v1/auth/federation/{alias}/callback` | OAuth/OIDC callback |
| `GET /api/v1/auth/federation/saml/{alias}/metadata?applicationId=…` | SAML SP metadata (XML) |
| `POST /api/v1/auth/federation/saml/acs/{providerId}` | SAML assertion consumer (IdP posts `SAMLResponse`, `RelayState`) |
| `GET/POST /api/v1/auth/federation/saml/slo/{providerId}` | SAML single logout (LogoutRequest / LogoutResponse) |
| `GET /api/v1/auth/federation/saml/logout` | End the session and, for a SAML session, sign out at the IdP |
| `POST /api/admin/v1/applications/{id}/federation/providers` · `POST /api/admin/v1/tenants/{id}/federation/providers` | Operator: create providers (type `GOOGLE`, `GITHUB`, `OIDC`, `SAML`); SAML responses include `serviceProvider` (entity ID, ACS, SLO, metadata URLs) |

Use **oauthClientBasic** in Swagger for PAR and token exchange when testing with `demo-client` / `demo-secret`.

## Swagger UI: "No API definition provided"

Usually **`disable-swagger-default-url: true`** without a default spec URL. Fix: use **`http://localhost:9000/docs`** or **`/swagger-ui/index.html?url=/v3/api-docs/all`**, and ensure `springdoc.swagger-ui.url` is set to `/v3/api-docs/all` in `application.yml`. Restart auth-server after config changes.

## Swagger UI: "Failed to fetch"

This is almost always a **browser / URL** issue, not a broken signup API.

1. Confirm auth-server is running: `GET http://localhost:9000/api/info` → `"status":"UP"`.
2. Open Swagger from **http://localhost:9000/docs** (same host you use for the API).
3. In **Servers**, select **Current host (use this in Swagger UI)** (`/`) — not a mismatched `localhost` vs `127.0.0.1` URL.
4. For sign-up, leave **Authorize** empty (public endpoint).
5. If it still fails, use curl or PowerShell (see below) — the API does not require CORS from the command line.

## Exporting the spec

```bash
curl -s http://localhost:9000/v3/api-docs/all -o secureone-openapi.json
```

Use the JSON for SDK generation, Postman import, or CI contract tests.

## Configuration

`apps/auth-server/src/main/resources/application.yml` → `springdoc.*`

Java configuration: `com.secureone.auth.config.openapi.OpenApiConfig`, `OAuth2OpenApiDocumentation`.

## SecureOne Confluence APIs (admin-web)

Platform documentation is served by **admin-web**, not auth-server. Auth-server exposes public **discovery** only.

| API | Host | Auth | Purpose |
|-----|------|------|---------|
| `GET /api/v1/confluence` | auth-server `:9000` | Public | Catalog by category + links to Confluence UI and content API |
| `GET /admin/api/confluence` | admin console (`/admin`) | Session cookie | Full catalog JSON (`categories` + flat `section` for compatibility) |
| `GET /api/confluence/{slug}` | admin-web | Session cookie | Page markdown + metadata |
| `GET /api/confluence/assets/{path}` | admin-web | Session cookie | Download docs assets (`.drawio`, images) |

UI: http://localhost:9000/admin/confluence — standalone docs browser; draw.io diagrams embed inline on doc pages. See [Documentation hub](../docs/README.md#secureone-confluence-in-app-ui).
