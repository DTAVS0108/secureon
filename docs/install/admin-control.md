---
title: Admin control
summary: Layered configuration and what each admin level controls
order: 6
audience: operator
---

# Admin control

Admins get **full control** over their scope: enable/disable features, choose authentication methods, manage MFA (including reset), and exercise complete user lifecycle control — all governed by least-privilege admin scopes, layered configuration, and strong guardrails.

> Admin power is itself **RBAC**: admins hold *admin scopes*, so you grant exactly what each admin needs. Nothing here weakens the [Security](../architecture/security.md) non-negotiables — every admin action is audited and sensitive ones require step-up.

---

## 1. Admin tiers & delegated administration

```mermaid
flowchart TB
    PA["Platform Super-Admin<br/>(platform_admin)<br/>platform defaults, tenant mgmt,<br/>feature availability / locks"]
    TA["Tenant Admin<br/>full control within a tenant"]
    OA["Org Admin (delegated)<br/>scoped to an org / sub-org"]
    HD["Help-desk / Scoped Admin<br/>narrow scopes only<br/>(e.g. mfa:reset, users:unlock)"]

    PA --> TA --> OA --> HD

    classDef sec fill:#e1d5e7,stroke:#9673a6,color:#000;
    classDef authc fill:#ffe6cc,stroke:#d79b00,color:#000;
    classDef uic fill:#d5e8d4,stroke:#82b366,color:#000;
    classDef client fill:#dae8fc,stroke:#6c8ebf,color:#000;
    class PA sec;
    class TA authc;
    class OA uic;
    class HD client;
```

**Admin scopes (examples):** `users:read`, `users:write`, `users:delete`, `users:impersonate`, `mfa:reset`, `mfa:policy`, `sessions:revoke`, `roles:assign`, `settings:write`, `feature:toggle`, `auth-config:write`, `clients:write`, `audit:read`.

Admin roles bundle scopes and are assigned with a **scope boundary** (tenant or org), so a delegated admin can never act outside their boundary.

---

## 2. Layered configuration (inheritance, override & locks)

Settings resolve top-down; a lower layer overrides an upper one **unless the upper layer locks it**. This lets the platform *force* security (e.g. mandatory MFA) that tenants cannot disable.

> Editable source: [`diagrams/config-inheritance.drawio`](../diagrams/config-inheritance.drawio)

```mermaid
flowchart TB
    P["Platform defaults<br/>(+ lockable, plan/feature availability)"] --> T["Tenant settings"]
    T --> O["Organization settings"]
    O --> A["Application settings"]
    A --> EFF["Effective configuration<br/>(resolved at runtime, cached in Redis)"]

    LOCK["🔒 A locked setting stops here —<br/>lower layers cannot override"] -.-> T

    classDef sec fill:#e1d5e7,stroke:#9673a6,color:#000;
    classDef authc fill:#ffe6cc,stroke:#d79b00,color:#000;
    classDef uic fill:#d5e8d4,stroke:#82b366,color:#000;
    classDef store fill:#f8cecc,stroke:#b85450,color:#000;
    classDef note fill:#fff2cc,stroke:#d6b656,color:#000;
    class P sec;
    class T authc;
    class O,A uic;
    class EFF store;
    class LOCK note;
```

### Setting groups admins control

| Group | Examples |
|---|---|
| **Feature toggles** | self-registration, social login, SAML, LDAP, passwordless, account linking, self-service portal, remember-device, password login on/off |
| **Authentication** | enabled methods + order (password, passkey/passwordless, social, SAML, LDAP, magic-link, OTP), default method, password policy, session/timeout rules |
| **MFA** | required?, min AAL, allowed factor types, phishing-resistant-only, AAGUID allow-list, grace period, remembered-device TTL, reauth interval, self-enroll permissions |
| **Tokens / security** | access/refresh TTLs, rotation, allowed grant types, redirect/CORS URIs per app |
| **Branding / notifications** | theme, email/SMS templates, locale/i18n |

---

## 3. Full user control

Admin actions available on a user (subject to scope + guardrails):

| Area | Actions |
|---|---|
| Lifecycle | create, edit profile/attributes, **enable / disable**, **lock / unlock**, soft-delete, force logout |
| Credentials | **reset password**, force change at next login, set temporary password |
| **MFA** | view enrolled factors, **reset / clear all MFA (force re-enrollment)**, revoke a specific factor, regenerate recovery codes, **MFA exemption (audited)**, require step-up |
| Verification | mark email/phone verified or unverified, resend verification |
| Roles & access | assign / revoke roles (time-bound), view effective permissions |
| Sessions & devices | view active sessions, **revoke one / all**, drop trusted devices |
| Identities | link / unlink external IdP identities |
| **Impersonation** | "log in as user" — time-boxed, consent/policy gated, heavily audited |
| Bulk | CSV import/export, bulk enable/disable/role-assign |

---

## 4. Guardrails (mandatory)

- **Audit everything** — every admin action writes to the append-only `audit_log` (actor, target, before/after, IP, reason). Impersonation sessions are tagged.
- **Step-up MFA / re-auth** for sensitive actions: impersonation, disabling MFA enforcement, deleting users, changing auth config, editing locked settings.
- **Self-lockout protection** — you cannot disable your own account, remove your own last admin role, or disable the auth method you're currently using. A tenant must always retain **≥ 1 active super-admin**.
- **Least privilege** — admin scopes + scope boundary; delegated admins confined to their org.
- **Platform locks** — platform can force-enable controls tenants cannot override.
- **Optional four-eyes / approval** on the most dangerous actions (ties into Phase 4 IGA in [Enterprise Capabilities](../architecture/enterprise.md#4-identity-governance--administration-iga)).

---

## 5. Admin API surface (illustrative)

```
# Configuration & features
GET|PUT   /admin/tenants/{id}/settings
GET|PUT   /admin/tenants/{id}/features
PUT       /admin/tenants/{id}/auth-methods
GET|PUT   /admin/tenants/{id}/mfa-policy

# User control
POST      /admin/users/{id}/disable | enable | lock | unlock
POST      /admin/users/{id}/password/reset
POST      /admin/users/{id}/password/force-change
POST      /admin/users/{id}/mfa/reset
DELETE    /admin/users/{id}/mfa/factors/{factorId}
POST      /admin/users/{id}/recovery-codes/regenerate
POST      /admin/users/{id}/sessions/revoke
POST      /admin/users/{id}/roles
POST      /admin/users/{id}/impersonate        # step-up required, audited
```

All admin endpoints enforce the admin's scopes + boundary, and emit audit events.

---

## 6. Admin dashboard layout

- **Settings** → Features · Authentication · MFA · Security/Tokens · Branding · Notifications (with a "locked by platform" indicator).
- **Users** → list + detail tabs: Profile · Credentials · **MFA** · Roles · Sessions · Identities · Activity.
- **Admins & delegation** → admin roles, scopes, boundaries.
- **Audit log** → searchable, filter by actor/target/action.

See the data model in [Data Model § Domain 8](../developer/data-model.md#domain-8--administration--configuration).
