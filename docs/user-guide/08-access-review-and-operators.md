# 8. Access review and platform operators

[User guide](README.md) › Access review

**Where:** sidebar **Access review** (`/admin/access-review`, platform operators). Tenant Super Admins have a tenant-scoped version under **Tenant › Access review** (`/admin/tenants/{id}/access-review`).

*Approve high-impact changes, and review who holds platform and console roles, when they last signed in, and whether they use MFA.*

## Pending approvals

Some changes are too risky for one person to make alone. They wait here until another admin approves them. For
now this covers **deleting an OAuth client** (see [OAuth clients › Danger zone](04-oauth-clients.md#danger-zone)).

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
second platform operator (below) or a tenant super admin.

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
