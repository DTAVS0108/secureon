---
title: Access review and operators
summary: Privileged access review and platform operators
order: 8
audience: public
---

# Access review and operators

[User guide](index.md) › Access review

**Where:** sidebar **Access review** (`/admin/access-review`, platform operators). Tenant Super Admins have a tenant-scoped version under **Tenant › Access review** (`/admin/tenants/{id}/access-review`).

*Approve high-impact changes, and review who holds platform and console roles, when they last signed in, and whether they use MFA.*

## Pending approvals

Some changes are too risky for one person to make alone. They wait here until another admin approves them:

- **Deleting an OAuth client** (see [OAuth clients › Danger zone](oauth-clients.md#danger-zone)).
- **Anything that weakens Two Factor Authentication** (see [Two-step verification › Approvals](two-step-verification.md#approvals)):
  an application turning it off, a weaker application, tenant or platform setting or a removed lock, closing it to
  applications under **For applications**, and exempting a user. Turning it on or making it stricter applies at once.
  Tenant and platform changes are decided by platform operators only, and nobody decides an exemption of
  themselves.

The **Change** column then also shows the change in words, for example *mode: required → off*. On approval the
setting is checked again: if it was changed in the meantime, the request is marked **Failed** (*the setting changed
after this request was made*) and must be asked for again.

- **Who decides:** platform operators (every tenant, on `/admin/access-review`) and the tenant's super admins
  (their tenant, on **Tenant › Access review**).
- **Not your own request:** the person who asked cannot approve or reject it; they can only **Withdraw** it.

| Column | Meaning |
|--------|---------|
| **Change** | What will happen, e.g. **Delete OAuth client**, and the client (link to its page) |
| **Tenant** | The tenant it belongs to (platform page only) |
| **Asked by** | Who asked and when (**You** for your own requests) |
| **Reason** | The reason given when asking |
| **Decision** | **Approve**, **Reject**, or for your own request *Waiting for another admin* and **Withdraw** |

**Approve** opens a dialog with an optional note; **Approve and delete** deletes the client at once (*Approved:
client deleted*). If the client was already removed, the request is marked **Failed**. **Reject** needs a reason,
which the person who asked can see. Nothing changes.

**Recent decisions** lists the last 50 requests with **Approved**, **Rejected**, **Withdrawn** or **Failed**, who
decided, and the note. Every step is also in the audit log (`change_request.requested`, `.approved`, `.rejected`,
`.withdrawn`, `.failed`).

With only one platform operator and no tenant super admin, nobody else can approve that operator's requests. Add a
second platform operator (below) or a tenant super admin. Until then, Two Factor Authentication changes apply at
once after a fresh **Verify it's you**, and are listed as **Approved** with the note *Applied without approval:
nobody else can approve yet* (audit `change_request.applied_without_approval`).

## Two Factor Authentication

*Where it is off or optional, who is exempt, and admins without a second step.*

| Part | Shows |
|------|-------|
| Approvals state | **Approvals active** (at least two people can approve) or **Approvals not active yet** |
| **Applications** | Each application's Two Factor Authentication (**Required**, **Optional**, **Off**) and who decides it: its own settings, *Switched off by the application*, the platform and tenant settings, or *Locked by the platform or tenant* |
| **Exempt users** | Active exemptions: user, application (or *Every application*), until when, reason and who gave it |
| **Admins without a second step** | Platform operators and console admins with no authenticator or passkey |

Use it in the quarterly review: anything Off or Optional that should be Required, and exemptions that should end.

## Platform operators

*Named people with full platform access. They sign in with tenant slug "platform", their email, password and an authenticator app. The configured emergency account is for incidents only.*

### Add platform operator

| Field | What to enter |
|-------|---------------|
| **Email** | The person's work email (`name@company.com`). |
| **First name**, **Last name** | Their name. |

Press **Add platform operator**. *Added. They get an email to set a password, then set up MFA at first sign-in.* SMTP must be configured so the email arrives.

The table lists **Person**, **Status**, **Added** and **Last sign-in**. **Remove** takes away the platform operator role (*Platform operator role removed*). Keep at least two operators so one can recover the other.

## Access review

A periodic check of who holds privileged access.

| Column | Meaning |
|--------|---------|
| **Person** | Name and email |
| **Role** | **Platform super admin**, **Tenant super admin**, **Tenant admin** or **Application admin** |
| **Scope** | **Whole platform**, **All applications**, or the named applications |
| **Tenant** | The tenant the role belongs to |
| **Granted** | When the role was given |
| **Last sign-in** | Most recent console sign-in, or **Never signed in** |
| **Flags** | Problems to look at: **No MFA**, **Unused 90+ days**, **Never signed in**, **Account not active** |

| Control | What it does |
|---------|--------------|
| **Flagged only** / **Show all** | Show only rows with flags, or everyone. *Nothing flagged.* means all is well. |
| **Export CSV** | Downloads the list, for example as evidence for an audit. |
| Review note | *Review note (optional), e.g. Q4 review, removed 2 stale admins* |
| **Mark review complete** | Records the review with your note in the audit log (*Review recorded in the audit log*). |

### How to run a quarterly review

1. Open **Access review** and select **Flagged only**.
2. For each flagged person, decide: remove the role (in the tenant roster or **Platform operators**), ask them to set up MFA, or keep it.
3. Select **Show all** and check that every remaining role is still needed.
4. **Export CSV** for your records.
5. Write a short note and press **Mark review complete**.
