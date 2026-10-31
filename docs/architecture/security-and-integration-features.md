---
title: Security and integration features
summary: Federation, SCIM, webhooks, attack protection and more
order: 5
audience: public
---

# Security and integration features

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
- **IP rules**: addresses or CIDR ranges blocked everywhere, and optionally the only addresses the admin console accepts. Client addresses come from `X-Forwarded-For` only when a trusted proxy sends it (`SECUREONE_TRUSTED_PROXIES`, see [Custom domain](../install/runbooks/custom-domain.md)).
- **Sign-in alerts**: "New sign-in to your account" emails when an account signs in from a browser and system, or a network (IPv4 /16, IPv6 /32), it has not used in 180 days (on by default).

## Audit log, retention and streaming (platform)

- **Audit log** (platform: every tenant; application: that application) with filters (tenant, dates, action, actor, result) and **CSV export** (up to 100,000 rows; the export itself is audited).
- **Logs & streaming**: keep the audit log and sign-in history 30–3650 days (default 365; purged nightly in batches of 5,000); stream every audit entry to up to five HTTPS endpoints (JSON, or Splunk HTTP Event Collector), with a header for the API key (stored encrypted) and a test button. Entries are sent in batches every 2 seconds (up to 500 per POST: a JSON array, or concatenated Splunk events). A batch a stream refuses is retried on the next two rounds, then dropped. Up to 10,000 entries wait while a stream is down, after which the oldest are dropped and a warning is logged.

- **Sign-ins**: every failed sign-in (hosted page or sign-in API) is a FAILURE row in the user's sign-in history and `user.login.failed` in the audit log (with a reason: `bad_credentials`, `locked`, `password_expired`, `unknown_or_inactive`; also for an unknown email of a known tenant), and sends the `user.login.failed` webhook. A lockout is audited as `user.locked` (security events on the home page, the `user.locked` webhook). Sign-in history rows name the application signed in to.

- **Audit log integrity**: entries cannot be changed, and are removed only by retention after at least 30 days (enforced in the database). Every change to platform or application settings is recorded as `settings.<section>_changed`, with the values saved (secrets masked). `actor_type` is ADMIN (console and admin API), USER (a person's own account) or SYSTEM (jobs).

## Webhooks (tenant)

Tenant → Webhooks: HMAC-signed HTTPS notifications for all events, or a chosen set: user created / updated / deleted / suspended / locked / unlocked, email verified / changed, password changed, two-factor added / removed, roles changed, application access granted / revoked, sign-in succeeded / failed, session ended, role and group changes, account deletion requests, access requests.
Events list: `GET /api/admin/v1/tenants/{id}/webhooks/events`.

- **Delivery**: events are stored and sent after the change commits. A delivery the receiver does not accept (no answer, or not 2xx) is tried again after 1 and 5 minutes, then 30 minutes, then 2, 6, 12 and 24 hours: 8 tries over about two days. After that it is marked failed, and **Send again** queues it once more. Each delivery shows its tries, last error and next try.
- **Signature**: `X-SecureOne-Signature-256: t=<unix time>,v1=<hex HMAC-SHA256 of "t.body">`. Refuse timestamps older than 5 minutes, and use `X-SecureOne-Delivery` (also `X-SecureOne-Attempt`) to ignore repeats. `X-SecureOne-Signature: sha256=<hex HMAC of the body>` remains for older receivers.
- **Rotate secret**: a new secret is shown once. The old one also signs, as a second `v1`, for 0–168 hours (default 24), so the receiver can switch over.
- **Send test**: sends a `webhook.test` event now and shows what the endpoint answered.
- **Application webhooks** (application **Settings → Integration → Webhooks**, for the application's admins): the same features, for that application's events only. The tenant's webhooks receive every application's events. A tenant webhook limited to one application must name an application of that tenant. API: `/api/admin/v1/applications/{id}/settings/webhooks`.

## Platform operators (platform)

Access review → Platform operators: **Super admin** (changes anything) or **Read-only** (sees everything a super admin sees and changes nothing: for auditors and support). At least one super admin always remains, and no one can change their own role. Adding, removing and changing roles need a fresh second step.

## Tenants (platform)

- **Suspending** a tenant signs every user out: their refresh tokens and app grants are revoked, their browser sessions end, and each app with a back-channel logout URL is told. New sign-ins, token refreshes and client-credentials tokens for the tenant's apps are refused until it is active again.
- **Deleting** a tenant needs a fresh second step and the tenant's slug typed again. Users are signed out first. The tenant's applications are deleted with their data (their database schemas too) and their settings exceptions.

## Impersonation (admin)

An admin can sign in as a user (Users → user → Sign in as user, with a reason) to see what they see.
- It needs a fresh second step. Platform operators and console admins cannot be impersonated.
- It never adds the user to an application; impersonating within an application needs them to be a member already.
- The one-time link works for 2 minutes. The session ends after 15 minutes without use.
- The hosted pages show a banner naming the admin. The user's account cannot be changed meanwhile (password, email, two-step, sessions, connected apps, deletion).
- Tokens issued during it carry `act: {"sub": "<admin>"}` (RFC 8693), so apps can tell.
- The reason is in the audit log.

## Signing keys (platform)

Platform settings → OAuth tokens → Signing keys:
- The next key is always published in JWKS ahead of time.
- **Rotate** makes it the signing key at once. The old key stays published, so tokens it signed keep verifying, until **Retire previous key**, which you do after the longest token lifetime.
- Every server picks a rotation up within 30 seconds.

A key set with `SECUREONE_JWK_PRIVATE_KEY_PEM` is rotated in the environment instead: set the new key, and the old one as `SECUREONE_JWK_PREVIOUS_PRIVATE_KEY_PEM`.

## Users

- **Change password** (Account security page, or `POST /api/v1/account/password/change` with the user's access token): needs the current password. On the page, this browser stays signed in and the user's other browser sessions end. The change is audited and the "password changed" notice is emailed.
- **Suspending or disabling** a user (console, API, SCIM deprovisioning, or the user asking for deletion) signs them out everywhere: grants and refresh tokens are revoked, browser sessions end (no new authorization codes), userinfo refuses their access tokens, and each application with a back-channel logout URL is told. Access tokens already issued still pass an API's local JWT check until they expire. A password reset link or code never turns a suspended or disabled account back on, even one sent before the change.
- **Sessions** (Users → user → Security): the user's browser sessions and the applications holding a grant for them (no token values). **Sign out everywhere** ends them all and tells the applications; the account stays active. In an application's console, the same view and button cover that application only. API: `GET /api/admin/v1/users/{id}/sessions`, `POST /api/admin/v1/users/{id}/sign-out` (and `/api/admin/v1/applications/{appId}/users/{id}/...`).
- **Profile attributes** (Users → user → Security): values from sign-up fields, imports or set by an admin; an empty value removes one. Keys: a letter, then letters, digits or `_` (up to 40); reserved names (`email`, `authMethods`…) are refused. API: `GET`/`PUT /api/admin/v1/users/{id}/attributes`.
- **Linked sign-in** (Account security page, and Users → user → Security): the Google, SAML… accounts a user signs in with; the user or an admin can unlink one. The last one cannot be unlinked while the account has no password.
- **Change email** (Account security page, or `POST /api/v1/account/email/change` with the user's access token): the current password is checked (accounts without one, such as Google, SAML or passkey-only accounts, must have signed in within the last 10 minutes), a link goes to the new address, and the email changes only when it is opened; the old address is told at both steps. Applications signed in under the old address get a back-channel logout.
- **Connected apps** (Account security page): the apps the user allowed on the consent screen, and any other app that still holds a valid token for them; removing one withdraws consent and revokes its tokens.
- Security notices (password changed, sign-in changes, new device, account locked) are always sent.
- **First and last name** are stored as entered (console, sign-up, CSV import, SCIM `name.givenName`/`name.familyName`, and the names a Google, OIDC or SAML provider sends for a new account), next to the display name. Tokens and userinfo carry them as `given_name` and `family_name` with the `profile` scope. Users change them on the Account security page (**Your name**) or with their access token: `PATCH /api/v1/account/profile` `{"givenName", "familyName", "displayName"}` (a field left out keeps its value; audited as `user.profile_updated`). Accounts from before 0.3.33 have only a display name until their names are saved once; tokens then leave the two claims out.
- **Temporary passwords**: an admin who sets a password can tick "Temporary" (ticked by default in the console; the API takes `"temporary": true`). The user signs in with it and is taken straight to choosing their own. Over the API sign-in, the answer is 403 with `passwordChangeRequired` and a `changeToken` for `POST /api/v1/account/password/reset`.
- **Personal data export** (Account → Privacy): profile, phone, custom attributes, two-step methods, sign-in history (latest 500), connected apps, application memberships and roles, and trusted devices.
- **Account deletion**: a user's request turns the account off at once. It is listed under the tenant's **Deletion requests**, where admins delete it now or cancel. Requests not handled are completed automatically 30 days after they were made. Deleting an account also removes its OAuth grants and consents, browser sessions, and its memberships, roles and groups in every application.

## Applications

- **Removing a user from an application**: their membership, the application's roles and groups and a pending invitation to it are removed; the grants and consents of its OAuth clients end (refresh tokens stop working) and those clients get a back-channel logout. Self-registration does not make them a member again; an admin adding them back does. Audited as `user.application_access_revoked` with `tenant:email`. Their account and other applications are untouched.
- **Users list** (`GET /api/admin/v1/users?applicationId=…`): `search` (email, username, name), `status` (active, invited, suspended, disabled), and `page` (from 0) with `size` (default 50, at most 500); `X-Total-Count` gives the number of matches. Reading the list never changes memberships.
- **Deleting an application** needs its slug typed again and a fresh second step. With OAuth clients it needs a second admin, as deleting one client does (202 with the change request; a platform operator or the tenant's super admin approves on Access review). Everyone signed in to it is signed out first: its clients' grants end, with a back-channel logout.
- **Sign-up** (**Settings → Authentication → Sign-up**): allowed email domains (empty: any; `*.example.com` for subdomains) and blocked ones, for hosted sign-up and for accounts made by signing in with Google or SAML; and up to 20 extra fields on the hosted sign-up page (text, list or checkbox, optionally required), saved as the user's attributes. Apps calling `POST /api/v1/applications/{id}/signup` send them in `attributes`; `GET …/signup` lists them in `fields`.
- **Role end dates** (Users → user → Security, in the application's console): a role can be given until a date. It stops counting at that moment (tokens, console permissions) and the assignment is then removed, audited as `user.role_expired`. Saving a user's roles keeps the end dates. API: `PUT`/`DELETE /api/admin/v1/applications/{id}/users/{userId}/role-assignments/{roleId}` with `{"expiresAt": "2026-12-31T18:00:00Z"}`.

## OAuth clients

- **RP-initiated logout** (`/connect/logout`): ends the browser session and revokes the grants given during it (their refresh tokens stop working; `offline_access` grants are kept, as intended), then tells the other applications. Send the latest ID token as `id_token_hint` (a refresh replaces the earlier one).
- **Back-channel logout URL** (OIDC Back-Channel Logout 1.0): SecureOne POSTs a signed `logout_token` (RS256, `typ: logout+jwt`, `sub` as the client sees it, and `sid` after an application's RP-initiated logout) to every application the user signed in to. This happens when the user signs out of SecureOne, when all their sessions are ended (admin or two-step reset, SAML logout), and when they are suspended, deleted or change their email. The address is checked again when sending: one that now resolves to a private network is skipped. Discovery advertises `backchannel_logout_supported`. The TypeScript SDK has `verifyLogoutToken()`.
- **Rotate secret** (confidential clients): the new secret is shown once. The old one keeps working for 0–168 hours (the console asks; 0 when it leaked), so the app can be updated without an outage.
- **Token subject**: User ID (stable, the default) or legacy `tenant:email`. With the legacy subject, a user who changes their email looks like a new user to the app; the client form warns about this.
- **Token endpoint authentication** (confidential clients): client secret (HTTP Basic or form body), or **private key JWT** (RFC 7523): the app signs a short-lived assertion with its own key and SecureOne keeps only the public keys, from the app's JWKS URL (https; keys rotate there) or pasted as a JWK set. Assertions are RS256/384/512, PS256/384/512 or ES256/384/512, name the token endpoint (or issuer) in `aud`, must have a `jti` and expire within 10 minutes, and are accepted once. A private key JWT client has no secret (switching removes it; switching back needs a new secret). SPAs and native apps use `none` with PKCE. Also for introspection and revocation. API: `tokenEndpointAuthMethod: "private_key_jwt"` with `jwksUri` or `jwks`.
- **Third-party app: ask users for consent**: users see what the app asks for and allow or cancel (`/oauth2/consent`); the choice is remembered and can be withdrawn under Connected apps.

- **Redirect URIs**: https, plain http only on `localhost`, `*.localhost`, `127.0.0.1` or `[::1]` (RFC 8252), or a reverse-domain app scheme. No wildcards or fragments. An http URI a client already had stays when the client is saved.
- **Introspection** (`/oauth2/introspect`): answers for tokens of the calling client's own application (the client it was issued to, or another client of that application). For any other client the token is `active: false`.

## OpenID Connect behaviour

- **`prompt=none`**: when the user is not signed in, or the sign-in is older than `max_age` or the application's "sign in again after", the app gets `error=login_required` at its registered redirect URI (with `state`) instead of the sign-in page, so silent sign-in in SPAs fails fast.
- **`prompt=login`**: a signed-in user signs in again before the app gets a code.
- **Userinfo** follows the granted scopes (OIDC Core 5.4): `email` and `email_verified` only with the `email` scope, `name` and `preferred_username` only with `profile`. `sub`, `user_id`, the tenant, `roles` and `permissions` are always there. Apps that read the email from userinfo must ask for `email`.
- **Tokens**: ID and access tokens carry `amr` (`pwd`, `otp`, `hwk`, `fed`, `mfa`…) and `acr` from how the user signed in; access tokens carry `client_id` (RFC 9068).
- **Discovery** lists `scopes_supported` (openid, profile, email, offline_access), `claims_supported`, `backchannel_logout_session_supported: true`, and only the client authentication methods a client can be set to (`client_secret_basic`, `client_secret_post`, `private_key_jwt`, `none`, with `token_endpoint_auth_signing_alg_values_supported`). JWKS keys carry `"use": "sig"` and `"alg": "RS256"`.

## Issuer per tenant and custom domains (tenant)

**Tenants → tenant → Issuer and domains**.
- Every tenant has its own issuer, `<issuer>/t/<slug>`, with its own discovery (`/t/<slug>/.well-known/openid-configuration`, and RFC 8414 at `/.well-known/oauth-authorization-server/t/<slug>`). Tokens issued there carry it in `iss`; it serves only the tenant's own clients (others get `unauthorized_client`), and its userinfo accepts only its own tokens. The shared issuer keeps working for every tenant.
- **Use this organization's own issuer only**: the tenant's clients are refused on the shared issuer (authorize and token), so its tokens never carry the shared one. Update every app's issuer first. The OAuth client page then shows the tenant's issuer.
- **Custom domains** (`login.example.com`, up to 10): prove control with the TXT record `_secureone-challenge.<domain>` = `secureone-domain-verification=<token>`, or point the domain at SecureOne (it then answers `/.well-known/secureone-domain-verification`), and press **Verify**. A verified domain is the tenant's issuer (`https://<domain>`) and serves the hosted pages; removing it stops that at once. HTTPS for the domain is the reverse proxy's: one that issues certificates on demand (Caddy `on_demand_tls`) can ask `GET /api/v1/domains/check?domain=<domain>` (200 only for verified domains). Emails still link to the main address.
- The signing keys are shared by all issuers; logout tokens carry the tenant's issuer when it uses its own only.
- API: `/api/admin/v1/tenants/{id}/issuer` (`PUT {"issuerMode": "shared"|"tenant"}`, `POST /domains`, `POST /domains/{id}/verify`, `DELETE /domains/{id}`), audited as `tenant.issuer_mode_changed`, `tenant.domain_added`, `tenant.domain_verified`, `tenant.domain_removed`.

## APIs (application)

**Settings → Integration → APIs**: the backends the application's access tokens are for. An API has a name, an identifier (an `https://` URL or a `urn:`, unique across the platform) and its scopes (not the OpenID ones).
- An access token granted one of an API's scopes has the API's identifier in `aud`, next to the client's ID, so the API checks `aud` before it accepts a token. Give the client the scopes under its OAuth client settings.
- A client can name one API with the RFC 8707 `resource` parameter (authorization or token request): the token then names only that API. A `resource` that is not one of the application's APIs is refused with `invalid_target`.
- Removing an API: new tokens stop naming it; tokens already issued keep it until they expire.
- API: `/api/admin/v1/applications/{id}/settings/apis` (audited as `api.created`, `api.updated`, `api.deleted`).

## Client registration (application)

**Settings → Integration → Client registration**: apps register themselves as OAuth clients of the application (RFC 7591 at `/oauth2/register`, OIDC Dynamic Client Registration 1.0 at `/connect/register`), for partner tools, AI assistants (MCP clients) or CLIs. Discovery lists `registration_endpoint` when an application takes registrations.
- **Who may register**: nobody (the default); apps with an **initial access token** (`Authorization: Bearer …`) created here with a number of registrations and an expiry, shown once; or **anyone** (open, 10 registrations a minute per address, one application per tenant, on the tenant's own issuer `<issuer>/t/<slug>/oauth2/register` since it names the tenant).
- **Policies**: allowed scopes (a client asking for others is refused; none asked: all allowed ones), allowed grant types (authorization code, refresh tokens, client credentials; never client credentials when open), trusted hosts for redirect URIs (`*.example.com`; empty: any https host), at most N registered clients, and removal of clients never used after N days (0: keep). Redirect URIs follow the same rules as for clients made in the console.
- Registered clients always show the consent screen, get the stable user ID as `sub`, and can be public (PKCE), use a secret, or `private_key_jwt` (`jwks_uri` or `jwks`). The console marks them "Registered by the app itself".
- **Self-management (RFC 7592)**: the response carries `registration_access_token` and `registration_client_uri`; with that token the app reads (`GET`), replaces (`PUT`) or deletes (`DELETE`) its registration. Each read or change returns a new token; the old one stops working.
- Audited as `oauth_client.registered`, `oauth_client.registration_updated`, `oauth_client.registration_deleted` and `client_registration.*`; webhooks `client.registered` and `client.registration_removed`. API: `/api/admin/v1/applications/{id}/settings/client-registration` (and `/tokens`).

## SAML apps (application)

**Settings → Integration → SAML apps**: applications that only speak SAML 2.0 sign users in through SecureOne as their identity provider. Each tenant has its IdP, entity ID `<base>/saml2/idp/<slug>`, with metadata at `…/metadata` (SSO and single logout over HTTP-Redirect and HTTP-POST, the signing certificate) and its own RSA key.
- A SAML app has the SP's entity ID and ACS URL (https; http only for localhost), an optional single logout URL, the Name ID (email, persistent = user ID, unspecified) and its attributes: name and source (email, names, display name, username, user ID, phone, tenant, the application's `roles` and `permissions`, a user attribute or a fixed value). Empty: email, given_name, family_name, name, roles.
- Single sign-on: the SP sends an AuthnRequest (Redirect or POST); the user signs in on the hosted page with the application's methods and two-step rules, and the application's access decides who may sign in (others get `RequestDenied`). The Response goes by HTTP-POST to the registered ACS URL only (a request naming another URL is refused); response and assertion are signed (RSA-SHA256, either can be turned off, not both), audience-restricted and valid 5 minutes (60–3600 s). `ForceAuthn`, `IsPassive` (`NoPassive`) and NameIDPolicy are honoured; stale requests are refused. IdP-initiated sign-in: `…/init?sp=<entity ID>&RelayState=…`.
- **Signed requests**: with the SP's certificate, signatures (Redirect binding RSA-SHA256/384/512, POST binding XML signatures) are checked; **Require signed requests** refuses unsigned ones.
- Single logout: an SP's LogoutRequest signs the browser out of SecureOne (apps with back-channel logout are told) and a signed LogoutResponse goes to the SP's logout URL.
- Audited: `saml_app.created`, `saml_app.updated`, `saml_app.deleted`, and `user.saml_sso`, `user.saml_logout`. API: `/api/admin/v1/applications/{id}/settings/saml-apps`.

## Token claims (application)

**Settings → Integration → Token claims**: up to 20 extra claims in the application's access and/or ID tokens, from a user attribute, a fixed value or a profile field (email, phone, display name, username, tenant). Names SecureOne already sets (`sub`, `roles`, `email`…) cannot be used; prefix with your domain to avoid clashes. Attributes SecureOne keeps for its own use (`pendingEmail`, `pendingEmailApplicationId`, `pendingInviteApplicationId`, `authMethods`) cannot be put in tokens.

## Settings for single applications (platform)

**Platform settings → For applications → Exceptions for single applications**: open or close a section for one application, different from the default. Two Factor Authentication keeps its own per-application switch instead. An application's exceptions are removed when it is deleted.

## Removed

- **Organizations** inside a tenant (names only; no members, no sign-in rules, no token claim). Tenants are the customer boundary. Migration V78 drops the empty table.
- **LDAP / AD** placeholders (import source, connection form, feature flag). Import users from CSV or Excel.

## Runbooks

- [Custom domain](../install/runbooks/custom-domain.md)
<!-- developer -->
- [OpenID certification and penetration test](../developer/oidc-conformance-and-pentest.md)
<!-- /developer -->
<!-- developer -->
- SDK publishing: push a tag `sdk-vX.Y.Z` matching `packages/secureone-client/package.json`; `.github/workflows/publish-sdk.yml` publishes to npm (needs the `NPM_TOKEN` secret).
<!-- /developer -->
