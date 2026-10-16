# 05 — Authentication & Authorization Standards

SecureOne adopts the modern, consolidated standards set so existing OAuth/OIDC client libraries work out of the box.

## Protocols

| Standard | Use | Phase |
|---|---|---|
| **OAuth 2.1** | Consolidated best practices: PKCE everywhere, no implicit flow, no password grant | MVP |
| **OpenID Connect (OIDC)** | Authentication, ID tokens, discovery (`/.well-known/openid-configuration`) | MVP |
| **WebAuthn / FIDO2** | Passkeys / hardware MFA (the strategic primary factor) | 2 |
| **TOTP (RFC 6238)** | App-based 2FA fallback | 2 |
| **SAML 2.0 (Web SSO, SP role)** | Enterprise SSO with company IdPs (any standards-compliant IdP): SP- and IdP-initiated sign-in, single logout, signed requests, encrypted assertions | 3 (implemented) |
| **Device Authorization Grant (RFC 8628)** | TVs, consoles, CLIs: sign in on another device | 3 (implemented) |
| **AS Metadata (RFC 8414)** | `/.well-known/oauth-authorization-server` | MVP (implemented) |
| **Protected Resource Metadata (RFC 9728)** | `/.well-known/oauth-protected-resource` for SecureOne's own bearer APIs | 3 (implemented) |
| **SCIM 2.0** | User provisioning/deprovisioning | 3 |
| **Token Exchange (RFC 8693)** | Delegation / impersonation, service-to-service token swap | 2–3 (implemented) |
| **PAR (RFC 9126)** | Pushed Authorization Requests (tamper-proof request params) | 3 (implemented) |
| **DPoP (RFC 9449) / mTLS (RFC 8705)** | Sender-constrained (proof-of-possession) tokens | 3 |
| **CIBA** | Decoupled / back-channel authentication | 3 |
| **JARM** | Signed authorization responses | 3 |
| **UMA 2.0** | User-managed access / resource sharing | 3 |
| **FAPI 2.0** | Financial-grade / high-assurance client profile | 4 |

See [Enterprise Capabilities](10-enterprise.md) for the full enterprise feature set; phases are defined in the [Roadmap](08-roadmap.md).

## Supported flows

| Flow | For |
|---|---|
| **Authorization Code + PKCE** | Web apps, SPAs, mobile, native |
| **Client Credentials** | Machine-to-machine / service accounts |
| **Refresh Token (with rotation)** | Long-lived sessions |
| **Device Authorization Grant (RFC 8628)** | CLIs / TVs — implemented (`/oauth2/device_authorization`, hosted page `/account/device.html`) |
| **CIBA** | Decoupled auth (approve on a second device) — Phase 3 |
| **Token Exchange** | Delegation / impersonation between services — implemented (`grant_type=urn:ietf:params:oauth:grant-type:token-exchange`) |

> The Authorization Code flow with PKCE is the default for all interactive clients. The legacy Implicit and Resource Owner Password grants are **not** supported.

For how integrated applications choose **hosted SecureOne pages vs a custom login UI** (magic link, session login, email links), see [Authentication UI integration](13-auth-ui-integration.md).

## Tokens

- **Access token:** short-lived (5–15 min) **JWT**, signed with **RS256 or ES256**, published via **JWKS** with key rotation (multiple active keys overlap during rotation).
- **ID token:** JWT per OIDC, carries identity claims + `nonce`.
- **Refresh token:** opaque, stored **server-side and hashed**, revocable, rotated on each use with **reuse detection** (replay revokes the whole token family).
- **Claims:** `sub`, `tenant`, `roles`, `permissions`, `scope`, standard OIDC profile claims. Roles/permissions resolved from `user_role` at issuance time.
- **Introspection (RFC 7662)** and **Revocation (RFC 7009)** endpoints provided for opaque validation and logout-everywhere.

## Endpoints (Spring Authorization Server)

| Endpoint | Purpose |
|---|---|
| `/.well-known/openid-configuration` | Discovery |
| `/oauth2/authorize` | Authorization (code flow) |
| `/oauth2/token` | Token issuance, refresh, **token exchange** (RFC 8693) |
| `/oauth2/jwks` | Public signing keys |
| `/oauth2/introspect` | Token introspection |
| `/oauth2/revoke` | Token revocation |
| `/oauth2/par` | Pushed Authorization Requests (RFC 9126); returns `request_uri` for `/oauth2/authorize` |
| `/oauth2/backchannel-logout` | OIDC back-channel logout (ack stub; full validation pending) |
| `/userinfo` | OIDC user claims (GET or POST; `401` + `WWW-Authenticate` without a token) |
| `/connect/logout` | RP-initiated logout (also signs out at the SAML IdP for SAML sessions) |
| `/oauth2/device_authorization` | Device authorization (RFC 8628): `device_code`, `user_code`, `verification_uri(_complete)`, `interval` |
| `/oauth2/device_verification` | Where the hosted device page sends the user to approve (always an explicit Allow / Deny) |
| `/.well-known/oauth-authorization-server` | RFC 8414 metadata |
| `/.well-known/oauth-protected-resource` | RFC 9728 metadata |
| `/api/v1/auth/federation/saml/{alias}/metadata` | SAML SP metadata (entity ID, ACS, SLO, signing + encryption certificate) |
| `/api/v1/auth/federation/saml/acs/{providerId}` | SAML assertion consumer service (HTTP-POST) |
| `/api/v1/auth/federation/saml/slo/{providerId}` | SAML single logout (LogoutRequest / LogoutResponse, Redirect or POST) |

## MFA / 2FA (graded, passkey-first)

SecureOne treats MFA as a **graded ladder** and steers users toward phishing-resistant factors. Every factor is supported, but defaults and policies favor the strongest.

| Tier | Factor | Notes |
|---|---|---|
| Weakest | **SMS / voice OTP** | Phishable + SIM-swap; **last-resort fallback only**, never default (NIST discourages). |
| Basic | **Email OTP** | Only as strong as the mailbox; low-risk fallback / recovery. |
| Good | **TOTP** (authenticator app) | No SIM-swap, but still phishable. Standard fallback. |
| Better | **Push w/ number-matching** | Defeats MFA-fatigue bombing; still relay-phishable. |
| **Best** | **WebAuthn / FIDO2 passkeys** | **Phishing-resistant** (origin-bound). The strategic primary factor; supports **passwordless** login. |
| Highest | **Hardware FIDO2 keys / smart cards (PIV/CAC) + attestation** | Meets **NIST AAL3**; for admins / regulated tenants. |

**Principles**
- **Passkeys-first**: WebAuthn is the primary factor and can be the *entire* login (passwordless), not just a 2nd step.
- **TOTP** as the standard fallback; **SMS / email OTP** offered only when policy explicitly allows.
- **Recovery codes** mandatory at enrollment (stored hashed, single-use).
- TOTP seeds and any factor secrets stored **encrypted with a KMS-managed key**, never plaintext.
- MFA challenges held in **Redis** (short TTL); verification is **rate-limited + lockable**; TOTP has replay prevention + bounded drift window.

**Enterprise controls**
- **Phishing-resistant enforcement** — a tenant/role can require *only* passkeys/FIDO2, rejecting TOTP/SMS for admins or sensitive apps.
- **Assurance levels (NIST 800-63B)** — track **AAL1/2/3**; emit `acr`/`amr` claims so relying apps can demand a level.
- **WebAuthn attestation** — verify authenticator model (AAGUID); allow-list hardware keys, block unknown authenticators.
- **Adaptive / step-up** — risk signals (new device, geo-velocity, IP reputation) trigger step-up; sensitive operations force re-auth via `max_age` / `acr_values`.
- **Per-tenant/org/role MFA policy** — required factors, grace periods, remembered-device TTL, reauth intervals, synced-vs-device-bound passkey rules.

**Secure recovery** — recovery requires a verified second factor or audited admin-assisted flow; **never** an email-only reset that silently bypasses MFA.

Data model: see `mfa_factor` and `mfa_policy` in [Data Model](04-data-model.md#domain-5--mfa). Libraries: Yubico **java-webauthn-server** for WebAuthn/FIDO2, RFC 6238 for TOTP.

## Federation & brokering (enterprise — Phase 3)

- **Implemented:** Google, GitHub and generic OIDC sign-in, and **SAML 2.0 as a service provider** — per application or tenant-wide, configured from IdP metadata (or entity ID + SSO URL + certificate):
  - SP-initiated sign-in (signed AuthnRequest, Redirect or POST binding) and opt-in **IdP-initiated** sign-in to an operator-set landing URL;
  - validation of signature, audience, recipient, validity window and `InResponseTo` (Spring Security / OpenSAML 5), **encrypted assertions**, and a **replay cache** so each response is accepted once;
  - **single logout** both ways: `/connect/logout` continues to the IdP, and an IdP's LogoutRequest ends every SecureOne session of that user;
  - just-in-time accounts or link-by-email, MFA and app-access rules applied as for any sign-in.
  See [23 — Integration guide](23-application-integration-guide.md#enterprise-sso-saml-20) and the [SAML flows diagram](diagrams/auth-flow-saml.drawio).
- **Not yet:** SecureOne as a SAML **IdP** (issuing assertions to SAML-only apps), artifact binding, SOAP back-channel logout.
- **Identity brokering** from many external IdPs (enterprise OIDC, SAML, social) with **home-realm discovery** and **JIT provisioning**.
- **LDAP / Active Directory** user federation (sync or on-demand, group→role mapping).
- External IdP logins link to `user_identity`. Full detail in [Enterprise Capabilities](10-enterprise.md#1-identity-federation--brokering).

## Authorization model

- **RBAC scoped per application/tenant**: `permission` (atomic) → `role` (bundle) → `user_role` (grant).
- **Multi-role + composite/hierarchical roles**: a principal can hold many roles, and a **composite role can include other roles** (`role_composite`). Effective permissions = transitive union of direct + child + group-derived roles, resolved at token issuance.
- All checks flow through a **`PolicyEvaluator` interface**.
- **Future (enterprise):** ABAC/ReBAC via **OpenFGA**, **policy-as-code** (OPA/Cedar), and **UMA 2.0** resource sharing — all new `PolicyEvaluator` implementations, no caller changes. See [Enterprise Capabilities](10-enterprise.md#3-fine-grained-authorization-pdp--pep).

## Standards compliance targets

- Run against the **OpenID Connect conformance suite**.
- Follow **OAuth 2.1** and the OAuth Security BCP (RFC 9700).
- Align with **OWASP ASVS** (see [Security](07-security.md)).
