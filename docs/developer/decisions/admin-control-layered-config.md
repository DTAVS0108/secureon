---
title: Admin control via admin scopes + layered, lockable configuration
order: 8
audience: internal
---

# Admin control via admin scopes + layered, lockable configuration

**Status:** Accepted

## Context
Admins must be able to control nearly everything in their scope — enable/disable features, choose authentication methods, manage MFA (including reset), and run the full user lifecycle — without that power becoming a security liability or leaking across tenants/orgs.

## Decision
- Model **admin power as RBAC** ("admin scopes") with an explicit **boundary** (platform / tenant / org), so each admin gets least privilege and cannot act outside their boundary.
- Provide **layered configuration** (Platform → Tenant → Org → App) where a lower layer overrides an upper one **unless locked**; the platform can lock settings to force security.
- Represent toggles as **feature flags** (with platform locks + plan gating) and selectable auth methods as `auth_method_config`.
- Enforce **guardrails** in the service layer: audit every admin action, require step-up for sensitive ones, protect against self-lockout, and keep ≥1 super-admin per tenant.

## Rationale
- Scopes + boundaries give "full control" safely and support delegated/help-desk admins.
- Lockable inheritance lets the platform guarantee baseline security while giving tenants flexibility.
- Reusing the audit + step-up mechanisms keeps the admin plane consistent with the rest of the platform.

## Consequences
- New tables: `admin_scope`, `admin_role`, `admin_role_scope`, `admin_assignment`, `setting`, `feature_flag`, `auth_method_config`, `password_policy`.
- Effective config is resolved per request and cached in Redis (invalidate on change).
- Sensitive admin actions depend on the MFA/step-up machinery (ADR 0006).
- Basic admin control ships in Phase 1–2; delegated administration in Phase 3.
