# Plan: two-step verification (2FA) with several options, including the MFA fixes

Status: **plan, not started.** Written 2026-10-20. Replaces the MFA parts of the
[security roadmap](security-roadmap.md) (gaps M1–M19, L1–L5, A1–A2), using the research in
[mfa-review.md](mfa-review.md).

## 1. Goal

Users protect their account with a **second step after their password** (or other first sign-in), choosing from
**several methods**. Admins decide which methods are allowed, how strong the second step must be, and whether
it is optional or required, with a grace period. Every sign-in path goes through the same check, and nobody
gets locked out without a way back in.

In the product it is called **two-step verification**: plainer than "MFA" and the term Google and Microsoft use.
"2FA" appears in short labels. The API and code keep `mfa` names, so nothing breaks for integrators.

## 2. The options

| Method | What the user does | Strength | Default | Notes |
|---|---|---|---|---|
| **Passkey** (fingerprint, face, device PIN) | Confirms on their phone or computer | **Strongest**, phishing-resistant | On, recommended first | WebAuthn with user verification **required** |
| **Security key** (USB/NFC, YubiKey) | Inserts or taps the key, enters its PIN | **Strongest**, phishing-resistant | On | Same WebAuthn code; shown separately so users recognise it |
| **Authenticator app** (Google/Microsoft Authenticator, 1Password…) | Types a 6-digit code | **Strong** | On | Existing TOTP; allow more than one app |
| **Text message (SMS) code** | Types a code sent to their verified phone | **Basic**: restricted under NIST (SIM swap) | **Off**; admin opt-in | Needs an SMS provider; warning shown to admins and users |
| **Email code** | Types a code sent to their verified email | **Basic**: NIST says email must not be a second factor | **Off**; admin opt-in | For consumer apps where nothing else works; warning shown |
| **Backup codes** (recovery codes) | Types one single-use code | Fallback only | Always, once any method exists | 10 codes, regenerate any time, warning when 3 or fewer remain |
| **Remember this device** | Ticks a box at the second step | Convenience, not a method | 0 days (off) | Listed and revocable; cleared on resets |

**Rules that keep it real two-step:**
- **The second step must be a different method from the first.** A sign-in that started with an email code or a
  magic link cannot finish with an email code; one started with an SMS code cannot finish with SMS. A passkey
  sign-in needs no second step, since it is already two factors.
- **SMS and email codes need a verified destination,** and changing the phone or email counts as changing a
  method (fresh verification plus notification).
- **SMS and email can't be someone's only method when the required strength is "Strong".**
- **Strength levels** decide what counts:

| Required strength (policy) | Accepted second steps |
|---|---|
| **Any** | all enabled methods |
| **Strong** (default for *Required*) | passkey, security key, authenticator app (SMS/email only as an extra, not alone) |
| **Phishing-resistant** (admin consoles, high-risk apps) | passkey, security key |

## 3. How it works for users

### 3.1 Sign-in

```
First step OK (password / email code / magic link / Google, OIDC, SAML / passkey)
   │
   ▼  one shared gate (every path)
   1. Account active, application access granted, not locked
   2. Passkey sign-in, or IdP proved MFA (when trusted)? ─────────────► signed in
   3. Trusted device for this user (not expired, not revoked)? ────────► signed in
   4. 2FA off for this app? ───────────────────────────────────────────► signed in
   5. User has methods that meet the required strength?
        └─ yes → "Verify it's you" with their default method
                 + "Try another way" (other methods, then backup codes)
                 + [ ] Remember this device for N days (if allowed)
                 wrong codes count per account → lockout + email
   6. No method
        ├─ Optional → reminder screen when due: Set up now · Remind me in 7 days
        ├─ Required, in grace → same screen: "Required by 3 Nov · 2 skips left"
        └─ Required, grace over → setup is part of the sign-in (no skip, never a dead end)
   ▼
Session: acr, amr (pwd/otp/sms/email/hwk/user/mfa), auth_time
```

**Second step:**

```
Verify it's you
  [ Use your passkey ]                      ← default = strongest the user has
  Try another way ▾
     Authenticator app code
     Text a code to ••• •• 4821
     Email a code to j•••@example.com
     Use a backup code
  [ ] Don't ask again on this device for 30 days
```

### 3.2 Setting it up

**Setup wizard** (from the reminder screen, a forced setup, or the account page):
1. **Choose a method.** Only allowed methods are shown, passkey first and marked "Recommended".
2. **Set it up:**
   - Passkey or key: one browser prompt.
   - App: QR code, then a code to confirm.
   - SMS or email: confirm the destination, then a code.
3. **Save your backup codes.** Shown once; download or copy; tick "I saved them".
4. **Optionally add a second method** ("Add another way to sign in in case you lose your phone").

### 3.3 Account › Two-step verification page

- **Status:** On / Off / Required by the administrator (with the deadline).
- **Methods:** each with its name, when it was added and last used, *Make default* and *Remove*.
  - Adding or removing a method asks for a fresh second step from the last 5 minutes.
  - When the account has a requirement, the last method can't be removed.
- **Backup codes:** how many remain, *Get new codes* (fresh step).
- **Remembered devices:** browser, approximate place, last used, *Forget*; *Forget all*.
- **Turn off two-step verification:** only when the policy is Optional; fresh step; email sent.

**Email notifications:**
- a method was added or removed;
- backup codes were used or regenerated;
- 2FA was turned off;
- repeated wrong codes;
- the account was locked;
- an admin reset or exempted the user.

## 4. Settings and who controls them

Settings are layered: platform, then tenant (new), then application. Each level sets a **lock mode** for the level
below: *Locked*, *Stricter only* or *Open* (see [security-roadmap.md §1](security-roadmap.md#1-who-decides-what)).

| Setting | Values | Default | Platform admin | Tenant super admin | Tenant / app admin |
|---|---|---|---|---|---|
| Two-step verification | Off / Optional / Required | Optional | ✏️ + minimum | ✏️ stricter | ✏️ stricter |
| Required strength | Any / Strong / Phishing-resistant | Strong | ✏️ + minimum | ✏️ stricter | ✏️ stricter |
| Methods allowed | passkey, key, app, SMS, email | passkey, key, app | ✏️ | ✏️ subset | ✏️ subset |
| Default method suggested | one of the allowed | passkey | ✏️ | ✏️ | ✏️ |
| Remind users without 2FA (Optional) / ask again after days / allow "Don't ask again" | on, 1–90, on/off | on, 7, off | ✏️ | ✏️ | ✏️ |
| Grace period / skips / enforce-from date / reminder emails | 0–180 d, 0–10, date, days | 14, 3, on save, 7+1 | ✏️ maximum | ✏️ ≤ max | ✏️ ≤ max |
| Remember device | 0–90 days | 0 | ✏️ maximum | ✏️ ≤ max | ✏️ ≤ max |
| Code expiry / wrong codes per challenge / per account before lock | 1–10 min, 1–10, 3–20 | 5, 5, 10 | ✏️ | 🔒 | 🔒 |
| Fresh step for sensitive actions (minutes) | 1–60 | 5 | ✏️ | ✏️ shorter | 🔒 |
| Trust the external IdP's MFA (`amr`) | per provider | off | ✏️ | ✏️ per provider | 🔒 |
| SMS/email code templates and senders | — | platform | ✏️ | ✏️ (if section open) | ✏️ (if section open) |
| **Admin console** | Required, strength **Strong** (passkey or app), no grace | fixed | 🔒 | 🔒 | 🔒 |

**User operations:**

| Action | Platform admin | Tenant super admin | Tenant admin | App admin |
|---|---|---|---|---|
| See a user's 2FA status and methods (never secrets) | all | own tenant | assigned apps | one app |
| Reset one method / all methods | all | own tenant | assigned apps | one app |
| Unlock | all | own tenant | assigned apps | one app |
| Extend deadline | any length ≤ platform max | ≤ platform max | once, ≤ 14 days | — |
| Exempt (reason + expiry, audited) | yes | own tenant | — | — |

A reset ends the user's sessions, forgets their devices and emails them. When 2FA is required, their next
sign-in starts setup.

## 5. Data and API changes

**Database** (one migration each phase where needed):
- `mfa_factor.type` gains `security_key`, `sms`, `email`.
- New columns:
  - `mfa_factor`: `destination` (masked, verified), `is_default`, `last_used_at` (exists), `strength`.
  - `user_account`: `mfa_failed_count`, `mfa_locked_until` (or reuse lockout with its own counter).
- `user_2fa_state`: `user_id`, `first_prompted_at`, `skips_used`, `next_prompt_at`, `deadline`, `exempt_until`,
  `exempt_reason`, `exempted_by`.
- `trusted_device`: add `user_agent`, `ip_hint`, `last_used_at`, `revoked_at` (list and revoke).
- Policy JSON (`mfa_policy`) gains: `mode`, `minimumStrength`, `methods[]`, `defaultMethod`, `prompt{}`,
  `grace{}`, `rememberDeviceDays`, `freshStepMinutes`, `trustIdpAmr`; `login.mfaRequired` moves here (with a
  migration that reads old values).

**API (end user)** under `/api/v1/account/2fa` (the old `/mfa` paths stay as aliases):
- `GET` status and methods;
- `POST /methods/{type}/start`, `POST /methods/{id}/confirm`, `DELETE /methods/{id}`, `POST /methods/{id}/default`;
- `POST /backup-codes` (regenerate);
- `GET /devices`, `DELETE /devices/{id}`, `DELETE /devices`;
- `POST /verify` (fresh step for sensitive actions).

**API (sign-in):**
- `/api/v1/auth/mfa/challenge` returns the allowed methods for this user;
- `POST /api/v1/auth/mfa/send` (SMS/email);
- `POST /api/v1/auth/mfa/verify` with `{method, code | webauthnResponse}`;
- `POST /api/v1/auth/mfa/skip` (grace);
- setup endpoints usable inside sign-in with the challenge token.

**API (admin):**
- `GET /api/admin/v1/users/{id}/2fa`;
- `POST …/2fa/reset` (one or all);
- `POST …/2fa/extend`;
- `POST …/2fa/exempt`;
- user list filter `?twoFactor=enabled|grace|overdue|exempt|off`.

**OIDC:**
- honour `max_age` and `acr_values` (`acr` 1 = one factor, 2 = two steps, 3 = phishing-resistant);
- standard `amr` values: `pwd`, `otp`, `sms`, `hwk`, `swk`, `user`, `mfa`.

## 6. Implementation phases

Each phase ships with unit tests, an isolated end-to-end run (two-browser where relevant), user-guide and
API-reference updates, and a release image. Sizes: S ≈ ½ day, M ≈ 1–2 days, L ≈ 3–5 days.

### Phase 1: one gate, close the holes (L): **done in 0.3.21** (2026-10-20)

What shipped:
- **One gate:** `MfaLoginService.completeBrowserSignIn` / `completePasswordLogin`, used by form login, session
  login, email code, magic link (new) and federation (now including "required").
- **Passkey sign-in:** checks application access like the other paths. User verification is required at
  registration and sign-in.
- **Wrong second-step codes** (and recovery codes) count toward the account lockout. A locked account cannot
  finish a pending second step.
- **Lockout:**
  - a fresh set of attempts after a lock ends;
  - the lock time doubles for each lockout in a row (cap 24 h, column `lockout_level`, V71);
  - an email on every lock;
  - a password reset (link, code, invitation) or an admin unlock ends the lock.
- **Session `amr`/`acr`:** built from the factors actually used. Before, every session ended at `acr=1`
  with `pwd`, even after MFA or a passkey.

Not done here, moved to phase 2: the "different method from the first step" rule. It only matters once SMS and
email codes become second-step options.

Tests:
- 239 backend tests;
- end-to-end:
  - `twofa_phase1_e2e.py` (26 checks: magic link, required, code lockout, back-off, reset, unlock);
  - `passkey_e2e.mjs` (18: virtual authenticator, access check, user verification, form sign-in);
  - regression of the approvals (26) and API-reference (22) suites.

Upgrade note: passkeys registered on a security key without a PIN no longer sign in; the user adds a PIN to the
key or registers the passkey again.
| Task | Fixes |
|---|---|
| `SignInGate` service: active, access, lockout, 2FA decision, session; used by password, email code, magic link, passkey and federation sign-in | M1, M2, A1, A2 |
| Per-account wrong-code counter for every method, feeding lockout and email | M4, L3 |
| WebAuthn user verification required (registration and sign-in); `amr` `hwk`/`user` | M5 |
| Lockout: count resets when a lock expires; password reset and admin unlock clear it; back-off with a maximum; email on each lock | L1, L2, L5 |
| "Different method from the first step" rule | new |

### Phase 2: the options and the second-step screen (L): **done in 0.3.22** (2026-10-20)
| Task | Fixes |
|---|---|
| Method catalog with strengths; policy `methods[]`, `minimumStrength` | new |
| Second step: default method, "Try another way", passkey and security key accepted | M7, M8 |
| SMS and email codes as second step (reuse `OtpChallengeService` and SMS providers; purpose `MFA`; resend cooldown) | new |
| Several authenticator apps; default method | new |
| Hosted pages: `login.html` second step, setup wizard | — |

### Phase 3: setup now or later, no lock-outs (L): **done in 0.3.22** (2026-10-20)
| Task | Fixes |
|---|---|
| Setup inside sign-in (wizard with backup codes) | M6 |
| Reminders, skips, grace period, deadline, reminder emails (`user_2fa_state`) | M17 |
| Backup codes for every user with a method; regenerate; low warning | M9 |
| Admin: status on user page, list filter, reset one/all, extend, exempt | M11, M18 |

### Phase 4: protect the account (M–L): **done in 0.3.22** (2026-10-20)
| Task | Fixes |
|---|---|
| Account › Two-step verification page (methods, default, backup codes, devices, turn off) | M10 |
| Fresh step (5 min) before: adding/removing methods, changing email or phone, API keys, client secrets, approving change requests, resetting another user's 2FA | M3, M13, C2 |
| Notifications listed in §3.3 | M12 |
| Resets end sessions and forget devices | M11, M10 |
| End-user session absolute limit (24 h default) | M15 |

### Phase 5: settings and governance (L): **done in 0.3.22** (2026-10-20)
| Task | Fixes |
|---|---|
| One **Two-step verification** settings section (platform, tenant, app) with everything in §4 | M16 |
| Lock modes (Locked / Stricter only / Open) and tenant defaults layer; server refuses weaker values | G1, G2, G3 |
| Admin console: accept passkeys and security keys as second step; "Phishing-resistant" option for console | M19 |
| OIDC `max_age`, `acr_values`, `acr` levels | M14 |

### Phase 6: docs and rollout (S–M): **done in 0.3.22** (2026-10-20)
- User guide: new chapter "Two-step verification" (users and admins); API reference regenerated; migration notes.
- Upgrade path:
  - existing TOTP users keep working;
  - `login.mfaRequired` values are copied into the new policy;
  - a "Required" app with users who have no method gets a 14-day grace period automatically instead of locking
    them out.

**Order and dependencies:** 1 → 2 → 3 → 4 → 5 → 6. Phase 1 is independent and urgent (security holes). Phases
2 and 3 depend on the gate. Phase 5's settings UI can start in parallel with phase 4 once the policy JSON is fixed
in phase 2.

## 7. Testing

- **Unit:**
  - gate decisions (every first-step type × policy × methods × trusted device);
  - strength rules;
  - "different method" rule;
  - counters and lockout;
  - grace maths;
  - lock modes.
- **End-to-end:**
  - every sign-in path with 2FA off, optional and required;
  - each method (passkey via a virtual authenticator in Playwright; SMS via the logging provider; email via
    MailHog);
  - "Try another way", backup codes, remember device;
  - grace skips to forced setup;
  - admin reset, extend, exempt;
  - sensitive actions asking for a fresh step;
  - lockout and unlock;
  - console sign-in.
- **Regression:** the existing approvals and API-reference suites.

## 8. Decisions needed

1. Product name: **"Two-step verification"** (recommended) or "Two-factor authentication (2FA)"?
2. Offer **SMS** and **email** codes as options, off by default with a warning (recommended), or leave them out?
3. Default for new installs: **Optional with reminders** (recommended) or **Required with 14-day grace**?
4. Admin console strength: **Strong** (passkey or app, recommended) or **Phishing-resistant only**?
5. Add the **tenant defaults** layer (recommended)?
6. Trust Google/Microsoft's own MFA per provider, default off (recommended)?
