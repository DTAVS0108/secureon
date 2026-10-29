---
title: Applications and OAuth clients
summary: Products, clients, schemas and isolation
order: 3
audience: public
---

# Applications and OAuth clients

How SecureOne separates **application products** (IAM/console scope) from **OAuth clients** (protocol credentials), provisions per-app database schemas, and registers both from the admin console.

Related: [Data model](../developer/data-model.md), [Database strategy](../developer/database.md), [Installation](../install/installation.md), [API documentation](../api/swagger-and-openapi.md).

---

## Two concepts, one product

| Concept | What it is | Stored in | Enables |
|---------|------------|-----------|---------|
| **Application product** | The IAM boundary for a product (e.g. “Example App”, “Billing portal”) | `platform.application` + optional dedicated schema | Users, roles, permissions, groups, settings, audit in the **application console** |
| **OAuth client** | OIDC/OAuth credentials used to obtain tokens | `platform.oauth_client` | `/oauth2/authorize`, `/oauth2/token`, redirect URIs, PKCE, client secret |

- **1 application → N OAuth clients** (web app, SPA, mobile, M2M can each have their own client).
- OAuth protocol fields were **removed** from `application.config`; they live only in `oauth_client`.

---

## Database layout

```
secureone (PostgreSQL database)
├── platform.*                     ← shared: tenant, application registry, oauth_client, identity, governance, Flyway log
└── {app_slug}.*                   ← per-app IAM (role, permission, user_application, settings, groups, logs)
```

- **Schema name** = `slugify(application.slug)` with hyphens → underscores (e.g. slug `test-web` → schema `test_web`).
- **`application.schema_name`** — `NULL` = legacy mode (IAM tables still in `platform.*`); non-null = isolated schema.
- **`platform.application_schema`** — registry of provisioned schemas (status, Flyway template version, provisioned_at).

### Schema routing (`search_path`)

For requests scoped to an application (`/api/admin/v1/applications/{id}/*`, `/api/v1/applications/{id}/*`, or OAuth flows resolved via `client_id`):

1. `ApplicationSchemaFilter` sets `ApplicationSchemaContext` (thread-local application id).
2. At **transaction begin**, `ApplicationSchemaJpaTransactionManager` runs:
   ```sql
   SET LOCAL search_path TO "{app_schema}", platform;
   ```
   (or `platform` only when `schema_name` is null).

JPA queries for app-scoped entities (`role`, `permission`, `user_application`, etc.) resolve to the correct schema without code changes per table.

---

## Registering from the admin console

**URL:** `/applications` (platform super-admin only)

| Button | When to use |
|--------|-------------|
| **+ Register application & OAuth client** | New product **and** first OAuth client in **one form** (recommended) |
| **+ Add OAuth client** | Attach another client to an **existing** application |

### Combined form (one submit)

1. **Application product** — name, tenant, slug, description, status  
   → creates `platform.application`, provisions `{slug}` schema, seeds default RBAC.
2. **OAuth client** — template (web/SPA/native/M2M), client ID, grants, scopes, redirect URIs, PKCE  
   → creates `platform.oauth_client` linked to the new application.

Confidential clients show the **client secret once** after submit (same as standalone OAuth registration).

**Grant types** accepted (others are rejected with `400`, not silently dropped):

| Grant | Typical client | Notes |
|-------|----------------|-------|
| `authorization_code` (+ PKCE) | web, SPA, native | Hosted or native sign-in |
| `refresh_token` | web, SPA, native, device | Public SPA / device clients refresh with `client_id` only |
| `client_credentials` | M2M | Service-to-service |
| `urn:ietf:params:oauth:grant-type:device_code` (alias `device_code`) | native (TV, console, CLI) | RFC 8628; users approve at `/account/device.html` |
| `urn:ietf:params:oauth:grant-type:token-exchange` | web / M2M | RFC 8693 delegation |

### OAuth-only auto-create

The **Add OAuth client** form can leave “Application product” empty and auto-create an application from the client name — equivalent to the old two-step flow in one OAuth form.

---

## API (platform super-admin)

Base: your SecureOne address · Auth: console session token (`Authorization: SecureOne-Admin-Session <token>`, see below)

### Application products

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/admin/v1/applications` | List products (`schemaName`, `oauthClientCount`) |
| GET | `/api/admin/v1/applications/{id}` | Get product |
| POST | `/api/admin/v1/applications` | Create product + provision schema + seed RBAC |
| PUT | `/api/admin/v1/applications/{id}` | Update metadata/status |
| DELETE | `/api/admin/v1/applications/{id}` | Delete product |
| POST | `/api/admin/v1/applications/{id}/isolate` | Migrate legacy IAM data from `platform` → dedicated schema |

### OAuth clients

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/admin/v1/oauth-clients` | List clients |
| GET | `/api/admin/v1/oauth-clients/{id}` | Get client |
| POST | `/api/admin/v1/oauth-clients` | Create client (`applicationId` or auto-create via `tenantId` + `applicationName`) |
| PUT | `/api/admin/v1/oauth-clients/{id}` | Update client |
| DELETE | `/api/admin/v1/oauth-clients/{id}` | Delete client |
| POST | `/api/admin/v1/oauth-clients/{id}/rotate-secret` | Rotate confidential client secret |

### Example: create product + client via API

```bash
# 0. Console session (break-glass admin in dev; named operators also send an authenticator code
#    to /api/admin/v1/auth/mfa/verify with the returned mfaToken)
TOKEN=$(curl -s -X POST $SECUREONE_URL/api/admin/v1/auth/login   -H "Content-Type: application/json"   -d '{"emailOrUsername":"admin","password":"admin"}' | jq -r .sessionToken)
AUTH="Authorization: SecureOne-Admin-Session $TOKEN"

# 1. Application (provisions schema automatically)
curl -H "$AUTH" -X POST $SECUREONE_URL/api/admin/v1/applications \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"<uuid>","name":"My App","slug":"my-app","status":"active"}'

# 2. OAuth client
curl -H "$AUTH" -X POST $SECUREONE_URL/api/admin/v1/oauth-clients \
  -H "Content-Type: application/json" \
  -d '{"applicationId":"<app-uuid>","clientId":"my-app-web","type":"web","status":"active",
       "grantTypes":["authorization_code","refresh_token"],"scopes":["openid","profile","email"],
       "redirectUris":["http://localhost:5173/callback"],"pkceRequired":true}'
```

---

## Isolating legacy applications

Apps created before schema isolation (or seeded by older migrations) have `schema_name = NULL` and IAM rows in `platform.*`.

**Platform super-admins** can isolate them:

- **UI:** OAuth client detail (`/applications/{clientId}`) → **Isolate application**, or **Settings → Integration → Database schema** in the app console.
- **API:** `POST /api/admin/v1/applications/{id}/isolate`

The operation:

1. Provisions `{slug}` schema (DDL from `application-schema/V1__app_core.sql`).
2. Copies app-scoped rows: `permission`, `role`, joins, `user_application`, `application_setting`, `rbac_group*`, `application_log`.
3. Deletes those rows from `platform.*`.
4. Sets `application.schema_name`.

OAuth clients remain in `platform.oauth_client`. This cannot be undone automatically.

---

## Migrations & provisioning

| Location | Purpose |
|----------|---------|
| `apps/auth-server/src/main/resources/db/migration/postgresql/` | Platform Flyway scripts (`V1__baseline`, `R__z_repair_catalog.sql`) |
| `apps/auth-server/src/main/resources/db/migration/application-schema/V1__app_core.sql` | Template DDL executed inside each new app schema (not a Flyway version on its own) |
| `platform.flyway_schema_history` | Flyway history table |

On auth-server boot, Flyway applies pending platform migrations. **Application schema creation** happens at runtime when you register or isolate an application (`ApplicationSchemaProvisioner`).

### JDBC / JPA config

```yaml
# application.yml (auth-server)
spring.datasource.url: jdbc:postgresql://localhost:5432/secureone?currentSchema=platform
# hibernate.default_schema is NOT set — search_path handles app-scoped tables
```

---

## Application console

After registration, operators manage IAM at:

```
/app/{applicationId}/users
/app/{applicationId}/roles
/app/{applicationId}/permissions
/app/{applicationId}/groups
/app/{applicationId}/settings
```

See [Application RBAC management](application-rbac.md).

---

## New machine / fresh database

If PostgreSQL is reachable and auth-server starts successfully:

1. Flyway applies platform migrations (`V1__baseline.sql` on an empty database).
2. Registering an application **automatically** creates the PostgreSQL schema and IAM tables — no manual SQL.

Requirements: Docker Compose (`postgres`, `redis`), `gradlew bootRun` (auth-server), `npm run dev` (admin-web). See [Installation](../install/installation.md).

---

## Troubleshooting

| Symptom | Likely cause |
|---------|----------------|
| App console shows no roles after create | Auth-server not restarted after schema-routing fix; or `schema_name` set but schema empty |
| `409` on application create | Slug collision for tenant, or orphan schema from failed provision |
| `409` “already isolated” on isolate | `schema_name` already set |
| OAuth works but console IAM empty | Legacy app not isolated and `search_path` not applied — check isolated schema or run isolate |

Verify after create:

```sql
SELECT name, slug, schema_name FROM platform.application;
SELECT schema_name, status FROM platform.application_schema;
SELECT client_id, application_id FROM platform.oauth_client;
```
