---
title: Application sign-in and sign-up
summary: For apps that draw their own sign-in screens instead of redirecting to the hosted page: application manifest, password and one-time-code sign-in, sign-up, MFA, passkeys, federation and device sign-in.
order: 4
audience: public
---

# Application sign-in and sign-up

[API reference](index.md) › Application sign-in and sign-up

> Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.

For apps that draw their own sign-in screens instead of redirecting to the hosted page: application manifest, password and one-time-code sign-in, sign-up, MFA, passkeys, federation and device sign-in.

In Swagger UI (`/docs`), choose the definition **Authentication** (`/v3/api-docs/auth`).

Set the variables from [Getting started](getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [Application public API](#application-public-api)
  - [GET `/api/v1/applications/resolve`](#find-an-application-by-client-id-or-host) — Find an application by client ID or host
  - [GET `/api/v1/applications/{applicationId}`](#get-application) — Get application
  - [POST `/api/v1/applications/{applicationId}/account/email/resend-verification`](#resend-email-verification) — Resend email verification
  - [POST `/api/v1/applications/{applicationId}/account/magic-link/request`](#request-magic-link) — Request magic link
  - [POST `/api/v1/applications/{applicationId}/account/password/forgot`](#forgot-password) — Forgot password
  - [POST `/api/v1/applications/{applicationId}/account/password/reset/otp`](#reset-password-with-a-one-time-code) — Reset password with a one-time code
  - [POST `/api/v1/applications/{applicationId}/account/verify/otp/confirm`](#confirm-otp) — Confirm OTP
  - [POST `/api/v1/applications/{applicationId}/auth/otp/request`](#request-otp) — Request OTP
  - [POST `/api/v1/applications/{applicationId}/auth/otp/verify`](#verify-otp) — Verify OTP
  - [POST `/api/v1/applications/{applicationId}/auth/session/login`](#login-session) — Login session
  - [GET `/api/v1/applications/{applicationId}/branding`](#get-the-applications-branding-public) — Get the application's branding (public)
  - [GET `/api/v1/applications/{applicationId}/signup`](#get-sign-up-options) — Get sign-up options
  - [POST `/api/v1/applications/{applicationId}/signup`](#signup) — Signup
- [Public auth](#public-auth)
  - [GET `/api/v1/auth/me`](#current-browser-session) — Current browser session
  - [GET `/api/v1/auth/methods`](#list-methods) — List methods
  - [GET `/api/v1/auth/mfa/challenge/{mfaChallengeId}`](#describe-a-pending-second-step-or-setup-options-deadline-whether-it-can-be-skipped) — Describe a pending second step or setup (options, deadline, whether it can be skipped)
  - [POST `/api/v1/auth/mfa/code/verify`](#complete-login-with-the-code-sent-by-text-message-or-email) — Complete login with the code sent by text message or email
  - [POST `/api/v1/auth/mfa/recovery/verify`](#complete-login-with-one-time-recovery-code) — Complete login with one-time recovery code
  - [POST `/api/v1/auth/mfa/send`](#send-a-code-by-text-message-or-email-to-one-of-the-users-options) — Send a code by text message or email to one of the user's options
  - [POST `/api/v1/auth/mfa/setup/code/confirm`](#setup-during-sign-in-confirm-the-code-signs-in-returns-backup-codes) — Setup during sign-in: confirm the code; signs in, returns backup codes
  - [POST `/api/v1/auth/mfa/setup/code/send`](#setup-during-sign-in-send-a-code-by-text-message-phone-or-to-the-account-email) — Setup during sign-in: send a code by text message (phone) or to the account email
  - [POST `/api/v1/auth/mfa/setup/totp/confirm`](#setup-during-sign-in-confirm-the-authenticator-app-code-signs-in-returns-backup-codes) — Setup during sign-in: confirm the authenticator app code; signs in, returns backup codes
  - [POST `/api/v1/auth/mfa/setup/totp/start`](#setup-during-sign-in-start-an-authenticator-app-qr-code) — Setup during sign-in: start an authenticator app (QR code)
  - [POST `/api/v1/auth/mfa/setup/webauthn/options`](#setup-during-sign-in-passkey-or-security-key-registration-options) — Setup during sign-in: passkey or security key registration options
  - [POST `/api/v1/auth/mfa/setup/webauthn/verify`](#setup-during-sign-in-register-the-passkey-or-security-key-signs-in-returns-backup-codes) — Setup during sign-in: register the passkey or security key; signs in, returns backup codes
  - [POST `/api/v1/auth/mfa/skip`](#set-up-later-sign-in-without-a-second-step-while-reminders-or-the-grace-period-allow) — Set up later: sign in without a second step while reminders or the grace period allow
  - [POST `/api/v1/auth/mfa/totp/verify`](#complete-login-with-totp-code) — Complete login with TOTP code
  - [POST `/api/v1/auth/mfa/webauthn/options`](#passkey-or-security-key-as-the-second-step-assertion-options) — Passkey or security key as the second step: assertion options
  - [POST `/api/v1/auth/mfa/webauthn/verify`](#complete-login-with-a-passkey-or-security-key) — Complete login with a passkey or security key
  - [POST `/api/v1/auth/passkey/options`](#begin-passkey-sign-in) — Begin passkey sign-in
  - [POST `/api/v1/auth/passkey/verify`](#complete-passkey-sign-in) — Complete passkey sign-in
  - [POST `/api/v1/auth/session/login`](#session-login-native-app-ui) — Session login (native app UI)
- [Federation](#federation)
  - [GET `/api/v1/auth/federation/providers`](#list-enabled-federation-providers-for-a-tenantapplication) — List enabled federation providers for a tenant/application
  - [POST `/api/v1/auth/federation/saml/acs/{providerId}`](#saml-assertion-consumer-service-http-post-binding-from-the-idp) — SAML assertion consumer service (HTTP-POST binding from the IdP)
  - [GET `/api/v1/auth/federation/saml/logout`](#sign-out-for-a-saml-session-also-at-the-idp-single-logout) — Sign out; for a SAML session also at the IdP (single logout)
  - [GET `/api/v1/auth/federation/saml/slo/{providerId}`](#saml-single-logout-the-idps-logoutrequest-or-logoutresponse-redirect-or-post-binding) — SAML single logout: the IdP's LogoutRequest or LogoutResponse (Redirect or POST binding)
  - [POST `/api/v1/auth/federation/saml/slo/{providerId}`](#saml-single-logout-the-idps-logoutrequest-or-logoutresponse-redirect-or-post-binding-1) — SAML single logout: the IdP's LogoutRequest or LogoutResponse (Redirect or POST binding)
  - [GET `/api/v1/auth/federation/saml/{alias}/metadata`](#saml-service-provider-metadata-for-a-configured-provider-give-this-to-the-idp) — SAML service-provider metadata for a configured provider (give this to the IdP)
  - [GET `/api/v1/auth/federation/saml/{alias}/start`](#saml-login-same-as-aliasstart-for-a-saml-provider) — SAML login (same as /{alias}/start for a SAML provider)
  - [GET `/api/v1/auth/federation/{alias}/callback`](#oauthoidc-callback-from-external-idp) — OAuth/OIDC callback from external IdP
  - [GET `/api/v1/auth/federation/{alias}/start`](#start-federation-login-redirects-to-the-external-idp-oauthoidc-or-saml) — Start federation login (redirects to the external IdP: OAuth/OIDC or SAML)
- [Device sign-in](#device-sign-in)
  - [POST `/api/v1/auth/device/lookup`](#look-up-a-device-user-code) — Look up a device user code
- [Impersonation](#impersonation)
  - [GET `/api/v1/auth/impersonation/consume`](#consume-impersonation-token-and-establish-a-user-session) — Consume impersonation token and establish a user session
  - [GET `/api/v1/auth/impersonation/status`](#list-status) — List status

## Application public API

Manifest and self-service sign-up for integrated apps

### Find an application by client ID or host

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

### Get application

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

### Resend email verification

`POST /api/v1/applications/{applicationId}/account/email/resend-verification`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [EmailRequest](data-models.md#emailrequest))

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

### Request magic link

`POST /api/v1/applications/{applicationId}/account/magic-link/request`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [EmailRequest](data-models.md#emailrequest))

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

### Forgot password

`POST /api/v1/applications/{applicationId}/account/password/forgot`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [ForgotPasswordRequest](data-models.md#forgotpasswordrequest))

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

### Reset password with a one-time code

`POST /api/v1/applications/{applicationId}/account/password/reset/otp`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [ResetPasswordOtpRequest](data-models.md#resetpasswordotprequest))

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

### Confirm OTP

`POST /api/v1/applications/{applicationId}/account/verify/otp/confirm`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [OtpVerifyRequest](data-models.md#otpverifyrequest))

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

### Request OTP

`POST /api/v1/applications/{applicationId}/auth/otp/request`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [OtpLoginRequest](data-models.md#otploginrequest))

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

### Verify OTP

`POST /api/v1/applications/{applicationId}/auth/otp/verify`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [OtpVerifyRequest](data-models.md#otpverifyrequest))

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

### Login session

`POST /api/v1/applications/{applicationId}/auth/session/login`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [SessionLoginRequest](data-models.md#sessionloginrequest))

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

### Get the application's branding (public)

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

### Get sign-up options

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

### Signup

`POST /api/v1/applications/{applicationId}/signup`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [SignupRequest](data-models.md#signuprequest))

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

## Public auth

Authentication method catalog and session login for native UIs

### Current browser session

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

### List methods

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

### Describe a pending second step or setup (options, deadline, whether it can be skipped)

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

### Complete login with the code sent by text message or email

`POST /api/v1/auth/mfa/code/verify`

**Authentication:** None (public)

**Request body** (`application/json`, [CodeVerifyRequest](data-models.md#codeverifyrequest))

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

### Complete login with one-time recovery code

`POST /api/v1/auth/mfa/recovery/verify`

**Authentication:** None (public)

**Request body** (`application/json`, [RecoveryVerifyRequest](data-models.md#recoveryverifyrequest))

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

### Send a code by text message or email to one of the user's options

`POST /api/v1/auth/mfa/send`

**Authentication:** None (public)

**Request body** (`application/json`, [SendCodeRequest](data-models.md#sendcoderequest))

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

### Setup during sign-in: confirm the code; signs in, returns backup codes

`POST /api/v1/auth/mfa/setup/code/confirm`

**Authentication:** None (public)

**Request body** (`application/json`, [SetupCodeConfirmRequest](data-models.md#setupcodeconfirmrequest))

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

### Setup during sign-in: send a code by text message (phone) or to the account email

`POST /api/v1/auth/mfa/setup/code/send`

**Authentication:** None (public)

**Request body** (`application/json`, [SetupSendCodeRequest](data-models.md#setupsendcoderequest))

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

### Setup during sign-in: confirm the authenticator app code; signs in, returns backup codes

`POST /api/v1/auth/mfa/setup/totp/confirm`

**Authentication:** None (public)

**Request body** (`application/json`, [SetupTotpConfirmRequest](data-models.md#setuptotpconfirmrequest))

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

### Setup during sign-in: start an authenticator app (QR code)

`POST /api/v1/auth/mfa/setup/totp/start`

**Authentication:** None (public)

**Request body** (`application/json`, [ChallengeRequest](data-models.md#challengerequest))

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

### Setup during sign-in: passkey or security key registration options

`POST /api/v1/auth/mfa/setup/webauthn/options`

**Authentication:** None (public)

**Request body** (`application/json`, [SetupWebAuthnRequest](data-models.md#setupwebauthnrequest))

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

### Setup during sign-in: register the passkey or security key; signs in, returns backup codes

`POST /api/v1/auth/mfa/setup/webauthn/verify`

**Authentication:** None (public)

**Request body** (`application/json`, [WebAuthnVerifyRequest](data-models.md#webauthnverifyrequest))

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

### Set up later: sign in without a second step while reminders or the grace period allow

`POST /api/v1/auth/mfa/skip`

**Authentication:** None (public)

**Request body** (`application/json`, [SkipRequest](data-models.md#skiprequest))

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

### Complete login with TOTP code

`POST /api/v1/auth/mfa/totp/verify`

**Authentication:** None (public)

**Request body** (`application/json`, [TotpVerifyRequest](data-models.md#totpverifyrequest))

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

### Passkey or security key as the second step: assertion options

`POST /api/v1/auth/mfa/webauthn/options`

**Authentication:** None (public)

**Request body** (`application/json`, [ChallengeRequest](data-models.md#challengerequest))

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

### Complete login with a passkey or security key

`POST /api/v1/auth/mfa/webauthn/verify`

**Authentication:** None (public)

**Request body** (`application/json`, [WebAuthnVerifyRequest](data-models.md#webauthnverifyrequest))

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

### Begin passkey sign-in

`POST /api/v1/auth/passkey/options`

**Authentication:** None (public)

**Request body** (`application/json`, [OptionsRequest](data-models.md#optionsrequest))

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

### Complete passkey sign-in

`POST /api/v1/auth/passkey/verify`

**Authentication:** None (public)

**Request body** (`application/json`, [VerifyRequest](data-models.md#verifyrequest))

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

### Session login (native app UI)

`POST /api/v1/auth/session/login`

Validates credentials and creates an auth-server session cookie. Prefer the application-scoped endpoint when integrating a single app.

**Authentication:** None (public)

**Request body** (`application/json`, [SessionLoginRequest](data-models.md#sessionloginrequest))

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

## Federation

Social / OIDC sign-in

### List enabled federation providers for a tenant/application

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

### SAML assertion consumer service (HTTP-POST binding from the IdP)

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

### Sign out; for a SAML session also at the IdP (single logout)

`GET /api/v1/auth/federation/saml/logout`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/auth/federation/saml/logout"
```

### SAML single logout: the IdP's LogoutRequest or LogoutResponse (Redirect or POST binding)

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

### SAML single logout: the IdP's LogoutRequest or LogoutResponse (Redirect or POST binding)

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

### SAML service-provider metadata for a configured provider (give this to the IdP)

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

### SAML login (same as /{alias}/start for a SAML provider)

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

### OAuth/OIDC callback from external IdP

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

### Start federation login (redirects to the external IdP: OAuth/OIDC or SAML)

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

## Device sign-in

Device authorization grant (TVs, CLIs)

### Look up a device user code

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

## Impersonation

Consume admin impersonation tokens

### Consume impersonation token and establish a user session

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

### List status

`GET /api/v1/auth/impersonation/status`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/auth/impersonation/status"
```
