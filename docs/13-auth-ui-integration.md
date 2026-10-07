# 13 — Authentication UI integration

How end-user sign-in works when you use **SecureOne hosted pages**, the **admin integration console**, or your **application’s own UI**.

Related: [Auth standards](05-auth-standards.md), [API documentation](12-api-documentation.md), [Settings governance](10-settings-governance.md), [Installation — dev flows](09-installation.md#8-email--notifications-dev).

## Three surfaces

| Surface | What it is | Who uses it |
|---------|------------|-------------|
| **SecureOne hosted UI** | Static pages on the **auth-server** (default `http://localhost:9000`) — `login.html`, `magic-link.html`, `signup.html`, `forgot-password.html`, etc. | End users when you redirect to OAuth or link directly to hosted pages |
| **Admin console** (`admin-web`, default `:3001`) | Operator dashboard — tenants, users, **Application → Settings → Integration** | Platform and application **admins**, not your product’s end users |
| **Custom / native app UI** | Your SPA, mobile app, or storefront (e.g. Vite on `:5173`) | End users when you build your own login screens |

The **Authentication UI** preference under **Application → Settings → Integration** (`authUiMode`: `hosted` or `native`) is stored in `client_integration` and shown in the admin UI. It documents how your team intends to integrate; **your client app must implement the chosen mode**. The auth-server does not automatically switch UI based on this flag alone.

## Choosing a mode

| Mode | Best for | How users sign in |
|------|----------|-------------------|
| **Hosted SecureOne UI** (`authUiMode: hosted`) | Fastest integration, standard OIDC clients | Redirect to `/oauth2/authorize` → SecureOne renders login, MFA, and consent |
| **Native / custom UI** (`authUiMode: native`) | Branded experience, full control | Your forms call application-scoped account APIs; optional OAuth PKCE for tokens |

Both modes use the **same backend APIs**. The difference is who renders the HTML and where email links land (see [Native app URLs](#native-app-urls) below).

## Discovery

Integrated clients can load the public manifest (no auth required when public API exposure is enabled):

```http
GET /api/v1/applications/{applicationId}
```

The `account` block lists application-scoped endpoints (no `tenantSlug` required from the client):

| Field | Path |
|-------|------|
| `magicLinkRequestEndpoint` | `POST /api/v1/applications/{id}/account/magic-link/request` |
| `forgotPasswordEndpoint` | `POST /api/v1/applications/{id}/account/password/forgot` |
| `sessionLoginEndpoint` | `POST /api/v1/applications/{id}/auth/session/login` |
| `otpLoginRequestEndpoint` | `POST /api/v1/applications/{id}/auth/otp/request` |
| `otpLoginVerifyEndpoint` | `POST /api/v1/applications/{id}/auth/otp/verify` |
| `verifyOtpConfirmEndpoint` | `POST /api/v1/applications/{id}/account/verify/otp/confirm` |
| `passwordResetEndpoint` | `POST /api/v1/account/password/reset` |
| `setPasswordEndpoint` | `POST /api/v1/account/set-password` |
| `loginUsernameFormat` | `tenantSlug:email` (e.g. `acme:user@example.com`) |

OIDC discovery applies to all modes:

```http
GET /.well-known/openid-configuration
```

## Hosted SecureOne UI

### OAuth login (primary path)

1. Your app redirects the browser to `/oauth2/authorize` with `client_id`, `redirect_uri`, PKCE `code_challenge`, etc.
2. Unauthenticated users land on **`/login.html`** on the auth-server.
3. After password (or future MFA) and consent, the user returns to your app with an authorization `code`.
4. Your backend or SPA exchanges the code at `/oauth2/token` for `access_token`, `id_token`, and optionally `refresh_token`.
5. Call `/userinfo` with the bearer token for claims.

Pass `applicationId` on hosted pages when you want app-scoped context in links:

```text
/login.html?applicationId={applicationId}
/account/signup.html?applicationId={applicationId}
/account/magic-link.html?applicationId={applicationId}
```

### Hosted account pages

| Page | Purpose |
|------|---------|
| `/login.html` | Password sign-in; links to magic link, signup, forgot password |
| `/account/magic-link.html` | Request magic link email |
| `/account/signup.html` | Self-registration (when enabled) |
| `/account/forgot-password.html` | Request password reset email |
| `/account/reset-password.html?token=…` | Complete password reset |
| `/account/set-password.html?token=…` | Set password from invite |
| `/account/security.html` | MFA enrollment, trusted devices, session security |
| `/account/privacy.html` | GDPR self-service: data export + deletion request (session auth) |

See [Installation — dev flows](09-installation.md#8-email--notifications-dev) for local URLs and MailHog testing.

## Native / custom app UI

When `authUiMode` is **native**, build your own screens and call the same APIs documented in **Application → Settings → Integration** (API catalog in admin-web).

### Session login (cookie-based)

```http
POST /api/v1/applications/{applicationId}/auth/session/login
Content-Type: application/json

{ "identifier": "user@example.com", "password": "…" }
```

`identifier` accepts **email** or **phone** (E.164). Legacy clients that sent `email` must migrate to `identifier`.

On success, the auth-server sets a **session cookie** (`JSESSIONID`) stored in **Redis** (Spring Session). This works when your UI is same-origin with the auth-server or when you have configured cookies/CORS correctly for cross-origin use. For most SPAs, **OAuth Authorization Code + PKCE** is the recommended path instead.

### OTP login (email or SMS)

See **[19 — Verification & OTP](19-verification-and-otp.md)** for full examples.

```http
POST /api/v1/applications/{applicationId}/auth/otp/request
{ "identifier": "user@example.com", "channel": "email" }

POST /api/v1/applications/{applicationId}/auth/otp/verify
{ "challengeId": "…", "code": "123456" }
```

Requires `m_email_otp` or `m_sms` enabled. Success returns `204` and a session cookie.

### Signup verification (link or OTP)

Signup accepts optional `verificationMode` (`link` | `otp`) and `phone`. Policy defaults are in **Platform/Application → Settings → Verification**. OTP confirm:

```http
POST /api/v1/applications/{applicationId}/account/verify/otp/confirm
{ "challengeId": "…", "code": "123456" }
```

### Password reset (custom landing pages)

Under **Native app URLs** in Integration settings, configure:

| Setting | Used for |
|---------|----------|
| `clientUrls.forgotPassword` | Your “forgot password” page (your UI calls the forgot API) |
| `clientUrls.passwordReset` | Base URL in reset emails; SecureOne appends `?token=…&applicationId=…` |

Password reset emails respect `clientUrls.passwordReset` when set; otherwise they use the hosted `/account/reset-password.html`.

### Magic link (custom UI)

**Request** (same as hosted):

```http
POST /api/v1/applications/{applicationId}/account/magic-link/request
Content-Type: application/json

{ "email": "user@example.com" }
```

Response is always a generic success message (anti-enumeration), whether or not the user exists.

**Email link (current behavior):** magic link emails always point to the **hosted** page:

```text
{SECUREONE_PUBLIC_BASE_URL}/account/magic-link.html?token=…
```

There is **no** `clientUrls.magicLink` override yet (unlike password reset). For a fully native magic-link landing page, you would need a future setting or have users complete verification on the hosted page and redirect back to your app.

**Verify (HTML or JSON):**

```http
GET /api/v1/account/magic-link/verify?token=…
POST /api/v1/account/magic-link/verify
{ "token": "…" }
```

HTML returns a success page. JSON returns `{ "signedIn": true, "email": "…", "loginUsername": "tenant:email" }` and **creates a session cookie** (`JSESSIONID` in Redis). Also sets `emailVerified=true`.

## Magic link flow (all UI types)

```mermaid
flowchart TB
  subgraph request [1. Request link]
    API["POST /api/v1/applications/{applicationId}/account/magic-link/request\n{ email }"]
  end

  subgraph email [2. Email]
    MAIL["Transactional email via application SMTP\nLink → /account/magic-link.html?token=…"]
  end

  subgraph verify [3. User clicks link]
    HOSTED["Hosted: magic-link.html redirects to verify"]
    CUSTOM["Custom: your page redirects to same verify URL"]
  end

  subgraph result [4. After verify]
    SESSION["Session cookie (Redis) + emailVerified\nHTML or JSON API"]
  end

  API --> MAIL --> HOSTED --> SESSION
  MAIL --> CUSTOM --> SESSION
```

### Requirements

| Requirement | Notes |
|-------------|-------|
| Auth method **`m_magic`** enabled | Platform and application **Authentication** settings; method must be implemented |
| Application SMTP configured | **Application → Settings → Notifications** (not platform SMTP) |
| User exists in the application’s tenant | Email must match an existing user linked to that application context |
| Feature flag `self_service_recovery` | Controls **forgot password** only; **not** magic link |

### After magic link verify

Magic link verification **creates an HTTP session** (`JSESSIONID` in Redis), sets `emailVerified=true`, and updates login metadata. For OAuth access tokens, redirect to `/oauth2/authorize` with PKCE after verify (session is already established).

## Comparison by flow

| Flow | Hosted UI | Custom UI | OAuth redirect |
|------|-----------|-----------|----------------|
| Password login | `/login.html` | Session API (`identifier` + password) | `/oauth2/authorize` → `/login.html` |
| OTP login | N/A | `POST …/auth/otp/request` + `verify` | After session → `/oauth2/authorize` |
| Magic link verify | Hosted redirect → verify URL | `POST /api/v1/account/magic-link/verify` (JSON) | Session cookie → OAuth |
| Signup verify | Email link or hosted | OTP confirm API | N/A |
| Get access token | Code exchange after OAuth | OAuth PKCE (recommended) or session cookie | Primary path |
| Forgot password | `/account/forgot-password.html` | Your page + forgot API | N/A |
| Reset password email link | Hosted or `clientUrls.passwordReset` | `clientUrls.passwordReset` | N/A |
| Self-registration | `/account/signup.html` | `POST …/signup` from your UI | N/A |
| Privacy (export / delete) | `/account/privacy.html` | Bearer privacy APIs | N/A |
| Account security / MFA | `/account/security.html` | Admin profile MFA (operators) | N/A |

## Admin console vs end-user UI

Do not confuse:

- **`admin-web`** — manage tenants, applications, users, SMTP, auth methods, integration docs. Sign-in here uses platform or tenant **admin** credentials.
- **Auth-server hosted pages** — end-user login, signup, magic link, password reset for **integrated applications**.

Integration settings (OAuth client ID, application UUID, API catalog, `authUiMode`, native URLs) live in the admin console under **Application → Settings → Integration**. End users never need access to the admin console.

## Configuration reference

| Setting | Location | Effect |
|---------|----------|--------|
| `authUiMode` | Application → Integration | `hosted` or `native` (team preference; client must implement) |
| `clientUrls.forgotPassword` | Application → Integration (native) | Documentation / your forgot-password page URL |
| `clientUrls.passwordReset` | Application → Integration (native) | Reset email link base URL |
| `SECUREONE_PUBLIC_BASE_URL` | auth-server env | Base for all email links (default `http://localhost:9000`) |
| Public API exposure | Platform + application settings | Whether `GET /api/v1/applications/{id}` is available unauthenticated |
| Auth methods | Platform + application Authentication | Password, magic link, email OTP, SMS OTP (`m_email_otp`, `m_sms`) |
| Verification policy | Platform/Application → **Verification** tab | Signup mode link vs OTP, TTL, attempts |
| Application SMTP | Application → Notifications | Required for magic link, reset, verification emails to end users |

## Known gaps (as of current implementation)

1. **Magic link email URL** — always hosted; no `clientUrls.magicLink` override.
2. **Password reset via OTP** — policy field exists; flow still uses email link only.
3. **TOTP / passkey MFA** — not wired at login (admin can list factors only).
4. **Session list/revoke** — admin session view exists; end-user revoke-all API not yet exposed.
5. **`login.html` magic link link** — default link omits `applicationId`; use `/account/magic-link.html?applicationId={id}` explicitly for app-scoped requests.

Full OTP/signup/login reference: **[19 — Verification & OTP](19-verification-and-otp.md)**.
