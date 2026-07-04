# SecureOne

> A centralized, multi-tenant **Identity and Access Management (IAM)** platform — authentication, authorization, user management, RBAC, tenant/application management, and integration APIs from one central place.

The monorepo includes a working **auth-server** (Spring Boot + Spring Authorization Server) and **admin-web** (Next.js console), plus design documentation in `docs/`.

---

## What SecureOne provides

- Multi-application support from a single central system
- **Application products** with optional **per-app PostgreSQL schemas** for isolated IAM
- **OAuth clients** separate from application products (1:N)
- User, admin, and super-admin (platform operator) management
- Application-specific roles and permissions (RBAC, evolvable to ABAC/ReBAC)
- Tenant / organization support (multi-tenancy)
- OAuth 2.1 and OpenID Connect (OIDC)
- JWT access tokens + refresh tokens
- MFA/2FA roadmap — passkeys-first (WebAuthn/FIDO2) with TOTP and OTP fallbacks
- Password reset & email verification (link or OTP)
- Email/SMS OTP login, phone signup, Redis sessions
- Audit logs & login history
- Admin dashboard
- OpenAPI / Swagger UI on auth-server
- **PostgreSQL** + Flyway migrations

---

## Quick start (local)

```bash
# 1. Infrastructure
docker compose -f deploy/docker-compose.yml up -d

# 2. Auth server (Flyway migrations run on boot)
cd apps/auth-server && ./gradlew bootRun    # http://localhost:9000

# 3. Admin console
cd apps/admin-web && npm install && npm run dev   # http://localhost:3001
```

Sign in as platform super-admin: **`admin` / `admin`** (dev). Register an application + OAuth client at **/applications**. Browse platform docs at **/confluence** (SecureOne Confluence).

Full setup: [docs/09-installation.md](docs/09-installation.md). Operations (migrations, schedulers): [docs/16-environment-setup-and-operations.md](docs/16-environment-setup-and-operations.md).

---

## Chosen stack

| Layer | Choice |
|---|---|
| Backend | Java + Spring Boot + Spring Authorization Server |
| Frontend | Next.js + TypeScript + Tailwind |
| Database | PostgreSQL (`platform` schema + per-app schemas) |
| Cache | Redis |
| Migrations | Flyway (`public` history + `postgresql/` scripts) |
| Deploy | Docker Compose (dev) → Kubernetes (scale) |

Details: [docs/02-tech-stack.md](docs/02-tech-stack.md).

---

## Documentation index

**Start here:** [docs/README.md](docs/README.md) — platform docs + application integration docs (Confluence-style hub).

| # | Document | What's inside |
|---|---|---|
| 01 | [Overview](docs/01-overview.md) | Vision, goals, glossary |
| 02 | [Tech Stack](docs/02-tech-stack.md) | Stack decisions |
| 03 | [Architecture](docs/03-architecture.md) | Components, flows, deployment |
| 04 | [Data Model](docs/04-data-model.md) | Entities, ERD, tables |
| 05 | [Auth Standards](docs/05-auth-standards.md) | OAuth2/OIDC, tokens, MFA |
| 06 | [Database Strategy](docs/06-database.md) | Platform + app schemas, Flyway, isolation |
| 07 | [Security](docs/07-security.md) | Security practices |
| 08 | [Roadmap](docs/08-roadmap.md) | Phased delivery |
| 09 | [Installation](docs/09-installation.md) | Local setup, env vars, smoke tests |
| 16 | [Environment & operations](docs/16-environment-setup-and-operations.md) | Docker, config, bootstrap, schedulers |
| 17 | [Schemas & tables](docs/17-database-schemas-and-tables.md) | PostgreSQL schemas, tables, FK connections |
| 18 | [Flyway migrations](docs/18-flyway-migration-files.md) | Full V1–V45 + app schema SQL |
| 10 | [Enterprise](docs/10-enterprise.md) | Federation, IGA, scale (phased) |
| 11 | [Admin Control](docs/11-admin-control.md) | Admin scopes, settings layers |
| 12 | [API documentation](docs/12-api-documentation.md) | OpenAPI, Swagger groups |
| 13 | [Auth UI integration](docs/13-auth-ui-integration.md) | Hosted vs native login |
| 14 | [Application RBAC](docs/14-application-rbac-management.md) | Groups, roles, permissions |
| **15** | **[Applications & OAuth clients](docs/15-applications-and-oauth-clients.md)** | **Product vs client, schema provisioning, isolate** |
| — | **[Documentation hub](docs/README.md)** | **Platform + application integration index** |
| — | **[Application integrator guide](docs/applications/README.md)** | **Setup, APIs, samples (per-app)** |
| — | [Diagrams](docs/diagrams/README.md) | Mermaid + draw.io sources (inline in Confluence) |
| — | [Decision Records](docs/decisions/README.md) | ADRs |

### App READMEs

- [auth-server](apps/auth-server/) — Spring Boot API (see `application.yml`, `db/migration/`)
- [admin-web](apps/admin-web/README.md) — Next.js console

### Integration samples

- [External Test Client](samples/external-test-client/README.md) — SPA OAuth sample (`external-test-client`)
- [Nook Commerce](../secureone-ecommerce) — demo storefront (OIDC + PKCE)

---

## Repository layout

```
secureone/
├── README.md
├── docs/                       # design & reference documentation
├── deploy/
│   ├── docker-compose.yml      # Dev: Postgres, Redis, MailHog
│   ├── Dockerfile.auth-server  # auth-server image
│   └── Dockerfile.admin-web    # admin-web image (+ docs for Confluence)
├── apps/
│   ├── auth-server/            # Spring Boot + Spring Authorization Server
│   └── admin-web/              # Next.js admin dashboard
├── samples/
│   └── external-test-client/   # integration sample
└── scripts/                    # reset-db, reseed-catalog, smoke tests
```

---

## Status

**Implemented:** Multi-tenant auth-server, admin console, platform schema (V45), OAuth client registry, per-application schema provisioning, application console (users/roles/groups/settings), OpenAPI docs, **SecureOne Confluence** (standalone in-app docs browser with inline draw.io).

**In progress / planned:** Full MFA, SAML, SCIM, SDK, enterprise tiers — see [Roadmap](docs/08-roadmap.md).
