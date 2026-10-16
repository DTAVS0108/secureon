# 20 — Production completion master plan

**Goal:** Take SecureOne from **development-phase MVP** to **enterprise production-ready** with explicit **development vs production** behavior, all Phase 2 hardening implemented, MFA at login, and a full platform + application test matrix.

**Reality check:** This is a **multi-sprint program** (~8–14 weeks for a small team), not a single PR. This document is the authoritative backlog. Each epic has **implementation tasks**, **verification (×2)**, and **exit criteria**.

**Current baseline (verified):**
- Phase 1 complete per `08-roadmap.md` (OIDC, RBAC, OTP signup/login/recovery, layered settings)
- Phase 2 ~30% (email/SMS OTP yes; TOTP/passkey/lockout/CI no)
- Phase 3–4: SAML 2.0 SP, SCIM, device sign-in and GDPR stubs shipped; multi-region and full GDPR tooling not started

---

## Guiding principles

1. **Dev and prod are first-class** — same codebase, different profiles, UI, and defaults.
2. **No “coming soon” in production profile** — unimplemented methods stay disabled with clear messaging in dev only.
3. **Verify twice** — unit/integration tests + scripted E2E per epic before marking done.
4. **Platform then application** — platform defaults and exposure before per-app overrides.
5. **Horizontal scale before worldwide scale** — JDBC OAuth + shared JWK before multi-region.

---

## Environment model (Epic 0 — foundation)

### Backend profiles

| Profile | `SPRING_PROFILES_ACTIVE` | Purpose |
|---------|------------------------|---------|
| `dev` | default locally | Swagger on, mail-link logging, dev admin auth, verbose SQL |
| `staging` | pre-prod | Prod-like + Swagger optional |
| `prod` | production | Swagger off, no dev auth, fail-fast on weak secrets |

**Files to create/modify:**
- `apps/auth-server/src/main/resources/application-dev.yml`
- `apps/auth-server/src/main/resources/application-prod.yml`
- `apps/auth-server/src/main/resources/application.yml` (shared + profile imports)
- `apps/auth-server/src/main/java/.../config/SecureOneEnvironmentProperties.java`
- `apps/auth-server/src/main/java/.../web/InfoController.java` — expose `environment`, `features`

**Env vars (unified catalog in `.env.example`):**
- `SECUREONE_ENV=development|staging|production`
- `SECUREONE_SWAGGER_ENABLED` (dev: true, prod: false)
- `SECUREONE_CORS_ALLOWED_ORIGINS` (comma-separated; optional: origins of registered redirect URIs are allowed automatically)
- `SECUREONE_OTP_PEPPER`, `SECUREONE_PUBLIC_BASE_URL`, `SECUREONE_ADMIN_WEB_URL`
- `SECUREONE_MAIL_LOG_WHEN_UNAVAILABLE` (dev: true, prod: false)
- `SECUREONE_DEV_AUTH_ENABLED` (dev: true, prod: false)

**Prod fail-fast checks at startup:**
- Reject default `SECUREONE_OTP_PEPPER`
- Reject `admin`/`admin` dev credentials when `prod` profile
- Warn if SMS provider is `log` in prod

### Admin-web environment

| Variable | Dev | Prod |
|----------|-----|------|
| `SECUREONE_ENV` (console reads it at runtime too) | `development` | `production` |
| `SECUREONE_PUBLIC_BASE_URL` (console links) | `http://localhost:9000` | real issuer host |
| Dev banner | visible | hidden |
| Integration “test send” panels | visible | hidden or admin-only |
| Proxy Basic-auth fallback | allowed | **disabled** |

**Files:**
- `apps/admin-web/src/lib/config.ts` — `IS_DEV`, `IS_PROD`, `ENVIRONMENT`
- `apps/admin-web/src/app/layout.tsx` — render banner
- `apps/admin-web/.env.local.example`, `deploy/Dockerfile` — no build-time values: the console reads its URLs and environment at runtime
- `apps/admin-web/src/app/api/proxy/[...path]/route.ts` — no dev fallback in prod

**Verification ×2:**
1. `SPRING_PROFILES_ACTIVE=dev` → `/docs` 200, banner visible, mail links logged
2. `SPRING_PROFILES_ACTIVE=prod` → `/docs` 403, no banner, startup fails on default pepper

---

## Epic 1 — Production scaling blockers (P0)

**Without these, multi-replica deploy is unsafe.**

### 1.1 Persistent OAuth authorizations

- Migration `V49__oauth2_authorization_jdbc.sql` (Spring SAS schema)
- `OAuth2AuthorizationServerPersistenceConfig.java` — `JdbcOAuth2AuthorizationService`
- Tests: auth code exchange across two simulated “replicas” (same DB)

### 1.2 Persistent JWK signing keys

- Migration `V50__signing_keys.sql` or env-based PEM + `OAuth2JwkConfig.java`
- Bootstrap: generate once, store encrypted in `platform_setting` or mount from secrets manager
- Tests: JWKS stable across restart; JWT validates after restart

### 1.3 Token policy → runtime TokenSettings

- `TokenSettingsMapper.java` — map `TokenPolicyDefaults` → `TokenSettings`
- Wire in `ApplicationRegisteredClientRepository.toRegisteredClient()`
- Prod default: `accessTokenTtlSeconds` = 900 (15 min), not 3600

**Verification ×2:**
1. Unit: policy values appear on `RegisteredClient`
2. E2E: `scripts/test-external-client.ps1` — token `expires_in` matches policy

---

## Epic 2 — Security hardening (P0)

### 2.1 Account lockout

- `AccountLockoutService.java` — `assertNotLocked`, `recordFailedAttempt`, `recordSuccess`
- Platform settings: `maxFailedAttempts` (default 5), `lockoutDurationMinutes` (default 15)
- Wire: `TenantUserDetailsService`, `TenantPasswordAuthenticationProvider`, `AuthSessionService`, `FormLoginFailureHandler`
- Atomic DB updates for multi-replica

### 2.2 Global rate limiting

- `RedisRateLimiter.java` + `RateLimitFilter.java`
- Limits: login, token, forgot-password, OTP request, admin login
- Config in `application.yml` per profile

### 2.3 Audit IP + actor

- Fix `AuditService` to capture real client IP from `RequestContextFilter`
- Login history already records IP — align audit

### 2.4 PostgreSQL RLS (defense in depth)

- Migration `V51__tenant_rls_policies.sql` — policies on `user_account`, `role`, etc. using `current_setting('app.tenant_id')`
- Set tenant context in `ApplicationSchemaFilter` / request filter

**Verification ×2:**
1. Integration: 6 failed logins → account locked
2. Integration: rate limit returns 429 after N requests
3. Isolation test: tenant A cannot read tenant B rows (new test class)

---

## Epic 3 — MFA at login (P0 for enterprise)

**Scope for “complete MFA” v1:** TOTP + recovery codes + MFA step-up after password. Passkeys v1.1 (same epic, phase B).

### 3.1 Schema

- `V52__mfa_factor_extended.sql` — WebAuthn fields, recovery code hash, last_used_at
- `V53__mfa_policy.sql` — per-tenant/app policy (required factors, remember device TTL)
- Update `MfaFactor.java` entity

### 3.2 TOTP enrollment + verify

- Dependency: `dev.samstevens.totp` or similar
- `TotpEnrollmentService`, `TotpVerificationService`
- APIs:
  - `POST /api/v1/account/mfa/totp/enroll` (authenticated)
  - `POST /api/v1/account/mfa/totp/confirm`
  - `DELETE /api/v1/account/mfa/totp/{factorId}`

### 3.3 MFA step-up at login

- `MfaChallengeService` (Redis) — pending auth state, 5 min TTL
- Flow:
  1. Password login succeeds → if user has TOTP and policy requires → return `428` + `mfaChallengeId` (no session)
  2. `POST /api/v1/auth/mfa/totp/verify` → issue session
- Wire: `AuthSessionService`, form login success handler
- Enforce `verification_policy.login.mfaRequired` + per-user factors
- Set `implemented: true` for `m_totp` in bootstrap when feature complete

### 3.4 Recovery codes

- Generate 10 one-time codes on TOTP enroll; store hashed in `mfa_factor` type `recovery`
- `POST /api/v1/auth/mfa/recovery/verify`

### 3.5 Passkeys (WebAuthn) — phase B

- Dependency: `com.yubico:webauthn-server-attestation`
- `WebAuthnRegistrationService`, `WebAuthnAuthenticationService`
- APIs: register/options, register/verify, auth/options, auth/verify
- `login.html` + hosted MFA step UI
- Set `implemented: true` for `m_passkey`

### 3.6 Admin + UI

- Remove “Coming soon” badges when `implemented: true`
- User self-service MFA in profile or hosted account page
- Admin: enroll/reset MFA (existing `UserAdminService` extended)

**Verification ×2:**
1. E2E: enroll TOTP → password login → MFA step → session
2. E2E: wrong TOTP 5× → locked / challenge invalidated
3. E2E: recovery code works once only

---

## Epic 4 — Dev vs prod settings defaults (P1)

### 4.1 Production settings preset (platform bootstrap)

When `SECUREONE_ENV=production`, seed/override:

| Setting | Dev | Prod |
|---------|-----|------|
| SMS provider | `log` | none until configured |
| `m_sms` | optional on | off until SMS configured |
| Access token TTL | 3600 | 900 |
| `self_registration` | on for testing | off by default |
| Swagger | on | off |
| Password min length | 12 | 12 (keep) |
| Recovery `requireVerifiedChannel` | true | true |

`PlatformSettingsBootstrap.java` — profile-aware defaults via `@Profile` or `EnvironmentPostProcessor`

### 4.2 Admin UI behavior

- Dev: show SMS log hints, MailHog links, “Send test” everywhere
- Prod: hide log provider option or show warning; require configured SMS before enabling phone OTP
- `AuthenticationSettingsPanel` — hide unimplemented federation toggles in prod (read-only)

**Verification ×2:**
1. Fresh prod bootstrap → no `log` SMS, no Swagger
2. Dev bootstrap → current behavior preserved

---

## Epic 5 — “Coming soon” → implemented or gated (P1)

| Feature | Action | Priority |
|---------|--------|----------|
| TOTP MFA | Epic 3 | P0 |
| Passkeys | Epic 3B | P1 |
| Push MFA | Keep `implemented: false`; hide in prod | P3 |
| Google/GitHub OIDC federation | Epic 6 stub or defer Phase 3 | P2 |
| SAML / External OIDC | **Done** — OIDC + SAML 2.0 SP (SSO, IdP-initiated, SLO); see doc 23 | — |
| Session revoke API | `DELETE /api/v1/account/sessions`, `DELETE /api/v1/account/sessions/{id}` | P1 |
| `clientUrls.magicLink` | Add to integration config + manifest | P2 |
| Token reuse detection | Wire SAS refresh token rotation + reuse handler | P1 |
| Leaked password check | HaveIBeenPwned k-anonymity on password set/reset | P2 |
| Admin impersonation | Phase 2 roadmap — defer | P3 |
| Service accounts / API keys | Phase 2 — defer | P2 |

---

## Epic 6 — CI, tests, and quality gate (P0)

### 6.1 CI pipeline (`.github/workflows/ci.yml`)

- auth-server: `./gradlew test check`
- admin-web: `npm run build && npm run lint`
- Flyway validate
- Dependency scan (OWASP or Snyk)
- SAST (optional Semgrep)

### 6.2 Test suites to add

| Suite | Location | Covers |
|-------|----------|--------|
| Unit | `*Test.java` | Policy sanitization, lockout, TOTP, token mapping |
| Integration | `@SpringBootTest` | OAuth JDBC, MFA flow, tenant isolation |
| E2E scripts | `scripts/test-*.ps1` | Full platform + app flows |

### 6.3 Master E2E script (new)

`scripts/test-full-platform.ps1` — runs in order:

1. Health + manifest
2. Platform settings CRUD (verification, SMS, password, flags, exposure)
3. Application settings (same)
4. User CRUD + roles + permissions
5. Signup link + signup OTP (email + SMS log)
6. Login password + login OTP
7. MFA TOTP step-up (after Epic 3)
8. Forgot password link + OTP recovery
9. OAuth PKCE (`test-external-client.ps1`)
10. Admin audit log entries present

**Verification ×2:** Run full script on dev stack; run subset on staging/prod smoke.

---

## Epic 7 — Observability & ops (P1)

- Micrometer + Prometheus (`/actuator/prometheus`)
- OpenTelemetry tracing (optional)
- Structured JSON logging in prod profile
- Helm chart skeleton `deploy/helm/secureone/`
- Runbooks: `docs/runbooks/` (deploy, rollback, key rotation, incident)

---

## Epic 8 — Enterprise protocols (P2 — Phase 3, post-GA)

Defer until Epics 0–6 complete:

- Identity brokering (Google/GitHub OIDC)
- SAML 2.0
- SCIM provisioning
- PAR, DPoP, Token Exchange
- GDPR export/delete APIs
- Multi-region active-active

---

## Implementation order (sprints)

| Sprint | Epics | Deliverable |
|--------|-------|-------------|
| **S1** | 0, 1.1, 1.2 | Dev/prod profiles + OAuth JDBC + shared JWK |
| **S2** | 1.3, 2.1, 2.2 | Token policy wired + lockout + rate limits |
| **S3** | 3.1–3.4, 4 | TOTP MFA at login + prod defaults |
| **S4** | 3.5, 5 (sessions), 6 | Passkeys + session revoke + CI + E2E suite |
| **S5** | 2.3, 2.4, 7 | RLS + audit IP + observability + Helm |
| **S6** | 5 (remaining), hardening | Leaked passwords, token reuse, integration checklist expansion |
| **S7+** | 8 | Federation, SAML, SCIM (enterprise sales driven) |

---

## Full test matrix (final gate)

Run **twice** (dev profile + staging profile) before declaring “complete”.

### Platform (super-admin)

- [ ] Login (form + session API)
- [ ] Settings → For applications (exposure toggles)
- [ ] Authentication → Sign-in / Verification & OTP / MFA / Password policy
- [ ] Feature flags (recovery_otp, self_registration, etc.)
- [ ] Notifications / SMTP test
- [ ] OAuth clients registry
- [ ] Tenants, applications, users (platform scope)
- [ ] Audit log readable
- [ ] Dev banner visible only in dev

### Per application

- [ ] Integration checklist all green
- [ ] Public manifest includes verification + account endpoints
- [ ] App-scoped verification policy override
- [ ] App-scoped SMS override
- [ ] App RBAC: roles, permissions, groups
- [ ] User roster, import, tenant access
- [ ] MFA tab enable + user MFA enroll (post Epic 3)

### End-user flows (public API)

- [ ] `GET /applications/{id}` manifest
- [ ] `GET /applications/{id}/signup` options
- [ ] Signup link mode
- [ ] Signup OTP email
- [ ] Signup OTP SMS (log provider in dev)
- [ ] Verify OTP confirm
- [ ] Session login (email + phone identifier)
- [ ] OTP login email + SMS
- [ ] Magic link login
- [ ] Password login + **MFA TOTP step-up**
- [ ] Forgot password link
- [ ] Forgot password OTP + reset
- [ ] OAuth authorization code + PKCE + refresh
- [ ] Token revoke on logout

### Production-specific smoke

- [ ] `/docs` returns 403
- [ ] No mail links logged to console
- [ ] CORS allows only configured origins
- [ ] Two auth-server instances behind LB: OAuth + JWT work on both

---

## Files index (primary touch points)

| Area | Paths |
|------|-------|
| Profiles | `application-dev.yml`, `application-prod.yml` |
| OAuth persistence | `OAuth2AuthorizationServerPersistenceConfig.java`, `V49__*.sql` |
| JWK | `OAuth2JwkConfig.java`, `V50__*.sql` |
| Token policy | `ApplicationRegisteredClientRepository.java`, `TokenSettingsMapper.java` |
| Lockout | `AccountLockoutService.java`, `TenantUserDetailsService.java` |
| Rate limit | `RateLimitFilter.java`, `RedisRateLimiter.java` |
| MFA | `auth/mfa/*`, `V52__*.sql`, `AuthSessionService.java`, `login.html` |
| Dev/prod UI | `config.ts` |
| Tests | `scripts/test-full-platform.ps1`, `.github/workflows/ci.yml` |
| Docs | `08-roadmap.md` (update checkboxes), `19-verification-and-otp.md` |

---

## What “complete” means

**Definition of done for this program:**

1. All P0 epics (0–3, 6) shipped and test matrix green ×2
2. No critical multi-replica blockers
3. TOTP MFA at login working end-to-end
4. Dev and prod profiles behave differently in UI and config
5. CI green on every PR
6. `08-roadmap.md` Phase 2 items checked for delivered scope

**Not required for v1 “complete”** (explicitly Phase 3+): SAML, SCIM, multi-region, GDPR portal, IGA, ABAC/ReBAC.

---

## Next action

**Start Sprint 1 (Epic 0 + 1.1 + 1.2)** in Agent mode:

1. Add Spring profiles + environment banner
2. JDBC OAuth authorization store
3. Persistent JWK keys
4. Update `.env.example` and `16-environment-setup-and-operations.md`

Then proceed sprint-by-sprint with E2E verification after each epic.

---

*Plan version: 1.0 — 2026-07-03. Review after each sprint; update checkboxes in this file and `08-roadmap.md`.*
