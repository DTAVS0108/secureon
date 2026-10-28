# 4. OAuth clients

[User guide](README.md) › OAuth clients

**Where:** sidebar **OAuth clients** (`/admin/applications`). **Who:** platform operators. Tenant Super Admins and Tenant Admins manage the clients of their applications through [Connect an app](03-connect-an-app.md) and the client page.

## Key ideas

- An **application** (also called an *application product*) is your product: its users, roles, permissions, groups and settings. Each application gets its own database schema.
- An **OAuth client** is one piece of software that signs users in to that application. A web shop and its mobile app are two clients of one application; they share the same users and roles.
- A **confidential** client has a secret kept on a server (web apps, backend services). A **public** client cannot keep a secret (browser and mobile apps) and uses PKCE instead.

## OAuth client registry page

The page title is **OAuth client registry**: *Register application products with OAuth credentials. Use one form for a new product + first client, or add extra OAuth clients to an existing application.*

### The three buttons

Hover a button to see the same advice as a tooltip.

| Button | Use it when | What it creates |
|--------|-------------|-----------------|
| **Connect an app** | **Recommended.** You know what kind of app you have and its framework. | Application + client with secure defaults, ready-to-paste code and a test sign-in. See [chapter 3](03-connect-an-app.md). |
| **+ Register application & OAuth client** | A new product, and you want to choose every OAuth setting yourself (grant types, scopes, PKCE, redirect URLs). | A new application and its first client in one form. |
| **+ Add OAuth client** | The application already exists and you need another client, for example a mobile app next to the web app. | One client for an existing application (or it can auto-create the application). |

### Summary cards

| Card | Counts |
|------|--------|
| **OAuth clients** | All registered clients |
| **Applications** | All application products |
| **Confidential** | Clients that have a secret |
| **Active clients** | Clients with status Active |

### Filters

| Filter | Options |
|--------|---------|
| **Search** | Client ID or application name (*Client ID, application name…*) |
| **Tenant** | **All tenants** or one tenant |
| **Application** | **All applications** or one application |
| **Type** | **All types**, Web app, SPA, Native, Machine-to-machine |
| **Status** | **All**, **Active**, **Disabled**, **Suspended** |

### Table columns

| Column | Shows |
|--------|-------|
| **OAuth client** | The client ID (click to open the client page) and its internal UUID |
| **Application** | The application it belongs to |
| **Tenant** | The tenant that owns the application |
| **Type** | Web app, SPA, Native or Machine-to-machine |
| **Security** | **Confidential** or **Public**, and **PKCE required** when set |
| **Grants** | The first three grant types (+N for more) |
| **Status** | Active, Disabled or Suspended |

### Applications without an OAuth client

Deleting an application's last client keeps the application and its data, but the table above lists clients, so such an application would otherwise be invisible. They are listed under the table in **Applications without an OAuth client** (*Nothing can sign in to these yet*), with the application's slug, tenant and status.

| Button | What it does |
|--------|--------------|
| **Add OAuth client** | Opens **Register OAuth client** with the application already selected. |
| **Delete application** | Permanently deletes the application with its roles, permissions, groups, settings, sign-in providers, webhooks and database schema. People keep their tenant accounts but lose access to this application. Type the application's slug to enable the button. Audit and sign-in history are kept. |

When no client exists at all, the page also shows **Register an OAuth client first** with **Connect an app**: the application console needs at least one client on the platform.

## Register application & OAuth client

**Button:** **+ Register application & OAuth client**. **Form title:** *Register application & OAuth client: Creates the application product (with isolated IAM schema), then attaches the first OAuth client in one step.*

### Section: Application product

| Field | Required | What to enter |
|-------|----------|---------------|
| **Display name** | Yes | The product name users see on the sign-in page, for example `Example App`. |
| **Tenant** | Yes | The organization that owns the product. Its users can sign in. Shows *No tenants available* until you [create a tenant](05-tenants.md). |
| **Slug** | Yes | Short lowercase name, for example `exampleapp`. *Schema name + default client ID.* It becomes the database schema name, and the client ID when you leave **Client ID** empty. Cannot be changed later. |
| **Description** | No | Internal note, for example *Customer-facing product*. |
| **Status** | Yes | **Active** (users can sign in), **Disabled** (switched off), **Suspended** (temporarily blocked). Normally Active. |

### Section: OAuth client

| Field | Required | What to enter |
|-------|----------|---------------|
| **Client template** | Yes | Picks safe defaults for the fields below (see [Client templates](#client-templates)). Choose the one matching your software. |
| **Client ID** | No | The identifier your app sends, for example `exampleapp-web`. *Optional — defaults to application slug.* Use lowercase and dashes; it must be unique. |
| **Grant types** | Yes | How the client obtains tokens; tick one or more (see [Grant types](#grant-types)). Filled by the template. |
| **Scopes** | Usually | *Comma-separated OIDC scopes*, for example `openid, profile, email`. Empty for machine-to-machine. |
| **Redirect URIs** | For browser sign-in | *One per line.* Where SecureOne sends the user after sign-in, for example `https://app.example.com/callback`. Must match exactly what your app sends (scheme, host, port, path). Add every environment: local, staging, production. |
| **Post-logout redirect URIs** | No | *One per line.* Where users may be sent after signing out. |
| **Require PKCE** | | Proof Key for Code Exchange. Keep it on for every client that uses `authorization_code`. Off only for machine-to-machine. |
| *Auth at token endpoint* | (readout) | How the client proves itself when exchanging a code for tokens: `client_secret_basic` for confidential clients, *none (public client)* for SPA and native. |

Press **Register application & client**. The result shows the client details and, for a confidential client, the **client secret once**: *Client registered. Copy the secret now — it will not be shown again.* Copy it into your server's secret store, then press **Done**.

### Example: a web shop

| Field | Value |
|-------|-------|
| Display name | Example App |
| Tenant | Tenant A |
| Slug | `exampleapp` |
| Client template | Web app (confidential) |
| Client ID | `exampleapp-web` |
| Grant types | authorization_code, refresh_token |
| Scopes | `openid, profile, email` |
| Redirect URIs | `http://localhost:3000/api/auth/callback/secureone` and `https://shop.example.com/api/auth/callback/secureone` |
| Post-logout redirect URIs | `https://shop.example.com/` |
| Require PKCE | On |

## Add OAuth client

**Button:** **+ Add OAuth client**. **Form title:** *Register OAuth client: Register OAuth credentials. Leave application empty to auto-create a product and schema.*

| Field | Required | What to enter |
|-------|----------|---------------|
| **Application product** | No | The existing application this client belongs to (preselected when you open the form from an application's **Add OAuth client**). **Auto-create from client** creates a new application from the **Tenant** and **Application name** fields below. |
| **Tenant** | When auto-creating | *Required when auto-creating application.* |
| **Application name** | When auto-creating | *Used for auto-created product and schema.* |
| **Client template** | Yes | See [Client templates](#client-templates). |
| **Status** | Yes | **Active**, **Disabled** or **Suspended**. A disabled or suspended client cannot obtain tokens. |
| **Client ID** | No | *Optional — defaults to application slug.* Give extra clients distinct IDs, for example `exampleapp-mobile`. |
| **Grant types**, **Scopes**, **Redirect URIs**, **Post-logout redirect URIs**, **Require PKCE** | | As in the form above. |
| **Token subject (sub)** | Yes | What goes into the token's `sub` claim. **User ID (stable)** (recommended): the account ID, which never changes. **Legacy (tenant:email)**: changes if the user changes email; use only for apps built on the old format. |

Press **Register client**; the secret appears once, then **Done**.

## Client templates

| Template | Use for | Grant types | Scopes | PKCE | Auth at token endpoint |
|----------|---------|-------------|--------|------|------------------------|
| **Web app (confidential)** | Server-rendered web apps that can keep a secret | authorization_code, refresh_token | openid, profile, email | On | client_secret_basic (has a secret) |
| **SPA (public + PKCE)** | Browser-only apps (React, Angular) | authorization_code, refresh_token | openid, profile, email | On | none |
| **Native (public + PKCE)** | Mobile and desktop apps | authorization_code, refresh_token | openid, profile, email | On | none |
| **Machine-to-machine** | Backend jobs calling APIs without a user | client_credentials | (empty) | Off | client_secret_basic (has a secret) |

You can change any field after picking a template.

## Grant types

| Grant type | Meaning |
|------------|---------|
| **authorization_code** | The standard user sign-in: the browser is redirected to SecureOne, the user signs in, the app receives a code and exchanges it for tokens. Use with PKCE. |
| **refresh_token** | Lets the app get new access tokens without asking the user to sign in again. Lifetime and rotation are set in [OAuth tokens settings](07-settings.md#oauth-tokens). |
| **client_credentials** | The client signs in as itself, without a user. For backend services. Needs a secret. |
| **device_code (TVs, CLIs)** | For devices without a browser or keyboard: the device shows a short code, the user approves it on another device on the **Connect a device** page ([end-user pages](10-end-user-pages.md#connect-a-device)). |
| **token-exchange** | Swaps one token for another (RFC 8693), for example a service calling another service on behalf of a user. Advanced use. |

## Keeping the setup for later

On the **Client created** card of [Connect an app](03-connect-an-app.md), **Download setup (.md)** saves a Markdown file with the client's values, the endpoints and the setup code. Tick **Include the client secret** to put the secret in the file too; it is shown only once, so this is the moment to keep it. A file with the secret belongs in a password manager or secret store, never in Git or chat.

## Client page

Open a client by clicking its client ID in the registry, or **Client settings** after Connect an app. The header shows the client ID, *OAuth client for {application}*, and links back to **OAuth clients**, **Connect an app** and **Application console →**.

| Button | What it does |
|--------|--------------|
| **Edit client** | Opens the client form (**Edit OAuth client**: *Update client metadata, grants, and redirect URIs.*). Save with **Save changes**. Use it to add production redirect URIs. |
| **Isolate** | Platform operators, only for older applications still in the shared platform schema (new applications are isolated from the start and show *Isolated in schema {name}* instead). The dialog **Isolate application?** explains that it moves IAM data (roles, permissions, users, settings) into a dedicated PostgreSQL schema derived from the slug; OAuth clients stay in the platform registry. *This cannot be undone automatically.* |

### Client credentials card

**Client ID**, **Application**, **Client type**, **Client authentication** and **Last updated**.

**Rotate client secret** (confidential clients) creates a new secret and shows it once under **Client secret (shown once)**: *Store this in your vault. It cannot be retrieved later.* The old secret stops working immediately, so update your server straight away.

### Security policy card

**PKCE required**, **Token subject (sub)**, **Grant types**, **Scopes**, **Status**.

### OAuth 2.1 / OIDC endpoints card

*Share with application teams integrating with SecureOne.* Each value has **Copy**.

| Endpoint | Used for |
|----------|----------|
| **Issuer** | The authority your OIDC library is configured with |
| **Authorization** | Where the browser is sent to sign in (`/oauth2/authorize`) |
| **Token** | Where codes and refresh tokens are exchanged (`/oauth2/token`) |
| **JWKS** | Public keys that APIs use to verify token signatures |
| **Discovery** | `/.well-known/openid-configuration`; most libraries need only this |

### Setup code

Ready-to-use code for the client, rebuilt from its saved settings whenever you open the page, so you don't need the result page of **Connect an app** again. The app type (web, single-page, mobile, backend service, TV or command line) follows from the client; the framework is guessed from the redirect URL and can be switched with the buttons. **Download (.md)** saves the values, endpoints and code as a Markdown file. The client secret is never shown here: use the value you stored, or **Rotate client secret**.

Shown when **Platform settings › Feature flags › Platform features › Client setup code** is on (the default).

### Redirect URIs and Post-logout redirect URIs

The lists currently registered. Change them with **Edit client**.

### Danger zone

**Request deletion**: deleting a client needs a second admin. The dialog asks for an optional **Reason** (*e.g. App retired, replaced by web-v2*); **Send for approval** creates the request (*Sent for approval*) and nothing is deleted yet. The Danger zone then shows *Deletion waiting for approval* with who asked and when; the person who asked can **Withdraw request**. A platform operator or the tenant's super admin approves it under [Access review › Pending approvals](08-access-review-and-operators.md#pending-approvals); then the client is deleted and its credentials stop working immediately. To remove the application as well, delete its clients, then use **Delete application** under [Applications without an OAuth client](#applications-without-an-oauth-client).

## Good practice

- One client per piece of software; never share a client between a web app and a mobile app.
- Keep PKCE on for every `authorization_code` client.
- Prefer **User ID (stable)** for the token subject.
- Register exact redirect URIs; wildcards are not accepted.
- Rotate a secret if it may have leaked, and after people with access leave.
