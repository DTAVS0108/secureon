---
title: Passkey-first, phishing-resistant MFA
order: 6
audience: internal
---

# Passkey-first, phishing-resistant MFA

**Status:** Accepted

## Context
MFA strength varies enormously. SMS OTP is phishable and SIM-swappable; TOTP is better but still phishable; only **WebAuthn/FIDO2 passkeys** are phishing-resistant (origin-bound). Enterprises increasingly require phishing-resistant MFA and NIST 800-63B AAL2/AAL3 assurance.

## Decision
Treat MFA as a **graded ladder** and make **WebAuthn/FIDO2 passkeys the primary factor** (including passwordless login). Support **TOTP** as the standard fallback and **SMS OTP / email OTP / push** as policy-gated last resorts. Provide a per-tenant/org/role **`mfa_policy`** (required factors, min AAL, attestation/AAGUID allow-lists, remembered-device TTL, reauth interval) and **step-up** authentication via `acr`/`amr`/`max_age`.

## Rationale
- Phishing-resistant auth eliminates the largest class of credential attacks.
- Passwordless passkeys are both stronger and better UX.
- A policy engine lets regulated tenants enforce hardware keys / AAL3 while others keep softer fallbacks.

## Consequences
- Richer `mfa_factor` schema (WebAuthn fields: credential_id, public_key, aaguid, sign_count, transports, attestation, backup flags) + new `mfa_policy` and `trusted_device` tables.
- SMS/email remain supported but discouraged and disabled by default for sensitive roles.
- Recovery flows must never bypass MFA (verified factor or audited admin-assisted recovery only).
- Use a vetted WebAuthn server library (Yubico java-webauthn-server) rather than hand-rolling.
