---
title: Security
summary: Security practices and protections
order: 4
audience: public
---

# Security

Security is the product. This document lists non-negotiables and scalability practices.

## Non-negotiable security controls

| Area | Control |
|---|---|
| Password hashing | **Argon2id** with tuned cost; rehash-on-login when params upgrade. Never MD5/SHA/bcrypt-only. |
| Secrets at rest | Encrypt TOTP seeds, client secrets, API keys; **hash** refresh tokens & API keys (store hash only). Stored secrets (SMTP/SMS credentials, IdP client secrets) **and the OAuth JWT + SAML SP private keys** are AES-256-GCM encrypted with `SECUREONE_ENCRYPTION_KEY` (kept outside the database; a database copy alone cannot decrypt them). Keys stored in plaintext by older versions are re-encrypted on first read without rotating them. |
| MFA | **Passkeys/FIDO2 first** (phishing-resistant); TOTP fallback; SMS/email OTP only when policy allows. Per-tenant `mfa_policy`, AAL tracking, attestation allow-lists, step-up for sensitive ops. Secure recovery never bypasses MFA. |
| Brute-force defense | Progressive delays, account lockout (`locked_until`), IP + device throttling, CAPTCHA on abuse, leaked-password detection. |
| Refresh tokens | Rotation + **reuse detection** → replay revokes the whole token family. |
| Access tokens | Short TTL (5–15 min) + Redis-backed revocation for "log out everywhere." |
| Signing keys | Rotation with overlapping keys in JWKS; private keys from env/KMS (`SECUREONE_JWK_PRIVATE_KEY_PEM`, `SECUREONE_SAML_SP_KEY_PEM`) or generated once and stored encrypted. First-time creation is cluster-safe (Redis lock), so replicas never sign with different keys. |
| Redirects | `continue` after sign-in is honoured only for OAuth endpoints on this server (`/oauth2/authorize`, `/oauth2/device_verification`, relative or same host) — server side and in `login.html`, and for federation sign-in. |
| API vs browser errors | Unauthenticated API calls (`/userinfo`, `/oauth2/device_authorization`, `Accept: */*`) get `401` + `WWW-Authenticate`; only browser navigation to `/oauth2/authorize` / device verification is sent to the login page. |
| SAML | Signature, audience, recipient, validity window and `InResponseTo` validated (OpenSAML 5); AuthnRequests signed; encrypted assertions; each response accepted once (replay cache); IdP-initiated sign-in off unless the operator enables it with a fixed landing URL; LogoutRequests must be signed. The ACS and SLO endpoints are excluded from CORS (top-level form POSTs from the IdP, any `Origin`, including `null`). |
| Device grant | Approval is always an explicit Allow/Deny (POST with per-request `state`), never a bare link — a signed-in user following an attacker's code link cannot connect the attacker's device. Only users of the client's tenant admitted to the app may approve; Deny always denies; `slow_down` when polling too fast; code lookups rate-limited; replaying a used device code revokes the tokens it issued. |
| Transport | TLS everywhere, **HSTS**, secure/HttpOnly/SameSite cookies. |
| Login UI hardening | Strict **CSP**, anti-clickjacking (frame-ancestors), XSS hygiene — critical on consent/login pages. |
| Audit | **Append-only** audit log shipped to a SIEM; no update/delete paths. |
| Tenant isolation | `tenant_id` + Hibernate filters + **PostgreSQL RLS** backstop + **automated cross-tenant isolation tests**. |
| Input/authz | Server-side validation; deny-by-default authorization via `PolicyEvaluator`. |

## Secure SDLC

- **SAST** + **dependency scanning (SCA)** + **secret scanning** in CI on every PR.
- Pin dependencies; automated update PRs (Dependabot/Renovate).
- **Third-party penetration test** before GA, and periodically after.
- Target **OWASP ASVS** Level 2+ and follow the **OAuth 2.0 Security BCP (RFC 9700)**.
- Run the **OpenID Connect conformance suite** in CI.
- Threat-model new auth surfaces before they ship.

## Highest-risk areas to test hardest

1. **Cross-tenant data leakage** — the #1 multi-tenant IAM bug class. Dedicated isolation test suite.
2. **Token handling** — rotation, reuse detection, revocation, signature/JWKS validation.
3. **Privilege escalation** — user → admin → platform-admin boundaries.
4. **Federation and devices** — SAML response forgery/replay, IdP-initiated CSRF login, device-code phishing, open redirects through `continue` (each exercised end to end, SAML against a real open-source SAML IdP running in Docker).
5. **Account recovery** — password reset & email verification token misuse; **password expiry** — automated reset emails, login block when `expires_at` is past, and `expiryDays = 0` for non-expiring passwords (see [settings governance — password expiry](../install/settings-governance.md#password-expiry-notifications)).

## Scalability practices

| Practice | Detail |
|---|---|
| Stateless services | `auth-server` keeps no local session state → horizontal scaling behind a load balancer. |
| Shared state in Redis | Sessions, refresh-token store/revocation, rate-limit counters, MFA challenges. |
| DB pooling | HikariCP; **PgBouncer** in front of Postgres at scale. |
| Read replicas | For read-heavy admin/audit queries. |
| Caching | JWKS, discovery docs, tenant config cached aggressively. |
| Async work | Emails, audit shipping, webhooks via a queue (Spring + Redis/broker). |
| Rate limiting | At the edge and per-client. |
| Partitioning | `audit_log` / `login_history` partitioned by time; archive cold data to object storage. |

## Operational security

- Secrets only in a **KMS/Vault/cloud secrets manager** in production — never committed, never in plain env files.
- Least-privilege DB and infra credentials.
- Centralized structured logging + metrics + tracing; alert on anomalous auth patterns.
- Backups with tested restore; documented key-rotation and incident-response runbooks.
