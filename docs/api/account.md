---
title: Account self-service
summary: What a signed-in user can do for themselves: password reset and change, email verification, profile, MFA factors, sessions, email change, connected apps and privacy (export, delete request).
order: 6
audience: public
---

# Account self-service

[API reference](index.md) › Account self-service

> Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.

What a signed-in user can do for themselves: password reset and change, email verification, profile, MFA factors, sessions, email change, connected apps and privacy (export, delete request).

In Swagger UI (`/docs`), choose the definition **Account** (`/v3/api-docs/account`).

Set the variables from [Getting started](getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [Account](#account)
  - [GET `/api/v1/account/2fa`](#two-step-status-options-backup-codes-remembered-devices-requirement-and-deadline) — Two-step status: options, backup codes, remembered devices, requirement and deadline
  - [POST `/api/v1/account/2fa/backup-codes`](#new-backup-codes-the-old-ones-stop-working) — New backup codes (the old ones stop working)
  - [DELETE `/api/v1/account/2fa/devices`](#forget-all-remembered-devices) — Forget all remembered devices
  - [DELETE `/api/v1/account/2fa/devices/{deviceId}`](#forget-a-remembered-device-it-will-ask-for-the-second-step-again) — Forget a remembered device (it will ask for the second step again)
  - [POST `/api/v1/account/2fa/methods/code/confirm`](#add-a-text-message-or-email-option-confirm-the-code) — Add a text message or email option: confirm the code
  - [POST `/api/v1/account/2fa/methods/code/send`](#add-a-text-message-or-email-option-send-a-code) — Add a text message or email option: send a code
  - [POST `/api/v1/account/2fa/methods/totp/confirm`](#add-an-authenticator-app-confirm-the-code) — Add an authenticator app: confirm the code
  - [POST `/api/v1/account/2fa/methods/totp/start`](#add-an-authenticator-app-qr-code) — Add an authenticator app: QR code
  - [POST `/api/v1/account/2fa/methods/webauthn/options`](#add-a-passkey-or-security-key-registration-options) — Add a passkey or security key: registration options
  - [POST `/api/v1/account/2fa/methods/webauthn/verify`](#add-a-passkey-or-security-key-register-it) — Add a passkey or security key: register it
  - [DELETE `/api/v1/account/2fa/methods/{factorId}`](#remove-an-option) — Remove an option
  - [POST `/api/v1/account/2fa/methods/{factorId}/default`](#make-an-option-the-one-asked-for-first) — Make an option the one asked for first
  - [POST `/api/v1/account/2fa/off`](#turn-two-step-verification-off-only-when-it-is-optional) — Turn two-step verification off (only when it is optional)
  - [POST `/api/v1/account/2fa/verify`](#verify-its-you-authenticator-sent-code-passkey-or-backup-code-for-sensitive-changes) — Verify it's you (authenticator, sent code, passkey or backup code) for sensitive changes
  - [POST `/api/v1/account/2fa/verify/send`](#send-a-code-text-message-or-email-option-to-verify-its-you) — Send a code (text message or email option) to verify it's you
  - [POST `/api/v1/account/2fa/verify/webauthn/options`](#passkey-or-security-key-to-verify-its-you-options) — Passkey or security key to verify it's you: options
  - [POST `/api/v1/account/email/resend-verification`](#resend-email-verification) — Resend email verification
  - [GET `/api/v1/account/email/verify`](#verify-email) — Verify email
  - [POST `/api/v1/account/email/verify`](#verify-email-1) — Verify email
  - [POST `/api/v1/account/magic-link/request`](#request-magic-link) — Request magic link
  - [GET `/api/v1/account/magic-link/verify`](#open-a-magic-link-browser-to-the-second-step-when-needed-else-signed-in) — Open a magic link (browser): to the second step when needed, else signed in
  - [POST `/api/v1/account/magic-link/verify`](#verify-a-magic-link-api-200-signed-in-428-second-step-403-mfa-required) — Verify a magic link (API): 200 signed in, 428 second step, 403 MFA required
  - [POST `/api/v1/account/password/forgot`](#start-password-reset-email-link-or-code) — Start password reset (email link or code)
  - [POST `/api/v1/account/password/reset`](#reset-password-with-the-emailed-link-token) — Reset password with the emailed link token
  - [POST `/api/v1/account/password/reset/otp`](#reset-password-with-a-one-time-code) — Reset password with a one-time code
  - [POST `/api/v1/account/set-password`](#set-password) — Set password
  - [GET `/api/v1/account/verify/options`](#list-verification-options) — List verification options
  - [POST `/api/v1/account/verify/otp/confirm`](#confirm-otp) — Confirm OTP
- [Account profile](#account-profile)
  - [POST `/api/v1/account/password/change`](#change-password) — Change password
  - [GET `/api/v1/account/profile`](#get-profile) — Get profile
  - [PATCH `/api/v1/account/profile`](#update-profile) — Update profile
- [Account MFA](#account-mfa)
  - [GET `/api/v1/account/mfa/factors`](#list-enrolled-mfa-factors) — List enrolled MFA factors
  - [POST `/api/v1/account/mfa/passkey/register/options`](#begin-passkey-registration) — Begin passkey registration
  - [POST `/api/v1/account/mfa/passkey/register/verify`](#finish-passkey-registration) — Finish passkey registration
  - [DELETE `/api/v1/account/mfa/passkey/{factorId}`](#remove-a-passkey) — Remove a passkey
  - [POST `/api/v1/account/mfa/totp/confirm`](#confirm-totp-enrollment) — Confirm TOTP enrollment
  - [POST `/api/v1/account/mfa/totp/enroll`](#begin-totp-enrollment) — Begin TOTP enrollment
  - [DELETE `/api/v1/account/mfa/totp/{factorId}`](#remove-totp-factor) — Remove TOTP factor
- [Account sessions](#account-sessions)
  - [GET `/api/v1/account/sessions`](#list-active-sessions) — List active sessions
  - [DELETE `/api/v1/account/sessions`](#revoke-other-sessions) — Revoke other sessions
  - [DELETE `/api/v1/account/sessions/{sessionId}`](#revoke-a-session) — Revoke a session
- [Account privacy](#account-privacy)
  - [POST `/api/v1/account/privacy/delete-request`](#delete-privacy-request) — Delete privacy request
  - [GET `/api/v1/account/privacy/export`](#export-my-data-gdpr) — Export my data (GDPR)
- [Account privacy (session)](#account-privacy-session)
  - [POST `/api/v1/account/privacy/session/delete-request`](#request-account-deletion) — Request account deletion
  - [GET `/api/v1/account/privacy/session/export`](#export-personal-data) — Export personal data
- [Account email change](#account-email-change)
  - [POST `/api/v1/account/email/change`](#change-email-integrated-apps-bearer-token) — Change email (integrated apps, Bearer token)
  - [POST `/api/v1/account/email/change/confirm`](#confirm-the-new-email-from-the-emailed-link) — Confirm the new email from the emailed link
  - [POST `/api/v1/account/email/change/session`](#change-email-hosted-account-security-page-browser-session) — Change email (hosted Account security page, browser session)
- [Account connected apps](#account-connected-apps)
  - [GET `/api/v1/account/consents`](#list-consents) — List consents
  - [DELETE `/api/v1/account/consents/{registeredClientId}`](#delete-consent) — Delete consent
- [Account password change](#account-password-change)
  - [POST `/api/v1/account/password/change/session`](#change-password-hosted-account-security-page-browser-session) — Change password (hosted Account security page, browser session)
  - [GET `/api/v1/account/profile/session`](#your-names-hosted-account-security-page-browser-session) — Your names (hosted Account security page, browser session)
  - [PATCH `/api/v1/account/profile/session`](#change-your-names-hosted-account-security-page-browser-session) — Change your names (hosted Account security page, browser session)
- [Account linked sign-in](#account-linked-sign-in)
  - [GET `/api/v1/account/identities`](#my-linked-sign-in-accounts) — My linked sign-in accounts
  - [DELETE `/api/v1/account/identities/{identityId}`](#unlink-one) — Unlink one

## Account

Password reset, email verification, magic link (public)

### Two-step status: options, backup codes, remembered devices, requirement and deadline

`GET /api/v1/account/2fa`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/account/2fa"
```

### New backup codes (the old ones stop working)

`POST /api/v1/account/2fa/backup-codes`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/2fa/backup-codes"
```

### Forget all remembered devices

`DELETE /api/v1/account/2fa/devices`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/v1/account/2fa/devices"
```

### Forget a remembered device (it will ask for the second step again)

`DELETE /api/v1/account/2fa/devices/{deviceId}`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `deviceId` | path | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/v1/account/2fa/devices/{deviceId}"
```

### Add a text message or email option: confirm the code

`POST /api/v1/account/2fa/methods/code/confirm`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Request body** (`application/json`, [CodeConfirmRequest](data-models.md#codeconfirmrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/2fa/methods/code/confirm" \
  -H "Content-Type: application/json" \
  --data '{
  "code": "123456"
}'
```

### Add a text message or email option: send a code

`POST /api/v1/account/2fa/methods/code/send`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Request body** (`application/json`, [CodeSendRequest](data-models.md#codesendrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `method` | string | no |  |
| `phone` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/2fa/methods/code/send" \
  -H "Content-Type: application/json" \
  --data '{
  "method": "method",
  "phone": "+14155552671"
}'
```

### Add an authenticator app: confirm the code

`POST /api/v1/account/2fa/methods/totp/confirm`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Request body** (`application/json`, [TotpConfirmRequest](data-models.md#totpconfirmrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | no |  |
| `factorId` | string (uuid) | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/2fa/methods/totp/confirm" \
  -H "Content-Type: application/json" \
  --data '{
  "code": "123456",
  "factorId": "{factorId}"
}'
```

### Add an authenticator app: QR code

`POST /api/v1/account/2fa/methods/totp/start`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/2fa/methods/totp/start"
```

### Add a passkey or security key: registration options

`POST /api/v1/account/2fa/methods/webauthn/options`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Request body** (`application/json`, [WebAuthnStartRequest](data-models.md#webauthnstartrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `kind` | string | no |  |
| `label` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/2fa/methods/webauthn/options" \
  -H "Content-Type: application/json" \
  --data '{
  "kind": "kind",
  "label": "Production"
}'
```

### Add a passkey or security key: register it

`POST /api/v1/account/2fa/methods/webauthn/verify`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Request body** (`application/json`, [WebAuthnFinishRequest](data-models.md#webauthnfinishrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `credential` | string | no |  |
| `label` | string | no |  |
| `requestId` | string (uuid) | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/2fa/methods/webauthn/verify" \
  -H "Content-Type: application/json" \
  --data '{
  "credential": "{credential}",
  "label": "Production",
  "requestId": "{requestId}"
}'
```

### Remove an option

`DELETE /api/v1/account/2fa/methods/{factorId}`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `factorId` | path | string (uuid) | yes | MFA factor ID (UUID) |
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/v1/account/2fa/methods/{factorId}"
```

### Make an option the one asked for first

`POST /api/v1/account/2fa/methods/{factorId}/default`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `factorId` | path | string (uuid) | yes | MFA factor ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/2fa/methods/{factorId}/default"
```

### Turn two-step verification off (only when it is optional)

`POST /api/v1/account/2fa/off`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | — |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/2fa/off"
```

### Verify it's you (authenticator, sent code, passkey or backup code) for sensitive changes

`POST /api/v1/account/2fa/verify`

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
curl -s -X POST "$SECUREONE_URL/api/v1/account/2fa/verify" \
  -H "Content-Type: application/json" \
  --data '{
  "code": "123456"
}'
```

### Send a code (text message or email option) to verify it's you

`POST /api/v1/account/2fa/verify/send`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Request body** (`application/json`, [SendRequest](data-models.md#sendrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `factorId` | string (uuid) | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/2fa/verify/send" \
  -H "Content-Type: application/json" \
  --data '{
  "factorId": "{factorId}"
}'
```

### Passkey or security key to verify it's you: options

`POST /api/v1/account/2fa/verify/webauthn/options`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/2fa/verify/webauthn/options"
```

### Resend email verification

`POST /api/v1/account/email/resend-verification`

**Authentication:** None (public)

**Request body** (`application/json`, [TenantEmailRequest](data-models.md#tenantemailrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `email` | string (email) | yes |  |
| `tenantSlug` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/email/resend-verification" \
  -H "Content-Type: application/json" \
  --data '{
  "applicationId": "{applicationId}",
  "email": "user@example.com",
  "tenantSlug": "tenant-a"
}'
```

### Verify email

`GET /api/v1/account/email/verify`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `token` | query | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | string |

```bash
curl -s "$SECUREONE_URL/api/v1/account/email/verify?token={token}"
```

### Verify email

`POST /api/v1/account/email/verify`

**Authentication:** None (public)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/email/verify"
```

### Request magic link

`POST /api/v1/account/magic-link/request`

**Authentication:** None (public)

**Request body** (`application/json`, [TenantEmailRequest](data-models.md#tenantemailrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `email` | string (email) | yes |  |
| `tenantSlug` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/magic-link/request" \
  -H "Content-Type: application/json" \
  --data '{
  "applicationId": "{applicationId}",
  "email": "user@example.com",
  "tenantSlug": "tenant-a"
}'
```

### Open a magic link (browser): to the second step when needed, else signed in

`GET /api/v1/account/magic-link/verify`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `token` | query | string | yes |  |
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/account/magic-link/verify?token={token}"
```

### Verify a magic link (API): 200 signed in, 428 second step, 403 MFA required

`POST /api/v1/account/magic-link/verify`

**Authentication:** None (public)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/magic-link/verify"
```

### Start password reset (email link or code)

`POST /api/v1/account/password/forgot`

**Authentication:** None (public)

**Request body** (`application/json`, [TenantEmailRequest](data-models.md#tenantemailrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `email` | string (email) | yes |  |
| `tenantSlug` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/password/forgot" \
  -H "Content-Type: application/json" \
  --data '{
  "applicationId": "{applicationId}",
  "email": "user@example.com",
  "tenantSlug": "tenant-a"
}'
```

### Reset password with the emailed link token

`POST /api/v1/account/password/reset`

**Authentication:** None (public)

**Request body** (`application/json`, [ResetPasswordRequest](data-models.md#resetpasswordrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `password` | string | yes |  |
| `token` | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/password/reset" \
  -H "Content-Type: application/json" \
  --data '{
  "applicationId": "{applicationId}",
  "password": "Choose-a-Strong-Pass-1!",
  "token": "{token}"
}'
```

### Reset password with a one-time code

`POST /api/v1/account/password/reset/otp`

**Authentication:** None (public)

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
curl -s -X POST "$SECUREONE_URL/api/v1/account/password/reset/otp" \
  -H "Content-Type: application/json" \
  --data '{
  "challengeId": "{challengeId}",
  "code": "123456",
  "password": "Choose-a-Strong-Pass-1!"
}'
```

### Set password

`POST /api/v1/account/set-password`

**Authentication:** None (public)

**Request body** (`application/json`, [ResetPasswordRequest](data-models.md#resetpasswordrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `password` | string | yes |  |
| `token` | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/set-password" \
  -H "Content-Type: application/json" \
  --data '{
  "applicationId": "{applicationId}",
  "password": "Choose-a-Strong-Pass-1!",
  "token": "{token}"
}'
```

### List verification options

`GET /api/v1/account/verify/options`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/account/verify/options"
```

### Confirm OTP

`POST /api/v1/account/verify/otp/confirm`

**Authentication:** None (public)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/verify/otp/confirm"
```

## Account profile

Authenticated end-user profile and password change (Bearer access token)

### Change password

`POST /api/v1/account/password/change`

**Authentication:** Access token (`Authorization: Bearer …`) from the token endpoint

**Request body** (`application/json`, [ChangePasswordRequest](data-models.md#changepasswordrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `currentPassword` | string | yes |  |
| `newPassword` | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/password/change" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "currentPassword": "Choose-a-Strong-Pass-1!",
  "newPassword": "Choose-a-Strong-Pass-1!"
}'
```

### Get profile

`GET /api/v1/account/profile`

**Authentication:** Access token (`Authorization: Bearer …`) from the token endpoint

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/account/profile" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

### Update profile

`PATCH /api/v1/account/profile`

**Authentication:** Access token (`Authorization: Bearer …`) from the token endpoint

**Request body** (`application/json`, [UpdateProfileRequest](data-models.md#updateprofilerequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `displayName` | string | no |  |
| `familyName` | string | no |  |
| `givenName` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PATCH "$SECUREONE_URL/api/v1/account/profile" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "displayName": "Tenant A Shop",
  "familyName": "familyName",
  "givenName": "givenName"
}'
```

## Account MFA

Self-service passkey enrollment (session-authenticated)

### List enrolled MFA factors

`GET /api/v1/account/mfa/factors`

**Authentication:** Signed-in session cookie (from a sign-in call with `-c cookies.txt`)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/v1/account/mfa/factors" \
  -b cookies.txt
```

### Begin passkey registration

`POST /api/v1/account/mfa/passkey/register/options`

**Authentication:** Signed-in session cookie (from a sign-in call with `-c cookies.txt`)

**Request body** (`application/json`, [RegisterOptionsRequest](data-models.md#registeroptionsrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `label` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/mfa/passkey/register/options" \
  -b cookies.txt \
  -H "Content-Type: application/json" \
  --data '{
  "applicationId": "{applicationId}",
  "label": "Production"
}'
```

### Finish passkey registration

`POST /api/v1/account/mfa/passkey/register/verify`

**Authentication:** Signed-in session cookie (from a sign-in call with `-c cookies.txt`)

**Request body** (`application/json`, [RegisterVerifyRequest](data-models.md#registerverifyrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `credential` | string | yes |  |
| `requestId` | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/mfa/passkey/register/verify" \
  -b cookies.txt \
  -H "Content-Type: application/json" \
  --data '{
  "credential": "{credential}",
  "requestId": "{requestId}"
}'
```

### Remove a passkey

`DELETE /api/v1/account/mfa/passkey/{factorId}`

**Authentication:** Signed-in session cookie (from a sign-in call with `-c cookies.txt`)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `factorId` | path | string (uuid) | yes | MFA factor ID (UUID) |
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/v1/account/mfa/passkey/{factorId}" \
  -b cookies.txt
```

### Confirm TOTP enrollment

`POST /api/v1/account/mfa/totp/confirm`

**Authentication:** Signed-in session cookie (from a sign-in call with `-c cookies.txt`)

**Request body** (`application/json`, [ConfirmRequest](data-models.md#confirmrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `code` | string | yes |  |
| `factorId` | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/mfa/totp/confirm" \
  -b cookies.txt \
  -H "Content-Type: application/json" \
  --data '{
  "applicationId": "{applicationId}",
  "code": "123456",
  "factorId": "{factorId}"
}'
```

### Begin TOTP enrollment

`POST /api/v1/account/mfa/totp/enroll`

**Authentication:** Signed-in session cookie (from a sign-in call with `-c cookies.txt`)

**Request body** (`application/json`, [EnrollRequest](data-models.md#enrollrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `label` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/mfa/totp/enroll" \
  -b cookies.txt \
  -H "Content-Type: application/json" \
  --data '{
  "applicationId": "{applicationId}",
  "label": "Production"
}'
```

### Remove TOTP factor

`DELETE /api/v1/account/mfa/totp/{factorId}`

**Authentication:** Signed-in session cookie (from a sign-in call with `-c cookies.txt`)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `factorId` | path | string (uuid) | yes | MFA factor ID (UUID) |
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/v1/account/mfa/totp/{factorId}" \
  -b cookies.txt
```

## Account sessions

List and revoke browser sessions (session cookie)

### List active sessions

`GET /api/v1/account/sessions`

Returns Redis-backed sessions for the signed-in principal.

**Authentication:** Signed-in session cookie (from a sign-in call with `-c cookies.txt`)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/account/sessions" \
  -b cookies.txt
```

### Revoke other sessions

`DELETE /api/v1/account/sessions`

Invalidates all sessions except the one used for this request.

**Authentication:** Signed-in session cookie (from a sign-in call with `-c cookies.txt`)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/v1/account/sessions" \
  -b cookies.txt
```

### Revoke a session

`DELETE /api/v1/account/sessions/{sessionId}`

Invalidates the given session. Revoking the current session signs you out.

**Authentication:** Signed-in session cookie (from a sign-in call with `-c cookies.txt`)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `sessionId` | path | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/v1/account/sessions/{sessionId}" \
  -b cookies.txt
```

## Account privacy

GDPR-oriented self-service export and deletion request (Bearer access token)

### Delete privacy request

`POST /api/v1/account/privacy/delete-request`

**Authentication:** Access token (`Authorization: Bearer …`) from the token endpoint

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/privacy/delete-request" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

### Export my data (GDPR)

`GET /api/v1/account/privacy/export`

**Authentication:** Access token (`Authorization: Bearer …`) from the token endpoint

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/account/privacy/export" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

## Account privacy (session)

GDPR export and deletion via browser session cookie

### Request account deletion

`POST /api/v1/account/privacy/session/delete-request`

Disables the account and records a deletion request.

**Authentication:** Signed-in session cookie (from a sign-in call with `-c cookies.txt`)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/privacy/session/delete-request" \
  -b cookies.txt
```

### Export personal data

`GET /api/v1/account/privacy/session/export`

Session-authenticated GDPR export for hosted privacy portal.

**Authentication:** Signed-in session cookie (from a sign-in call with `-c cookies.txt`)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/account/privacy/session/export" \
  -b cookies.txt
```

## Account email change

Users change their sign-in email; the new address must be confirmed

### Change email (integrated apps, Bearer token)

`POST /api/v1/account/email/change`

**Authentication:** None (public)

**Request body** (`application/json`, [ChangeEmailRequest](data-models.md#changeemailrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `currentPassword` | string | no |  |
| `newEmail` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/email/change" \
  -H "Content-Type: application/json" \
  --data '{
  "applicationId": "{applicationId}",
  "currentPassword": "Choose-a-Strong-Pass-1!",
  "newEmail": "user@example.com"
}'
```

### Confirm the new email from the emailed link

`POST /api/v1/account/email/change/confirm`

**Authentication:** None (public)

**Request body** (`application/json`, [ConfirmRequest](data-models.md#confirmrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `code` | string | yes |  |
| `factorId` | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/email/change/confirm" \
  -H "Content-Type: application/json" \
  --data '{
  "applicationId": "{applicationId}",
  "code": "123456",
  "factorId": "{factorId}"
}'
```

### Change email (hosted Account security page, browser session)

`POST /api/v1/account/email/change/session`

**Authentication:** None (public)

**Request body** (`application/json`, [ChangeEmailRequest](data-models.md#changeemailrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `currentPassword` | string | no |  |
| `newEmail` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/email/change/session" \
  -H "Content-Type: application/json" \
  --data '{
  "applicationId": "{applicationId}",
  "currentPassword": "Choose-a-Strong-Pass-1!",
  "newEmail": "user@example.com"
}'
```

## Account connected apps

Third-party applications the user allowed, and withdrawing them

### List consents

`GET /api/v1/account/consents`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/v1/account/consents"
```

### Delete consent

`DELETE /api/v1/account/consents/{registeredClientId}`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `registeredClientId` | path | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/v1/account/consents/{registeredClientId}"
```

## Account password change

Users change their password on the hosted Account security page

### Change password (hosted Account security page, browser session)

`POST /api/v1/account/password/change/session`

Needs the current password. This browser stays signed in; the user's other browser sessions end.

**Authentication:** None (public)

**Request body** (`application/json`, [ChangePasswordRequest](data-models.md#changepasswordrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `currentPassword` | string | yes |  |
| `newPassword` | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/password/change/session" \
  -H "Content-Type: application/json" \
  --data '{
  "currentPassword": "Choose-a-Strong-Pass-1!",
  "newPassword": "Choose-a-Strong-Pass-1!"
}'
```

### Your names (hosted Account security page, browser session)

`GET /api/v1/account/profile/session`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/account/profile/session"
```

### Change your names (hosted Account security page, browser session)

`PATCH /api/v1/account/profile/session`

null keeps a name, an empty string clears it. Apps see the new names in the next tokens they get.

**Authentication:** None (public)

**Request body** (`application/json`, [UpdateNamesRequest](data-models.md#updatenamesrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `displayName` | string | no |  |
| `familyName` | string | no |  |
| `givenName` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PATCH "$SECUREONE_URL/api/v1/account/profile/session" \
  -H "Content-Type: application/json" \
  --data '{
  "displayName": "Tenant A Shop",
  "familyName": "familyName",
  "givenName": "givenName"
}'
```

## Account linked sign-in

External accounts (social, OIDC, SAML) a user signs in with (hosted Account security page)

### My linked sign-in accounts

`GET /api/v1/account/identities`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/v1/account/identities"
```

### Unlink one

`DELETE /api/v1/account/identities/{identityId}`

Refused (409) when it is the only way the account signs in.

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `identityId` | path | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/v1/account/identities/{identityId}"
```
