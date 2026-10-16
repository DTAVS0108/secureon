# 08 — Roadmap

A phased plan from MVP to enterprise. The architecture is production-shaped from day one, so phases add **feature breadth**, not structural rewrites.

> **Status (2026-10):** Phase 0–1 complete. Phase 2 complete (MFA, M2M, hardening, token exchange). Phase 3: SAML 2.0 SP (SSO, IdP-initiated, single logout, encrypted assertions), device sign-in (RFC 8628) and RFC 9728 metadata shipped. Remaining enterprise scope (SecureOne as a SAML IdP, OpenFGA, multi-region, IGA campaigns, push MFA, full GDPR) is multi-month work.

## Phase 0 — Foundations
- [x] Planning & architecture documentation (`docs/`)
- [x] Monorepo scaffold (`apps/auth-server`, `apps/admin-web`, `deploy/`)
- [x] CI skeleton (build, lint, SAST, dependency scan)
- [x] PostgreSQL + Flyway wiring

## Phase 1 — MVP core
**Goal: a usable OIDC provider with multi-tenant user management and RBAC.**
- [x] Core schema migrations (tenants, applications, users, roles, permissions)
- [x] Platform schema + `oauth_client` table (V45)
- [x] Per-application PostgreSQL schema provisioning + isolate
- [x] Spring Authorization Server integration (Auth Code + PKCE, Client Credentials)
- [x] JWT issuance + JWKS
- [x] User management (CRUD, lifecycle)
- [x] Multi-tenancy (`tenant_id` + app-layer isolation)
- [x] Per-application RBAC (roles, permissions, composite roles, groups)
- [x] Hosted login UI + admin dashboard (admin-web)
- [x] Password reset + email verification (basic)
- [x] **Verification policy** — signup link vs OTP; email/phone OTP login; Redis sessions
- [x] Platform-admin (super-admin) tier
- [x] Admin control basics — scopes, user lifecycle, sessions
- [x] OAuth client registry UI (application product separate from OAuth client)
- [x] **SecureOne Confluence** — in-app docs browser

## Phase 2 — Hardening, MFA & M2M
**Goal: production-grade security, phishing-resistant MFA, machine integration.**
- [x] **MFA — passkeys-first:** WebAuthn/FIDO2, **TOTP**, **recovery codes**
- [x] MFA fallbacks: **SMS OTP**, **email OTP** (push not implemented)
- [x] Per-tenant `mfa_policy` + app overrides; trusted-device cookie MFA skip
- [x] Step-up auth (`acr`/`amr` in session, JWT, userinfo)
- [x] **Admin MFA control** — reset/clear factors, recovery codes, audited
- [x] **Admin impersonation** — time-boxed Redis token + consume endpoint
- [x] **Layered settings** + feature toggles + auth-method selection
- [x] Service accounts + API keys + **M2M** (`/api/v1/m2m/health`, `/whoami`, `/tenant`; per-key rate limits)
- [x] Audit logs + login history
- [x] Brute-force defense, lockout, rate limiting, leaked-password detection
- [x] Self-service account portal (`account/security.html` + admin profile MFA)
- [x] **Webhooks / event stream** — signed outbound delivery + admin CRUD
- [x] Token introspection & revocation; refresh token reuse detection
- [x] OpenAPI spec + Swagger UI
- [x] **TypeScript SDK** — `packages/secureone-client` (manifest, PKCE, session login, introspect, revoke)

## Phase 3 — Enterprise
**Goal: enterprise SSO/brokering, governance, advanced authz & protocol hardening.**
- [x] **Identity brokering** + JIT (Google/GitHub/OIDC)
- [x] **SAML 2.0 SP login** — per app or tenant-wide; metadata URL or manual config; signed AuthnRequests, encrypted assertions, replay cache; opt-in IdP-initiated sign-in; single logout both ways (tested end to end against an open-source SAML IdP)
- [x] **Device authorization grant (RFC 8628)** — hosted device page with explicit Allow/Deny, tenant/app check, `slow_down`, replay revocation
- [x] **RFC 9728** protected resource metadata
- [ ] SecureOne as a SAML **IdP** (assertions for SAML-only apps); SOAP back-channel logout
- [x] SCIM 2.0 provisioning (Users + Groups CRUD)
- [x] **Authentication flows (foundation)** — trusted device; adaptive/risk scoring deferred
- [ ] **Advanced MFA:** attestation/AAGUID allow-lists, AAL2/AAL3, smart-card/PIV, mTLS
- [ ] ABAC/ReBAC via OpenFGA + OPA/Cedar + UMA 2.0
- [x] **Organizations (stub)** — nested org CRUD per tenant + admin UI panel
- [x] **IGA (stub):** access request workflow + **auto-grant role on approve**
- [x] **Protocol:** PAR (`POST /oauth2/par`, one-time `request_uri`); **Token Exchange RFC 8693**; back-channel logout (ack stub)
- [x] Per-application schema isolation (implemented); database-per-tenant tier deferred
- [ ] Terraform provider; Java/Python/Go SDKs
- [x] Helm chart (`deploy/helm/secureone/`): one Deployment, rolling updates with no unavailable pods, non-root UID
- [x] **One deployable** — a single image (`deploy/Dockerfile`): the auth-server serves the admin console at `/admin` (one host, one port); all settings read at start-up, nothing baked into the build; production refuses to start without https URLs and the encryption key; CI builds and smoke-tests the image; `docker-compose.quickstart.yml` for evaluation (tested end to end); JVM start-up cache recorded at build time (start-up about twice as fast); health and metrics on an internal management port (9001), not on the public port
- [x] **Connect an app** wizard: pick the kind of app and framework; the client gets secure defaults; setup code for 15 frameworks with real values; test sign-in page; registered redirect origins allowed for CORS automatically
- [x] **Sample apps** (`samples/`): React SPA → Express API (token + permission check), Next.js (server-side, client secret), device-code CLI → API; tested end to end and built in CI
- [x] **First-run setup**: one-time setup code in the log (or `SECUREONE_SETUP_CODE`) → `/admin/setup` creates the first platform operator (MFA at first sign-in); production needs no emergency account
- [x] **Clear integration errors**: unregistered redirect URI, unknown client, scope, PKCE, code and refresh-token mistakes explained in `error_description` or on a browser page
- [x] **Configuration as code**: export a tenant (apps, OAuth clients, permissions, roles, groups, settings overrides) to YAML/JSON; preview the changes a file makes; apply it (idempotent, optional prune); sign-in providers, webhooks, service accounts and API keys, SCIM token, per-application SMTP/SMS, and a platform file (default SMTP, SMS, SCIM); every credential from an environment variable named in the file (literal secrets refused); files applied at every start-up with `SECUREONE_CONFIG_IMPORT` (Helm ConfigMap)
- [x] **Upgrade safety**: per-application schemas are upgraded at start-up (versioned, one transaction each); an older image refuses a database from a newer release; `SECUREONE_DB_MIGRATE=only|validate` and a Helm pre-upgrade migration job; release and schema versions on `/actuator/info`; upgrade scenarios tested (empty database, V1 application schema, newer database refused)
- [ ] OIDC conformance certification + third-party pen test

## Phase 4 — Governance & Scale
**Goal: identity governance (IGA), compliance, and global scale.**
- [x] **IGA (stub):** access request workflow + **auto-grant role on approve**
- [ ] Full IGA: certification campaigns, SoD, time-bound/JIT access workflows
- [x] **GDPR/CCPA (stub):** export + deletion-request APIs; **hosted privacy portal** (`/account/privacy.html`)
- [ ] Compliance posture: SOC 2 / ISO 27001 / HIPAA; retention policies
- [ ] Multi-region HA / active-active, DR, blue-green/canary
- [x] **Tenant quotas (stub):** max users/applications enforcement hooks
- [ ] Usage metering / MAU billing
- [ ] HSM / BYOK key management + automated signing-key rotation
- [ ] FAPI 2.0 high-assurance profile
- [x] **Observability (partial):** Prometheus actuator; OpenTelemetry tracing deferred

See [Enterprise Capabilities](10-enterprise.md) for detail on Phase 3–4 items.

## Definition of "production-ready" (GA gate)
- [ ] OIDC conformance suite passing
- [ ] Third-party penetration test passed
- [x] Cross-tenant isolation (RLS + test suite)
- [x] Key rotation, backup/restore, incident runbooks (`docs/runbooks/`)
- [x] Prometheus metrics endpoint; full OTel dashboards deferred
- [x] Helm-based reproducible deployment

## Remaining multi-sprint work (honest backlog)

| Area | Effort | Notes |
|------|--------|-------|
| SAML IdP role | 4–6 weeks | Issue assertions to SAML-only apps (SP role is done) |
| OpenFGA / ABAC | 4+ weeks | Policy engine integration |
| Multi-region active-active | 8+ weeks | Redis/Postgres replication, conflict resolution |
| IGA campaigns | 6+ weeks | Workflow engine, notifications |
| OIDC certification | 2–4 weeks | Conformance suite + fixes |
| Push MFA | 2–3 weeks | Needs a SecureOne authenticator mobile app (or SDK in the customer's app) + FCM/APNs credentials; number-matching approval |
| TypeScript SDK v1 | ongoing | Expand coverage, publish to npm |
| **Pending guide** | — | See [21 — Pending implementation guide](21-pending-implementation-guide.md) |
