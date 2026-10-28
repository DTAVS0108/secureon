# SecureOne documentation hub

Central index for **platform** documentation (operators) and **application integration** documentation (developers).

## New here?

| I am… | Start with |
|-------|------------|
| **Platform owner / operator** — install, configure tenants, run day to day | **[22 — Operator getting started](22-operator-getting-started.md)** |
| **DevOps / architect** — image+env deploy or enterprise from source | **[24 — Deployment: image + env & enterprise](24-deployment-image-env-and-enterprise.md)** |
| **App developer** — integrate your own frontend (signup, signin, APIs) | **[23 — Application integration guide](23-application-integration-guide.md)** |
| **Console user** — what every page, button and field does | **[User guide](user-guide/README.md)** |

Browse everything in **SecureOne Documentation**: http://localhost:9000/admin/documentation

## SecureOne Documentation (in-app UI)

Standalone documentation browser in **admin-web** — no admin console sidebar or topbar. Open from the admin sidebar: **Platform admin → SecureOne Documentation** (opens in a **new browser tab** with an external-link icon).

| Access | URL (dev: console at **:9000/admin**) |
|--------|-----------------------------------|
| **UI** | http://localhost:9000/admin/documentation |
| **Doc page** | http://localhost:9000/admin/documentation/{slug} (e.g. `/documentation/architecture`) |
| **Catalog API** (session required) | `GET /api/documentation` |
| **Page API** (session required) | `GET /api/documentation/{slug}` |
| **Assets API** (session required) | `GET /api/documentation/assets/{path}` (download `.drawio` / images) |
| **Public discovery** (auth-server) | `GET http://localhost:9000/api/v1/documentation` |

Example: `GET /api/documentation/architecture` returns markdown JSON for the Architecture page.

**Sidebar categories:** Getting started · Platform — fundamentals · Platform — operations · Deployment · Platform — reference · Application integration (aligned with this hub below).

**PDF export:** every page has a **Download PDF** button in the header (print-friendly view → browser "Save as PDF"), and **All docs PDF** exports the entire catalog as one document at `/documentation/print/all`.

### Draw.io diagrams

- **Inline on doc pages** — each “Editable source” `.drawio` link renders a view-only embed on the **same page** (e.g. Architecture → Logical architecture section).
- **Diagrams index** — `/documentation/diagrams` table links jump to the parent doc that contains the embed.
- **Internet required** — viewer uses `embed.diagrams.net`; download source via the link or assets API if offline.
- **Legacy URLs** — `/documentation/diagram/diagrams/{file}.drawio` redirects to the parent doc page.

> Markdown source remains in this `docs/` folder; Documentation renders it at runtime from the monorepo.

---

## Platform documentation (operators & architects)

Everything needed to **run, configure, and govern** the SecureOne deployment.

| Topic | Document |
|-------|----------|
| **New operator — install & run** | **[22 — Operator getting started](22-operator-getting-started.md)** |
| **Deploy (images+env or enterprise source)** | **[24 — Deployment: image + env & enterprise](24-deployment-image-env-and-enterprise.md)** |
| **New developer — integrate your app (e.g. an online store)** | **[23 — Application integration guide](23-application-integration-guide.md)** |
| **Roles — who sees and can do what** | **[25 — Roles and access](25-roles-and-access.md)** |
| **Tenant setup in files; upgrades and rollback** | **[26 — Configuration as code and upgrades](26-configuration-as-code-and-upgrades.md)** |
| **Attack protection, audit streaming, webhooks, consent, back-channel logout, branding** | **[27 — Security and integration features](27-security-and-integration-features.md)** |
| **Using the console — page by page, field by field** | **[User guide](user-guide/README.md)** |
| Vision & glossary | [01 — Overview](01-overview.md) |
| Stack choices | [02 — Tech Stack](02-tech-stack.md) |
| System architecture & flows | [03 — Architecture](03-architecture.md) |
| Database tables & ERD | [04 — Data Model](04-data-model.md) |
| OAuth / OIDC / tokens / MFA | [05 — Auth Standards](05-auth-standards.md) |
| PostgreSQL, Flyway, schemas | [06 — Database Strategy](06-database.md) |
| **Schemas, tables & connections** | **[17 — Database schemas & tables](17-database-schemas-and-tables.md)** |
| **Flyway migration SQL** | **[18 — Flyway migration files](18-flyway-migration-files.md)** |
| Security practices | [07 — Security](07-security.md) |
| **Local install & smoke tests** | **[09 — Installation](09-installation.md)** |
| **Image+env deploy & enterprise Helm** | **[24 — Deployment: image + env & enterprise](24-deployment-image-env-and-enterprise.md)** · [runbooks/deploy.md](runbooks/deploy.md) |
| **Run stack, migrations & schedulers** | **[16 — Environment & operations](16-environment-setup-and-operations.md)** · **[17 — Schemas & tables](17-database-schemas-and-tables.md)** · **[18 — Flyway SQL](18-flyway-migration-files.md)** |
| Enterprise features (phased) | [10 — Enterprise](10-enterprise.md) |
| Admin scopes & settings layers | [11 — Admin Control](11-admin-control.md) |
| API reference (every endpoint, with curl) | [API reference](api-reference/README.md) · [12 — API documentation](12-api-documentation.md) (Swagger, OpenAPI) |
| Platform settings & governance | [10 — Settings governance](10-settings-governance.md) |
| Application RBAC (groups/roles) | [14 — Application RBAC](14-application-rbac-management.md) |
| **Applications vs OAuth clients** | **[15 — Applications & OAuth clients](15-applications-and-oauth-clients.md)** |
| **Verification, OTP & phone auth** | **[19 — Verification & OTP](19-verification-and-otp.md)** |
| Architecture decisions (ADRs) | [decisions/](decisions/README.md) |
| Diagrams (draw.io + Mermaid) | [diagrams/](diagrams/README.md) |

## Deployment

Ship SecureOne to a server or cluster and go live.

| Topic | Document |
|-------|----------|
| **Two deploy paths — image+env only (no source) vs enterprise source/Helm** | **[24 — Deployment: image + env & enterprise](24-deployment-image-env-and-enterprise.md)** |
| Short runbook (prereqs, images, Helm, post-deploy checks) | [runbooks/deploy.md](runbooks/deploy.md) |
| Rollback procedure | [runbooks/rollback.md](runbooks/rollback.md) |
| Env vars, profiles (dev/prod), schedulers | [16 — Environment & operations](16-environment-setup-and-operations.md) |

### App READMEs (repo)

| Component | Path |
|-----------|------|
| Monorepo overview | [../README.md](../README.md) |
| Auth server | [../apps/auth-server/README.md](../apps/auth-server/README.md) |
| Admin console | [../apps/admin-web/README.md](../apps/admin-web/README.md) |

### Live API reference (running auth-server)

| Resource | URL (local) |
|----------|-------------|
| Swagger UI | http://localhost:9000/docs |
| OpenAPI JSON | http://localhost:9000/v3/api-docs/all |
| OIDC discovery | http://localhost:9000/.well-known/openid-configuration |

---

## Application integration documentation (developers)

Everything needed to **integrate a product** (your app) with SecureOne: OAuth, login UI, account APIs, samples.

| Topic | Where |
|-------|--------|
| **Start here — full integration walkthrough** | **[23 — Application integration guide](23-application-integration-guide.md)** |
| Auth UI modes, OAuth, session login | [13 — Auth UI integration](13-auth-ui-integration.md) |
| **OTP signup/login, phone, verification policy** | **[19 — Verification & OTP](19-verification-and-otp.md)** |
| Register product + OAuth client | [15 — Applications & OAuth clients](15-applications-and-oauth-clients.md) |
| OpenAPI (all endpoints) | [API reference](api-reference/README.md) · [12 — API documentation](12-api-documentation.md) + Swagger |

### In the admin console (per application)

When you open **Application console → Settings → Integration**, each application gets:

| Section | What it covers |
|---------|----------------|
| Authentication UI | Hosted vs native sign-in |
| Integration checklist | Redirect URIs, SMTP, auth methods, signup, etc. |
| **API reference** | Expandable endpoint list with request/response examples (scoped to **your** `applicationId` and `clientId`) |
| API health check | Probes public app-scoped endpoints |
| Database schema | Isolated schema status / isolate (platform super-admin) |

This is the closest thing to **per-application Documentation** today — it is **dynamic** (your IDs, your client) but lives inside the console, not as standalone wiki pages.

### Application-scoped public APIs (no admin auth)

| API | Purpose |
|-----|---------|
| `GET /api/v1/applications/{id}` | Public manifest (auth methods, branding, account endpoint URLs) |
| `GET/POST …/signup` | Self-registration |
| `POST …/account/password/forgot` | Password reset request |
| `POST …/account/verify/otp/confirm` | Confirm signup verification (OTP) |
| `POST …/auth/otp/request` | Login OTP request (email/SMS) |
| `POST …/auth/otp/verify` | Login OTP verify → session |
| `POST …/auth/session/login` | Native UI session login (`identifier` = email or phone) |
| OAuth `/oauth2/authorize`, `/oauth2/token` | Hosted OIDC + PKCE |

Full catalog is built in `apps/admin-web/src/lib/integration-api-catalog.ts` and rendered in the Integration settings tab.

---

## How this maps to “Documentation”

| Documentation idea | SecureOne today | Planned |
|-----------------|-----------------|---------|
| **Platform space** | `docs/` markdown + **SecureOne Documentation** in admin-web (standalone UI, session APIs) | Public `docs-site` portal |
| **Application space** | Settings → Integration + doc 13 + samples | Per-app docs export / developer portal |
| **API catalog** | Swagger + in-console API reference | SDK + generated client docs |
| **Search** | IDE / GitHub search | Docs site search |
| **Versioned with releases** | Git tags + Flyway versions | Published doc versions per release |

---

## Suggested reading paths

### Platform operator (first week)

1. **[22 — Operator getting started](22-operator-getting-started.md)** — what SecureOne is, install, first tenant/app/user
2. [09 — Installation](09-installation.md)
3. [15 — Applications & OAuth clients](15-applications-and-oauth-clients.md)
4. [11 — Admin Control](11-admin-control.md)
5. [14 — Application RBAC](14-application-rbac-management.md)

### Application developer (integrating a new app)

1. **[23 — Application integration guide](23-application-integration-guide.md)** — Example App example, public APIs, hosted vs native UI
2. [15 — Applications & OAuth clients](15-applications-and-oauth-clients.md) — register product + client
3. [13 — Auth UI integration](13-auth-ui-integration.md) — hosted vs native detail
4. [19 — Verification & OTP](19-verification-and-otp.md) — signup/login OTP, phone, magic link session
5. Admin → **your app → Settings → Integration** — checklist + API reference
6. http://localhost:9000/docs — try OAuth and account APIs

### Architect / security review

1. [03 — Architecture](03-architecture.md) (draw.io diagrams inline in Documentation)
2. [06 — Database Strategy](06-database.md)
3. [07 — Security](07-security.md)
4. [decisions/](decisions/README.md)
5. **SecureOne Documentation** — http://localhost:9000/admin/documentation

---

## Contributing documentation

- **Platform changes** → update the relevant `docs/NN-*.md` file and this hub.
- **Draw.io diagrams** → edit files in `docs/diagrams/`; Documentation embeds them inline on the parent doc page automatically.
- **New integration endpoints** → update `integration-api-catalog.ts` and [13 — Auth UI integration](13-auth-ui-integration.md).
- **Schema / migration changes** → [06 — Database](06-database.md), [15 — Applications](15-applications-and-oauth-clients.md), ADR in `decisions/`.
