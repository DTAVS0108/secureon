# Plan: change approvals (four-eyes for high-impact changes)

Status: **planned, not started**, except two slices: *OAuth client delete needs approval* and *weakening Two Factor
Authentication needs approval* (see the end of this document). Written 2026-10-20.

## Goal

When an admin makes a high-impact change, the change does not run at once. It becomes a **change request** that a
second admin must approve. Each organization (tenant) can turn this on or off, and the switch itself is protected
so one compromised admin account cannot quietly turn it off.

## 1. How it works

```
Admin A changes something covered (e.g. deletes an OAuth client)
   │
   ├─ Approvals off for this organization → change runs now (as today)
   │
   └─ Approvals on → nothing changes yet
        Saved as a change request (PENDING): who, what, before → after, reason
        A sees "Sent for approval"; approvers are notified
              │
     Admin B (not A) opens Approvals, sees the difference,
     confirms with a fresh MFA code
        ├─ Approve → server re-checks the target hasn't changed since,
        │            runs the change as B, records A as requester → APPLIED (or FAILED + error)
        ├─ Reject (comment required) → REJECTED
        ├─ A cancels → CANCELLED
        └─ No decision in 72 h → EXPIRED
```

Rules the server always enforces:

- **No self-approval.** The requester cannot approve their own request; nobody approves a change that targets
  themselves (for example, their own console access).
- **The approver must be allowed to make the change.** Their rights are checked again at approval time.
- **Fresh MFA to approve** (authenticator code or passkey at the moment of approval).
- **Stale check.** If the target changed after the request, approval is refused and the change must be requested
  again, so an old "before" picture cannot overwrite newer work.
- **One pending request per target.** A second change to the same object waits.
- **Secrets are never stored in the request.** "Set a password for X" stores only that; a new client secret or key
  is generated on approval and shown once, to the requester.
- **Everything is audited:** requested, approved, rejected, applied, failed, expired, emergency override.

## 2. What it covers

**Critical** is on by default when approvals are on; **High** can be added per organization.

| Area | Critical (default on) | High (optional) |
|---|---|---|
| Platform operators | Add or remove a platform operator | — |
| Platform settings | Password, MFA, token, sign-in methods and verification policies; Platform features; Feature flags; App exposure | SMTP / SMS provider, Appearance |
| Tenants | Delete; suspend; change plan | Edit details |
| Applications | Delete; isolate | Edit |
| OAuth clients | **Delete** (first slice, done); rotate secret; change redirect URIs, grant types or scopes | Create |
| Sign-in federation | Add, edit or delete an identity provider (SAML/OIDC) | — |
| Console access | Give or remove tenant super admin / tenant admin; console capabilities | Console role features |
| Users | Delete; impersonate; reset MFA, set or remove a password, unlock (admin accounts) | Same for ordinary users; bulk roster changes; user import (CSV, LDAP) |
| Machine access | Service account key create; SCIM token create | Service account create or delete |
| Integrations | Webhook create or URL change (data could leak out) | — |
| Roles and permissions | A role that grants admin or console permissions | Any role, group or permission change |
| Configuration as code | Apply an imported file | — |
| Approvals itself | **Turning approvals off, or narrowing what they cover** | — |

Never covered: reading anything; your own profile, password and MFA; sign-in; test emails and SMS; IGA access
requests (they already have their own approval).

## 3. The on/off switch, made secure

Per **tenant**, plus one **platform-level** setting for platform-wide actions (platform settings, platform operators,
tenant delete).

- **Who can change it:** tenant super admins (their tenant), platform operators (platform level). It is *not* an
  ordinary feature flag: its own table, never editable by application admins.
- **Turning it on** is immediate. **Turning it off, or removing actions,** is itself a critical change that needs
  another admin's approval.
- **Fresh MFA** for every change of the switch; every approver is emailed when it changes.
- **Lock-out guard:** it cannot be turned on unless the organization has at least **2 people who can approve**;
  removing an admin is blocked when that would leave fewer than 2.
- **Server lock:** `SECUREONE_APPROVALS_ENFORCED=true` forces approvals on everywhere; nobody can turn them off from
  the console (regulated installations).
- **Configuration as code** cannot switch it off silently; such a file becomes a change request.
- **Optional plan tie-in:** available only on the *Enterprise* plan (the first real difference between plans).
- **Emergency override:** when no second approver can be reached, the emergency (break-glass) account can apply a
  pending request with a written reason and fresh MFA; marked red in the audit log and every approver is emailed.

Settings per organization:

| Setting | Default |
|---|---|
| Enabled | Off |
| Covered actions (tick list) | Critical actions ticked |
| Approvals needed | 1 (2 can be chosen for critical actions) |
| Who approves | Tenant super admins (tenant), platform operators (platform) |
| Request expires after | 72 h |
| Reason required when requesting | On |

## 4. What to build

**Backend** (package `admin/approval/`, one migration):

- Tables: `approval_policy` (tenant or platform, enabled, actions, approvals needed, expiry, who approves, version);
  `change_request` (exists since V70, see below; add risk level, before/after, payload without secrets, expiry);
  `change_request_decision` (approver, decision, comment, time) for multi-approver requests.
- `@RequiresApproval("oauth_client.delete")` on each covered controller method; one interceptor saves the request
  and answers **202 Accepted** with its id when the policy covers that action.
- `ChangeActionRegistry`: action → the service call that applies it. Approval calls the service directly (no HTTP
  replay), so permission checks and validation still run.
- `ApprovalPolicyService`, `ChangeRequestService` (the rules above) and an hourly expiry job.
- API: list pending / mine / history, detail, approve, reject, cancel; get and save the policy.
- Notifications: email through the existing SMTP setup; webhook events `change_request.created`, `.approved`,
  `.rejected`, `.applied`.
- Reuse: audit log, the console sign-in step-up MFA, the self-review rule from IGA access requests.

**Console:**

- Sidebar **Approvals** page with a count of what waits for you: *Waiting for me*, *My requests*, *History*; detail
  with the before/after difference, reason, Approve (MFA code) and Reject (comment).
- Covered forms: a note *"This change needs approval"* and a reason field; after saving, *"Sent for approval"* with
  a link instead of "Saved".
- **Settings → Approvals** tab (tenant and platform): switch with the 2-approver check, action tick list grouped as in
  section 2, approvals needed, expiry, who approves.
- The periodic "who has access" review stays on Access review; the unused `access_review` table (V69) can hold its
  completed reviews later.

**Tests:** unit tests for every rule (self-approval, stale target, expiry, 2-approver guard, switching off needs
approval); an isolated end-to-end run with two admins approving and rejecting; user guide pages.

## 5. Order of work

1. Core: tables, policy, interceptor, registry, approve/reject API, five actions (OAuth client delete and rotate,
   platform operator add and remove, tenant delete), with tests.
2. Console: Approvals page, form behaviour, Settings tab.
3. The remaining actions from section 2, one area at a time.
4. Notifications, emergency override, `SECUREONE_APPROVALS_ENFORCED`, expiry job, documentation.

## Open decisions

1. "Organization" = **tenant** (recommended) plus platform level, or the separate *Organizations* entity inside
   tenants?
2. Every plan, off by default (recommended), or Enterprise only?
3. Approvals needed fixed at 1, or configurable up to 2 for critical actions (recommended)?

---

## First slice (built): OAuth client delete needs approval

Always on for now (no switch yet).

- **Delete client** on an OAuth client's page no longer deletes. It creates a change request (`oauth_client.delete`,
  table `platform.change_request`, migration V70) and the page shows *Deletion waiting for approval* with
  **Withdraw request**. Asking again while one is pending returns the same request.
- **Who can ask:** whoever can manage the client (platform operator, tenant super admin, tenant admin of the app).
- **Who approves:** a platform operator, or a tenant super admin of the client's tenant, on **Access review →
  Pending approvals** (platform page: every request; tenant page: that tenant's requests).
- **Not your own request:** the requester cannot approve or reject it (they can withdraw it).
- On approval the client is deleted; if it is already gone the request is marked *Failed*. Every step is audited
  (`change_request.requested`, `.approved`, `.rejected`, `.withdrawn`).

## Second slice (built): weakening Two Factor Authentication needs approval

Actions (`platform.change_request.action`, with `payload` and `details` added by V74):

| Action | When | Approver |
|---|---|---|
| `two_factor.app_off` | An application's Two Factor Authentication switch on → off | Tenant super admin or platform operator |
| `two_factor.app_policy` | A weaker application policy (`MfaPolicyDefaults.weakenings`) | Tenant super admin or platform operator |
| `two_factor.user_exempt` | Exempting a user (one application or every one) | Tenant super admin or platform operator, not the user |
| `two_factor.tenant_policy` | Weaker tenant defaults or a loosened/removed tenant lock (also "back to platform") | Platform operator |
| `two_factor.platform_policy` | Weaker platform policy or a loosened/removed platform lock | Another platform operator |
| `two_factor.platform_exposure` | For applications › Two Factor Authentication closed | Another platform operator |

- Tightening is never gated. One pending request per action and target; the setting stays as it is meanwhile.
- `payload.values` is applied on approval; `payload.before` is compared first, and a changed setting fails the
  request (*the setting changed after this request was made*).
- **No other approver** (e.g. a single platform operator, no tenant super admin): the change applies at once after a
  fresh step-up and is stored as APPROVED by the requester with the note *Applied without approval* (audit
  `change_request.applied_without_approval`). Access review › Two Factor Authentication shows *Approvals not active
  yet*.
- Access review › Two Factor Authentication (`GET /api/admin/v1/two-factor/review`, tenant:
  `/api/admin/v1/tenants/{id}/two-factor/review`): applications and their mode, active exemptions, admins without an
  authenticator or passkey.
- Not yet: expiry after 72 hours, email to approvers, resetting an admin's 2FA, configuration-as-code imports.
