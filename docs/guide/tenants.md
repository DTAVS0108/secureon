---
title: Tenants
summary: Tenant panels, roster, console access and tenant roles
order: 5
audience: public
---

# Tenants

[User guide](index.md) › Tenants

A **tenant** is an organization on the platform. Every tenant's users, applications and settings are isolated from the others by PostgreSQL row-level security. The built-in tenant `platform` holds the platform operators.

## Tenants list (`/admin/tenants`)

**Who:** platform operators. Tenant Super Admins open their own tenant page directly from **Tenant › Tenants**.

*Organizations using the platform. Each tenant is isolated via PostgreSQL row-level security.*

| Element | Meaning |
|---------|---------|
| **Total tenants**, **Active**, **Total users**, **Enterprise** | Summary cards |
| **+ New tenant** | Opens the tenant form |
| Row **Edit** | Edit name, plan or status |
| Row **Delete** | *Delete {name}? This removes the tenant and all associated configuration. This action cannot be undone.* The `platform` tenant cannot be deleted (*The platform tenant holds the platform operators and cannot be deleted.*). |

### Tenant form (**New tenant** / **Edit tenant**)

*Create a new isolated organization.* / *Update organization details.*

| Field | Required | What to enter |
|-------|----------|---------------|
| **Name** | Yes | The organization's name, for example `Tenant A`. |
| **Slug** | Yes | Short lowercase identifier, for example `tenant-a`. Operators of this tenant type it in **Tenant slug** on the sign-in page, and API calls use it. Choose carefully; it appears in URLs. |
| **Plan** | Yes | **Free**, **Team** or **Enterprise**. A label for your records and reporting. |
| **Status** | Yes | **Active**, or **Suspended** to block sign-in for everyone in the tenant. |

Press **Create tenant** or **Save changes**.

## Tenant page (`/admin/tenants/{id}`)

The header shows the tenant's **Users**, **Applications**, **Plan** and **Status**, with **Edit** and **Delete**. The panels below are described in order.

### Applications

*Applications registered in this tenant*, with member counts. **Settings** opens an application's settings and **Open console →** its console. New applications are created from [OAuth clients](oauth-clients.md) or [Connect an app](connect-an-app.md).

### Tenant roster

The tenant's operators and administrators: *import Tenant Admin operators from an app for tenant-wide console access; end users stay in application user management.*

| Control | What it does |
|---------|--------------|
| Search box | *Search users, roles, permissions, source…* |
| **Filters** | A popup with **Status**, **Source**, **Application**, **Console access** and **Email** sections. Tick any options; each shows how many people it matches. Options in one section are combined with "or", sections with "and". Active filters show as chips under the search box (**×** removes one, **Clear all** removes all). |
| **Application** (toolbar under the header) | The application that **Import from application**, **+ Invite Tenant Admin** and import/export work on. |
| **+ Invite Tenant Admin** | Opens **Assign Tenant Admin**: pick the **Application** (*Tenant Admin role is created on this application*), the **User list** (**All users in tenant** or the application's users) and the **User**. *Grants Tenant Admin on the selected application. The user can then manage all applications in this tenant.* |
| **+ Add to roster** | Opens **Import from application**: search Tenant Admins of an application (*Search by name, email, role, or permission…*) and press **Import**. |
| Row: columns | **User**, **Status** (**Verified** / **Unverified** / **Invite pending**), **Admin console** (**Has console access** / **No console access**), **Tenant roles**, **In-app roles**, **Permissions**, **Source** |
| **Grant console access →** / **Manage access →** | Opens **Manage operator access** (below). |
| **Roles & permissions** | Opens **Manage roles & permissions**: pick an **Application**, tick roles, see **Effective permissions (all selected roles)**, then **Save roles**. |
| **Remove** | *Remove from tenant roster?* Takes the person off the roster; their application accounts stay. |

#### Manage operator access

*Tenant operators only — controls who can sign in to this admin console and which applications they manage.*

| Tab / field | What it does |
|-------------|--------------|
| **Console access** → **Role** | **Application Admin** (one application), **Tenant Admin** (assigned applications only), **Tenant Super Admin** (all applications in the tenant). |
| **Application scope** / **Application** | For Application Admin and Tenant Admin: which application(s) they manage. Tenant Admins can manage the OAuth clients of these applications. |
| **Grant console access** / **Revoke** | Gives or removes console sign-in. |
| **Console features** | A matrix of console sections (Users, Groups, Roles, Settings…). For each: **Default** (role default), **Grant** or **Deny**. **Effective** shows the result. Press **Save feature overrides**. |
| **Tenant roles** | Custom governance roles from **Roles & Permissions**; **Save tenant roles**. |
| **In-app RBAC** | The person's roles inside an application; **Save in-app roles**. |

After granting access, set the person's password from the application's **Users** page (or send a set-password email) before they sign in: *Console access granted. Set the user password under the application Users tab before they sign in.*

### Admin console access

*Summary of tenant operators with console sign-in.* The form **Grant admin console access** offers a quick grant:

| Field | What to enter |
|-------|---------------|
| **User** | *Pick an application member; they are added to the tenant roster when access is granted.* |
| **Role** | **Application Admin**, **Tenant Admin** or **Tenant Super Admin**. *Application Admin — one app. Tenant Admin — assigned apps only. Tenant Super Admin — all apps in this tenant.* |
| **Application** | The application for Application Admin / Tenant Admin. |

Press **Grant access**. Each row has **Revoke**.

### Federation providers

Lists the tenant's external sign-in providers (Google, GitHub, OIDC, SAML). Providers are added in an application's **Settings › Authentication › Identity federation**; see [Settings](settings.md#identity-federation).

### Organizations

*Nested org hierarchy within a tenant (departments, business units).*

| Field | What to enter |
|-------|---------------|
| **Name** | For example `Engineering`. |
| **Slug** | Optional short name. |
| **Parent org** | **None (root)** or an existing organization, to build a tree. |

Press **Create organization**. Rows can be deleted.

### Access requests

*Request access to tenant or application roles. Approval automatically grants the role.*

| Field | What to enter |
|-------|---------------|
| **Requester** | The user asking. |
| **Grant to (optional)** | **Same as requester**, or another user who should receive the role. |
| **Resource type** | **role (tenant first, then app)**, **tenant_role** or **application_role**. |
| **Application** | For application roles. |
| **Role name or UUID** | For example `Support Agent`. |
| **Reason** | Optional justification, kept in the audit log. |

Press **Submit request**. Pending requests show **Approve** and **Deny**; approving grants the role at once. Statuses: PENDING, APPROVED, DENIED.

### Service accounts

*Machine identities for M2M integration. API keys are shown once at creation.*

| Control | What it does |
|---------|--------------|
| **Name**, **Description** | For example `billing-sync`. |
| **Create service account** | Creates the account and a **Default key**: *API key created — copy it now* under **New API key (copy now)**. |
| **Keys** | Lists the account's keys. **Generate API key** adds one (shown once); **Revoke** disables one. |
| **Delete** | Removes the account and its keys. |

### Webhooks

*Outbound HTTPS notifications for login and lifecycle events (HMAC-signed).*

Enter the **Endpoint URL** (for example `https://example.com/hooks/secureone`) and press **Add webhook**. The **Signing secret (copy now)** is shown once; your receiver uses it to verify the HMAC signature of each call. New webhooks receive these events: `user.login.succeeded`, `user.created`, `access_request.created`, `access_request.approved`, `access_request.denied`. Rows can be deleted.

> The tenant's SCIM token has no screen here; set it through the API or a [configuration file](configuration-as-code.md).

## Roles & Permissions (`/admin/tenants/roles`)

Platform operators first pick a **Tenant** and press **Manage roles**; Tenant Super Admins go straight to their tenant. *Console operator roles, Groups access, and custom tenant governance.*

Summary cards: **Console roles**, **Custom roles**, **Permissions**, **Assignments**.

### Permission catalog

*Platform catalog keys plus custom permissions for tenant roles.* **Platform catalog** lists built-in keys. Under **Custom permissions**, enter a **Permission key** (for example `custom:reports`) and a **Description** to add your own.

### Role directory

*Console and custom roles.* Search with *Search roles…*.

- **Console roles** (Application Admin, Tenant Admin, Tenant Super Admin; marked **System**): choose their **Console sections**, *Admin console areas operators with this role can access*, and press **Save console sections**. The scope reads *Single assigned application*, *Assigned applications only* or *All applications in tenant*.
- **Custom roles**: **New custom role** asks for a **Role name** (*Shown in the role directory*), a **Description** and an **Application scope** (*Leave none selected to allow all applications in this tenant.*); press **Create role**. Open a role to edit **Name**, **Description**, **Console sections**, **Governance permissions** and **Application scope**, then **Save changes**. **Active assignments** lists who holds it.

Custom roles are assigned per person in the roster's **Manage operator access › Tenant roles**.

## Tenant access review (`/admin/tenants/{id}/access-review`)

*Console roles in this tenant, when each person last signed in, and whether they use MFA.* See [Access review](access-review-and-operators.md).
