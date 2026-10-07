# 21 — Pending implementation guide

**Purpose:** Single backlog for work that remains after Phases 0–2 completion and Phase 3–4 **foundations**. Each item lists **what**, **where**, **how**, dependencies, and verification.

**Last updated:** 2026-07

**Recently completed (July 2026 sprint):**
- Admin UI: Organizations + Access requests panels on tenant detail
- IGA approve → auto-grant role + access-request webhooks (`AccessRequestGrantService`, V58/V59)
- Startup fixes: indexed Redis sessions, OAuth JDBC delegate, `-parameters` compiler flag
- TypeScript SDK: `introspectToken`, `revokeToken`
- **GDPR hosted portal:** `/account/privacy.html` + session bridge (`/api/v1/account/privacy/session/*`); link from `security.html`
- **Token Exchange RFC 8693:** `OAuthTokenExchangeService` + filter (replaces 501 stub); unit tests
- **PAR hardening:** Redis-backed one-time `request_uri` consume + authorize merge (`PushedAuthorizationRequestService`, `PushedAuthorizationRequestFilter`)
- **M2M expansion:** `/api/v1/m2m/health`, `/tenant`, expanded `/whoami`; per-key rate limit (120/min); scopes on key creation in admin UI
- Admin-web CI: ESLint 0 errors (`useDeferredEffect` pattern)
- Integration catalog: SCIM groups, M2M routes, PAR, token exchange, introspect, revoke

---

## Quick reference — status matrix

| Item | Status | Effort | Blocking deps |
|------|--------|--------|---------------|
| SAML 2.0 IdP login | Metadata stub only | 4–8 wks | OpenSAML / Spring SAML |
| OpenFGA / ABAC / ReBAC | Not started | 4+ wks | Policy engine deploy |
| Full IGA campaigns | Stub API only | 6+ wks | Workflow + notifications |
| Adaptive / risk auth | Trusted device only | 3–4 wks | Risk signal pipeline |
| Advanced MFA (AAGUID, PIV, mTLS) | Not started | 4+ wks | Hardware / attestation |
| Token Exchange RFC 8693 | **Done** | — | `OAuthTokenExchangeService` |
| PAR (one-time `request_uri`) | **Done** | — | Redis store + authorize merge |
| Back-channel logout (full hardening) | Ack-only stub | 1–2 wks | Logout token validation |
| Push MFA (FCM/APNs) | Not started | 2 wks | Mobile push credentials |
| Multi-region HA | Not started | 8+ wks | Infra + replication |
| MAU billing / quotas UI | Hooks only | 3–4 wks | Metering pipeline |
| HSM / BYOK | Runbook only | 4+ wks | HSM vendor |
| FAPI 2.0 | Not started | 4+ wks | Profile audit |
| OpenTelemetry tracing | Prometheus only | 1–2 wks | OTel collector |
| OIDC conformance + pen test | Not started | 2–4 wks | External process |
| GDPR full portal | **Partial** — hosted export/delete | 2–3 wks | Consent store, residency, grace-period deletion |
| Terraform + Java/Python/Go SDKs | Not started | ongoing | API stability |
| npm publish TypeScript SDK | Local package | 1 wk | CI + npm org |
| M2M routes + rate limits | **Done** | — | health, tenant, whoami, 120/min/key |
| IGA approve → grant role | **Done** | — | Auto-grant on approve |
| Self-service signup → webhook | Done | — | — |

---

## 1. SAML 2.0 IdP login (full)

### Current state
- SP metadata stub: `GET /api/v1/auth/federation/saml/{alias}/metadata`
- Login returns **501**: `FederationAuthController`, `IdentityProviderService`
- Federation providers can be configured as SAML type but login throws

### Target files
| Layer | Path |
|-------|------|
| Controller | `apps/auth-server/.../federation/FederationAuthController.java` |
| Service | `apps/auth-server/.../federation/IdentityProviderService.java` |
| New | `.../federation/saml/SamlAuthnRequestBuilder.java`, `SamlResponseValidator.java`, `SamlAssertionMapper.java` |
| Config | `apps/auth-server/build.gradle` — add OpenSAML or Spring Security SAML2 SP |
| UI | `apps/admin-web/.../FederationProvidersPanel.tsx` — SAML-specific fields (entityId, SSO URL, cert) |

### Implementation steps
1. Add OpenSAML 4.x (or `spring-security-saml2-service-provider`) dependency.
2. Extend `identity_provider` table / DTO with SAML fields: `entityId`, `ssoUrl`, `x509Cert`, `nameIdFormat`, `signRequests`.
3. Implement `GET .../saml/{alias}/start` → build AuthnRequest, redirect to IdP SSO URL.
4. Implement ACS endpoint `POST .../saml/{alias}/acs` → validate signature, clock skew, audience; map NameID → local user (JIT or link).
5. Wire JIT provisioning via existing `IdentityProviderService.linkOrCreateUser`.
6. Add integration test with sample IdP metadata (Keycloak or samltest.id offline fixture).

### Verification
- Manual SP-initiated flow against Keycloak SAML IdP
- Unit tests for assertion validation (expired, wrong audience, invalid signature)
- Update `docs/08-roadmap.md` Phase 3 SAML checkbox

---

## 2. OpenFGA / ABAC / ReBAC / OPA

### Current state
- RBAC only: `RoleRepository`, `UserRoleRepository`, `PolicyEvaluator` abstraction exists but no external engine

### Target files
| Layer | Path |
|-------|------|
| Abstraction | `apps/auth-server/.../rbac/PolicyEvaluator.java` (extend) |
| New | `.../authz/openfga/OpenFgaPolicyEvaluator.java` |
| New | `.../authz/opa/OpaPolicyEvaluator.java` |
| Deploy | `deploy/helm/secureone/templates/openfga.yaml` (optional subchart) |
| Docs | `docs/10-enterprise.md` — authorization section |

### Implementation steps
1. Deploy OpenFGA (or use managed) — store tuples: `user:{id}` `member` `group:{id}`, `user:{id}` `can_read` `document:{id}`.
2. Implement `OpenFgaPolicyEvaluator.check(subject, action, resource)` calling OpenFGA API.
3. Add `@PreAuthorize` or servlet filter for resource-server routes that delegate to evaluator.
4. Admin UI: policy model viewer (read-only) under Platform settings.
5. Optional: OPA sidecar for Cedar/Rego policies on admin mutations.

### Verification
- Integration test: deny/allow tuple change reflects in API within 1s
- Document model in `docs/` with example tuples

---

## 3. Full IGA (campaigns, SoD, JIT workflows)

### Current state
- `access_request` table + CRUD API: `AccessRequestAdminController`, `AccessRequestService`
- Admin UI: `AccessRequestsPanel.tsx` (create / approve / deny)
- **Done:** Approve auto-grants tenant or application roles via `AccessRequestGrantService` (V58 `application_id`, V59 `granted_role_name`)
- **Gap:** No certification campaigns, SoD rules, or time-bound grants

### Target files
| Layer | Path |
|-------|------|
| Service | `apps/auth-server/.../iga/AccessRequestService.java` — add `onApproved` hook |
| New | `.../iga/CertificationCampaignService.java`, `SodRuleService.java` |
| Migration | `V58__iga_campaigns.sql` — campaigns, campaign_items, sod_rules |
| UI | `CertificationCampaignsPanel.tsx`, extend `AccessRequestsPanel` |
| Notifications | `AccountNotificationService` — campaign reminders |

### Implementation steps
1. ~~On `approve`, grant role~~ — **done** in `AccessRequestGrantService`.
2. Add `certification_campaign` + items linking users/roles; scheduled job marks overdue.
3. SoD rules: before grant, query conflicting role pairs; reject with 409.
4. Time-bound access: store `expiresAt` on grant; scheduled revoke job.
5. Webhook events: `access_request.approved`, `access_request.denied`, `certification.completed`.

### Verification
- `AccessRequestServiceTest` + new campaign tests
- E2E: request → approve → user has role in tenant workspace

---

## 4. Adaptive / risk-based authentication

### Current state
- Trusted device cookie: `TrustedDeviceService`, `secureone_trust` cookie skips MFA
- No IP velocity, impossible travel, or device fingerprint scoring

### Target files
| Layer | Path |
|-------|------|
| New | `.../authn/risk/RiskScoringService.java`, `RiskSignalCollector.java` |
| Integration | `MfaLoginService.shouldChallengeFormLogin` — consult risk score |
| Settings | Platform `auth_policy.risk` defaults in `PlatformSettingsBootstrap` |
| UI | Risk thresholds in platform settings panel |

### Implementation steps
1. Collect signals on login: IP, geo (MaxMind DB), user-agent hash, prior device cookie.
2. Score 0–100; if above threshold, force MFA step-up even on trusted device.
3. Store last login geo/IP on `user_account` or `login_history`.
4. Admin audit: `risk.challenge_forced` events.

---

## 5. Advanced MFA (AAGUID, AAL2/3, PIV, mTLS)

### Target files
- `apps/auth-server/.../mfa/WebAuthnService.java` — attestation statement validation, AAGUID allow-list from tenant policy
- `MfaPolicyDefaults.java` — `allowedAaguids`, `minimumAssuranceLevel`
- New: `ClientCertificateAuthenticationFilter` for mTLS at reverse proxy + header trust

### Steps
1. Parse AAGUID from registration; compare to tenant allow-list.
2. Map WebAuthn UV flag + hardware attestation → `acr` amr values `aal2` / `aal3`.
3. PIV: document integration via browser platform authenticator + government cert trust store.

---

## 6. OAuth protocol hardening

### Token Exchange (RFC 8693) — **DONE**

Implemented in `OAuthTokenExchangeService` + `OAuthTokenExchangeFilter`:
- Grant type `urn:ietf:params:oauth:grant-type:token-exchange` on `POST /oauth2/token`
- Validates client credentials, decodes `subject_token` JWT, checks `azp`/audience, issues delegated JWT
- Tests: `OAuthTokenExchangeServiceTest`

### PAR (RFC 9126) — **DONE**

Implemented in `PushedAuthorizationRequestService` + `PushedAuthorizationRequestFilter`:
- `POST /oauth2/par` stores params in Redis, returns `request_uri`
- `GET /oauth2/authorize?request_uri=…` merges stored params (one-time consume via `getAndDelete`)
- Tests: `PushedAuthorizationRequestServiceTest`

### Back-channel logout — **partial**

- `POST /oauth2/backchannel-logout` returns 200 (ack-only stub)
- **Remaining:** logout token validation per OIDC Back-Channel Logout spec, session termination, conformance tests

### DPoP / CIBA / JARM
- Not started — see `docs/10-enterprise.md`; add after core SAS hardening

---

## 7. Push MFA

### Current state
- Manifest marks push as `implemented: false`
- SMS/email OTP work

### Implementation
1. Add `PushMfaProvider` interface; implementations `FcmPushProvider`, `ApnsPushProvider`
2. Store device tokens on `mfa_factor` metadata JSON
3. Challenge flow: send push with approve/deny deep link or number matching
4. Env: `SECUREONE_FCM_CREDENTIALS`, `SECUREONE_APNS_KEY`

---

## 8. Multi-region HA / active-active

### Not in application code — infrastructure program

### Steps
1. PostgreSQL: read replicas + Citus or CRDB for multi-write (or single-primary with regional read pools)
2. Redis: Redis Cluster or ElastiCache Global Datastore for session replication
3. OAuth: shared JDBC authorization store (already) + sticky-less JWT validation
4. Flyway: single migration leader; document in `docs/runbooks/multi-region.md`
5. Helm: pod topology spread, PDB, HPA per `deploy/helm/secureone/`

---

## 9. Usage metering / MAU billing

### Current state
- `TenantQuotaService` enforces max users/applications on create

### Implementation
1. New table `usage_daily_snapshot` (tenant_id, date, mau, logins, api_calls)
2. Nightly job aggregate from `login_history` + audit
3. Admin UI: `TenantUsagePanel.tsx` with charts
4. Optional: Stripe metered billing webhook

---

## 10. HSM / BYOK / automated key rotation

### Current state
- Manual JWK rotation runbook in `docs/runbooks/`

### Implementation
1. Integrate AWS CloudHSM / GCP KMS for signing key storage
2. `JwkSource` implementation delegating sign operations to KMS
3. Scheduled rotation: generate new key, publish JWKS with both kids, retire old after TTL
4. Tenant BYOK: store customer KMS key ARN in `tenant_settings`

---

## 11. FAPI 2.0

Requires: PAR (partial), DPoP, strict redirect URI, confidential clients, sender-constrained tokens. Audit existing endpoints against [FAPI 2.0 Security Profile](https://openid.net/specs/fapi-security-profile-2_0-final.html); gap list in CI checklist.

---

## 12. OpenTelemetry

### Current state
- Prometheus metrics via Spring Actuator

### Implementation
1. Add `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`
2. Instrument login, token, SCIM spans
3. Helm: OTel collector sidecar or DaemonSet
4. Grafana dashboards JSON in `deploy/grafana/`

---

## 13. OIDC conformance + penetration test

External process — not code-complete in-repo.

1. Run [OIDC conformance suite](https://www.certification.openid.net/) against staging
2. Fix failures in SAS configuration / custom endpoints
3. Engage third-party pen test; track findings in GitHub Security tab
4. Update GA gate in `docs/08-roadmap.md`

---

## 14. GDPR / CCPA full portal

### Current state — **partial (hosted portal shipped)**

- **Bearer APIs:** `GET /api/v1/account/privacy/export`, `POST /api/v1/account/privacy/delete-request` (`AccountPrivacyController`)
- **Session bridge:** `GET /api/v1/account/privacy/session/export`, `POST /api/v1/account/privacy/session/delete-request` (`AccountPrivacySessionController`) — for logged-in users without a bearer token
- **Hosted UI:** `static/account/privacy.html` — export JSON download + deletion request; linked from `account/security.html`

### Remaining
1. Consent records table + API
2. Data residency flags on tenant
3. Extend export to include audit trail summary, federation links
4. Deletion workflow: grace period, admin completion queue, hard delete job

---

## 15. SDKs and Terraform

| Package | Location | Next steps |
|---------|----------|------------|
| TypeScript | `packages/secureone-client` | Publish to npm; add SCIM, admin client |
| Java | `packages/secureone-java-client` (new) | OpenAPI generator from auth-server spec |
| Python / Go | new packages | Same generator |
| Terraform | `terraform-provider-secureone` (new repo) | Resources: tenant, application, oauth_client, user |

---

## 16. M2M API key coverage — **DONE**

Implemented:
- Routes: `GET /api/v1/m2m/health`, `/whoami` (apiKeyId, scopes), `/tenant`
- `ApiKeyAuthenticationFilter` on `/api/v1/m2m/**` with per-key rate limit (120 req/min via `RedisRateLimiter`)
- Key creation returns `scopes` + `scopeNote` in `ServiceAccountService`; shown in `ServiceAccountsPanel.tsx`

**Files:** `M2mController.java`, `ApiKeyAuthenticationFilter.java`, `ServiceAccountContext.java`

---

## 17. IGA approve → auto-grant role — **DONE**

Implemented in `AccessRequestGrantService` — on approve, resolves tenant or application role (by UUID or name) and grants to target user (or requester). Migration V58 adds optional `application_id` on access requests for disambiguating application roles.

---

## How to pick up any item

1. Read this section + linked source files
2. Add Flyway migration if schema changes needed (`docs/18-flyway-migration-files.md`)
3. Implement service + controller + tests (`apps/auth-server/src/test/java`)
4. Wire admin UI if operator-facing (`apps/admin-web/src/components`)
5. Update `docs/08-roadmap.md` checkbox
6. Run verification:
   ```bash
   cd apps/auth-server && ./gradlew.bat test
   cd apps/admin-web && npm run build
   ```

---

## Related documents

- [08 — Roadmap](08-roadmap.md) — phase checklist
- [20 — Production completion master plan](20-production-completion-master-plan.md) — epic breakdown
- [10 — Enterprise capabilities](10-enterprise.md) — feature context
- [Runbooks](runbooks/) — operations
