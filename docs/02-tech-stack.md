# 02 — Technology Stack

This document records the stacks that were compared and the decisions made, with rationale, so future contributors understand *why*.

## The decisive principle: don't hand-roll the protocol

The OAuth2/OIDC protocol surface (PKCE, token rotation, JWKS, introspection, discovery, consent, nonce/state) is where most IAM breaches occur. **We build our product on top of a certified OIDC engine** and own only the UX, admin, multi-tenancy, and business logic. This single principle drives the backend choice.

---

## Backend

| Option | OIDC engine | Pros | Cons |
|---|---|---|---|
| Node.js + NestJS + TS | `panva/oidc-provider` (certified) | Full-stack TS, fast feature velocity, huge ecosystem | Single-threaded; assemble more yourself |
| **Java/Kotlin + Spring Boot** ✅ | **Spring Authorization Server** (first-class) | Enterprise IAM gold standard; strongest security pedigree; mature SAML/LDAP; widely used by established IAM products | Heavier runtime, more memory, slower iteration |
| Go + Ory `fosite` | `fosite` | Single static binary, great M2M throughput, low memory | More boilerplate, slower for rich admin features |
| Python + FastAPI | `Authlib` | Very fast MVP, clean async | Thinner enterprise IAM/SAML track record |

**Decision: Java/Kotlin + Spring Boot + Spring Authorization Server.**
Chosen for maximum enterprise and security pedigree, first-class OAuth2/OIDC support, and mature federation libraries. Spring Authorization Server provides the authorization/token endpoints, JWKS, discovery, and persistence interfaces we customize with our tenant-aware repositories.

---

## Frontend

| Option | Pros | Cons | Verdict |
|---|---|---|---|
| **Next.js (React) + TS + Tailwind + shadcn/ui** ✅ | Largest ecosystem & hiring pool; SSR for docs/marketing; strong admin-grid and auth libs | React churn; assemble pieces | Recommended |
| Vue + Nuxt | Clean DX, good SSR | Smaller admin/auth ecosystem | Alternative |
| Angular | Batteries-included, enterprise-friendly | Heavy, slower iteration | Only if team is Angular-native |
| SvelteKit | Fastest runtime, lean | Smallest ecosystem | Riskier for large admin surface |

**Decision: Next.js + TypeScript + Tailwind + shadcn/ui.** One framework serves the admin dashboard, hosted login/consent/MFA pages, and **SecureOne Confluence** (in-app platform docs on admin-web). A separate public **docs-site** portal is planned for Phase 2+. Add **TanStack Query** (server state) and **TanStack Table** (admin grids).

---

## Database

**Decision: PostgreSQL only**, abstracted through **JPA/Hibernate** with **Flyway** migrations. The data layer sits behind **repository interfaces** so additional engines could be added later without rewriting domain code, but only Postgres is supported today.

Key upside of committing to Postgres: we can use **Row-Level Security (RLS)** as a database-enforced tenant-isolation layer, on top of application-layer scoping (defense in depth). We also get `jsonb`, rich indexing, and strong concurrency. See [Database Strategy](06-database.md) and [Architecture](03-architecture.md).

NoSQL is explicitly excluded — relational integrity (roles, grants, hierarchical tenants) is core to IAM.

---

## Supporting components

| Concern | Choice | Notes |
|---|---|---|
| Cache / sessions / rate-limit | **Redis** | Sessions, refresh-token store + revocation, rate-limit counters, JWKS cache, MFA challenges |
| Password hashing | **Argon2id** | Tuned cost; never weaker |
| Migrations | **Flyway** | Engine-neutral SQL; engine-specific dirs where unavoidable |
| Async work | Queue (Spring + Redis / broker) | Emails, audit shipping, webhooks |
| Secrets | KMS / Vault / cloud secrets manager | Signing keys, TOTP seeds, client secrets |
| Build | **Gradle** (Kotlin DSL) for backend; **pnpm + Turborepo** for JS workspaces | |
| API contract | **OpenAPI** | Swagger on auth-server; Confluence + future docs-site; SDK generation |

---

## Authorization model evolution

- **Today:** RBAC, scoped per application/tenant.
- **Future:** ABAC/ReBAC via **OpenFGA** (Google Zanzibar style).
- **Enabler:** all permission checks go through a `PolicyEvaluator` interface, so the model can change without touching callers.

---

## Escape hatches (intentional flexibility)

- The **auth core is isolated** so the OIDC engine could be swapped if ever needed.
- The **data access is behind repository interfaces** so the ORM/engine can change.
- The **policy layer** decouples authorization decisions from call sites.
