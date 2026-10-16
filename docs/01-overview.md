# 01 — Overview

## Vision

SecureOne is a **centralized Identity and Access Management (IAM) platform**. Independent applications delegate their authentication and authorization to SecureOne instead of each building their own login, user store, and permission system. It is a self-hostable identity provider that you own end to end.

One central system provides:

- **Authentication** — verifying who a user (or machine) is.
- **Authorization** — deciding what that identity is allowed to do.
- **User management** — lifecycle of accounts across many applications and tenants.
- **Federation & integration** — standards-based APIs so any app can plug in.

## Goals

1. **Single source of truth for identity** across many applications.
2. **Standards-first** — OAuth 2.1, OIDC, SAML 2.0, SCIM — so existing client libraries "just work."
3. **Multi-tenant by design** — one deployment serves many organizations with strict isolation.
4. **Secure by default** — strong hashing, encryption of secrets, short-lived tokens, auditability.
5. **Operable & portable** — runs on PostgreSQL, deployable from a single VM up to Kubernetes; data layer abstracted so other engines can be added later.
6. **Developer-friendly** — clean admin UI, OpenAPI, SDKs, and documentation.

## Non-goals (initially)

- Being a general-purpose CRM or user-profile product.
- Supporting arbitrary/NoSQL database engines (relational only — see [Database Strategy](06-database.md)).
- Hand-rolling the OAuth/OIDC protocol (we build **on top of** Spring Authorization Server, a certified engine).

## Key concepts & glossary

| Term | Meaning |
|---|---|
| **Tenant** | An organization/customer boundary. All tenant data is isolated. |
| **Application (product)** | A registered product within a tenant (e.g. "Billing", "Mobile App"). Owns IAM scope; may have a dedicated PostgreSQL schema (`schema_name`). |
| **OAuth client** | Protocol credentials for token exchange (`platform.oauth_client`). One application can have many clients (web, SPA, native, M2M). |
| **Application schema** | Per-app PostgreSQL schema (e.g. `demostore`) holding roles, permissions, settings — provisioned on create or via **isolate**. |
| **User** | A human identity within a tenant. |
| **Admin** | A user who manages a tenant (`type = ADMIN`). |
| **Super-admin / Platform admin** | A platform operator who manages *all* tenants. Kept in a separate trust tier. |
| **Service account** | A non-human identity for machine-to-machine access; authenticates via API keys / client credentials. |
| **Role** | A named bundle of permissions, scoped to an application. |
| **Permission** | An atomic capability declared by an application (e.g. `invoice:read`). |
| **Session** | A server-side login session (backs SSO and the hosted login UI). |

## Core features (definitions)

- **Multi-application support** — many apps share one identity system; each **application product** defines its own roles/permissions; **OAuth clients** are registered separately (see [Applications & OAuth clients](15-applications-and-oauth-clients.md)).
- **User / admin / super-admin management** — full lifecycle (create, disable, lock, delete), with three distinct trust tiers.
- **Application-specific roles & permissions** — RBAC scoped per application, so the same role name can differ between apps.
- **Tenant / organization support** — multi-tenancy with app-layer isolation (see [Architecture](03-architecture.md)).
- **OAuth2 & OIDC** — Authorization Code + PKCE, Client Credentials, refresh-token rotation, discovery, JWKS.
- **JWT access + refresh tokens** — short-lived signed JWTs; refresh tokens server-side and revocable.
- **Enterprise SSO & identity brokering** — Google, GitHub, any OIDC IdP and **SAML 2.0** IdPs (any standards-compliant company IdP): SP- and IdP-initiated sign-in, single logout, encrypted assertions; JIT accounts or link-by-email. LDAP / AD sync is Phase 3+.
- **Every kind of client** — hosted SecureOne pages or the app's own (native) screens, SPAs and BFFs (code + PKCE), machines (client credentials, API keys), and **TVs / CLIs (device sign-in, RFC 8628)**.
- **MFA/2FA — passkeys-first** — phishing-resistant WebAuthn/FIDO2 (incl. passwordless), with TOTP, **SMS OTP**, **email OTP**, and push fallbacks; recovery codes; per-tenant MFA policy, AAL levels, and step-up.
- **Password reset & email verification** — single-use, short-lived, hashed tokens.
- **Audit logs & login history** — append-only audit trail + per-user login outcomes.
- **API keys / service accounts** — M2M access mapped onto the same RBAC engine.
- **Admin dashboard & self-service portal** — manage tenants, apps, users, roles, clients, sessions, audit; end-user self-service for security/MFA/sessions.
- **Developer docs & SDKs** — OpenAPI spec, **SecureOne Confluence** (in-app platform docs on admin-web), and a TypeScript SDK (more in later phases). See [Authentication UI integration](13-auth-ui-integration.md) for hosted vs custom end-user login.
- **Flexible database setup** — PostgreSQL connection configured at install; the data layer is abstracted (repository interfaces) so other engines can be added later.

> Beyond the core above, SecureOne has a full **enterprise capability roadmap** (governance/IGA, fine-grained authorization, adaptive auth, compliance, multi-region scale). See [Enterprise Capabilities](10-enterprise.md).

## Primary user personas

1. **Platform operator (super-admin)** — runs the SecureOne deployment, manages tenants.
2. **Tenant admin** — manages users, apps, roles within their organization.
3. **End user** — logs into one of the integrated applications.
4. **Developer/integrator** — wires an application into SecureOne using OIDC + the SDK.
5. **Service/machine** — calls APIs using a service account + API key.
