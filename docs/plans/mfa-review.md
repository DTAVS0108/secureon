# MFA review: how it should work, and how SecureOne does it

Status: **review, nothing changed yet.** Written 2026-10-20 from the standards below and a reading of the
auth-server code (`mfa/`, `authn/`, `federation/`, `account/`, `trusteddevice/`, `admin/user/`) and the hosted
pages (`static/login.html`, `static/account/security.html`).

## 1. What the standards say

Sources: [NIST SP 800-63B-4](https://pages.nist.gov/800-63-4/sp800-63b.html) (US federal guideline, the usual
reference for identity providers), [OWASP MFA Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html),
[RFC 9470 Step-up authentication](https://www.iana.org/go/rfc9470), and how
[Okta](https://developer.okta.com/docs/guides/step-up-authentication/-/main/) and Auth0 implement step-up.

| # | Rule | Source |
|---|------|--------|
| S1 | **Every sign-in path** applies MFA, including APIs, mobile, federation and passwordless links. "A common area that is missed is if the application provides a separate API." | OWASP |
| S2 | Offer at least one **phishing-resistant** option (passkey / WebAuthn). Typed codes (TOTP, OTP) are not phishing-resistant. | NIST §2 AAL2, §3.2.5 |
| S3 | **Email SHALL NOT** be used as an out-of-band second factor. **SMS/voice is restricted**: allowed only with a risk assessment, an unrestricted alternative, and telling users the risk. | NIST §3.1.3.2, §3.2.9 |
| S4 | Accounts can hold **several authenticators**. Adding one requires authenticating at the account's current strength, and the user is **notified on a separate channel**. | NIST §4.1.2 |
| S5 | **Removing or changing** a factor requires re-authentication with an existing factor, not only the session, and an out-of-band notification. | OWASP; NIST §4.5 |
| S6 | Limit failed attempts **per authenticator** across attempts (NIST: at most 100 consecutive failures, then disable; lower is allowed), not only per challenge. Tell the user about failed attempts. | NIST §3.2.2; OWASP |
| S7 | **Recovery** must not be weaker than MFA: saved recovery codes (hashed, single use, replaced after use), admin reset with identity checks; notify after recovery. Encourage several factor types so users rarely lose all. | NIST §4.2; OWASP |
| S8 | **Re-authenticate for sensitive actions** (password, email, factors, admin elevation), and periodically: AAL2 overall session at most **24 h**, inactivity at most **1 h**. | OWASP; NIST §2, §5.2 |
| S9 | After recovery or reset: invalidate the replaced factor, **end sessions and forget trusted devices**. | OWASP / recovery guidance |
| S10 | Apps can ask for stronger or fresher authentication with OIDC `acr_values` / `max_age`, and read `acr`, `amr`, `auth_time` from the ID token. APIs can demand it with RFC 9470 (`insufficient_user_authentication`). | OIDC Core; RFC 9470 |
| S11 | Risk signals (new device, location, IP) decide **when** to ask, but are never a factor themselves. | OWASP |

## 2. How SecureOne works today

**Settings.** Per platform, overridable per application:
- **MFA required** is `off`, `optional` or `required`. It is in Settings › Authentication › *Verification*.
- **MFA policy**: ask after password, prefer passkey, recovery code count, challenge time (300 s), attempts (5),
  remember device (0 days). It is in Settings › Authentication › *MFA*.
- **Factors**: authenticator app (TOTP), passkeys, recovery codes. Email and SMS codes are *sign-in methods*,
  not second factors.

**Sign-in.** After a password (or an email code, or a Google/OIDC/SAML sign-in), a user who has an authenticator
is asked for its code, or a recovery code. A trusted-device cookie skips this when "remember device" is set.
Tokens carry `amr` and `acr` (`acr=2` after MFA or a passkey).

**Admin console.** MFA is always required. Operators set up an authenticator during their first sign-in and get
recovery codes. Failed codes count toward account lockout.

## 3. Comparison

| # | Area | SecureOne today | Standard | Gap |
|---|------|-----------------|----------|-----|
| 1 | **Magic-link sign-in** | Creates a full session with **no MFA step** (`MagicLinkAuthService`). Magic link is **on by default**. | S1 | 🔴 **Bypass**: anyone with the user's mailbox skips their authenticator, even when MFA is *required*. |
| 2 | **Federation (Google, OIDC, SAML)** | Asks for MFA if the user has an authenticator, but **skips the "required but not set up" check**. | S1 | 🔴 With MFA *required*, a user without MFA signs in through Google with no second factor. The IdP's own MFA is not checked (`amr`) either. |
| 3 | **Removing an authenticator** | One click on the account security page; **no fresh code, no notification**. Removes recovery codes too. | S5 | 🔴 A stolen session can turn MFA off silently. |
| 4 | **Guessing codes** | 5 attempts per challenge, then the challenge ends. A new password sign-in gives a new challenge. **No per-account count, no lockout** for end users (the console has one). | S6 | 🔴 With the password, codes can be guessed indefinitely (about 10 sign-ins a minute, 5 codes each). |
| 5 | **Passkeys** | Accepted at sign-in **without requiring user verification** (library default "preferred"), yet marked `acr=2`. | S2 | 🟠 A security key without PIN or biometrics counts as MFA though it is one factor. |
| 6 | **MFA "required", user has no factor** | Sign-in **fails**: *"Enroll an authenticator app before signing in."* No setup during sign-in (the console has it). | S7 | 🟠 Lock-out. After an admin **Reset MFA** under *required*, the user can never sign in again. |
| 7 | **"Required" counts only authenticator apps** | A user with only a passkey is refused. | S2, S4 | 🟠 The strongest factor is not accepted as meeting the requirement. |
| 8 | **Passkey as the second step** | The second step offers only authenticator code and recovery code. With *prefer passkey* on, a passkey-only user is asked for a code they don't have. | S2 | 🟠 Lock-out for passkey-only users; phishing-resistant option missing at the MFA step. |
| 9 | **Recovery codes** | Issued only when an authenticator app is confirmed. **End users cannot regenerate them**; passkey-only users have none; no "running low" warning. Hashed and single-use ✅. | S7 | 🟠 Users lose access instead of recovering. |
| 10 | **Trusted devices** | Can be issued and checked; **cannot be listed or revoked**, and are not cleared on MFA reset, password reset or factor removal. | S9 | 🟠 A remembered device keeps skipping MFA after the account was secured. |
| 11 | **Admin "Reset MFA"** | Deletes factors and emails the user ✅, audited with the operator ✅. Does **not** end sessions or forget trusted devices. | S7, S9 | 🟡 |
| 12 | **Security notifications** | Only for admin reset. Nothing when a factor is added or removed, a recovery code is used, or codes fail repeatedly. | S4, S5, S6 | 🟡 |
| 13 | **Sensitive actions** | No re-authentication before changing email, factors, approving a change request, creating API keys or client secrets. Password change asks for the current password ✅. | S8 | 🟡 |
| 14 | **Apps asking for MFA (OIDC)** | `acr`/`amr` are sent ✅, but `max_age` and `acr_values` are **ignored**; `amr` uses the non-standard `passkey` and no `mfa`. | S10 | 🟡 Apps cannot demand MFA or a fresh sign-in for a sensitive page. |
| 15 | **End-user session lifetime** | 30 min idle (framework default), **no absolute limit**. Console: 30 min idle, 8 h max ✅. | S8 | 🟡 |
| 16 | **Settings layout** | "MFA required" is under *Verification*; the other MFA settings under *MFA*. | — | 🟡 Easy to configure half. |
| 17 | **Email / SMS as second factor** | Not offered. | S3 | ✅ Correct: email must not be a second factor and SMS is restricted. |
| 18 | **Authenticator codes** | Secret encrypted at rest, one use per code (replay refused), 5-minute challenge. | S6 | ✅ |
| 19 | **Console MFA** | Always required, set up at first sign-in, recovery codes, failures count toward lockout. | S7 | ✅ |

🔴 security gap · 🟠 lock-out or weak factor · 🟡 missing good practice · ✅ matches

## 4. How it should work in SecureOne

```
Any primary sign-in: password · email code · magic link · Google/OIDC/SAML
   │
   ▼
One MFA gate (same code for every path)
   ├─ Policy off ............................................. signed in (acr=1)
   ├─ Trusted device, not expired, not revoked ............... signed in
   ├─ Federated IdP already did MFA (amr contains mfa/hwk) ... signed in (when the app trusts it)
   ├─ User has a factor ...................................... ask: passkey ▸ authenticator code ▸ recovery code
   │                                                           (failures count per account → lockout + email)
   └─ No factor
        ├─ optional → signed in, suggest setting one up
        └─ required → set one up now (passkey or authenticator app) inside the sign-in, show recovery codes
   ▼
Session: acr=2, amr=[pwd|otp|hwk|fed…, mfa], auth_time; idle and absolute limits
```

- **Passkey sign-in** requires user verification, so it is MFA on its own (`amr: hwk, user`).
- **Factors page**: add or remove only after a fresh second factor (5 minutes); email the user every time. The
  user can regenerate recovery codes, list and forget trusted devices, and sign out other sessions.
- **Recovery**: recovery codes, then admin reset. A reset or recovery ends all sessions, forgets trusted devices
  and emails the user; under *required*, the next sign-in sets up a new factor.
- **Step-up**: honour `max_age` and `acr_values` on `/oauth2/authorize`, and ask for a fresh factor before
  sensitive console and account actions, including approving change requests.

## 4a. Setting up MFA: now or later (grace period)

**What the major products do.**
- [Microsoft Entra registration campaign](https://learn.microsoft.com/en-gb/entra/identity/authentication/how-to-mfa-registration-campaign):
  "Skip for now", limited to 3 snoozes, with admin-set days between prompts.
- [Okta authenticator enrollment policy](https://help.okta.com/oie/en-us/content/topics/identity-engine/policies/about-mfa-enrollment-policies.htm):
  grace period by end date or by number of skips, prompted daily, custom message.
- [Google Workspace](https://knowledge.workspace.google.com/admin/security/deploy-2-step-verification): new-user
  enrollment period from 1 day to 6 months.
- [GitHub](https://github.blog/news-insights/product-news/raising-the-bar-for-software-security-github-2fa-begins-march-13/):
  45 days, then a daily snooze for up to a week, then a check-up after 28 days.
- [CISA Secure by Design](https://www.cisa.gov/sites/default/files/2024-05/CISA%20Secure%20by%20Design%20Pledge_508c.pdf):
  MFA on by default, set up at registration, "seat-belt chime" nudges.

The common pattern: ask at sign-in, allow limited postponing, remind, then make setup part of sign-in at the
deadline (never a dead end), with no grace period for admins.

**SecureOne today.** *Optional* never prompts anyone. *Required* refuses users without a factor ("Set up an
authenticator app in your account… then sign in again", which they cannot reach).

**Target flow** for a user without a factor, after a successful primary sign-in:
- **Off:** signed in.
- **Optional, reminder due:** "Protect your account" with *Set up now* / *Remind me in N days* / (optional)
  *Don't ask again*.
- **Required, in grace:** the same screen, showing the deadline and skips left.
- **Required, grace over:** setup inside the sign-in, no skip.

Setup offers a passkey (recommended) or an authenticator app, then shows recovery codes before the session starts.

**Settings** (platform default, application override, one *MFA* section):

| Setting | Values | Default |
|---|---|---|
| MFA | Off / Optional / Required | Optional |
| Ask users without MFA to set it up (Optional) | on / off | on |
| Ask again after | 1–90 days | 7 |
| Allow "Don't ask again" | on / off | off |
| Grace period before Required applies | 0–180 days | 14 |
| Skips allowed in grace period | 0–10 / unlimited until deadline | 3 |
| Enforce from (existing users) | date | when saved |
| Reminder emails before deadline | days list | 7, 1 |
| Factors offered at setup | Passkey, Authenticator app | both, passkey first |
| Prompt message | text | default |

No grace period, ever: admin console operators and users with a console role.

**Per user** (stored on the account): first prompted at, skips used, next prompt at, deadline, exempt until +
reason. The user page shows *Enabled / In grace until … (n skips left) / Overdue / Exempt*; admins can extend or
exempt (audited); the user list filters by MFA status.

## 5. Plan (in order)

1. **Close the bypasses:** route magic link and federation through the same MFA gate (gaps 1, 2); per-account
   failure counting and lockout (gap 4); require user verification for passkeys (gap 5).
2. **No lock-outs:** set up MFA during sign-in, with the "now or later" grace period of section 4a (gap 6); count passkeys as meeting *required* and
   offer them at the second step (gaps 7, 8); end users can regenerate recovery codes, passkey users get them
   too (gap 9).
3. **Protect the factors:** fresh code to add or remove a factor, notifications (gaps 3, 12); trusted devices
   listed, revocable, cleared on reset (gap 10); admin reset ends sessions (gap 11).
4. **Step-up and sessions:** `max_age` / `acr_values`, standard `amr` values (gap 14); re-authentication before
   sensitive actions (gap 13); absolute end-user session limit, default 24 h (gap 15).
5. **Console:** one *MFA* settings section with "required" in it (gap 16); user guide and API reference updated.

Each step ships with unit tests and an end-to-end run of the affected sign-in paths.
