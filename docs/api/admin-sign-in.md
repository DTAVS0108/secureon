---
title: Admin API: sign-in and operator
summary: Getting a console session token (sign-in plus MFA), the admin context (who am I, what can I open) and the signed-in operator's own profile and authenticators.
order: 8
audience: public
---

# Admin API: sign-in and operator

[API reference](index.md) › Admin API: sign-in and operator

> Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.

Getting a console session token (sign-in plus MFA), the admin context (who am I, what can I open) and the signed-in operator's own profile and authenticators.

In Swagger UI (`/docs`), choose the definition **Admin — sign-in and operator** (`/v3/api-docs/admin-sign-in`).

Set the variables from [Getting started](getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [Auth](#auth)
  - [POST `/api/admin/v1/auth/login`](#sign-in-step-1-password) — Sign in (step 1: password)
  - [POST `/api/admin/v1/auth/logout`](#sign-out-end-the-console-session) — Sign out (end the console session)
  - [POST `/api/admin/v1/auth/mfa/enroll/confirm`](#sign-in-step-2-first-time-confirm-new-authenticator) — Sign in (step 2, first time: confirm new authenticator)
  - [POST `/api/admin/v1/auth/mfa/passkey/options`](#options-passkey) — Options passkey
  - [POST `/api/admin/v1/auth/mfa/passkey/verify`](#verify-passkey) — Verify passkey
  - [POST `/api/admin/v1/auth/mfa/verify`](#sign-in-step-2-authenticator-code) — Sign in (step 2: authenticator code)
- [Context](#context)
  - [GET `/api/admin/v1/context`](#get-admin-context-role-applications-console-switches) — Get admin context (role, applications, console switches)
  - [GET `/api/admin/v1/context/tenants`](#list-tenants) — List tenants
- [Operator profile](#operator-profile)
  - [GET `/api/admin/v1/me`](#get-the-signed-in-operator) — Get the signed-in operator
  - [PATCH `/api/admin/v1/me`](#update-me) — Update me
  - [GET `/api/admin/v1/me/avatar`](#my-profile-picture) — My profile picture
  - [PUT `/api/admin/v1/me/avatar`](#set-my-profile-picture) — Set my profile picture
  - [DELETE `/api/admin/v1/me/avatar`](#remove-my-profile-picture) — Remove my profile picture
  - [POST `/api/admin/v1/me/password`](#password) — Password
  - [GET `/api/admin/v1/me/step-up`](#whether-the-operator-verified-recently-and-with-which-methods-they-can) — Whether the operator verified recently, and with which methods they can
  - [POST `/api/admin/v1/me/step-up`](#verify-its-you-with-an-authenticator-code-or-a-recovery-code) — Verify it's you with an authenticator code or a recovery code
  - [POST `/api/admin/v1/me/step-up/passkey/options`](#verify-its-you-with-a-passkey-or-security-key-options) — Verify it's you with a passkey or security key: options
  - [POST `/api/admin/v1/me/step-up/passkey/verify`](#verify-its-you-with-a-passkey-or-security-key) — Verify it's you with a passkey or security key
- [Operator MFA](#operator-mfa)
  - [GET `/api/admin/v1/me/mfa/factors`](#list-factors) — List factors
  - [POST `/api/admin/v1/me/mfa/passkey/register/options`](#start-passkey-registration) — Start passkey registration
  - [POST `/api/admin/v1/me/mfa/passkey/register/verify`](#finish-passkey-registration) — Finish passkey registration
  - [DELETE `/api/admin/v1/me/mfa/passkey/{factorId}`](#delete-passkey) — Delete passkey
  - [POST `/api/admin/v1/me/mfa/recovery-codes`](#recovery-mfa-codes) — Recovery MFA codes
  - [GET `/api/admin/v1/me/mfa/status`](#list-status) — List status
  - [POST `/api/admin/v1/me/mfa/totp/confirm`](#confirm-totp) — Confirm TOTP
  - [POST `/api/admin/v1/me/mfa/totp/enroll`](#enroll-totp) — Enroll TOTP
  - [DELETE `/api/admin/v1/me/mfa/totp/{factorId}`](#delete-totp) — Delete TOTP

## Auth

Admin console sign-in: password, then authenticator code

### Sign in (step 1: password)

`POST /api/admin/v1/auth/login`

**Authentication:** None (public)

**Request body** (`application/json`, [AdminLoginRequest](data-models.md#adminloginrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `emailOrUsername` | string | no |  |
| `password` | string | no |  |
| `tenantSlug` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/auth/login" \
  -H "Content-Type: application/json" \
  --data '{
  "emailOrUsername": "admin@example.com",
  "password": "Choose-a-Strong-Pass-1!",
  "tenantSlug": "tenant-a"
}'
```

### Sign out (end the console session)

`POST /api/admin/v1/auth/logout`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | — |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/auth/logout" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Sign in (step 2, first time: confirm new authenticator)

`POST /api/admin/v1/auth/mfa/enroll/confirm`

**Authentication:** None (public)

**Request body** (`application/json`, [MfaEnrollConfirmRequest](data-models.md#mfaenrollconfirmrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | no |  |
| `factorId` | string | no |  |
| `mfaToken` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/auth/mfa/enroll/confirm" \
  -H "Content-Type: application/json" \
  --data '{
  "code": "123456",
  "factorId": "{factorId}",
  "mfaToken": "{mfaToken}"
}'
```

### Options passkey

`POST /api/admin/v1/auth/mfa/passkey/options`

**Authentication:** None (public)

**Request body** (`application/json`, [PasskeyStepRequest](data-models.md#passkeysteprequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `mfaToken` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/auth/mfa/passkey/options" \
  -H "Content-Type: application/json" \
  --data '{
  "mfaToken": "{mfaToken}"
}'
```

### Verify passkey

`POST /api/admin/v1/auth/mfa/passkey/verify`

**Authentication:** None (public)

**Request body** (`application/json`, [PasskeyStepVerifyRequest](data-models.md#passkeystepverifyrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `credential` | string | no |  |
| `mfaToken` | string | no |  |
| `requestId` | string (uuid) | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/auth/mfa/passkey/verify" \
  -H "Content-Type: application/json" \
  --data '{
  "credential": "{credential}",
  "mfaToken": "{mfaToken}",
  "requestId": "{requestId}"
}'
```

### Sign in (step 2: authenticator code)

`POST /api/admin/v1/auth/mfa/verify`

**Authentication:** None (public)

**Request body** (`application/json`, [MfaVerifyRequest](data-models.md#mfaverifyrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | no |  |
| `mfaToken` | string | no |  |
| `recoveryCode` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/auth/mfa/verify" \
  -H "Content-Type: application/json" \
  --data '{
  "code": "123456",
  "mfaToken": "{mfaToken}",
  "recoveryCode": "123456"
}'
```

## Context

Current operator, act-as, accessible tenants and applications

### Get admin context (role, applications, console switches)

`GET /api/admin/v1/context`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [AdminContextResponse](data-models.md#admincontextresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/context" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List tenants

`GET /api/admin/v1/context/tenants`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/context/tenants?applicationId={applicationId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## Operator profile

Signed-in operator: verify it's you again

### Get the signed-in operator

`GET /api/admin/v1/me`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [OperatorProfileResponse](data-models.md#operatorprofileresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/me" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update me

`PATCH /api/admin/v1/me`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [OperatorProfileUpdateRequest](data-models.md#operatorprofileupdaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `displayName` | string | no |  |
| `username` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [OperatorProfileResponse](data-models.md#operatorprofileresponse) |

```bash
curl -s -X PATCH "$SECUREONE_URL/api/admin/v1/me" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "displayName": "Tenant A Shop",
  "username": "tenant-a:user@example.com"
}'
```

### My profile picture

`GET /api/admin/v1/me/avatar`

PNG, JPEG or WebP; 404 when none is set.

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | string |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/me/avatar" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Set my profile picture

`PUT /api/admin/v1/me/avatar`

Multipart field `file`: PNG, JPEG or WebP, at most 512 KB (checked by content).

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`multipart/form-data`)

| Field | Type | Required | Description |
|---|---|---|---|
| `file` | string (binary) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [OperatorProfileResponse](data-models.md#operatorprofileresponse) |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/me/avatar" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -F "file=@users.csv"
```

### Remove my profile picture

`DELETE /api/admin/v1/me/avatar`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [OperatorProfileResponse](data-models.md#operatorprofileresponse) |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/me/avatar" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Password

`POST /api/admin/v1/me/password`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [OperatorPasswordChangeRequest](data-models.md#operatorpasswordchangerequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `currentPassword` | string | yes |  |
| `newPassword` | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/me/password" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "currentPassword": "Choose-a-Strong-Pass-1!",
  "newPassword": "Choose-a-Strong-Pass-1!"
}'
```

### Whether the operator verified recently, and with which methods they can

`GET /api/admin/v1/me/step-up`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/me/step-up" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Verify it's you with an authenticator code or a recovery code

`POST /api/admin/v1/me/step-up`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [StepUpRequest](data-models.md#stepuprequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | no |  |
| `recoveryCode` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/me/step-up" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "code": "123456",
  "recoveryCode": "123456"
}'
```

### Verify it's you with a passkey or security key: options

`POST /api/admin/v1/me/step-up/passkey/options`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/me/step-up/passkey/options" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Verify it's you with a passkey or security key

`POST /api/admin/v1/me/step-up/passkey/verify`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [PasskeyStepUpRequest](data-models.md#passkeystepuprequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `credential` | string | no |  |
| `requestId` | string (uuid) | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/me/step-up/passkey/verify" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "credential": "{credential}",
  "requestId": "{requestId}"
}'
```

## Operator MFA

Tenant operator self-service TOTP and passkey enrollment

### List factors

`GET /api/admin/v1/me/mfa/factors`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/me/mfa/factors" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Start passkey registration

`POST /api/admin/v1/me/mfa/passkey/register/options`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [PasskeyRegisterOptionsRequest](data-models.md#passkeyregisteroptionsrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `label` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/me/mfa/passkey/register/options" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "label": "Production"
}'
```

### Finish passkey registration

`POST /api/admin/v1/me/mfa/passkey/register/verify`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [PasskeyRegisterVerifyRequest](data-models.md#passkeyregisterverifyrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `credential` | string | yes |  |
| `requestId` | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/me/mfa/passkey/register/verify" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "credential": "{credential}",
  "requestId": "{requestId}"
}'
```

### Delete passkey

`DELETE /api/admin/v1/me/mfa/passkey/{factorId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `factorId` | path | string (uuid) | yes | MFA factor ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/me/mfa/passkey/{factorId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Recovery MFA codes

`POST /api/admin/v1/me/mfa/recovery-codes`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/me/mfa/recovery-codes" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List status

`GET /api/admin/v1/me/mfa/status`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/me/mfa/status" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Confirm TOTP

`POST /api/admin/v1/me/mfa/totp/confirm`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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
curl -s -X POST "$SECUREONE_URL/api/admin/v1/me/mfa/totp/confirm" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "code": "123456",
  "factorId": "{factorId}"
}'
```

### Enroll TOTP

`POST /api/admin/v1/me/mfa/totp/enroll`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [TotpEnrollRequest](data-models.md#totpenrollrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `label` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/me/mfa/totp/enroll" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "label": "Production"
}'
```

### Delete TOTP

`DELETE /api/admin/v1/me/mfa/totp/{factorId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `factorId` | path | string (uuid) | yes | MFA factor ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/me/mfa/totp/{factorId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```
