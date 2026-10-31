---
title: Operator getting started
summary: What SecureOne is, its parts, and your first hour running it as an operator
order: 3
audience: public
---

# Operator getting started

**Audience:** You are new to SecureOne — a platform owner, tenant admin, or DevOps engineer who needs to **install**, **understand**, and **run** the system day to day.

**Not this guide?**

- Building a product frontend (e.g. an online store) and need signup/login APIs → **[Application integration guide](../integrate/integration-guide.md)**
- Installing SecureOne from the image on your own servers → **[Deployment](../install/deployment.md)**

---

## What is SecureOne?

SecureOne is a **central identity platform** (an identity provider you host yourself). Your applications do **not** store passwords or issue their own login tokens. Instead they delegate to SecureOne:

| SecureOne provides | Your apps get |
|--------------------|---------------|
| User accounts per tenant | One login across many products |
| OAuth 2.1 / OpenID Connect | Standard `access_token`, `id_token`, refresh tokens |
| Hosted login pages (optional) | Ready-made signup, login, password reset |
| Admin console | Manage tenants, users, roles, OAuth clients |
| MFA, audit, sessions | Security without rebuilding it in every app |

### Main components

```mermaid
flowchart LR
    operator(["You<br/>(operators)"])
    users(["End users"])
    apps(["Your applications"])

    subgraph container["SecureOne container · port 9000"]
        direction TB
        console["<b>Admin console</b><br/>/admin<br/>tenants, users, apps, settings<br/>+ Documentation"]
        auth["<b>Sign-in and APIs</b><br/>OAuth 2.1 · OpenID Connect · SAML · SCIM<br/>hosted sign-in pages"]
        console --> auth
    end

    postgres[("<b>PostgreSQL</b><br/>tenants, users, settings,<br/>audit log")]
    redis[("<b>Redis</b><br/>sessions, codes,<br/>rate limits")]

    operator -- "manage" --> console
    users -- "sign in" --> auth
    apps -- "tokens, APIs" --> auth
    auth --> postgres
    auth --> redis
```

The console and the auth server run in **one container** and share port 9000: the console is the `/admin` path.
PostgreSQL holds everything that must last; Redis holds short-lived state that several SecureOne instances share.

<!-- developer -->
| Component | URL (dev) | Who uses it |
|-----------|-----------|-------------|
| **admin-web** | http://localhost:9000/admin | Platform admins, tenant admins, app operators |
| **auth-server** | http://localhost:9000 | End-user login, OAuth, public account APIs |
| **Swagger / OpenAPI** | http://localhost:9000/docs | API exploration |
| **SecureOne Documentation** | http://localhost:9000/admin/documentation | In-app documentation browser |
| **MailHog** (dev email) | http://localhost:8025 | Catches verification/reset emails |
<!-- /developer -->

---

## Key concepts (5-minute glossary)

| Term | Meaning |
|------|---------|
| **Tenant** | An organization (e.g. `tenant-a`, `exampleapp`). Users belong to a tenant. |
| **Application (product)** | A product registered in SecureOne (e.g. “Example App Web”, “Billing Portal”). Has its own roles, permissions, and database schema. |
| **OAuth client** | Protocol credentials (`client_id`, redirect URIs) used to obtain tokens. One application can have several clients (web, mobile, M2M). |
| **Platform super-admin** | Runs the whole deployment and signs in on the **Platform operator** tab. |
| **Tenant admin** | Manages users and apps inside one tenant. Login: `tenantSlug:email`. |
| **End user** | Someone who signs into your product (e.g. a Example App customer). |
| **Hosted UI** | SecureOne’s built-in pages (`login.html`, `signup.html`, …) on auth-server. |
| **Native / custom UI** | Your own frontend calls SecureOne APIs directly. |

More detail: [Overview](overview.md), [Applications & OAuth clients](../integrate/applications-and-oauth-clients.md).

---

<!-- developer -->
## First-time setup (local development)

### Prerequisites

| Tool | Version |
|------|---------|
| JDK | 21+ |
| Node.js | 20+ LTS |
| Docker + Compose | Latest |

### Step 1 — Start infrastructure

From the repo root:

```powershell
docker compose -f deploy/docker-compose.yml up -d
```

This starts **PostgreSQL** (`5432`), **Redis** (`6379`), and **MailHog** (SMTP `1025`, UI `8025`).

**Windows shortcut:** `.\scripts\start-all.ps1` also starts auth-server and admin-web. See [Environment & operations](../install/environment-and-operations.md).

### Step 2 — Start auth-server

```powershell
cd apps/auth-server
.\gradlew.bat bootRun
```

- Listens on **http://localhost:9000**
- **Flyway** runs database migrations automatically on boot
- Verify: `GET http://localhost:9000/api/info` → `"status":"UP"`

### Step 3 — Start admin-web

```powershell
cd apps/admin-web
npm install
npm run dev
```

Open **http://localhost:9000/admin**. In local development you can sign in with the emergency account **`admin` / `admin`** (Emergency access). On a new installation without any platform operator, the sign-in page links to **first-run setup** (`/admin/setup`): enter the setup code from the auth-server log and create your named operator account; see [First-run setup](../install/deployment.md#1-first-run-setup-create-the-first-operator).

### Step 4 — Environment variables (reference)

| Variable | Default (dev) | Purpose |
|----------|---------------|---------|
| `SECUREONE_DB_URL` | `jdbc:postgresql://localhost:5432/secureone?currentSchema=platform` | Database |
| `SECUREONE_REDIS_URL` | `redis://localhost:6379` | Sessions, OTP, rate limits |
| `SECUREONE_ISSUER_URL` | `http://localhost:9000` | OIDC issuer in tokens |
| `SECUREONE_PUBLIC_BASE_URL` | `http://localhost:9000` | Links in emails |
| `SECUREONE_DEV_USER` / `_PASSWORD` | `admin` / `admin` | Platform admin API auth |

Full list: [Installation](../install/installation.md), [Environment & operations](../install/environment-and-operations.md).
<!-- /developer -->

---

## Your first hour as operator

Follow this sequence once after install. It mirrors a real onboarding.

### 1. Create a tenant

1. Admin → **Tenants** → **+ Add tenant**
2. Name: `Example App` (example), slug: `exampleapp`
3. Note the **tenant slug** — users sign in as `exampleapp:user@example.com` when using hosted login

### 2. Register an application + OAuth client

1. Admin → **Applications** → **+ Register application & OAuth client**
2. **Application:** tenant = Example App, name = `Example App Web`, slug = `exampleapp-web`
3. **OAuth client:** type = SPA or Web, client ID = `exampleapp-web`, redirect URI = `http://localhost:5173/callback`, PKCE enabled
4. **Copy** the application UUID and client secret (if confidential)

SecureOne provisions a dedicated PostgreSQL schema (`exampleapp_web`) and seeds default roles.

### 3. Create a test user

1. **Tenants** → open Example App → **+ Add user**
2. Email: `customer@example.com`, status Active
3. **Grant access** → assign a role (e.g. Tenant User or app-scoped role)
4. **Applications** → Example App Web console → **Users** → set password

### 4. Configure the application

Open **Application console → Settings** for Example App Web:

| Tab | What to configure |
|-----|-------------------|
| **Integration** | Hosted vs native UI, redirect URIs checklist, API reference |
| **Authentication** | Password, magic link, email/SMS OTP |
| **Verification & OTP** | Signup link vs OTP, recovery mode |
| **Notifications** | The SMTP server SecureOne sends email through |
| **Feature flags** | Enable **Self Registration** if users can sign up |
| **Public manifest** | Enable so frontends can call `GET /api/v1/applications/{id}` |

### 5. Smoke-test login

| Test | How |
|------|-----|
| Hosted login | Open `/login.html?applicationId={your-app-uuid}` on your SecureOne address |
| Admin as tenant user | Sign out → login with `exampleapp:customer@example.com` |
| OAuth PKCE | **Connect an app** → **Test sign-in** |
| Email | Trigger a password reset and check the inbox |

---

## How SecureOne works (operator view)

### Request flow (simplified)

```mermaid
flowchart LR
  subgraph your_app [Your application e.g. Example App]
    FE[Frontend]
    BE[Backend API]
  end
  subgraph secureone [SecureOne]
    AS[auth-server :9000]
    DB[(PostgreSQL)]
    RD[(Redis)]
  end
  FE -->|OAuth authorize / account APIs| AS
  BE -->|Token introspect / admin APIs| AS
  AS --> DB
  AS --> RD
```

1. **End user** hits your app → redirected to SecureOne (hosted) or your UI calls SecureOne APIs (native).
2. **auth-server** validates credentials, creates session (Redis) or issues JWT tokens.
3. **Your backend** validates `access_token` (JWT signature via JWKS) or calls `/oauth2/introspect`.
4. **Roles/permissions** are embedded in the token at issuance time from the application schema.

### Who can do what

| Role | Can manage |
|------|------------|
| **Platform super-admin** | All tenants, all applications, OAuth registry, platform settings |
| **Tenant super-admin** | Users, roles, apps within their tenant |
| **Application operator** | Users, RBAC, settings for one application (via Application console) |
| **End user** | Own account security (`/account/security.html`, privacy portal) |

Detail: [Admin control](../install/admin-control.md), [Application RBAC](../integrate/application-rbac.md).

<!-- developer -->
### Database layout

- **`platform` schema** — tenants, application registry, OAuth clients, global identity
- **`{app_slug}` schema** — per-application roles, permissions, settings, audit (e.g. `exampleapp_web`)

Detail: [Database strategy](../developer/database.md), [Schemas & tables](../developer/database-schemas.md).

---
<!-- /developer -->

## Day-to-day operations

### Admin console map

| Area | URL | Purpose |
|------|-----|---------|
| Applications registry | `/applications` | Register products + OAuth clients (super-admin) |
| Application console | `/app/{applicationId}/…` | Users, roles, settings for one product |
| Tenants | `/tenants` | Organizations, roster, access |
| Platform settings | `/settings` | Global auth policy, SMTP defaults, feature exposure |
| SecureOne Documentation | `/documentation` | Documentation (opens in new tab) |

### Common tasks

| Task | Where |
|------|-------|
| Add a user | Tenant detail → Add user → Grant access → App console → set password |
| Reset user MFA | Application console → Users → MFA → Reset factors |
| Rotate OAuth secret | Applications → OAuth client → Rotate secret |
| Enable self-signup | App settings → Feature flags → Self Registration |
| Configure SMTP | App settings → Notifications |
| View audit | Application console → Audit / Platform audit |
| Impersonate user (support) | Application console → Users → Impersonate (time-boxed) |
| Hosted or native sign-in for an app | App settings → Integration → Authentication UI (published in the manifest as `authUiMode`) |
| Company SSO (SAML) | App settings → Authentication → Add provider → SAML 2.0 → give the IdP the SP values shown ([23](../integrate/integration-guide.md#enterprise-sso-saml-20)) |
| Sign-in on TVs / CLIs | Applications → OAuth client → Grant types → `device_code` ([23](../integrate/integration-guide.md#device-sign-in-tvs-consoles-clis--rfc-8628)) |
| Rotate signing keys | [Key rotation runbook](../install/runbooks/key-rotation.md) |

<!-- developer -->
### Email in development

All outbound mail goes to **MailHog** when SMTP points to `localhost:1025`. Open http://localhost:8025 to read verification links, OTP codes (if using log provider, check auth-server console instead), and password resets.
<!-- /developer -->

<!-- developer -->
### Health checks

| Check | Command / URL |
|-------|---------------|
| Auth-server up | `GET http://localhost:9000/api/info` |
| OIDC discovery | `GET http://localhost:9000/.well-known/openid-configuration` |
| Database migrations | Restart auth-server; check logs for Flyway success |
| Integration probes | App console → Settings → Integration → Run health checks |
<!-- /developer -->

### Troubleshooting

| Problem | Likely fix |
|---------|------------|
| Cannot sign in as tenant admin | Use `tenantSlug:email` format on login form |
| Public manifest 404 | Enable public manifest + public API exposure in platform/app settings |
| Emails not arriving | Check SMTP under **Settings › Notifications** and send a test email |
| OAuth redirect mismatch | Add exact callback URL to OAuth client redirect URIs |
| Session lost between requests | Ensure Redis is running; check `SECUREONE_REDIS_URL` |

---

## Production deployment (summary)

To install SecureOne from the image with your own database, secrets and domain, see:

**[Deployment](../install/deployment.md)**

Production hardening also includes:

1. **TLS** on auth-server and admin-web
2. **Managed PostgreSQL + Redis** (not single-node dev containers)
3. **Secrets** via vault/KMS — never commit `.env` with production passwords
4. **Runbooks** — backup, key rotation and incident response, under Install and operate › Runbooks

Detail: [Security](../architecture/security.md), [Deploy](../install/runbooks/deploy.md).

---

## What to read next

| Goal | Document |
|------|----------|
| Installing from the image | **[Deployment](../install/deployment.md)** |
| Wire your own frontend | **[Application integration guide](../integrate/integration-guide.md)** |
| Hosted vs custom login UI | [Auth UI integration](../integrate/hosted-and-native-sign-in.md) |
| Signup, OTP, phone auth | [Verification & OTP](../integrate/verification-and-one-time-codes.md) |
| OAuth clients & schemas | [Applications & OAuth clients](../integrate/applications-and-oauth-clients.md) |
| All API endpoints | [API reference](../api/index.md) and Swagger UI at `/docs` |
| Settings inheritance | [Settings governance](../install/settings-governance.md) |

---

<!-- developer -->
## Quick reference card

```
Install:     docker compose -f deploy/docker-compose.yml up -d
             cd apps/auth-server && .\gradlew.bat bootRun
             cd apps/admin-web && npm run dev

Admin UI:    http://localhost:9000/admin  (admin / admin)
Auth server: http://localhost:9000
API docs:    http://localhost:9000/docs
Docs UI:     http://localhost:9000/admin/documentation
Mail (dev):  http://localhost:8025

Tenant login format:  {tenantSlug}:{email}
Platform admin:       admin / admin (no slug)
```
<!-- /developer -->
