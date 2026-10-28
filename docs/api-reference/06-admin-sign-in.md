# 6. Admin API: sign-in, context and operator profile

[API reference](README.md) › Admin API: sign-in, context and operator profile

> Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.

Getting a console session token (sign-in plus MFA), the admin context (who am I, what can I open) and the signed-in operator's own profile and authenticators.

In Swagger UI (`/docs`), choose the definition **Admin — sign-in and operator** (`/v3/api-docs/admin-sign-in`).

Set the variables from [Getting started](00-getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [6.1 Auth](#61-auth)
  - [POST `/api/admin/v1/auth/login`](#611-sign-in-step-1-password) — Sign in (step 1: password)
  - [POST `/api/admin/v1/auth/logout`](#612-sign-out-end-the-console-session) — Sign out (end the console session)
  - [POST `/api/admin/v1/auth/mfa/enroll/confirm`](#613-sign-in-step-2-first-time-confirm-new-authenticator) — Sign in (step 2, first time: confirm new authenticator)
  - [POST `/api/admin/v1/auth/mfa/passkey/options`](#614-options-passkey) — Options passkey
  - [POST `/api/admin/v1/auth/mfa/passkey/verify`](#615-verify-passkey) — Verify passkey
  - [POST `/api/admin/v1/auth/mfa/verify`](#616-sign-in-step-2-authenticator-code) — Sign in (step 2: authenticator code)
- [6.2 Context](#62-context)
  - [GET `/api/admin/v1/context`](#621-get-admin-context-role-applications-console-switches) — Get admin context (role, applications, console switches)
  - [GET `/api/admin/v1/context/tenants`](#622-list-tenants) — List tenants
- [6.3 Operator profile](#63-operator-profile)
  - [GET `/api/admin/v1/me`](#631-get-the-signed-in-operator) — Get the signed-in operator
  - [PATCH `/api/admin/v1/me`](#632-update-me) — Update me
  - [POST `/api/admin/v1/me/password`](#633-password) — Password
  - [GET `/api/admin/v1/me/step-up`](#634-whether-the-operator-verified-recently-and-with-which-methods-they-can) — Whether the operator verified recently, and with which methods they can
  - [POST `/api/admin/v1/me/step-up`](#635-verify-its-you-with-an-authenticator-code-or-a-recovery-code) — Verify it's you with an authenticator code or a recovery code
  - [POST `/api/admin/v1/me/step-up/passkey/options`](#636-verify-its-you-with-a-passkey-or-security-key-options) — Verify it's you with a passkey or security key: options
  - [POST `/api/admin/v1/me/step-up/passkey/verify`](#637-verify-its-you-with-a-passkey-or-security-key) — Verify it's you with a passkey or security key
- [6.4 Operator MFA](#64-operator-mfa)
  - [GET `/api/admin/v1/me/mfa/factors`](#641-list-factors) — List factors
  - [POST `/api/admin/v1/me/mfa/passkey/register/options`](#642-start-passkey-registration) — Start passkey registration
  - [POST `/api/admin/v1/me/mfa/passkey/register/verify`](#643-finish-passkey-registration) — Finish passkey registration
  - [DELETE `/api/admin/v1/me/mfa/passkey/{factorId}`](#644-delete-passkey) — Delete passkey
  - [POST `/api/admin/v1/me/mfa/recovery-codes`](#645-recovery-mfa-codes) — Recovery MFA codes
  - [GET `/api/admin/v1/me/mfa/status`](#646-list-status) — List status
  - [POST `/api/admin/v1/me/mfa/totp/confirm`](#647-confirm-totp) — Confirm TOTP
  - [POST `/api/admin/v1/me/mfa/totp/enroll`](#648-enroll-totp) — Enroll TOTP
  - [DELETE `/api/admin/v1/me/mfa/totp/{factorId}`](#649-delete-totp) — Delete TOTP

## 6.1 Auth

Admin console sign-in: password, then authenticator code

### 6.1.1 Sign in (step 1: password)

`POST /api/admin/v1/auth/login`

**Authentication:** None (public)

**Request body** (`application/json`, [AdminLoginRequest](13-data-models.md#adminloginrequest))

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

### 6.1.2 Sign out (end the console session)

`POST /api/admin/v1/auth/logout`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | — |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/auth/logout" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 6.1.3 Sign in (step 2, first time: confirm new authenticator)

`POST /api/admin/v1/auth/mfa/enroll/confirm`

**Authentication:** None (public)

**Request body** (`application/json`, [MfaEnrollConfirmRequest](13-data-models.md#mfaenrollconfirmrequest))

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

### 6.1.4 Options passkey

`POST /api/admin/v1/auth/mfa/passkey/options`

**Authentication:** None (public)

**Request body** (`application/json`, [PasskeyStepRequest](13-data-models.md#passkeysteprequest))

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

### 6.1.5 Verify passkey

`POST /api/admin/v1/auth/mfa/passkey/verify`

**Authentication:** None (public)

**Request body** (`application/json`, [PasskeyStepVerifyRequest](13-data-models.md#passkeystepverifyrequest))

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

### 6.1.6 Sign in (step 2: authenticator code)

`POST /api/admin/v1/auth/mfa/verify`

**Authentication:** None (public)

**Request body** (`application/json`, [MfaVerifyRequest](13-data-models.md#mfaverifyrequest))

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

## 6.2 Context

Current operator, act-as, accessible tenants and applications

### 6.2.1 Get admin context (role, applications, console switches)

`GET /api/admin/v1/context`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [AdminContextResponse](13-data-models.md#admincontextresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/context" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 6.2.2 List tenants

`GET /api/admin/v1/context/tenants`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

## 6.3 Operator profile

Signed-in operator: verify it's you again

### 6.3.1 Get the signed-in operator

`GET /api/admin/v1/me`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [OperatorProfileResponse](13-data-models.md#operatorprofileresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/me" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 6.3.2 Update me

`PATCH /api/admin/v1/me`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [OperatorProfileUpdateRequest](13-data-models.md#operatorprofileupdaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `displayName` | string | no |  |
| `username` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [OperatorProfileResponse](13-data-models.md#operatorprofileresponse) |

```bash
curl -s -X PATCH "$SECUREONE_URL/api/admin/v1/me" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "displayName": "Tenant A Shop",
  "username": "tenant-a:user@example.com"
}'
```

### 6.3.3 Password

`POST /api/admin/v1/me/password`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [OperatorPasswordChangeRequest](13-data-models.md#operatorpasswordchangerequest))

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

### 6.3.4 Whether the operator verified recently, and with which methods they can

`GET /api/admin/v1/me/step-up`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/me/step-up" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 6.3.5 Verify it's you with an authenticator code or a recovery code

`POST /api/admin/v1/me/step-up`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [StepUpRequest](13-data-models.md#stepuprequest))

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

### 6.3.6 Verify it's you with a passkey or security key: options

`POST /api/admin/v1/me/step-up/passkey/options`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/me/step-up/passkey/options" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 6.3.7 Verify it's you with a passkey or security key

`POST /api/admin/v1/me/step-up/passkey/verify`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [PasskeyStepUpRequest](13-data-models.md#passkeystepuprequest))

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

## 6.4 Operator MFA

Tenant operator self-service TOTP and passkey enrollment

### 6.4.1 List factors

`GET /api/admin/v1/me/mfa/factors`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/me/mfa/factors" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 6.4.2 Start passkey registration

`POST /api/admin/v1/me/mfa/passkey/register/options`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [PasskeyRegisterOptionsRequest](13-data-models.md#passkeyregisteroptionsrequest))

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

### 6.4.3 Finish passkey registration

`POST /api/admin/v1/me/mfa/passkey/register/verify`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [PasskeyRegisterVerifyRequest](13-data-models.md#passkeyregisterverifyrequest))

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

### 6.4.4 Delete passkey

`DELETE /api/admin/v1/me/mfa/passkey/{factorId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 6.4.5 Recovery MFA codes

`POST /api/admin/v1/me/mfa/recovery-codes`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/me/mfa/recovery-codes" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 6.4.6 List status

`GET /api/admin/v1/me/mfa/status`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/me/mfa/status" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 6.4.7 Confirm TOTP

`POST /api/admin/v1/me/mfa/totp/confirm`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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
curl -s -X POST "$SECUREONE_URL/api/admin/v1/me/mfa/totp/confirm" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "code": "123456",
  "factorId": "{factorId}"
}'
```

### 6.4.8 Enroll TOTP

`POST /api/admin/v1/me/mfa/totp/enroll`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [TotpEnrollRequest](13-data-models.md#totpenrollrequest))

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

### 6.4.9 Delete TOTP

`DELETE /api/admin/v1/me/mfa/totp/{factorId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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
