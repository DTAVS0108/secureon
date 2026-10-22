# 8. Admin API: users

[API reference](README.md) › Admin API: users

> Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.

User accounts across tenants: create, edit, suspend, delete, passwords, email verification, MFA reset, unlock and impersonation.

Set the variables from [Getting started](00-getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [8.1 Users](#81-users)
  - [POST `/api/admin/v1/applications/{applicationId}/users/two-step-statuses`](#811-two-step-status-of-several-of-an-applications-users) — Two-step status of several of an application's users
  - [GET `/api/admin/v1/applications/{applicationId}/users/{userId}/two-step`](#812-two-step-status-of-an-applications-user) — Two-step status of an application's user
  - [GET `/api/admin/v1/users`](#813-list-users) — List users
  - [POST `/api/admin/v1/users`](#814-create-user) — Create user
  - [GET `/api/admin/v1/users/{id}`](#815-get-user) — Get user
  - [PUT `/api/admin/v1/users/{id}`](#816-update-user) — Update user
  - [DELETE `/api/admin/v1/users/{id}`](#817-delete-user) — Delete user
  - [PATCH `/api/admin/v1/users/{id}/email-verification`](#818-update-email-verification) — Update email verification
  - [POST `/api/admin/v1/users/{id}/email/resend-verification`](#819-resend-email-verification) — Resend email verification
  - [POST `/api/admin/v1/users/{id}/email/verify`](#8110-verify-email) — Verify email
  - [POST `/api/admin/v1/users/{id}/impersonate`](#8111-impersonate-a-user-support) — Impersonate a user (support)
  - [POST `/api/admin/v1/users/{id}/mfa/reset`](#8112-reset-mfa) — Reset MFA
  - [POST `/api/admin/v1/users/{id}/password/remove`](#8113-remove-password) — Remove password
  - [POST `/api/admin/v1/users/{id}/password/reset-email`](#8114-reset-password-email) — Reset password email
  - [POST `/api/admin/v1/users/{id}/password/set`](#8115-set-password) — Set password
  - [POST `/api/admin/v1/users/{id}/password/set-password-email`](#8116-set-password-email) — Set password email
  - [PATCH `/api/admin/v1/users/{id}/status`](#8117-update-status) — Update status
  - [GET `/api/admin/v1/users/{id}/two-step`](#8118-two-step-status-of-a-user-options-without-secrets-deadline-exemption) — Two-step status of a user (options without secrets, deadline, exemption)
  - [POST `/api/admin/v1/users/{id}/two-step/exempt`](#8119-exempt-a-user-from-two-step-verification-until-a-date-reason-required) — Exempt a user from two-step verification until a date (reason required)
  - [DELETE `/api/admin/v1/users/{id}/two-step/exempt`](#8120-end-a-users-exemption) — End a user's exemption
  - [POST `/api/admin/v1/users/{id}/two-step/extend`](#8121-give-a-user-more-time-to-set-up-two-step-verification) — Give a user more time to set up two-step verification
  - [POST `/api/admin/v1/users/{id}/two-step/reset`](#8122-reset-a-users-two-step-verification-one-option-or-all-ends-their-sessions) — Reset a user's two-step verification (one option or all); ends their sessions
  - [POST `/api/admin/v1/users/{id}/unlock`](#8123-unlock-user) — Unlock user

## 8.1 Users

Two-step verification of a user

### 8.1.1 Two-step status of several of an application's users

`POST /api/admin/v1/applications/{applicationId}/users/two-step-statuses`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [StatusesRequest](13-data-models.md#statusesrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `userIds` | string (uuid)[] | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/two-step-statuses" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "userIds": [
    "{userId}"
  ]
}'
```

### 8.1.2 Two-step status of an application's user

`GET /api/admin/v1/applications/{applicationId}/users/{userId}/two-step`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}/two-step" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 8.1.3 List users

`GET /api/admin/v1/users`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | query | string (uuid) | no | Tenant ID (UUID) |
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |
| `adminOnly` | query | boolean | no |  |
| `search` | query | string | no | Text to search for |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserResponse](13-data-models.md#userresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/users" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 8.1.4 Create user

`POST /api/admin/v1/users`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [UserCreateRequest](13-data-models.md#usercreaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `email` | string (email) | yes |  |
| `firstName` | string | no |  |
| `lastName` | string | no |  |
| `roleIds` | string (uuid)[] | no |  |
| `status` | string | no |  |
| `tenantId` | string (uuid) | no |  |
| `username` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | [UserResponse](13-data-models.md#userresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/users" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "applicationId": "{applicationId}",
  "email": "user@example.com",
  "firstName": "Jane",
  "lastName": "Doe",
  "roleIds": [
    "{roleId}"
  ],
  "status": "ACTIVE",
  "tenantId": "{tenantId}",
  "username": "acme:user@example.com"
}'
```

### 8.1.5 Get user

`GET /api/admin/v1/users/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserResponse](13-data-models.md#userresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/users/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 8.1.6 Update user

`PUT /api/admin/v1/users/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`, [UserUpdateRequest](13-data-models.md#userupdaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | string (email) | yes |  |
| `emailVerified` | boolean | no |  |
| `firstName` | string | no |  |
| `lastName` | string | no |  |
| `roleIds` | string (uuid)[] | no |  |
| `status` | string | no |  |
| `username` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserResponse](13-data-models.md#userresponse) |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/users/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "email": "user@example.com",
  "emailVerified": true,
  "firstName": "Jane",
  "lastName": "Doe",
  "roleIds": [
    "{roleId}"
  ],
  "status": "ACTIVE",
  "username": "acme:user@example.com"
}'
```

### 8.1.7 Delete user

`DELETE /api/admin/v1/users/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/users/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 8.1.8 Update email verification

`PATCH /api/admin/v1/users/{id}/email-verification`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserResponse](13-data-models.md#userresponse) |

```bash
curl -s -X PATCH "$SECUREONE_URL/api/admin/v1/users/{id}/email-verification" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 8.1.9 Resend email verification

`POST /api/admin/v1/users/{id}/email/resend-verification`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `202` | Accepted | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/users/{id}/email/resend-verification" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 8.1.10 Verify email

`POST /api/admin/v1/users/{id}/email/verify`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserResponse](13-data-models.md#userresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/users/{id}/email/verify" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 8.1.11 Impersonate a user (support)

`POST /api/admin/v1/users/{id}/impersonate`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ImpersonationTokenResponse](13-data-models.md#impersonationtokenresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/users/{id}/impersonate" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 8.1.12 Reset MFA

`POST /api/admin/v1/users/{id}/mfa/reset`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/users/{id}/mfa/reset" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 8.1.13 Remove password

`POST /api/admin/v1/users/{id}/password/remove`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/users/{id}/password/remove" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 8.1.14 Reset password email

`POST /api/admin/v1/users/{id}/password/reset-email`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `202` | Accepted | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/users/{id}/password/reset-email" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 8.1.15 Set password

`POST /api/admin/v1/users/{id}/password/set`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`, [AdminSetPasswordRequest](13-data-models.md#adminsetpasswordrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `password` | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/users/{id}/password/set" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "password": "Choose-a-Strong-Pass-1!"
}'
```

### 8.1.16 Set password email

`POST /api/admin/v1/users/{id}/password/set-password-email`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `202` | Accepted | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/users/{id}/password/set-password-email" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 8.1.17 Update status

`PATCH /api/admin/v1/users/{id}/status`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserResponse](13-data-models.md#userresponse) |

```bash
curl -s -X PATCH "$SECUREONE_URL/api/admin/v1/users/{id}/status" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 8.1.18 Two-step status of a user (options without secrets, deadline, exemption)

`GET /api/admin/v1/users/{id}/two-step`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/users/{id}/two-step" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 8.1.19 Exempt a user from two-step verification until a date (reason required)

`POST /api/admin/v1/users/{id}/two-step/exempt`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`, [ExemptRequest](13-data-models.md#exemptrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `reason` | string | no |  |
| `until` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/users/{id}/two-step/exempt" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "reason": "Short explanation",
  "until": "until"
}'
```

### 8.1.20 End a user's exemption

`DELETE /api/admin/v1/users/{id}/two-step/exempt`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/users/{id}/two-step/exempt" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 8.1.21 Give a user more time to set up two-step verification

`POST /api/admin/v1/users/{id}/two-step/extend`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`, [ExtendRequest](13-data-models.md#extendrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `days` | integer (int32) | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/users/{id}/two-step/extend" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "applicationId": "{applicationId}",
  "days": 10
}'
```

### 8.1.22 Reset a user's two-step verification (one option or all); ends their sessions

`POST /api/admin/v1/users/{id}/two-step/reset`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`, [ResetRequest](13-data-models.md#resetrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `method` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/users/{id}/two-step/reset" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "applicationId": "{applicationId}",
  "method": "method"
}'
```

### 8.1.23 Unlock user

`POST /api/admin/v1/users/{id}/unlock`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserResponse](13-data-models.md#userresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/users/{id}/unlock" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```
