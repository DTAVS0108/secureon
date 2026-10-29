---
title: Console layout and navigation
summary: Top bar, sidebars and what each role sees
order: 2
audience: public
---

# Console layout and navigation

[User guide](index.md) › Console layout

## Top bar

| Element | What it does |
|---------|--------------|
| **Application** selector | Chooses which application the application console shows. Lists only the applications you may manage. Switching keeps you on the same section (for example Users). Before any OAuth client exists it reads *Application console unavailable — register an OAuth client first*. |
| **Search…** | A search box placeholder; it does not search yet. |
| **Sign out** | Ends your console session and returns to the sign-in page. |
| **My profile** (your initials and name) | Your account, password and authenticator. See [My profile](sign-in-and-profile.md#my-profile). |

The **Connect an app** shortcut is in the sidebar for platform operators, Tenant Super Admins, and Tenant Admins who can manage clients.

## Sidebar

### Application section

Shown when an application is selected. Every entry works on the selected application.

| Entry | Page | Purpose |
|-------|------|---------|
| **Users** | `/app/{id}/users` | Members of this application |
| **Groups** | `/app/{id}/groups` | Role bundles assigned to many users |
| **Roles** | `/app/{id}/roles` | RBAC roles for this application |
| **Permissions** | `/app/{id}/permissions` | Permission catalog and role assignment |
| **Settings** | `/app/{id}/settings` | Sign-in and notification overrides for this application |
| **Audit log** | `/app/{id}/audit` | Admin and security events for this application |
| **Application logs** | `/app/{id}/logs` | Runtime logs, searchable by session or request |
| **Sessions** | `/app/{id}/sessions` | Sign-ins for this application |

All of these are described in [Application console](application-console.md).

### Platform admin section (platform operators)

| Entry | Purpose |
|-------|---------|
| **Tenant › Tenants** | Organizations on the platform ([Tenants](tenants.md)) |
| **Tenant › Roles & Permissions** | Console roles and custom governance roles per tenant |
| **Connect an app** | Guided registration with ready-to-paste code ([chapter 3](connect-an-app.md)). Hidden when turned off in **Platform settings › Feature flags**. |
| **OAuth clients** | The client registry ([chapter 4](oauth-clients.md)) |
| **SecureOne Documentation** | This documentation, inside the console (opens in a new tab) |
| **Platform settings** | Global defaults inherited by all applications ([chapter 7](settings.md)) |
| **Configuration as code** | Export, preview and apply configuration files ([chapter 9](configuration-as-code.md)). Shown only when turned on in **Platform settings › Feature flags**. |
| **Access review** | Who holds privileged roles; platform operators ([chapter 8](access-review-and-operators.md)) |

### Tenant Super Admin

The **Tenant** group links only to your own organization: **Tenants** (your tenant page), **Roles & Permissions** and **Access review**. You also see **Connect an app**, **Application console** and **SecureOne Documentation**.

### Tenant Admin and Application Admin

**Application console** (the application section above, for your applications only) and **SecureOne Documentation**. Tenant Admins who can manage clients also see **Connect an app**.

## Where you land after sign-in

- Platform operator with no OAuth clients yet: **OAuth clients** (`/applications`), so you can register the first app.
- Tenant operator: the **Users** page of the first application assigned to you.
- Otherwise: the application console of the first application.

Old addresses such as `/users`, `/roles`, `/audit`, `/sessions`, `/logs` and `/tenant` redirect to the same page of your first application.

## Help in the console

- **i** icons beside field labels show an explanation on hover or keyboard focus.
- The three action buttons on the OAuth clients page show a tooltip explaining when to use each one.
- **SecureOne Documentation** offers **Download PDF** (the current page) and **All docs PDF** (every page in one file). The documentation is public: anyone can read it at `/admin/documentation` without signing in, for example to share a page with a developer.

## Common controls

- **Cancel** closes a form without saving.
- Delete actions always ask for confirmation in a dialog; read the message, it says what is removed.
- Secrets appear in a highlighted box with **Copy**. They are not shown again.
- Settings pages with *Changes auto-save after a short delay* save as you type; other forms have an explicit **Save** button.
