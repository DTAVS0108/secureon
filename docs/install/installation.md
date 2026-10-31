---
title: Installation
summary: Run SecureOne locally for development and evaluation
order: 1
audience: internal
---

# Installation

Local development setup for **auth-server** and **admin-web**.

> **Full operations guide** (one-command start, Flyway migrations, startup bootstrap, scheduled jobs): [Environment & operations](environment-and-operations.md).

## Prerequisites

| Tool | Version | For |
|---|---|---|
| JDK | 21+ (25 tested) | auth-server |
| Gradle | wrapper in `apps/auth-server` | Backend build |
| Node.js | 20+ LTS | admin-web |
| Docker + Compose | latest | Postgres, Redis, MailHog |
| PostgreSQL | 16+ | System of record |
| Redis | 7+ | Sessions, cache |

## 1. Clone & infrastructure

**One-command start (Windows):** `.\scripts\start-all.ps1` — see [Environment & operations](environment-and-operations.md).

```bash
git clone <repo-url> secureone
cd secureone
docker compose -f deploy/docker-compose.yml up -d
```

Starts **PostgreSQL** (`5432`), **Redis** (`6379`), **MailHog** (SMTP `1025`, UI `http://localhost:8025`).

## 2. Configure auth-server

Environment variables (or `apps/auth-server` defaults in `application.yml`):

```dotenv
SECUREONE_DB_URL=jdbc:postgresql://localhost:5432/secureone?currentSchema=platform
SECUREONE_DB_USERNAME=secureone
SECUREONE_DB_PASSWORD=secureone
SECUREONE_REDIS_URL=redis://localhost:6379
SECUREONE_ISSUER_URL=http://localhost:9000
SECUREONE_DEV_USER=admin
SECUREONE_DEV_PASSWORD=admin
```

> Never commit production secrets. See [Security](../architecture/security.md).

## 3. Start auth-server

```bash
cd apps/auth-server
./gradlew bootRun          # Windows: .\gradlew bootRun
```

- Listens on **http://localhost:9000**
- **Flyway** applies `db/migration/postgresql` on boot (`V1__baseline.sql` on an empty database)
- API docs: **http://localhost:9000/docs**
- Health: `GET /api/info`

## 4. Start admin-web

```bash
cd apps/admin-web
npm install
npm run dev
```

Open **http://localhost:9000/admin** (the auth-server serves the admin-web dev server there; hot reload also at http://localhost:3001/admin). Sign in as platform super-admin: **`admin` / `admin`**.

Set `AUTH_SERVER_URL=http://localhost:9000` in `.env.local` if the default differs.

## 5. Register your first application

1. Go to **/applications**
2. Click **+ Register application & OAuth client**
3. Fill application (name, tenant, slug) and OAuth client (template, redirect URIs)
4. Copy the **client secret** if shown (confidential clients only)

This provisions a **PostgreSQL schema** for the app and seeds default RBAC. See [Applications & OAuth clients](../integrate/applications-and-oauth-clients.md).

## 6. End-to-end operator test

1. Sign in as platform admin (`admin` / `admin`, no tenant slug).
2. **Tenants** → create a tenant (note slug, e.g. `myorg`).
3. **Applications** → **Register application & OAuth client** for that tenant.
4. **Tenants** → open tenant → **+ Add user** (Active).
5. **Grant access** → Tenant Super Admin (or app-scoped role).
6. **Applications** → **Application console →** Users → set password.
7. Sign out → sign in with tenant slug `myorg`, user email, password.

## 7. Database maintenance

| Situation | Command |
|-----------|---------|
| Empty database / start fresh | `.\scripts\reset-database.ps1` |
| Truncated catalog tables only | Restart auth-server (the startup bootstrap re-seeds the catalog) |
| Repair after partial migration | Restart auth-server; check `platform.flyway_schema_history` |

**Platform super admin** (dev): the break-glass account `admin` / `admin` (console sign-in page → **Emergency access**; not stored in Postgres). Add named platform operators under **Access review** for daily use. See [Roles and access](../integrate/roles-and-access.md).

### Legacy application isolation

If an app has `schema_name = NULL` (seeded before schema isolation), platform super-admins can run **Isolate application** from the OAuth client detail page or `POST /api/admin/v1/applications/{id}/isolate`.

## 8. Email & notifications (dev)

MailHog catches outbound mail. Configure in **Settings → Notifications**, then test flows (verify email, password reset). Links log to auth-server console if SMTP is down (`SECUREONE_MAIL_LOG_WHEN_UNAVAILABLE`).

## 9. Admin console layout

| Area | Who | URL |
|------|-----|-----|
| **Application console** | Super admin + app operators | `/app/{applicationId}/users` |
| **OAuth client registry** | Platform super-admin | `/applications` |
| **Platform settings** | Super-admin | `/settings` |
| **Tenants** | Platform / tenant super-admin | `/tenants` |
| **SecureOne Documentation** | All signed-in operators | `/documentation` (sidebar opens **new tab**; standalone docs UI) |

## 10. Verify

| Check | URL |
|-------|-----|
| Discovery | `GET http://localhost:9000/.well-known/openid-configuration` |
| Documentation discovery | `GET http://localhost:9000/api/v1/documentation` |
| SecureOne Documentation UI | http://localhost:9000/admin/documentation |
| JWKS | `GET http://localhost:9000/oauth2/jwks` |
| Admin UI | http://localhost:9000/admin |
| MailHog | `http://localhost:8025` |
| App schema (SQL) | `SELECT name, schema_name FROM platform.application;` |

## Production (summary)

Docker/Kubernetes, managed Postgres + Redis, TLS, KMS for secrets. See [Architecture](../architecture/architecture.md) and [Security](../architecture/security.md).

## Configuration reference

| Variable | Purpose |
|---|---|
| `SECUREONE_DB_URL` | JDBC URL (`currentSchema=platform`) |
| `SECUREONE_DB_USERNAME` / `_PASSWORD` | DB credentials |
| `SECUREONE_REDIS_URL` | Redis |
| `SECUREONE_ISSUER_URL` | OIDC issuer |
| `SECUREONE_DEV_USER` / `_PASSWORD` | Admin API Basic auth |
| `SECUREONE_ADMIN_WEB_URL` | Console URL for Documentation links in `/api/info` (default `<SECUREONE_PUBLIC_BASE_URL>/admin`) |
| `SECUREONE_CONSOLE_UPSTREAM_URL` | Where the auth-server forwards `/admin` in development (`http://localhost:3001`; set by `start-all.ps1`) |
| `SECUREONE_PUBLIC_BASE_URL` | Links in emails |
| `SECUREONE_SMTP_*` | Outbound email |
