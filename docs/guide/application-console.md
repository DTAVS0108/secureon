---
title: Application console
summary: Users, groups, roles, permissions, audit, logs, sessions
order: 6
audience: public
---

# Application console

[User guide](index.md) › Application console

**Where:** sidebar **Application console**, or pick an application in the top-bar **Application** selector. Every page works on the selected application (`/admin/app/{applicationId}/…`).

**Who:** platform operators (all applications), Tenant Super Admins (all applications of their tenant), Tenant Admins (assigned applications), Application Admins (their one application). Individual sections can be granted or denied per person; see [Tenant roster](tenants.md#tenant-roster).

The console needs at least one OAuth client on the platform. Until then it shows *Application console unavailable — register an OAuth client first*, with **Connect an app**.

## Users

*User management: browse members, then open a user to manage account settings.*

### Users list

| Element | What it does |
|---------|--------------|
| **Members**, **Active**, **With MFA** | Summary cards for this application |
| **+ Invite user** | Opens **Invite a user** (*Send an invitation and assign roles for this application.*) |
| Search | *Search name, email, username, role…* |
| **Filters** | Opens a popup with **Status** (Active, Invited, Suspended, Disabled) and **Role** sections. Tick any number of options; each shows how many members it matches. Options in one section are combined with "or", the two sections with "and". The button shows how many filters are on; **Clear all** removes them. |
| Filter chips | Active filters appear under the search box; **×** removes one. |
| ↑ / ↓ on created date | Sort newest or oldest first |
| Row | Click to open the user. Columns **User**, **Username**, **Roles**, **Status**, **Manage**. |
| **Import users** | *Bulk load from CSV or Excel (save as CSV) when import is enabled.* Choose the **Import source** (**CSV**, **Excel (CSV)**, **LDAP** *Coming soon*) and upload. Errors are listed under **Import errors**. Enable it in **Settings › User directory**. |
| **Export CSV** | Downloads the application's members. Enable it in **Settings › User directory**. |

Empty state: *No users in this application yet. Invite someone or import from CSV when enabled in settings.* with **Invite first user**.

### Invite a user / Edit user

**Profile**

| Field | Required | What to enter |
|-------|----------|---------------|
| **First name**, **Last name** | Yes | For example `First` `Last`. |
| **Email** | Yes | The sign-in email. The invitation is sent here. |
| **Username** | No | Optional alternative sign-in name, for example `username`. |
| **Tenant** | Yes | Fixed to the application's tenant. *User will be added to this application automatically after invite.* |

**Account status**

| Field | What it means |
|-------|---------------|
| **Status** (option cards) | **Invited**: must set a password from the email link before signing in. **Active**: can sign in. **Suspended**: blocked for now; reactivate any time. **Disabled**: switched off; cannot sign in. |
| **Email verified** (switch, edit only) | Unverified users cannot sign in with a password. Turn it off to make them confirm their address again (**Security › Resend verification email**). |

**Roles** (right-hand column): the application's roles with their descriptions and a "*n* of *m* selected" count. **Search roles…** narrows the list without losing ticks; **Select all** and **Clear** change every role at once.

The dialog is wide: **Profile** and **Account status** on the left, **Roles** on the right (stacked on narrow screens).

Press **Invite** (or **Save changes** when editing).

### User page

The header shows the name, status badges (**Verified** / **Unverified**, **Locked**) and **Edit**, **Delete**, **Impersonate** and **← Back to user list**.

| Field | Meaning |
|-------|---------|
| **User ID** | The permanent account ID (the token `sub` with User ID subject) |
| **Tenant**, **Application** | Where the account lives |
| **Created**, **Last login** | Dates |
| **Phone** | For SMS codes |
| **Password set** | Yes, or **No (invite pending)** |
| **Failed logins** | Wrong passwords in a row (resets on success) |
| **Roles** | Count |

**Delete**: *Permanently removes this account and all its two-step verification options.*

**Impersonate** asks for a *Reason for impersonation (audited)* and opens a new tab signed in as that user, to reproduce a problem they report. Every impersonation is recorded in the audit log.

The page has these tabs (**Overview** shows the fields above, the buttons and recent sign-ins):

#### Overview / Security

*Account access, email verification, password emails, and MFA shortcuts.*

| Card | Buttons |
|------|---------|
| **Account access**: *Status, lockout, and suspension. Suspended users cannot sign in.* | **Suspend account**, **Activate account**, **Unlock account** (after too many failed sign-ins) |
| **Email verification**: *Unverified users cannot use password sign-in.* | **Resend verification email** (*Verification mail goes to…*), **Verify email (allow sign-in)** to verify by hand |
| **Password & credentials**: *Email links for self-service password setup, or set/remove the password directly as admin.* | Under **Password emails**: **Set-password** (sends the invite link) and **Reset** (sends a reset link). Under **Admin password actions**: **Set password now** (opens **Set password**, *sets a new password immediately; policy rules apply*) and **Remove password** (*Clears the stored password hash.*) |
| **Multi-factor authentication** | **Manage MFA**, **View sign-in history** |

#### Two-step

The user's two-step verification status (**2FA on**, **2FA off**, **2FA due**, **2FA overdue**, **2FA required**, **2FA exempt**), their options, backup codes left and remembered devices. **Remove** deletes one option, **Reset all** removes every option; **Extend** gives more time to set it up and **Exempt** lets them sign in without it until a date. Reset and exempt ask you to **Verify it's you** first. See [Two-step verification](two-step-verification.md#admin-actions-for-a-user).

The users list shows the same status as a badge and has a **Two-step verification** filter.

#### Roles

*RBAC roles assigned to this user.* **Manage roles** opens the role picker. Composite roles are marked **Composite**.

#### Sign-in methods

*Per-user allow list for app-enabled authentication.* For this one user, mark each method **Allowed** or **Blocked** under **Primary sign-in**, **Federation / SSO** and **Multi-factor (allow at sign-in)**. Only methods enabled for the application appear. Use it, for example, to force one user onto SSO only.

#### Sign-ins

Recent sign-in attempts (**Signed in**, **Failed sign-in**). *No sign-in history for this application.* when empty.

## Groups

*Group management: assign multiple roles to a group, then add members.*

Summary cards: **Groups**, **Roles available**, **Application**. The **Group directory** lists groups (*Search groups…*). Selecting a group shows **Roles assigned**, **Members** and **Created**, with tabs for **Roles**, **Permissions** and **Users**.

**+ New group** / **Edit**:

| Field | What to enter |
|-------|---------------|
| **Name** | For example `Engineering`. |
| **Description** | Optional note for admins. |
| **Roles in this group** | *Members receive all selected roles.* |
| **Members** | *Users in this tenant who belong to the application.* |

**Delete**: *Remove "{name}"? Members lose group-derived roles immediately.*

## Roles

*Role management: roles, direct permissions, composite inheritance, and group bundles.*

Summary cards: **Total roles**, **System / built-in**, **Composite**, **Permissions**. The **Role directory** (*click a row to inspect*) has search and **+ New role**.

### New role / Edit role

| Section | Field | What to enter |
|---------|-------|---------------|
| **Details** | **Name** | For example `Support Agent`. |
| | **Description** | *What members with this role can do.* |
| **Composite role** | (switch) | *Inherit all permissions from child roles (no nested composites).* Then tick the child roles. |
| **Direct permissions** | (checkboxes) | Permissions this role grants by itself. |

### Role page

Cards: **Assigned users**, **Effective permissions** (direct + inherited), **Inherited roles**, **Catalog size**. Tabs: **Overview**, **Permissions** (*Toggle permissions directly — changes save immediately. Or use Edit role for bulk changes.*), **Inherited roles** (**Edit inheritance**), users (**Manage users →**).

- When the application has no permissions yet, the Permissions tab says *No permissions in catalog.* with an **Install defaults** link to the Permissions page.
- Built-in and system role names are locked.
- **Delete**: *Remove "{name}"? Unassign all {n} user(s) first.*

## Permissions

*Permission catalog: manage permissions and assign them to roles.*

Summary cards: **Total permissions**, **Resources**, **Application**. The **Permission directory** lists every key (*Search key, description, id…*).

**+ New permission** (**Permission row**):

| Field | What to enter |
|-------|---------------|
| **Key** | *Format: resource:action (lowercase)*, for example `user:read`, `order:refund`. Your app checks these keys in the token or via the API. |
| **Description** | *What this permission allows.* |

Selecting a permission shows its database fields (**id (PK)**, **application_id**, **key**, **description**), a **SQL reference**, and the role matrix: *Toggle which roles grant* it. **Delete** asks you to *Unassign from all roles first*.

## Settings

Application overrides of the platform defaults. See [Settings › Application settings](settings.md#application-settings).

## Audit log

Every admin and security event for this application. Filter with *Filter by actor, action, target…* and the result filter (**All**, **Success**, **Failures**). Columns: **Time**, **Actor**, **Action**, **Target**, **Result**.

## Application logs

*Runtime log events for this application. Filter by session ID (from the Sessions page) or request ID, or search the message text. A session or request ID searches all time.*

| Filter | What to enter |
|--------|---------------|
| **Session ID** | *JSESSIONID or full value*, copied from **Sessions** |
| **Request ID** | The `X-Request-Id` value, for example from an error page or your app's logs |
| **Search message** | Words such as `error`, `login`, `oauth` |
| **Level** | **ALL**, **ERROR**, **WARN**, **INFO**, **DEBUG** |

**Refresh** reloads. Columns: **Time**, **Level**, **Message**, **User**, **Session**.

**Delete logs by date** (expand ▼): *Removes stored log events from the database.* Dates use your local timezone. **Delete today**, **Delete yesterday**, **Delete on date** (pick **On date**) or **Delete between dates** (**From** / **To**). Tick *Also apply current session / request / level / message filters* to delete only the matching events.

## Sessions

*Sign-in history for {application}.* Filter with *Filter by user, session, IP…* and the result filter (**All**, **Signed in**, **Failed**, **MFA required**). Columns: **When**, **User**, **Method**, **Result**, **Session**. Click a session ID (*View logs for session…*) to open **Application logs** filtered to that session.
