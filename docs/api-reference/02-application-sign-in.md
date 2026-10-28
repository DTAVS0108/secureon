# 2. Application sign-in and sign-up (native UI)

[API reference](README.md) › Application sign-in and sign-up (native UI)

> Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.

For apps that draw their own sign-in screens instead of redirecting to the hosted page: application manifest, password and one-time-code sign-in, sign-up, MFA, passkeys, federation and device sign-in.

In Swagger UI (`/docs`), choose the definition **Authentication** (`/v3/api-docs/auth`).

Set the variables from [Getting started](00-getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [2.1 Application public API](#21-application-public-api)
  - [GET `/api/v1/applications/resolve`](#211-find-an-application-by-client-id-or-host) — Find an application by client ID or host
  - [GET `/api/v1/applications/{applicationId}`](#212-get-application) — Get application
  - [POST `/api/v1/applications/{applicationId}/account/email/resend-verification`](#213-resend-email-verification) — Resend email verification
  - [POST `/api/v1/applications/{applicationId}/account/magic-link/request`](#214-request-magic-link) — Request magic link
  - [POST `/api/v1/applications/{applicationId}/account/password/forgot`](#215-forgot-password) — Forgot password
  - [POST `/api/v1/applications/{applicationId}/account/password/reset/otp`](#216-reset-password-with-a-one-time-code) — Reset password with a one-time code
  - [POST `/api/v1/applications/{applicationId}/account/verify/otp/confirm`](#217-confirm-otp) — Confirm OTP
  - [POST `/api/v1/applications/{applicationId}/auth/otp/request`](#218-request-otp) — Request OTP
  - [POST `/api/v1/applications/{applicationId}/auth/otp/verify`](#219-verify-otp) — Verify OTP
  - [POST `/api/v1/applications/{applicationId}/auth/session/login`](#2110-login-session) — Login session
  - [GET `/api/v1/applications/{applicationId}/branding`](#2111-get-the-applications-branding-public) — Get the application's branding (public)
  - [GET `/api/v1/applications/{applicationId}/signup`](#2112-get-sign-up-options) — Get sign-up options
  - [POST `/api/v1/applications/{applicationId}/signup`](#2113-signup) — Signup
- [2.2 Public auth](#22-public-auth)
  - [GET `/api/v1/auth/me`](#221-current-browser-session) — Current browser session
  - [GET `/api/v1/auth/methods`](#222-list-methods) — List methods
  - [GET `/api/v1/auth/mfa/challenge/{mfaChallengeId}`](#223-describe-a-pending-second-step-or-setup-options-deadline-whether-it-can-be-skipped) — Describe a pending second step or setup (options, deadline, whether it can be skipped)
  - [POST `/api/v1/auth/mfa/code/verify`](#224-complete-login-with-the-code-sent-by-text-message-or-email) — Complete login with the code sent by text message or email
  - [POST `/api/v1/auth/mfa/recovery/verify`](#225-complete-login-with-one-time-recovery-code) — Complete login with one-time recovery code
  - [POST `/api/v1/auth/mfa/send`](#226-send-a-code-by-text-message-or-email-to-one-of-the-users-options) — Send a code by text message or email to one of the user's options
  - [POST `/api/v1/auth/mfa/setup/code/confirm`](#227-setup-during-sign-in-confirm-the-code-signs-in-returns-backup-codes) — Setup during sign-in: confirm the code; signs in, returns backup codes
  - [POST `/api/v1/auth/mfa/setup/code/send`](#228-setup-during-sign-in-send-a-code-by-text-message-phone-or-to-the-account-email) — Setup during sign-in: send a code by text message (phone) or to the account email
  - [POST `/api/v1/auth/mfa/setup/totp/confirm`](#229-setup-during-sign-in-confirm-the-authenticator-app-code-signs-in-returns-backup-codes) — Setup during sign-in: confirm the authenticator app code; signs in, returns backup codes
  - [POST `/api/v1/auth/mfa/setup/totp/start`](#2210-setup-during-sign-in-start-an-authenticator-app-qr-code) — Setup during sign-in: start an authenticator app (QR code)
  - [POST `/api/v1/auth/mfa/setup/webauthn/options`](#2211-setup-during-sign-in-passkey-or-security-key-registration-options) — Setup during sign-in: passkey or security key registration options
  - [POST `/api/v1/auth/mfa/setup/webauthn/verify`](#2212-setup-during-sign-in-register-the-passkey-or-security-key-signs-in-returns-backup-codes) — Setup during sign-in: register the passkey or security key; signs in, returns backup codes
  - [POST `/api/v1/auth/mfa/skip`](#2213-set-up-later-sign-in-without-a-second-step-while-reminders-or-the-grace-period-allow) — Set up later: sign in without a second step while reminders or the grace period allow
  - [POST `/api/v1/auth/mfa/totp/verify`](#2214-complete-login-with-totp-code) — Complete login with TOTP code
  - [POST `/api/v1/auth/mfa/webauthn/options`](#2215-passkey-or-security-key-as-the-second-step-assertion-options) — Passkey or security key as the second step: assertion options
  - [POST `/api/v1/auth/mfa/webauthn/verify`](#2216-complete-login-with-a-passkey-or-security-key) — Complete login with a passkey or security key
  - [POST `/api/v1/auth/passkey/options`](#2217-begin-passkey-sign-in) — Begin passkey sign-in
  - [POST `/api/v1/auth/passkey/verify`](#2218-complete-passkey-sign-in) — Complete passkey sign-in
  - [POST `/api/v1/auth/session/login`](#2219-session-login-native-app-ui) — Session login (native app UI)
- [2.3 Federation](#23-federation)
  - [GET `/api/v1/auth/federation/providers`](#231-list-enabled-federation-providers-for-a-tenantapplication) — List enabled federation providers for a tenant/application
  - [POST `/api/v1/auth/federation/saml/acs/{providerId}`](#232-saml-assertion-consumer-service-http-post-binding-from-the-idp) — SAML assertion consumer service (HTTP-POST binding from the IdP)
  - [GET `/api/v1/auth/federation/saml/logout`](#233-sign-out-for-a-saml-session-also-at-the-idp-single-logout) — Sign out; for a SAML session also at the IdP (single logout)
  - [GET `/api/v1/auth/federation/saml/slo/{providerId}`](#234-saml-single-logout-the-idps-logoutrequest-or-logoutresponse-redirect-or-post-binding) — SAML single logout: the IdP's LogoutRequest or LogoutResponse (Redirect or POST binding)
  - [POST `/api/v1/auth/federation/saml/slo/{providerId}`](#235-saml-single-logout-the-idps-logoutrequest-or-logoutresponse-redirect-or-post-binding) — SAML single logout: the IdP's LogoutRequest or LogoutResponse (Redirect or POST binding)
  - [GET `/api/v1/auth/federation/saml/{alias}/metadata`](#236-saml-service-provider-metadata-for-a-configured-provider-give-this-to-the-idp) — SAML service-provider metadata for a configured provider (give this to the IdP)
  - [GET `/api/v1/auth/federation/saml/{alias}/start`](#237-saml-login-same-as-aliasstart-for-a-saml-provider) — SAML login (same as /{alias}/start for a SAML provider)
  - [GET `/api/v1/auth/federation/{alias}/callback`](#238-oauthoidc-callback-from-external-idp) — OAuth/OIDC callback from external IdP
  - [GET `/api/v1/auth/federation/{alias}/start`](#239-start-federation-login-redirects-to-the-external-idp-oauthoidc-or-saml) — Start federation login (redirects to the external IdP: OAuth/OIDC or SAML)
- [2.4 Device sign-in](#24-device-sign-in)
  - [POST `/api/v1/auth/device/lookup`](#241-look-up-a-device-user-code) — Look up a device user code
- [2.5 Impersonation](#25-impersonation)
  - [GET `/api/v1/auth/impersonation/consume`](#251-consume-impersonation-token-and-establish-a-user-session) — Consume impersonation token and establish a user session
  - [GET `/api/v1/auth/impersonation/status`](#252-list-status) — List status

## 2.1 Application public API

Manifest and self-service sign-up for integrated apps

### 2.1.1 Find an application by client ID or host

`GET /api/v1/applications/resolve`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `clientId` | query | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/applications/resolve?clientId={clientId}"
```

### 2.1.2 Get application

`GET /api/v1/applications/{applicationId}`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/applications/{applicationId}"
```

### 2.1.3 Resend email verification

`POST /api/v1/applications/{applicationId}/account/email/resend-verification`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [EmailRequest](13-data-models.md#emailrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | string (email) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/applications/{applicationId}/account/email/resend-verification" \
  -H "Content-Type: application/json" \
  --data '{
  "email": "user@example.com"
}'
```

### 2.1.4 Request magic link

`POST /api/v1/applications/{applicationId}/account/magic-link/request`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [EmailRequest](13-data-models.md#emailrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | string (email) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/applications/{applicationId}/account/magic-link/request" \
  -H "Content-Type: application/json" \
  --data '{
  "email": "user@example.com"
}'
```

### 2.1.5 Forgot password

`POST /api/v1/applications/{applicationId}/account/password/forgot`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [ForgotPasswordRequest](13-data-models.md#forgotpasswordrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `channel` | string | no |  |
| `email` | string | no |  |
| `identifier` | string | no |  |
| `mode` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/applications/{applicationId}/account/password/forgot" \
  -H "Content-Type: application/json" \
  --data '{
  "channel": "email",
  "email": "user@example.com",
  "identifier": "user@example.com",
  "mode": "otp"
}'
```

### 2.1.6 Reset password with a one-time code

`POST /api/v1/applications/{applicationId}/account/password/reset/otp`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [ResetPasswordOtpRequest](13-data-models.md#resetpasswordotprequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `challengeId` | string | yes |  |
| `code` | string | yes |  |
| `password` | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/applications/{applicationId}/account/password/reset/otp" \
  -H "Content-Type: application/json" \
  --data '{
  "challengeId": "{challengeId}",
  "code": "123456",
  "password": "Choose-a-Strong-Pass-1!"
}'
```

### 2.1.7 Confirm OTP

`POST /api/v1/applications/{applicationId}/account/verify/otp/confirm`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [OtpVerifyRequest](13-data-models.md#otpverifyrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `challengeId` | string | yes |  |
| `channel` | string | no |  |
| `code` | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/applications/{applicationId}/account/verify/otp/confirm" \
  -H "Content-Type: application/json" \
  --data '{
  "challengeId": "{challengeId}",
  "channel": "email",
  "code": "123456"
}'
```

### 2.1.8 Request OTP

`POST /api/v1/applications/{applicationId}/auth/otp/request`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [OtpLoginRequest](13-data-models.md#otploginrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `channel` | string | no |  |
| `identifier` | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/applications/{applicationId}/auth/otp/request" \
  -H "Content-Type: application/json" \
  --data '{
  "channel": "email",
  "identifier": "user@example.com"
}'
```

### 2.1.9 Verify OTP

`POST /api/v1/applications/{applicationId}/auth/otp/verify`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [OtpVerifyRequest](13-data-models.md#otpverifyrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `challengeId` | string | yes |  |
| `channel` | string | no |  |
| `code` | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/applications/{applicationId}/auth/otp/verify" \
  -H "Content-Type: application/json" \
  --data '{
  "challengeId": "{challengeId}",
  "channel": "email",
  "code": "123456"
}'
```

### 2.1.10 Login session

`POST /api/v1/applications/{applicationId}/auth/session/login`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [SessionLoginRequest](13-data-models.md#sessionloginrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `email` | string (email) | yes |  |
| `password` | string | yes |  |
| `tenantSlug` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/applications/{applicationId}/auth/session/login" \
  -H "Content-Type: application/json" \
  --data '{
  "applicationId": "{applicationId}",
  "email": "user@example.com",
  "password": "Choose-a-Strong-Pass-1!",
  "tenantSlug": "tenant-a"
}'
```

### 2.1.11 Get the application's branding (public)

`GET /api/v1/applications/{applicationId}/branding`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/applications/{applicationId}/branding"
```

### 2.1.12 Get sign-up options

`GET /api/v1/applications/{applicationId}/signup`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/applications/{applicationId}/signup"
```

### 2.1.13 Signup

`POST /api/v1/applications/{applicationId}/signup`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [SignupRequest](13-data-models.md#signuprequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `attributes` | object | no |  |
| `displayName` | string | no |  |
| `email` | string (email) | yes |  |
| `firstName` | string | no |  |
| `lastName` | string | no |  |
| `password` | string | yes |  |
| `phone` | string | no |  |
| `verificationChannel` | string | no |  |
| `verificationMode` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/applications/{applicationId}/signup" \
  -H "Content-Type: application/json" \
  --data '{
  "attributes": {},
  "displayName": "Tenant A Shop",
  "email": "user@example.com",
  "firstName": "First",
  "lastName": "Last",
  "password": "Choose-a-Strong-Pass-1!",
  "phone": "+14155552671",
  "verificationChannel": "email",
  "verificationMode": "otp"
}'
```

## 2.2 Public auth

Authentication method catalog and session login for native UIs

### 2.2.1 Current browser session

`GET /api/v1/auth/me`

Returns the signed-in user after form login (session cookie). Does not issue OAuth access tokens — use the authorization code + PKCE flow for those.

**Authentication:** Signed-in session cookie (from a sign-in call with `-c cookies.txt`)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/auth/me" \
  -b cookies.txt
```

### 2.2.2 List methods

`GET /api/v1/auth/methods`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/v1/auth/methods"
```

### 2.2.3 Describe a pending second step or setup (options, deadline, whether it can be skipped)

`GET /api/v1/auth/mfa/challenge/{mfaChallengeId}`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `mfaChallengeId` | path | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/auth/mfa/challenge/{mfaChallengeId}"
```

### 2.2.4 Complete login with the code sent by text message or email

`POST /api/v1/auth/mfa/code/verify`

**Authentication:** None (public)

**Request body** (`application/json`, [CodeVerifyRequest](13-data-models.md#codeverifyrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | yes |  |
| `mfaChallengeId` | string (uuid) | yes |  |
| `rememberDevice` | boolean | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/auth/mfa/code/verify" \
  -H "Content-Type: application/json" \
  --data '{
  "code": "123456",
  "mfaChallengeId": "{mfaChallengeId}",
  "rememberDevice": true
}'
```

### 2.2.5 Complete login with one-time recovery code

`POST /api/v1/auth/mfa/recovery/verify`

**Authentication:** None (public)

**Request body** (`application/json`, [RecoveryVerifyRequest](13-data-models.md#recoveryverifyrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | yes |  |
| `mfaChallengeId` | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/auth/mfa/recovery/verify" \
  -H "Content-Type: application/json" \
  --data '{
  "code": "123456",
  "mfaChallengeId": "{mfaChallengeId}"
}'
```

### 2.2.6 Send a code by text message or email to one of the user's options

`POST /api/v1/auth/mfa/send`

**Authentication:** None (public)

**Request body** (`application/json`, [SendCodeRequest](13-data-models.md#sendcoderequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `factorId` | string (uuid) | yes |  |
| `mfaChallengeId` | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/auth/mfa/send" \
  -H "Content-Type: application/json" \
  --data '{
  "factorId": "{factorId}",
  "mfaChallengeId": "{mfaChallengeId}"
}'
```

### 2.2.7 Setup during sign-in: confirm the code; signs in, returns backup codes

`POST /api/v1/auth/mfa/setup/code/confirm`

**Authentication:** None (public)

**Request body** (`application/json`, [SetupCodeConfirmRequest](13-data-models.md#setupcodeconfirmrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | yes |  |
| `mfaChallengeId` | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/auth/mfa/setup/code/confirm" \
  -H "Content-Type: application/json" \
  --data '{
  "code": "123456",
  "mfaChallengeId": "{mfaChallengeId}"
}'
```

### 2.2.8 Setup during sign-in: send a code by text message (phone) or to the account email

`POST /api/v1/auth/mfa/setup/code/send`

**Authentication:** None (public)

**Request body** (`application/json`, [SetupSendCodeRequest](13-data-models.md#setupsendcoderequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `method` | string | yes |  |
| `mfaChallengeId` | string (uuid) | yes |  |
| `phone` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/auth/mfa/setup/code/send" \
  -H "Content-Type: application/json" \
  --data '{
  "method": "method",
  "mfaChallengeId": "{mfaChallengeId}",
  "phone": "+14155552671"
}'
```

### 2.2.9 Setup during sign-in: confirm the authenticator app code; signs in, returns backup codes

`POST /api/v1/auth/mfa/setup/totp/confirm`

**Authentication:** None (public)

**Request body** (`application/json`, [SetupTotpConfirmRequest](13-data-models.md#setuptotpconfirmrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | yes |  |
| `mfaChallengeId` | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/auth/mfa/setup/totp/confirm" \
  -H "Content-Type: application/json" \
  --data '{
  "code": "123456",
  "mfaChallengeId": "{mfaChallengeId}"
}'
```

### 2.2.10 Setup during sign-in: start an authenticator app (QR code)

`POST /api/v1/auth/mfa/setup/totp/start`

**Authentication:** None (public)

**Request body** (`application/json`, [ChallengeRequest](13-data-models.md#challengerequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `mfaChallengeId` | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/auth/mfa/setup/totp/start" \
  -H "Content-Type: application/json" \
  --data '{
  "mfaChallengeId": "{mfaChallengeId}"
}'
```

### 2.2.11 Setup during sign-in: passkey or security key registration options

`POST /api/v1/auth/mfa/setup/webauthn/options`

**Authentication:** None (public)

**Request body** (`application/json`, [SetupWebAuthnRequest](13-data-models.md#setupwebauthnrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `kind` | string | no |  |
| `mfaChallengeId` | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/auth/mfa/setup/webauthn/options" \
  -H "Content-Type: application/json" \
  --data '{
  "kind": "kind",
  "mfaChallengeId": "{mfaChallengeId}"
}'
```

### 2.2.12 Setup during sign-in: register the passkey or security key; signs in, returns backup codes

`POST /api/v1/auth/mfa/setup/webauthn/verify`

**Authentication:** None (public)

**Request body** (`application/json`, [WebAuthnVerifyRequest](13-data-models.md#webauthnverifyrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `credential` | string | yes |  |
| `mfaChallengeId` | string (uuid) | yes |  |
| `rememberDevice` | boolean | no |  |
| `requestId` | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/auth/mfa/setup/webauthn/verify" \
  -H "Content-Type: application/json" \
  --data '{
  "credential": "{credential}",
  "mfaChallengeId": "{mfaChallengeId}",
  "rememberDevice": true,
  "requestId": "{requestId}"
}'
```

### 2.2.13 Set up later: sign in without a second step while reminders or the grace period allow

`POST /api/v1/auth/mfa/skip`

**Authentication:** None (public)

**Request body** (`application/json`, [SkipRequest](13-data-models.md#skiprequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `dontAskAgain` | boolean | no |  |
| `mfaChallengeId` | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/auth/mfa/skip" \
  -H "Content-Type: application/json" \
  --data '{
  "dontAskAgain": true,
  "mfaChallengeId": "{mfaChallengeId}"
}'
```

### 2.2.14 Complete login with TOTP code

`POST /api/v1/auth/mfa/totp/verify`

**Authentication:** None (public)

**Request body** (`application/json`, [TotpVerifyRequest](13-data-models.md#totpverifyrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | yes |  |
| `mfaChallengeId` | string (uuid) | yes |  |
| `rememberDevice` | boolean | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/auth/mfa/totp/verify" \
  -H "Content-Type: application/json" \
  --data '{
  "code": "123456",
  "mfaChallengeId": "{mfaChallengeId}",
  "rememberDevice": true
}'
```

### 2.2.15 Passkey or security key as the second step: assertion options

`POST /api/v1/auth/mfa/webauthn/options`

**Authentication:** None (public)

**Request body** (`application/json`, [ChallengeRequest](13-data-models.md#challengerequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `mfaChallengeId` | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/auth/mfa/webauthn/options" \
  -H "Content-Type: application/json" \
  --data '{
  "mfaChallengeId": "{mfaChallengeId}"
}'
```

### 2.2.16 Complete login with a passkey or security key

`POST /api/v1/auth/mfa/webauthn/verify`

**Authentication:** None (public)

**Request body** (`application/json`, [WebAuthnVerifyRequest](13-data-models.md#webauthnverifyrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `credential` | string | yes |  |
| `mfaChallengeId` | string (uuid) | yes |  |
| `rememberDevice` | boolean | no |  |
| `requestId` | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/auth/mfa/webauthn/verify" \
  -H "Content-Type: application/json" \
  --data '{
  "credential": "{credential}",
  "mfaChallengeId": "{mfaChallengeId}",
  "rememberDevice": true,
  "requestId": "{requestId}"
}'
```

### 2.2.17 Begin passkey sign-in

`POST /api/v1/auth/passkey/options`

**Authentication:** None (public)

**Request body** (`application/json`, [OptionsRequest](13-data-models.md#optionsrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `email` | string | no |  |
| `tenantSlug` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/auth/passkey/options" \
  -H "Content-Type: application/json" \
  --data '{
  "applicationId": "{applicationId}",
  "email": "user@example.com",
  "tenantSlug": "tenant-a"
}'
```

### 2.2.18 Complete passkey sign-in

`POST /api/v1/auth/passkey/verify`

**Authentication:** None (public)

**Request body** (`application/json`, [VerifyRequest](13-data-models.md#verifyrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/auth/passkey/verify" \
  -H "Content-Type: application/json" \
  --data '{
  "code": "123456"
}'
```

### 2.2.19 Session login (native app UI)

`POST /api/v1/auth/session/login`

Validates credentials and creates an auth-server session cookie. Prefer the application-scoped endpoint when integrating a single app.

**Authentication:** None (public)

**Request body** (`application/json`, [SessionLoginRequest](13-data-models.md#sessionloginrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `email` | string (email) | yes |  |
| `password` | string | yes |  |
| `tenantSlug` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/auth/session/login" \
  -H "Content-Type: application/json" \
  --data '{
  "applicationId": "{applicationId}",
  "email": "user@example.com",
  "password": "Choose-a-Strong-Pass-1!",
  "tenantSlug": "tenant-a"
}'
```

## 2.3 Federation

Social / OIDC sign-in

### 2.3.1 List enabled federation providers for a tenant/application

`GET /api/v1/auth/federation/providers`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantSlug` | query | string | no |  |
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/v1/auth/federation/providers"
```

### 2.3.2 SAML assertion consumer service (HTTP-POST binding from the IdP)

`POST /api/v1/auth/federation/saml/acs/{providerId}`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `providerId` | path | string (uuid) | yes | Identity provider ID (UUID) |

**Request body** (`application/x-www-form-urlencoded`)

| Field | Type | Required | Description |
|---|---|---|---|
| `RelayState` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/auth/federation/saml/acs/{providerId}" \
  --data-urlencode "RelayState={RelayState}"
```

### 2.3.3 Sign out; for a SAML session also at the IdP (single logout)

`GET /api/v1/auth/federation/saml/logout`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/auth/federation/saml/logout"
```

### 2.3.4 SAML single logout: the IdP's LogoutRequest or LogoutResponse (Redirect or POST binding)

`GET /api/v1/auth/federation/saml/slo/{providerId}`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `providerId` | path | string (uuid) | yes | Identity provider ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/auth/federation/saml/slo/{providerId}"
```

### 2.3.5 SAML single logout: the IdP's LogoutRequest or LogoutResponse (Redirect or POST binding)

`POST /api/v1/auth/federation/saml/slo/{providerId}`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `providerId` | path | string (uuid) | yes | Identity provider ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/auth/federation/saml/slo/{providerId}"
```

### 2.3.6 SAML service-provider metadata for a configured provider (give this to the IdP)

`GET /api/v1/auth/federation/saml/{alias}/metadata`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `alias` | path | string | yes |  |
| `tenantSlug` | query | string | no |  |
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | string |

```bash
curl -s "$SECUREONE_URL/api/v1/auth/federation/saml/{alias}/metadata"
```

### 2.3.7 SAML login (same as /{alias}/start for a SAML provider)

`GET /api/v1/auth/federation/saml/{alias}/start`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `alias` | path | string | yes |  |
| `tenantSlug` | query | string | no |  |
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |
| `continue` | query | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/auth/federation/saml/{alias}/start"
```

### 2.3.8 OAuth/OIDC callback from external IdP

`GET /api/v1/auth/federation/{alias}/callback`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `alias` | path | string | yes |  |
| `code` | query | string | no |  |
| `state` | query | string | no |  |
| `error` | query | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/auth/federation/{alias}/callback"
```

### 2.3.9 Start federation login (redirects to the external IdP: OAuth/OIDC or SAML)

`GET /api/v1/auth/federation/{alias}/start`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `alias` | path | string | yes |  |
| `tenantSlug` | query | string | no |  |
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |
| `continue` | query | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/auth/federation/{alias}/start"
```

## 2.4 Device sign-in

Device authorization grant (TVs, CLIs)

### 2.4.1 Look up a device user code

`POST /api/v1/auth/device/lookup`

Returns the application a pending code belongs to.

**Authentication:** None (public)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/auth/device/lookup"
```

## 2.5 Impersonation

Consume admin impersonation tokens

### 2.5.1 Consume impersonation token and establish a user session

`GET /api/v1/auth/impersonation/consume`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `token` | query | string | yes |  |
| `continueUrl` | query | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | — |

```bash
curl -s "$SECUREONE_URL/api/v1/auth/impersonation/consume?token={token}"
```

### 2.5.2 List status

`GET /api/v1/auth/impersonation/status`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/auth/impersonation/status"
```
