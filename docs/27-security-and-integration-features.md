# 27 — Security and integration features

What SecureOne offers alongside sign-in itself, and where each is set. Settings marked *platform* are under **Platform settings**; *application* ones under an application's **Settings**.

## Console home (Overview)

Platform super admins start on **Overview**; tenant super admins have one per tenant (**Tenant → Overview**):
sign-ins and failures (24 hours, 7 days), active users over 30 days, how many users have two-factor, locked and suspended accounts, a 14-day sign-in chart and the latest security events.
API: `GET /api/admin/v1/overview`, `GET /api/admin/v1/tenants/{id}/overview`.

## Hosted pages: branding and languages

SecureOne's sign-in, sign-up, password reset, device sign-in, consent and account pages show the application's name, logo, colours, corner radius and dark mode (**Settings → Appearance**; logo must be `https://`).
They are translated into English, Spanish, French, German, Portuguese and Hindi. The language comes from `?lang=`, the OIDC `ui_locales` parameter, the person's earlier choice, the application's **Sign-in page language**, then the browser; a picker on each page changes it. Messages the server sends back (wrong password, password rules, expired links and codes, email change) are translated too.
The translations (`static/account/i18n.js`) have not yet been reviewed by native speakers; have each language checked before relying on it for customers.
API for your own screens: `GET /api/v1/applications/{id}/branding` (public).

## Attack protection (platform)

- **Bot protection**: Cloudflare Turnstile, hCaptcha or Google reCAPTCHA v2 on sign-up, password reset, magic links, one-time codes, and password sign-in (never, always, or after N failures from one address within 15 minutes). This covers both the hosted pages and the API sign-in (`/api/v1/.../auth/session/login`), which takes the token in the `X-Captcha-Token` header and answers 400 with `captchaRequired` when one is needed. The secret key is stored encrypted.
  - **Allowed host names**: optional. A challenge solved on any other site is refused, so a site key copied to another site cannot be used to farm tokens.
  - **If the CAPTCHA provider is down**: requests are refused by default (fails closed). Turning on fail open lets them through without a check until the provider is back.
- **IP rules**: addresses or CIDR ranges blocked everywhere, and optionally the only addresses the admin console accepts. Client addresses come from `X-Forwarded-For` only when a trusted proxy sends it (`SECUREONE_TRUSTED_PROXIES`, see [Custom domain](runbooks/custom-domain.md)).
- **Sign-in alerts**: "New sign-in to your account" emails when an account signs in from a browser and system, or a network (IPv4 /16, IPv6 /32), it has not used in 180 days (on by default).

## Audit log, retention and streaming (platform)

- **Audit log** (platform: every tenant; application: that application) with filters (tenant, dates, action, actor, result) and **CSV export** (up to 100,000 rows; the export itself is audited).
- **Logs & streaming**: keep the audit log and sign-in history 30–3650 days (default 365; purged nightly in batches of 5,000); stream every audit entry to up to five HTTPS endpoints (JSON, or Splunk HTTP Event Collector), with a header for the API key (stored encrypted) and a test button. Entries are sent in batches every 2 seconds (up to 500 per POST: a JSON array, or concatenated Splunk events). A batch a stream refuses is retried on the next two rounds, then dropped. Up to 10,000 entries wait while a stream is down, after which the oldest are dropped and a warning is logged.

## Webhooks (tenant)

Tenant → Webhooks: HMAC-signed HTTPS notifications for all events, or a chosen set: user created / updated / deleted / suspended / locked / unlocked, email verified / changed, password changed, two-factor added / removed, roles changed, application access granted / revoked, sign-in succeeded / failed, session ended, role and group changes, account deletion requests, access requests. Recent deliveries are listed per webhook.
Events list: `GET /api/admin/v1/tenants/{id}/webhooks/events`.

## Users

- **Change email** (Account security page, or `POST /api/v1/account/email/change` with the user's access token): the current password is checked (accounts without one, such as Google, SAML or passkey-only accounts, must have signed in within the last 10 minutes), a link goes to the new address, and the email changes only when it is opened; the old address is told at both steps. Applications signed in under the old address get a back-channel logout.
- **Connected apps** (Account security page): the apps the user allowed on the consent screen, and any other app that still holds a valid token for them; removing one withdraws consent and revokes its tokens.
- Security notices (password changed, sign-in changes, new device, account locked) are always sent.

## OAuth clients

- **Back-channel logout URL** (OIDC Back-Channel Logout 1.0): SecureOne POSTs a signed `logout_token` (RS256, `typ: logout+jwt`, `sub` as the client sees it, and `sid` after an application's RP-initiated logout) to every application the user signed in to. This happens when the user signs out of SecureOne, when all their sessions are ended (admin or two-step reset, SAML logout), and when they are suspended, deleted or change their email. The address is checked again when sending: one that now resolves to a private network is skipped. Discovery advertises `backchannel_logout_supported`. The TypeScript SDK has `verifyLogoutToken()`.
- **Token subject**: User ID (stable, the default) or legacy `tenant:email`. With the legacy subject, a user who changes their email looks like a new user to the app; the client form warns about this.
- **Third-party app: ask users for consent**: users see what the app asks for and allow or cancel (`/oauth2/consent`); the choice is remembered and can be withdrawn under Connected apps.

## Token claims (application)

**Settings → Integration → Token claims**: up to 20 extra claims in the application's access and/or ID tokens, from a user attribute, a fixed value or a profile field (email, phone, display name, username, tenant). Names SecureOne already sets (`sub`, `roles`, `email`…) cannot be used; prefix with your domain to avoid clashes. Attributes SecureOne keeps for its own use (`pendingEmail`, `pendingEmailApplicationId`, `pendingInviteApplicationId`, `authMethods`) cannot be put in tokens.

## Settings for single applications (platform)

**Platform settings → For applications → Exceptions for single applications**: open or close a section for one application, different from the default. Two Factor Authentication keeps its own per-application switch instead. An application's exceptions are removed when it is deleted.

## Removed

- **Organizations** inside a tenant (names only; no members, no sign-in rules, no token claim). Tenants are the customer boundary. Migration V78 drops the empty table.
- **LDAP / AD** placeholders (import source, connection form, feature flag). Import users from CSV or Excel.

## Runbooks

- [Custom domain](runbooks/custom-domain.md)
- [OpenID certification and penetration test](runbooks/oidc-conformance-and-pentest.md)
- SDK publishing: push a tag `sdk-vX.Y.Z` matching `packages/secureone-client/package.json`; `.github/workflows/publish-sdk.yml` publishes to npm (needs the `NPM_TOKEN` secret).
