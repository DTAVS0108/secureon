# 4. Account self-service

[API reference](README.md) › Account self-service

> Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.

What a signed-in user can do for themselves: password reset and change, email verification, profile, MFA factors, sessions, email change, connected apps and privacy (export, delete request).

Set the variables from [Getting started](00-getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [4.1 Account](#41-account)
  - [GET `/api/v1/account/2fa`](#411-two-step-status-options-backup-codes-remembered-devices-requirement-and-deadline) — Two-step status: options, backup codes, remembered devices, requirement and deadline
  - [POST `/api/v1/account/2fa/backup-codes`](#412-new-backup-codes-the-old-ones-stop-working) — New backup codes (the old ones stop working)
  - [DELETE `/api/v1/account/2fa/devices`](#413-forget-all-remembered-devices) — Forget all remembered devices
  - [DELETE `/api/v1/account/2fa/devices/{deviceId}`](#414-forget-a-remembered-device-it-will-ask-for-the-second-step-again) — Forget a remembered device (it will ask for the second step again)
  - [POST `/api/v1/account/2fa/methods/code/confirm`](#415-add-a-text-message-or-email-option-confirm-the-code) — Add a text message or email option: confirm the code
  - [POST `/api/v1/account/2fa/methods/code/send`](#416-add-a-text-message-or-email-option-send-a-code) — Add a text message or email option: send a code
  - [POST `/api/v1/account/2fa/methods/totp/confirm`](#417-add-an-authenticator-app-confirm-the-code) — Add an authenticator app: confirm the code
  - [POST `/api/v1/account/2fa/methods/totp/start`](#418-add-an-authenticator-app-qr-code) — Add an authenticator app: QR code
  - [POST `/api/v1/account/2fa/methods/webauthn/options`](#419-add-a-passkey-or-security-key-registration-options) — Add a passkey or security key: registration options
  - [POST `/api/v1/account/2fa/methods/webauthn/verify`](#4110-add-a-passkey-or-security-key-register-it) — Add a passkey or security key: register it
  - [DELETE `/api/v1/account/2fa/methods/{factorId}`](#4111-remove-an-option) — Remove an option
  - [POST `/api/v1/account/2fa/methods/{factorId}/default`](#4112-make-an-option-the-one-asked-for-first) — Make an option the one asked for first
  - [POST `/api/v1/account/2fa/off`](#4113-turn-two-step-verification-off-only-when-it-is-optional) — Turn two-step verification off (only when it is optional)
  - [POST `/api/v1/account/2fa/verify`](#4114-verify-its-you-authenticator-sent-code-passkey-or-backup-code-for-sensitive-changes) — Verify it's you (authenticator, sent code, passkey or backup code) for sensitive changes
  - [POST `/api/v1/account/2fa/verify/send`](#4115-send-a-code-text-message-or-email-option-to-verify-its-you) — Send a code (text message or email option) to verify it's you
  - [POST `/api/v1/account/2fa/verify/webauthn/options`](#4116-passkey-or-security-key-to-verify-its-you-options) — Passkey or security key to verify it's you: options
  - [POST `/api/v1/account/email/resend-verification`](#4117-resend-email-verification) — Resend email verification
  - [GET `/api/v1/account/email/verify`](#4118-verify-email) — Verify email
  - [POST `/api/v1/account/email/verify`](#4119-verify-email) — Verify email
  - [POST `/api/v1/account/magic-link/request`](#4120-request-magic-link) — Request magic link
  - [GET `/api/v1/account/magic-link/verify`](#4121-open-a-magic-link-browser-to-the-second-step-when-needed-else-signed-in) — Open a magic link (browser): to the second step when needed, else signed in
  - [POST `/api/v1/account/magic-link/verify`](#4122-verify-a-magic-link-api-200-signed-in-428-second-step-403-mfa-required) — Verify a magic link (API): 200 signed in, 428 second step, 403 MFA required
  - [POST `/api/v1/account/password/forgot`](#4123-start-password-reset-email-link-or-code) — Start password reset (email link or code)
  - [POST `/api/v1/account/password/reset`](#4124-reset-password-with-the-emailed-link-token) — Reset password with the emailed link token
  - [POST `/api/v1/account/password/reset/otp`](#4125-reset-password-with-a-one-time-code) — Reset password with a one-time code
  - [POST `/api/v1/account/set-password`](#4126-set-password) — Set password
  - [GET `/api/v1/account/verify/options`](#4127-list-verification-options) — List verification options
  - [POST `/api/v1/account/verify/otp/confirm`](#4128-confirm-otp) — Confirm OTP
- [4.2 Account profile](#42-account-profile)
  - [POST `/api/v1/account/password/change`](#421-change-password) — Change password
  - [GET `/api/v1/account/profile`](#422-get-profile) — Get profile
  - [PATCH `/api/v1/account/profile`](#423-update-profile) — Update profile
- [4.3 Account MFA](#43-account-mfa)
  - [GET `/api/v1/account/mfa/factors`](#431-list-enrolled-mfa-factors) — List enrolled MFA factors
  - [POST `/api/v1/account/mfa/passkey/register/options`](#432-begin-passkey-registration) — Begin passkey registration
  - [POST `/api/v1/account/mfa/passkey/register/verify`](#433-finish-passkey-registration) — Finish passkey registration
  - [DELETE `/api/v1/account/mfa/passkey/{factorId}`](#434-remove-a-passkey) — Remove a passkey
  - [POST `/api/v1/account/mfa/totp/confirm`](#435-confirm-totp-enrollment) — Confirm TOTP enrollment
  - [POST `/api/v1/account/mfa/totp/enroll`](#436-begin-totp-enrollment) — Begin TOTP enrollment
  - [DELETE `/api/v1/account/mfa/totp/{factorId}`](#437-remove-totp-factor) — Remove TOTP factor
- [4.4 Account sessions](#44-account-sessions)
  - [GET `/api/v1/account/sessions`](#441-list-active-sessions) — List active sessions
  - [DELETE `/api/v1/account/sessions`](#442-revoke-other-sessions) — Revoke other sessions
  - [DELETE `/api/v1/account/sessions/{sessionId}`](#443-revoke-a-session) — Revoke a session
- [4.5 Account privacy](#45-account-privacy)
  - [POST `/api/v1/account/privacy/delete-request`](#451-delete-privacy-request) — Delete privacy request
  - [GET `/api/v1/account/privacy/export`](#452-export-my-data-gdpr) — Export my data (GDPR)
- [4.6 Account privacy (session)](#46-account-privacy-session)
  - [POST `/api/v1/account/privacy/session/delete-request`](#461-request-account-deletion) — Request account deletion
  - [GET `/api/v1/account/privacy/session/export`](#462-export-personal-data) — Export personal data
- [4.7 Account email change](#47-account-email-change)
  - [POST `/api/v1/account/email/change`](#471-change-email-integrated-apps-bearer-token) — Change email (integrated apps, Bearer token)
  - [POST `/api/v1/account/email/change/confirm`](#472-confirm-the-new-email-from-the-emailed-link) — Confirm the new email from the emailed link
  - [POST `/api/v1/account/email/change/session`](#473-change-email-hosted-account-security-page-browser-session) — Change email (hosted Account security page, browser session)
- [4.8 Account connected apps](#48-account-connected-apps)
  - [GET `/api/v1/account/consents`](#481-list-consents) — List consents
  - [DELETE `/api/v1/account/consents/{registeredClientId}`](#482-delete-consent) — Delete consent
- [4.9 Account password change](#49-account-password-change)
  - [POST `/api/v1/account/password/change/session`](#491-change-password-hosted-account-security-page-browser-session) — Change password (hosted Account security page, browser session)
  - [GET `/api/v1/account/profile/session`](#492-your-names-hosted-account-security-page-browser-session) — Your names (hosted Account security page, browser session)
  - [PATCH `/api/v1/account/profile/session`](#493-change-your-names-hosted-account-security-page-browser-session) — Change your names (hosted Account security page, browser session)
- [4.10 Account linked sign-in](#410-account-linked-sign-in)
  - [GET `/api/v1/account/identities`](#4101-my-linked-sign-in-accounts) — My linked sign-in accounts
  - [DELETE `/api/v1/account/identities/{identityId}`](#4102-unlink-one) — Unlink one

## 4.1 Account

Two-step verification for the signed-in user

### 4.1.1 Two-step status: options, backup codes, remembered devices, requirement and deadline

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

### 4.1.2 New backup codes (the old ones stop working)

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

### 4.1.3 Forget all remembered devices

`DELETE /api/v1/account/2fa/devices`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/v1/account/2fa/devices"
```

### 4.1.4 Forget a remembered device (it will ask for the second step again)

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

### 4.1.5 Add a text message or email option: confirm the code

`POST /api/v1/account/2fa/methods/code/confirm`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Request body** (`application/json`, [CodeConfirmRequest](13-data-models.md#codeconfirmrequest))

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

### 4.1.6 Add a text message or email option: send a code

`POST /api/v1/account/2fa/methods/code/send`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Request body** (`application/json`, [CodeSendRequest](13-data-models.md#codesendrequest))

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

### 4.1.7 Add an authenticator app: confirm the code

`POST /api/v1/account/2fa/methods/totp/confirm`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Request body** (`application/json`, [TotpConfirmRequest](13-data-models.md#totpconfirmrequest))

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

### 4.1.8 Add an authenticator app: QR code

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

### 4.1.9 Add a passkey or security key: registration options

`POST /api/v1/account/2fa/methods/webauthn/options`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Request body** (`application/json`, [WebAuthnStartRequest](13-data-models.md#webauthnstartrequest))

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

### 4.1.10 Add a passkey or security key: register it

`POST /api/v1/account/2fa/methods/webauthn/verify`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Request body** (`application/json`, [WebAuthnFinishRequest](13-data-models.md#webauthnfinishrequest))

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

### 4.1.11 Remove an option

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

### 4.1.12 Make an option the one asked for first

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

### 4.1.13 Turn two-step verification off (only when it is optional)

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

### 4.1.14 Verify it's you (authenticator, sent code, passkey or backup code) for sensitive changes

`POST /api/v1/account/2fa/verify`

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
curl -s -X POST "$SECUREONE_URL/api/v1/account/2fa/verify" \
  -H "Content-Type: application/json" \
  --data '{
  "code": "123456"
}'
```

### 4.1.15 Send a code (text message or email option) to verify it's you

`POST /api/v1/account/2fa/verify/send`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Request body** (`application/json`, [SendRequest](13-data-models.md#sendrequest))

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

### 4.1.16 Passkey or security key to verify it's you: options

`POST /api/v1/account/2fa/verify/webauthn/options`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/account/2fa/verify/webauthn/options"
```

### 4.1.17 Resend email verification

`POST /api/v1/account/email/resend-verification`

**Authentication:** None (public)

**Request body** (`application/json`, [TenantEmailRequest](13-data-models.md#tenantemailrequest))

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
  "tenantSlug": "acme"
}'
```

### 4.1.18 Verify email

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

### 4.1.19 Verify email

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

### 4.1.20 Request magic link

`POST /api/v1/account/magic-link/request`

**Authentication:** None (public)

**Request body** (`application/json`, [TenantEmailRequest](13-data-models.md#tenantemailrequest))

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
  "tenantSlug": "acme"
}'
```

### 4.1.21 Open a magic link (browser): to the second step when needed, else signed in

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

### 4.1.22 Verify a magic link (API): 200 signed in, 428 second step, 403 MFA required

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

### 4.1.23 Start password reset (email link or code)

`POST /api/v1/account/password/forgot`

**Authentication:** None (public)

**Request body** (`application/json`, [TenantEmailRequest](13-data-models.md#tenantemailrequest))

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
  "tenantSlug": "acme"
}'
```

### 4.1.24 Reset password with the emailed link token

`POST /api/v1/account/password/reset`

**Authentication:** None (public)

**Request body** (`application/json`, [ResetPasswordRequest](13-data-models.md#resetpasswordrequest))

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

### 4.1.25 Reset password with a one-time code

`POST /api/v1/account/password/reset/otp`

**Authentication:** None (public)

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
curl -s -X POST "$SECUREONE_URL/api/v1/account/password/reset/otp" \
  -H "Content-Type: application/json" \
  --data '{
  "challengeId": "{challengeId}",
  "code": "123456",
  "password": "Choose-a-Strong-Pass-1!"
}'
```

### 4.1.26 Set password

`POST /api/v1/account/set-password`

**Authentication:** None (public)

**Request body** (`application/json`, [ResetPasswordRequest](13-data-models.md#resetpasswordrequest))

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

### 4.1.27 List verification options

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

### 4.1.28 Confirm OTP

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

## 4.2 Account profile

Authenticated end-user profile and password change (Bearer access token)

### 4.2.1 Change password

`POST /api/v1/account/password/change`

**Authentication:** Access token (`Authorization: Bearer …`) from the token endpoint

**Request body** (`application/json`, [ChangePasswordRequest](13-data-models.md#changepasswordrequest))

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

### 4.2.2 Get profile

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

### 4.2.3 Update profile

`PATCH /api/v1/account/profile`

**Authentication:** Access token (`Authorization: Bearer …`) from the token endpoint

**Request body** (`application/json`, [UpdateProfileRequest](13-data-models.md#updateprofilerequest))

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
  "displayName": "Acme Shop",
  "familyName": "familyName",
  "givenName": "givenName"
}'
```

## 4.3 Account MFA

Self-service TOTP enrollment (session-authenticated)

### 4.3.1 List enrolled MFA factors

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

### 4.3.2 Begin passkey registration

`POST /api/v1/account/mfa/passkey/register/options`

**Authentication:** Signed-in session cookie (from a sign-in call with `-c cookies.txt`)

**Request body** (`application/json`, [RegisterOptionsRequest](13-data-models.md#registeroptionsrequest))

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

### 4.3.3 Finish passkey registration

`POST /api/v1/account/mfa/passkey/register/verify`

**Authentication:** Signed-in session cookie (from a sign-in call with `-c cookies.txt`)

**Request body** (`application/json`, [RegisterVerifyRequest](13-data-models.md#registerverifyrequest))

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

### 4.3.4 Remove a passkey

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

### 4.3.5 Confirm TOTP enrollment

`POST /api/v1/account/mfa/totp/confirm`

**Authentication:** Signed-in session cookie (from a sign-in call with `-c cookies.txt`)

**Request body** (`application/json`, [ConfirmRequest](13-data-models.md#confirmrequest))

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

### 4.3.6 Begin TOTP enrollment

`POST /api/v1/account/mfa/totp/enroll`

**Authentication:** Signed-in session cookie (from a sign-in call with `-c cookies.txt`)

**Request body** (`application/json`, [EnrollRequest](13-data-models.md#enrollrequest))

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

### 4.3.7 Remove TOTP factor

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

## 4.4 Account sessions

List and revoke browser sessions (session cookie)

### 4.4.1 List active sessions

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

### 4.4.2 Revoke other sessions

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

### 4.4.3 Revoke a session

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

## 4.5 Account privacy

GDPR-oriented self-service export and deletion request (Bearer access token)

### 4.5.1 Delete privacy request

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

### 4.5.2 Export my data (GDPR)

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

## 4.6 Account privacy (session)

GDPR export and deletion via browser session cookie

### 4.6.1 Request account deletion

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

### 4.6.2 Export personal data

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

## 4.7 Account email change

Users change their sign-in email; the new address must be confirmed

### 4.7.1 Change email (integrated apps, Bearer token)

`POST /api/v1/account/email/change`

**Authentication:** None (public)

**Request body** (`application/json`, [ChangeEmailRequest](13-data-models.md#changeemailrequest))

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

### 4.7.2 Confirm the new email from the emailed link

`POST /api/v1/account/email/change/confirm`

**Authentication:** None (public)

**Request body** (`application/json`, [ConfirmRequest](13-data-models.md#confirmrequest))

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

### 4.7.3 Change email (hosted Account security page, browser session)

`POST /api/v1/account/email/change/session`

**Authentication:** None (public)

**Request body** (`application/json`, [ChangeEmailRequest](13-data-models.md#changeemailrequest))

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

## 4.8 Account connected apps

Third-party applications the user allowed, and withdrawing them

### 4.8.1 List consents

`GET /api/v1/account/consents`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/v1/account/consents"
```

### 4.8.2 Delete consent

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

## 4.9 Account password change

Users change their password on the hosted Account security page

### 4.9.1 Change password (hosted Account security page, browser session)

`POST /api/v1/account/password/change/session`

Needs the current password. This browser stays signed in; the user's other browser sessions end.

**Authentication:** None (public)

**Request body** (`application/json`, [ChangePasswordRequest](13-data-models.md#changepasswordrequest))

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

### 4.9.2 Your names (hosted Account security page, browser session)

`GET /api/v1/account/profile/session`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/account/profile/session"
```

### 4.9.3 Change your names (hosted Account security page, browser session)

`PATCH /api/v1/account/profile/session`

null keeps a name, an empty string clears it. Apps see the new names in the next tokens they get.

**Authentication:** None (public)

**Request body** (`application/json`, [UpdateNamesRequest](13-data-models.md#updatenamesrequest))

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
  "displayName": "Acme Shop",
  "familyName": "familyName",
  "givenName": "givenName"
}'
```

## 4.10 Account linked sign-in

The Google, SAML… accounts a user signs in with (hosted Account security page)

### 4.10.1 My linked sign-in accounts

`GET /api/v1/account/identities`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/v1/account/identities"
```

### 4.10.2 Unlink one

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
