# SecureOne security roadmap: settings ownership, gaps and tasks

Status: **plan, nothing in it is built yet** unless marked done. Written 2026-10-20. One place for everything
discussed: change approvals, MFA, account lockout, settings governance, docs and release housekeeping.

Detail behind it:
- **[Two-step verification (2FA) plan](two-factor-authentication.md): the implementation plan for all MFA and
  lockout gaps below (M*, L*, A*); its phases replace phases 1, 2 and 4 of section 4**
- [Change approvals plan](change-approvals.md)
- [MFA review](mfa-review.md) (standards, comparison, now-or-later setup)
- Lockout findings (section 3.3 below)

## 1. Who decides what

### 1.1 Roles

| Role | Scope |
|------|-------|
| **Platform admin** (platform super admin / platform operator) | The whole installation |
| **Tenant super admin** | One tenant: all its applications |
| **Tenant admin** | Applications assigned to them in one tenant |
| **Application admin** | One application |
| **End user** | Their own account |

### 1.2 How settings layer (target)

```
Platform defaults + limits  (platform admin)
      ▼   apps and tenants may only change what the platform allows, and never go below its minimum
Tenant defaults             (tenant super admin)        ← NEW layer (today: platform → application only)
      ▼   same rule: within the tenant's minimum
Application settings        (tenant super admin; tenant/application admin if granted the Settings section)
      ▼
User choices                (end user: set up MFA now or later, own factors, devices, sessions)
```

Every setting carries a **lock mode** chosen by the level above. This replaces today's on/off "app exposure" per
section:

| Lock mode | Meaning |
|-----------|---------|
| **Locked** | The value above applies; lower levels cannot change it (shown read-only with "Set by platform"). |
| **Stricter only** | Lower levels may make it stricter, never weaker. For example: MFA Optional → Required, yes; Required → Off, no. 5 attempts → 3, yes; → 10, no. |
| **Open** | Lower levels may change it freely (today's behaviour). |

### 1.3 Settings by role

✏️ = can change · 🔒 = sees it, cannot change · — = does not see it. "Stricter" means only in the stricter
direction, within the limit set above.

**Authentication and MFA**

| Setting | Platform admin | Tenant super admin | Tenant / application admin | End user |
|---|---|---|---|---|
| Sign-in methods allowed (password, email code, magic link, passkey, Google/OIDC/SAML) | ✏️ default + lock mode | ✏️ tenant default, within lock | ✏️ per app if Settings granted, within lock | — |
| MFA: Off / Optional / Required | ✏️ default + **minimum** | ✏️ stricter | ✏️ stricter | — |
| MFA factors offered (passkey, authenticator app) | ✏️ | ✏️ subset | ✏️ subset | chooses among offered |
| Prompt users without MFA (Optional), ask-again days, allow "Don't ask again" | ✏️ | ✏️ | ✏️ | Set up now / Later |
| Grace period (days), skips allowed, enforce-from date, reminder emails | ✏️ **maximum** grace | ✏️ ≤ platform max | ✏️ ≤ tenant max | sees deadline and skips left |
| Remember device (days) | ✏️ **maximum** | ✏️ ≤ max | ✏️ ≤ max | ticks "remember this device" |
| Challenge time, attempts per challenge | ✏️ | 🔒 | 🔒 | — |
| Ask again before sensitive actions (minutes) | ✏️ | ✏️ stricter | 🔒 | — |
| Trust MFA done by the external IdP (`amr`) | ✏️ | ✏️ per provider | 🔒 | — |
| Admin console MFA | 🔒 always on (cannot be turned off) | 🔒 | 🔒 | — |

**Passwords, lockout, sessions**

| Setting | Platform admin | Tenant super admin | Tenant / application admin |
|---|---|---|---|
| Password rules (length, history, breach check) | ✏️ minimum | ✏️ stricter | ✏️ stricter |
| Lockout: attempts, first lock minutes, back-off growth, max lock | ✏️ | ✏️ stricter | 🔒 |
| Count MFA failures toward lockout | 🔒 always on | 🔒 | 🔒 |
| End-user session idle / absolute limit (default 1 h / 24 h) | ✏️ maximum | ✏️ shorter | ✏️ shorter |
| Console session idle / absolute (30 min / 8 h) | ✏️ (today env vars; show in UI) | 🔒 | 🔒 |
| Token lifetimes | ✏️ maximum | ✏️ shorter | ✏️ shorter |

**Change approvals** ([plan](change-approvals.md))

| Setting | Platform admin | Tenant super admin | Tenant / application admin |
|---|---|---|---|
| Approvals on/off, covered actions, approvals needed, expiry | ✏️ platform scope; can **force on** for all tenants | ✏️ own tenant (turning off needs approval) | — |
| Approve / reject requests | ✏️ all | ✏️ own tenant, not own requests | — (can request and withdraw) |
| Emergency override | break-glass account only | — | — |

**People operations**

| Action | Platform admin | Tenant super admin | Tenant admin | Application admin |
|---|---|---|---|---|
| Unlock account | ✏️ all | ✏️ own tenant | ✏️ assigned apps' users | ✏️ app users |
| Reset a user's MFA | ✏️ (sensitive action, approval when covered) | ✏️ own tenant | ✏️ assigned apps | ✏️ app users |
| Extend a user's MFA deadline | ✏️ | ✏️ up to the platform max | ✏️ once, up to 14 days | — |
| Exempt a user from MFA (with reason, expiry) | ✏️ | ✏️ own tenant | — | — |
| See MFA status of users (enabled, in grace, overdue, exempt) | ✏️ all | ✏️ own tenant | ✏️ assigned apps | ✏️ app |

Admins never get a grace period or an exemption for **their own** console MFA.

## 2. Already done (this session)

| Item | Where |
|------|-------|
| OAuth client delete needs another admin's approval; Access review › Pending approvals | 0.3.18–0.3.20 (V70) |
| Change-approvals plan written | [change-approvals.md](change-approvals.md) |
| Swagger/OpenAPI working in the image (`SECUREONE_SWAGGER_ENABLED`), tags and security schemes fixed | 0.3.19 |
| API reference: 12 sections + data models + curl for every endpoint; generator script; docs/12 rewritten | `docs/api-reference/`, `scripts/api-docs/` |
| In-console API reference: sections, Copy curl, per-app values | 0.3.19 |
| Sidebar hydration warning (last-used application) | 0.3.20 |
| Tenant plan field saved correctly while locked | 0.3.17 |
| Sidebar: Tenant before OAuth clients; deploy guide | 0.3.17 |
| MFA review written | [mfa-review.md](mfa-review.md) |

## 3. All gaps

Severity: 🔴 security hole · 🟠 lock-out, weak control or missing protection · 🟡 good practice / usability.

### 3.1 MFA (details: [mfa-review.md §3](mfa-review.md#3-comparison))

| ID | Gap | Sev |
|----|-----|-----|
| M1 ✅ 0.3.21 | Magic-link sign-in skips MFA entirely (magic link is on by default) | 🔴 |
| M2 ✅ 0.3.21 | Google/OIDC/SAML sign-in ignores "MFA required" for users without a factor; IdP's own MFA (`amr`) not considered | 🔴 |
| M3 | Removing an authenticator: no fresh code, no notification | 🔴 |
| M4 ✅ 0.3.21 | Code guessing unlimited across challenges; end-user MFA failures don't count toward lockout | 🔴 |
| M5 ✅ 0.3.21 | Passkey sign-in doesn't require user verification, yet counts as MFA | 🟠 |
| M6 | "Required" with no factor → sign-in refused, no setup inside sign-in; admin MFA reset under "required" locks the user out for good | 🟠 |
| M7 | "Required" only counts authenticator apps; passkey-only users refused | 🟠 |
| M8 | Second step offers no passkey; "prefer passkey" + passkey-only user can't finish | 🟠 |
| M9 | Recovery codes only with an authenticator app; end users can't regenerate; no "running low" warning | 🟠 |
| M10 | Trusted devices can't be listed or revoked; not cleared on reset, password reset or factor removal | 🟠 |
| M11 | Admin "Reset MFA" doesn't end sessions or forget devices | 🟡 |
| M12 | No email when a factor is added/removed, a recovery code is used, or codes fail repeatedly | 🟡 |
| M13 | No re-authentication before sensitive actions (email, factors, approvals, API keys, secrets) | 🟡 |
| M14 | OIDC `max_age` / `acr_values` ignored; non-standard `amr` values | 🟡 |
| M15 | End-user sessions: no absolute limit | 🟡 |
| M16 | MFA settings split across *Verification* and *MFA* tabs | 🟡 |
| M17 | No "now or later" setup: Optional never prompts, Required blocks; no grace period, reminders, deadline, extend/exempt | 🟠 |
| M18 | No per-user MFA status for admins (enabled / in grace / overdue / exempt), no filter | 🟡 |
| M19 | Console second factor: operator passkeys can be enrolled but aren't accepted at console sign-in | 🟡 |

### 3.2 Access and sign-in paths

| ID | Gap | Sev |
|----|-----|-----|
| A1 ✅ 0.3.21 | Passkey sign-in adds the user to the requested application (`ensureMemberWithDefaultRole`) instead of checking access like password and federation sign-in (`access.admit`). To verify with a test: likely lets a user into an app they were not granted. | 🔴 |
| A2 ✅ 0.3.21 | Sign-in paths differ in what they check (MFA, access, lockout). Needs one shared "after primary sign-in" gate used by password, email code, magic link, passkey, federation. | 🟠 |

### 3.3 Account lockout

| ID | Gap | Sev |
|----|-----|-----|
| L1 ✅ 0.3.21 | After a lock expires the failure count isn't reset: one more wrong password locks again, with no email | 🟠 |
| L2 ✅ 0.3.21 | Password reset doesn't unlock, though the lock email says "reset your password" | 🟠 |
| L3 ✅ 0.3.21 | End-user MFA failures don't count (same as M4) | 🔴 |
| L4 | ~~Unlock audited as "admin"~~: not a gap. The audit service already records the signed-in operator (`actorEmail`) when callers pass "admin". | ✅ |
| L5 ✅ 0.3.21 | No back-off (15 → 30 → 60 min) for repeated lockouts; email only for the first | 🟡 |

### 3.4 Settings governance

| ID | Gap | Sev |
|----|-----|-----|
| G1 | Platform can only show/hide whole sections for apps; no "locked" or "stricter only". An app admin can set MFA Off while the platform wants Required. | 🟠 |
| G2 | `mfa-policy` is not one of the controllable sections at all | 🟠 |
| G3 | No tenant-level defaults: a tenant super admin must set every app one by one | 🟡 |
| G4 | Console session limits only as environment variables, not visible in Platform settings | 🟡 |

### 3.5 Change approvals (details: [change-approvals.md](change-approvals.md))

| ID | Gap | Sev |
|----|-----|-----|
| C1 | Only OAuth client delete is covered, always on; no per-tenant switch, no covered-actions list | 🟠 |
| C2 | Approving doesn't ask for a fresh MFA code | 🟠 |
| C3 | No notifications to approvers; no expiry job; no emergency override; no `SECUREONE_APPROVALS_ENFORCED` | 🟡 |
| C4 | Deleting a whole application still removes its clients without approval | 🟡 |
| C5 | Single platform operator can't get own requests approved without a tenant super admin; no 2-approver guard | 🟡 |

### 3.6 Other open items

| ID | Item | Sev |
|----|------|-----|
| O1 | Periodic access review history: V69 table exists, code was removed; decide to rebuild (record reviews, history, per-row revoke, tenant overview) or drop | 🟡 |
| O2 | Plans Free / Team / Enterprise are labels only; entitlements (limits, which features) undecided; plan field locked to Enterprise | 🟡 |
| O3 | Endpoint texts: only 46 of 358 operations have a written summary; add `@Operation` summaries for the admin API | 🟡 |
| O4 | Regenerate the API reference in CI on each release | 🟡 |
| O5 | Uncommitted work (0.3.17–0.3.20) not in git; 0.3.20 not on Docker Hub (`latest` is older) | 🟠 |

## 4. Plan

> **Superseded for MFA:** phases 1, 2 and 4 below are now planned in detail, with multiple second-step options,
> in [two-factor-authentication.md §6](two-factor-authentication.md#6-implementation-phases). Phase 3 (settings
> governance) is its phase 5. Phases 0, 5 and 6 here still apply.

Each phase ends with unit tests, an isolated end-to-end run of the affected paths, a user-guide and API-reference
update, and a release image. Sizes: S ≈ half a day, M ≈ 1–2 days, L ≈ 3–5 days.

### Phase 0: housekeeping (S)
- O5 commit and push current work; publish 0.3.20 + `latest` on request.

### Phase 1: close the holes (M–L)
1. **One sign-in gate** (A2) used by every primary sign-in: access check → lockout → MFA decision → session. Fixes
   **M1** (magic link), **M2** (federation; plus optional trust of IdP `amr`), **A1** (passkey access check).
2. **Per-account failure counting** for MFA codes and recovery codes; feeds lockout with email (**M4**, **L3**).
3. **Passkeys require user verification** at sign-in and registration (**M5**); standard `amr` (`hwk`, `user`, `mfa`).
4. **Lockout rules** (**L1, L2, L5**): reset the count when a lock expires; a password reset or admin unlock
   clears the lock; back-off with a maximum; email on each lock.

### Phase 2: no lock-outs, MFA setup now or later (L) — **done in 0.3.22**
1. **Setup inside sign-in** (**M6**): passkey or authenticator app, then recovery codes, then the session.
2. **Now or later** (**M17**): prompt screen, skips, ask-again, grace period, deadline, reminder emails; per-user
   state (V71); setup after the deadline is mandatory but never a dead end.
3. **Passkeys count** for "required" and are offered at the second step (**M7, M8**); recovery codes for every
   user with a factor, regenerate from the account page, low-codes warning (**M9**).
4. **Admin view** (**M18**): MFA status on the user page and in the user list filter; extend and exempt (audited,
   with reason and expiry).

### Phase 3: settings governance (L) — **done in 0.3.22** (two-step verification settings; other settings still open)
1. **Lock modes** per setting (Locked / Stricter only / Open) replacing section exposure (**G1**), including MFA
   policy (**G2**).
2. **Tenant defaults** layer for tenant super admins (**G3**).
3. One **MFA** settings section with everything from §1.3 (**M16**); console session limits shown in Platform
   settings (**G4**).
4. Server-side enforcement of §1.3: the API refuses a weaker value than the level above allows.

### Phase 4: protect factors and sensitive actions (M–L) — **done in 0.3.22**
1. **Fresh-factor check** (5 minutes) before: adding/removing factors (**M3**), changing email (no self-service email change exists yet), creating API keys
   or client secrets, approving change requests (**C2**), resetting another user's MFA (**M13**).
2. **Notifications** for factor added/removed, recovery code used, repeated failures (**M12**).
3. **Trusted devices** listed and revocable by the user; cleared on MFA reset, password reset, factor removal
   (**M10**); admin reset ends sessions and forgets devices (**M11**).
4. **Sessions**: end-user absolute limit, default 24 h (**M15**).
5. **OIDC step-up**: honour `max_age` and `acr_values`, `auth_time` in tokens (**M14**).
6. Console accepts passkeys as the second factor (**M19**).

### Phase 5: change approvals, full version (L)
Per [change-approvals.md](change-approvals.md): policy per tenant and platform with covered actions (**C1**),
fresh MFA to approve (**C2**), notifications, expiry, emergency override, enforced env (**C3**), more actions
including application delete (**C4**), 2-approver guard (**C5**).

### Phase 6: open decisions (S each, once decided)
- **O1** access review history: rebuild or drop V69.
- **O2** plan entitlements.
- **O3 / O4** endpoint summaries and API reference in CI.

## 5. Decisions needed before building

1. **Tenant defaults layer** (§1.2): add it (recommended), or keep platform → application only?
2. **Defaults for new installs:** MFA *Optional with prompts* (recommended) or *Required with 14-day grace*?
3. **Magic link default:** keep it on (it will ask for MFA after Phase 1) or turn it off by default?
4. **Trust the IdP's MFA** for Google/Microsoft sign-in when its token says MFA was done: per provider, default off
   (recommended)?
5. **Who may exempt users from MFA:** platform and tenant super admin only (recommended)?
6. **Access review history (O1)** and **plan entitlements (O2):** build, change, or drop?
