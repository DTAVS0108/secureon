---
title: Settings
summary: Platform and application settings, tab by tab
order: 7
audience: public
---

# Settings

[User guide](index.md) › Settings

Settings exist at two levels:

- **Platform settings** (`/admin/settings`, platform operators): *Global defaults inherited by all applications unless overridden.*
- **Application settings** (`/admin/app/{id}/settings`): *overrides platform defaults* for one application.

An application inherits every platform value until someone saves an override for it. A platform operator decides which sections applications may override, in **For applications**. Sections not opened to applications stay platform-only and are hidden on the application's Settings page.

Many settings pages **auto-save** a moment after you change a field (*Changes auto-save after a short delay*); cards with their own **Save** button save only when pressed. Hover the **i** icons for field help.

## Tabs at a glance

| Tab | Platform | Application | Contents |
|-----|:-------:|:-----------:|----------|
| **Integration** | | ✓ | IDs, database schema, auth UI URLs, checklist, API reference, health check |
| **Notifications** | ✓ | ✓ (if opened) | Alert channels, recipients, sender, SMTP, email templates, test email |
| **Appearance** | ✓ | ✓ (if opened) | Platform: admin console theme. Application: branding and theme for client UIs |
| **Authentication** | ✓ | ✓ (if opened) | Sub-tabs **Sign-in methods**, **Verification & OTP**, **Two Factor Authentication** (applications: only while its Feature flags switch is on), **Password policy** |
| **Feature flags** | ✓ | ✓ (if opened) | Capability toggles and which settings tabs appear |
| **User directory** | | ✓ (if opened) | Import/export of users |
| **Public API** | | ✓ (if opened) | The unauthenticated application manifest |
| **OAuth tokens** | | ✓ (if opened and enabled) | Token lifetimes and refresh-token rules |
| **For applications** | ✓ | | Which sections applications may override; SCIM provisioning |

---

## Integration (application)

Everything a developer needs to wire the application.

| Card | Contents |
|------|----------|
| IDs | **OAuth client ID** and **Application UUID**, with copy buttons; **register one** if the application has no client |
| **Database schema** | The schema holding this application's IAM data, and **Isolate** for older shared-schema applications |
| **Authentication UI** | Pages in *your* app that SecureOne links to in emails (below) |
| **Native app URLs** | Mobile deep links used in emails |
| **Integration checklist** | Steps to complete; **Notes** (*Environment URLs, test accounts, rollout notes…*) for your team |
| **API reference** | The endpoints this application needs, in sections (Discovery, Sign-in, Signup, Tokens, Session, Profile, Password, Devices, Machine, Provisioning) with chips to jump to each. Every endpoint has **Copy curl** and **Copy URL**; expanding it shows a ready-to-run curl (with this application's ID and client ID filled in), authentication, request fields, examples and responses. **OpenAPI docs →** opens Swagger UI (needs `SECUREONE_SWAGGER_ENABLED=true`). The full reference for every endpoint is the [API reference](../api/index.md). |
| **API health check** | *Probes public application-scoped endpoints.* Run it to confirm the application's public endpoints answer |

### Authentication UI fields

*Password reset emails include a one-time token in the link. Point these URLs at your storefront pages so users land on your UI (not SecureOne's hosted HTML).* Leave empty to use SecureOne's hosted pages.

| Field | What to enter |
|-------|---------------|
| **Forgot password page** | Where users request a reset email, for example `https://shop.example.com/forgot-password`. Your page calls `POST /api/v1/account/password/forgot`. |
| **Reset password page** | Base URL for the email link; SecureOne appends `?token=…`. |
| **Magic link landing page** | Base URL for magic-link emails; SecureOne appends `?token=…&applicationId=…`. |

---

## Notifications

### Platform: Platform alert channels

| Field | Meaning |
|-------|---------|
| **Email alerts** | *Master switch for platform operator notifications.* |
| **Audit alerts** | *Tenant and application lifecycle events.* |
| **Security alerts** | *Platform-level security events when no application context is available.* |
| **Platform admin recipients** | *Operators who receive platform alert emails.* |

**Platform email sender**: **From name**, **From address**, **Reply-to** for operator alert emails.

### Application: Notification channels

| Field | Meaning |
|-------|---------|
| **Email notifications (admin)** | *Application admin alerts to recipients below.* |
| **User email (transactional)** | *Verify email, password reset, and password-changed messages to end users.* Turn off only if your app sends these itself. |
| **Push notifications** | *Mobile / web push — not available yet.* |
| **Audit alerts** | *Email on high-risk admin actions.* |
| **Security alerts** | *Failed logins, lockouts, MFA changes.* |
| **Admin recipients** | Expand to pick **Users with admin roles**, or **Add any user (custom email)** and press **Add**. **Assign an admin role** links to the Users page. |
| **Recipient groups** | *Named lists merged into admin alert delivery.* Enter a group name and **Emails** (comma-separated), then **Add group**. Groups can be used in CC/BCC of templates. |

**Email sender**: *From address shown on outbound mail*: **From name**, **From address**, **Reply-to**.

### SMTP server

How SecureOne sends email. Platform SMTP is the default; an application can use its own.

| Field | What to enter |
|-------|---------------|
| **Host** | Your mail provider's SMTP host. |
| **Port** | Usually 587 (STARTTLS) or 465 (SSL). |
| **Security** | **SSL (port 465)**, **STARTTLS (port 587)**, or **None** (only for a local test server such as MailHog). |
| **SMTP authentication** | On when the server needs a login (almost always). |
| **Username** | The mailbox login, for example `you@example.com`. |
| **Password** | The mailbox password or an app password. Stored encrypted; leave blank to keep the saved one. |

### Email templates

*View and edit existing templates, import platform defaults, add custom templates, and use {{placeholders}} for dynamic values.*

| Field | What to enter |
|-------|---------------|
| **Template** | Which template to edit; the switch enables or disables it. |
| **Display name** | *Shown in the template picker.* |
| **Description** | *Internal note for admins.* |
| **Subject** | The email subject; may use placeholders, for example `Welcome to {{appName}}`. |
| **CC** / **BCC** | *Admin recipients, recipient groups, or individual emails. Groups resolve when the email is sent.* |
| **Variables** | *Comma-separated placeholder names used in subject/body (without braces)*, for example `userName, actionLink, appName`. |
| **Plain text body** | Text version of the email. |
| **HTML body** | *Full HTML markup; use {{variable}} placeholders.* |

**Discard changes** reverts edits. **Add custom template** asks for a **Template key** (for example `welcome_email`) and a display name; press **Add template**.

### Send test email

Shown when the **Email test console** flag is on. Fill **To**, pick a **Template**, optional **CC** / **BCC**, and **Custom template data** (*JSON key/value pairs for {{placeholders}}*), then send. Use it to check SMTP settings and templates.

---

## Appearance

### Platform appearance

*Admin console theme only.* It does not affect client applications.

### Application branding (application)

*Name and logo returned in the public manifest appearance block.* Your app's login UI reads them from the [Public API](#public-api).

| Field | What to enter |
|-------|---------------|
| **App display name** | The product name shown on sign-in screens. |
| **Logo URL** | An `https://` link to the logo image. |

### Theme editor (both levels)

| Field | Meaning |
|-------|---------|
| **Theme mode** | **System**, **Light** or **Dark** |
| **Font scale** | Text size multiplier |
| **Buttons & navigation**: **Button / nav color**, **Button label text**, **Links & accent** | *Primary buttons, active tabs, and solid button fills.* |
| **Light mode — page & cards** / **Dark mode — page & cards**: **Page background**, **Cards & panels**, **Body text** | Colors per mode |
| **Popup notifications**: **Info toasts**, **Success toasts**, **Error toasts** (each **Background**, **Text**, **Border**) | *In-app toast / snackbar colors for client applications.* |
| **Gradient theme**: **Gradient style** | **Solid (no gradient)**, **Primary → Accent**, **Sunset**, **Ocean**, **Violet dusk**, **Custom gradient** |
| **Apply gradient to** | **Page background only**, **Buttons & nav only**, **Page + buttons & nav** |
| **Gradient start**, **Gradient end**, **Gradient angle (°)** | For a custom gradient |
| **Border radius (px)** | Corner rounding of buttons, inputs and cards |
| **Dialogs & modals** (application) | **Dialog background**, **Dialog text**, **Dialog border**, **Backdrop overlay** (*RGBA hex, e.g. #00000066*) |
| **Inputs & forms** (application) | **Input background**, **Input text**, **Input border**, **Placeholder text** |
| **Common UI** (application) | **Muted text**, **Dividers** |

A live preview shows the result. **Reset defaults** restores the original theme.

---

## Authentication

### Sign-in methods

| Card | Meaning |
|------|---------|
| **Primary login**: *How users authenticate first* | **Password** (username + password), **Magic Link** (email passwordless login), **Passkeys (WebAuthn)** (phishing-resistant sign-in) |
| **Identity federation** | *Google, GitHub, external OIDC and SAML 2.0 sign-in. Configure the providers below when enabled.* |

Switch each method on or off. Users can only use methods that are on (and that are allowed for them personally; see the user's **Sign-in methods** tab).

#### Identity federation

**Add provider**:

| Field | What to enter |
|-------|---------------|
| **Provider type** | **Google OIDC**, **GitHub OAuth**, **External OIDC** or **SAML 2.0 (enterprise SSO)** |
| **Alias** | *Used in login URLs, e.g. /federation/google/start.* Short lowercase name. |
| **Display name** | The button label, for example *Continue with Google*. |
| **OAuth client ID** / **OAuth client secret** | Google, GitHub and OIDC: the credentials you created at the provider. The secret is stored encrypted. |
| **IdP metadata URL** | SAML: *Preferred: keeps certificates current.* For example `https://idp.example.com/saml/metadata`. |
| **IdP entity ID**, **IdP SSO URL (HTTP-Redirect)**, **IdP signing certificate (PEM)** | SAML manual setup when you have no metadata URL. |
| **IdP single logout URL** | SAML, optional. |
| **Landing URL for IdP-initiated sign-in** | *Where users go when they start from the IdP's app portal. Leave empty to only allow sign-in started from the app.* |
| **Email attribute** | SAML, optional: *Defaults to email/mail attributes, then an email NameID.* |

**Configured providers** lists each provider with an on/off switch.

### Verification & OTP

*Hover the info icons on each field for details. Changes auto-save after a short delay.*

**Phone numbers**: **Default phone country** (ISO code, for example `IN` or `US`), assumed when users type a mobile number without `+country`.

One-time codes (all channels): **OTP length** (digits), **OTP TTL (seconds)** (how long a code stays valid), **Max attempts** (wrong codes before it is void), **Resend cooldown (seconds)** (wait before another code can be sent).

| Card | Fields |
|------|--------|
| **Signup verification**: *How new accounts confirm email or phone after registration.* | **Default mode** (**Email link** / **OTP code**), **Allowed modes**, **Required channels** (email, phone), **Allow user channel choice** |
| **Login & passwordless**: *Email/phone identifiers and OTP or magic-link sign-in.* | **Identifier types** (email, phone, username), **Passwordless modes** (the one switch for sign-in by email code, text-message code or magic link; text-message codes also need an SMS provider), (whether a second step is required is set on the **Two Factor Authentication** tab) |
| **Password recovery**: *Forgot-password via email link or OTP (email/SMS).* | **Allowed channels**, **Require verified email/phone**, **Fallback to link if OTP unavailable** |

#### SMS provider

| Field | What to enter |
|-------|---------------|
| **Enabled** | Turns SMS sending on. |
| **Provider** | **log** (codes are written to the server log, for development), **Twilio**, **AWS SNS** or **MSG91**. |
| Twilio | **Account SID**, **From number** (E.164, for example `+15551234567`), **Auth token** |
| AWS SNS | **Region**, **Access key ID**, **Secret access key**, **Sender ID (optional)** |
| MSG91 | **Sender ID**, **Template ID**, **API key** |
| **Test phone (E.164)** | *Send a test message using the saved provider.* |

Secrets are stored encrypted and can be left blank to keep the saved value.

### Two Factor Authentication

Which second-step options users can use, whether it is **Off**, **Optional** or **Required**, the grace period, reminders, remembered devices and the sign-in limits. Platform settings also have a lock menu on each setting that decides what tenants and applications may change; tenants have the same page under **Tenants › {tenant} › Two Factor Authentication**. Every field is described in [Two-step verification](two-step-verification.md#settings).

Second-step options are chosen here only (not under Sign-in methods). In application settings this tab appears only when the platform opens **Two Factor Authentication** to applications (**For applications**) and the application's **Feature flags › Settings tabs › Two Factor Authentication** switch is on. While the switch is off, two-factor is off for the application: sign-in never asks a second step, and the application's user pages hide the **Two-step** tab and 2FA badges. If the platform or tenant locks **Two Factor Authentication** (the mode) as **Locked** or **Only stricter**, that requirement still applies. Values saved earlier are kept for when the switch is turned on again.

### Password policy

*Enforced on signup, set-password, reset, and admin set-password flows.*

| Field | Meaning |
|-------|---------|
| **Minimum length** | Shortest allowed password |
| **Require uppercase**, **Require number**, **Require symbol** | Complexity rules |
| **Password expiry (days)** | *0 = never expires* |
| **Password history** | *Block reuse of the last N passwords (0 = off)* |
| **Lock account after failed sign-ins** | *Wrong passwords or codes in a row* before the account locks |
| **Lockout duration (minutes)** | How long the first lock lasts. Each further lockout in a row doubles it (15 → 30 → 60 min…, at most 24 hours). After a lock ends the user gets the full number of attempts again. A successful sign-in, a password reset or **Unlock account** ends the lock and the doubling. The owner is emailed on every lock. |
| **Hash algorithm** | *bcrypt only (stored on each credential)* |
| **Block leaked passwords (Have I Been Pwned)** | Rejects passwords found in public breaches (only a hash prefix is sent) |

---

## Feature flags

### Platform features (platform only)

*Console features for platform operators. Applications cannot override these.*

| Switch | Meaning |
|--------|---------|
| **Connect an app** | On by default. When off, every **Connect an app** link and button is hidden, `/admin/connect` is not found and the wizard refuses to create anything, for platform and tenant operators alike. **Register application & OAuth client** and **Add OAuth client** still work. |
| **Client setup code** | On by default. The **Setup code** section on each OAuth client's page (never the client secret); hidden when off. |
| **Configuration as code** | Off by default. When on, **Configuration as code** appears in the sidebar and its page and API work. When off, they are hidden and blocked, including by direct URL. See [Configuration as code](configuration-as-code.md#turning-it-on). |

### Feature flags

There are two kinds of flag.

**Application features** — main and advanced capabilities. In **Platform settings** they are the default for every application. While one is on there, each application's admin can turn it off for their application in the application's **Settings › Feature flags** (when **Feature flags** is opened to applications under **For applications**). While it is off in Platform settings, applications don't see it and can't use it.

| Flag | Meaning |
|------|---------|
| **Self-service Recovery** | Application-scoped forgot-password: by link or by code, as **Verification & OTP › Password recovery** allows |
| **Self Registration** | Public user signup on the hosted **Create account** page. Off by default. |
| **2FA: Try another way** | On the two-step verification screen, a **Try another way** button lists every option the user has enabled (authenticator app, passkey, security key, text or email code, backup code) when they can't use the one asked first. When off, users get only the option asked first. On by default. The admin console sign-in follows the **Platform settings** value. Audit log: switching it records **settings.feature_flag_changed** (as for every flag, e.g. *on → off*); signing in with another option records **user.two_step_another_way** (admin console: **admin.console_login.another_way**), e.g. *recovery instead of totp*. |

**Console controls** — parts of the admin console. They are decided in **Platform settings › Feature flags** for every application; applications never see or change them.

| Flag | Shows or hides | Default |
|------|----------------|---------|
| **Email test console** | The SMTP test panel under an application's Notifications | On |
| **Email templates** | The **Email templates** editor under an application's Notifications | On |
| **Add sign-in provider** | The **Add provider** form under Authentication › Identity federation (existing providers stay listed) | On |
| **Integration checklist** | The go-live checklist and notes on the Integration tab | **Off** |
| **API reference** | The API reference on the Integration tab | **Off** |
| **API health check** | The API health check on the Integration tab | **Off** |
| **Sessions** | The **Sessions** page of every application console (sidebar entry and URL) | On |

Flags hide parts of the console; who may see the data is still decided by roles and permissions. The **Audit log**
page is always available. A flag is on or off; there is no rollout percentage.

Retired (they did nothing, or a second switch decided the same thing): **Recovery OTP** (codes for
password recovery are allowed under **Verification & OTP › Password recovery**; where the flag was off, the upgrade
removed codes there), **LDAP / AD** (back when LDAP import is built), **Audit log** (always shown).

**Settings tabs**: **Two Factor Authentication**, *Give this application its own two-factor settings (requires platform to allow Two Factor Authentication under For applications)*; **OAuth tokens tab**, *Show the OAuth tokens settings tab for this application (requires platform to allow OAuth tokens under For applications).*

---

## User directory (application)

| Card | Fields |
|------|--------|
| **Import & export** | **Allow user import**, **Allow user export** (*Download CSV of application members*). Disabled features are hidden on the Users page. |
| **Import sources** | Which formats appear in the import menu: CSV, Excel (CSV). *LDAP is coming soon.* |
| **LDAP connection** | *Used when LDAP import is enabled.* *Directory sync is not available yet. Export users from LDAP / AD to CSV and import that.* |

---

## Public API (application)

**Public application API**: *Unauthenticated endpoint for client apps to discover sign-in options and flags. No login required.*

| Field | Meaning |
|-------|---------|
| **Enable public manifest** | *When off, GET returns 404. When on, clients can fetch configured sections.* |
| **Auth methods: enabled only** | *Omit disabled methods from the response (recommended for login pages).* |
| **Include in response** | Tick **Application info**, **Authentication methods**, **Feature flags**, **Password policy**, **Verification & OTP**, **Appearance**, **Federation providers**. *Sensitive admin settings (SMTP, LDAP passwords) are never exposed.* |
| **Endpoint** | The URL your login UI calls |

---

## OAuth tokens

Appears for an application when the platform allows it (**For applications › OAuth tokens tab**) and the application enables it (**Enable OAuth tokens tab**).

| Card | Field | Meaning |
|------|-------|---------|
| **Access & ID tokens** | **Access token lifetime** | How long an access token is valid (short: minutes) |
| | **ID token lifetime** | The OIDC `id_token` |
| | **Client credentials token** | Tokens for machine-to-machine clients |
| **Refresh tokens** | **Allow refresh tokens** | *Requires refresh_token grant on the OAuth client.* |
| | **Rotate on each use** | *Issue a new refresh token when the old one is exchanged.* Recommended. |
| | **Reuse detection** | *Revoke token family if a rotated refresh token is reused.* Protects against stolen refresh tokens. |
| | **Refresh token lifetime** | How long a user stays signed in without re-entering credentials |
| **Authorization flow** | **Authorization code lifetime** | The code returned after sign-in (seconds) |
| | **Device code lifetime** | How long a TV/CLI code waits for approval |

---

## For applications (platform)

*Choose which platform setting sections appear under each application's Settings page. Disabled sections stay platform-only.*

| Section | Lets applications override |
|---------|----------------------------|
| **Notifications** | Application email alerts, user mail, SMTP, and templates |
| **Email sender** | From name, address and reply-to |
| **Sign-in methods** | Password, magic link, passkeys and identity providers |
| **Two Factor Authentication** | The application's own two-factor settings (its Feature flags switch turns them on); platform and tenant locks still apply |
| **Password policy** | Length, complexity and history rules |
| **Feature flags** | Capability toggles |
| **OAuth tokens tab** | Token lifetimes |
| **Verification & OTP** | Signup verification mode, OTP TTL and channel policy |
| **SMS provider** | Per-application SMS provider (Twilio, AWS SNS, MSG91 or log) |
| **Appearance** | Per-application branding |
| **User directory** | Bulk import and export |
| **Public API** | The unauthenticated application manifest |

### SCIM 2.0 provisioning (platform super admin)

*Bearer-token API for user provisioning from your company's identity provider. Pair it with SAML or OIDC sign-in under Authentication → Identity federation.*

| Field | Meaning |
|-------|---------|
| **Enable SCIM** | *When disabled, /scim/v2 endpoints return 404.* |
| **Bearer token** | The token your identity provider (Okta, Entra ID…) sends. *Leave blank to keep the current token. Shown only on save.* |
| **Username domain suffix** | Optional hint for provisioned usernames, for example `secureone.local`. |
