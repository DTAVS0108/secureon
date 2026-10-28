# 8. Admin API: users

[API reference](README.md) › Admin API: users

> Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.

User accounts across tenants: create, edit, suspend, delete, passwords, email verification, MFA reset, unlock and impersonation.

In Swagger UI (`/docs`), choose the definition **Admin — tenants and users** (`/v3/api-docs/admin-tenants`).

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
  - [GET `/api/admin/v1/users/{id}/attributes`](#818-the-users-profile-attributes) — The user's profile attributes
  - [PUT `/api/admin/v1/users/{id}/attributes`](#819-replace-the-users-profile-attributes) — Replace the user's profile attributes
  - [PATCH `/api/admin/v1/users/{id}/email-verification`](#8110-update-email-verification) — Update email verification
  - [POST `/api/admin/v1/users/{id}/email/resend-verification`](#8111-resend-email-verification) — Resend email verification
  - [POST `/api/admin/v1/users/{id}/email/verify`](#8112-verify-email) — Verify email
  - [GET `/api/admin/v1/users/{id}/identities`](#8113-the-users-linked-sign-in-accounts-google-saml) — The user's linked sign-in accounts (Google, SAML…)
  - [DELETE `/api/admin/v1/users/{id}/identities/{identityId}`](#8114-unlink-a-sign-in-account) — Unlink a sign-in account
  - [POST `/api/admin/v1/users/{id}/impersonate`](#8115-impersonate-a-user-support) — Impersonate a user (support)
  - [POST `/api/admin/v1/users/{id}/mfa/reset`](#8116-reset-mfa) — Reset MFA
  - [POST `/api/admin/v1/users/{id}/password/remove`](#8117-remove-password) — Remove password
  - [POST `/api/admin/v1/users/{id}/password/reset-email`](#8118-reset-password-email) — Reset password email
  - [POST `/api/admin/v1/users/{id}/password/set`](#8119-set-password) — Set password
  - [POST `/api/admin/v1/users/{id}/password/set-password-email`](#8120-set-password-email) — Set password email
  - [GET `/api/admin/v1/users/{id}/sessions`](#8121-where-the-user-is-signed-in) — Where the user is signed in
  - [POST `/api/admin/v1/users/{id}/sign-out`](#8122-sign-the-user-out-everywhere) — Sign the user out everywhere
  - [PATCH `/api/admin/v1/users/{id}/status`](#8123-update-status) — Update status
  - [GET `/api/admin/v1/users/{id}/two-step`](#8124-two-step-status-of-a-user-options-without-secrets-deadline-exemption) — Two-step status of a user (options without secrets, deadline, exemption)
  - [POST `/api/admin/v1/users/{id}/two-step/exempt`](#8125-exempt-a-user-from-two-step-verification-until-a-date-reason-required) — Exempt a user from two-step verification until a date (reason required)
  - [DELETE `/api/admin/v1/users/{id}/two-step/exempt`](#8126-end-a-users-exemption-in-one-application-or-the-one-for-every-application) — End a user's exemption (in one application, or the one for every application)
  - [POST `/api/admin/v1/users/{id}/two-step/extend`](#8127-give-a-user-more-time-to-set-up-two-step-verification) — Give a user more time to set up two-step verification
  - [POST `/api/admin/v1/users/{id}/two-step/reset`](#8128-reset-a-users-two-step-verification-one-option-or-all-ends-their-sessions) — Reset a user's two-step verification (one option or all); ends their sessions
  - [POST `/api/admin/v1/users/{id}/unlock`](#8129-unlock-user) — Unlock user

## 8.1 Users

Platform-wide user directory

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
| `status` | query | string | no | Filter by status |
| `page` | query | integer (int32) | no | Page number, from 0 |
| `size` | query | integer (int32) | no | Items per page |

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
  "firstName": "First",
  "lastName": "Last",
  "roleIds": [
    "{roleId}"
  ],
  "status": "ACTIVE",
  "tenantId": "{tenantId}",
  "username": "tenant-a:user@example.com"
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
  "firstName": "First",
  "lastName": "Last",
  "roleIds": [
    "{roleId}"
  ],
  "status": "ACTIVE",
  "username": "tenant-a:user@example.com"
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

### 8.1.8 The user's profile attributes

`GET /api/admin/v1/users/{id}/attributes`

Sign-up fields, imported values and attributes set by an administrator.

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
curl -s "$SECUREONE_URL/api/admin/v1/users/{id}/attributes" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 8.1.9 Replace the user's profile attributes

`PUT /api/admin/v1/users/{id}/attributes`

Body: {"attributes": {"key": value}}. An empty value removes the attribute.

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/users/{id}/attributes" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 8.1.10 Update email verification

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

### 8.1.11 Resend email verification

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

### 8.1.12 Verify email

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

### 8.1.13 The user's linked sign-in accounts (Google, SAML…)

`GET /api/admin/v1/users/{id}/identities`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/users/{id}/identities" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 8.1.14 Unlink a sign-in account

`DELETE /api/admin/v1/users/{id}/identities/{identityId}`

Refused (409) when it is the user's only way to sign in.

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |
| `identityId` | path | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/users/{id}/identities/{identityId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 8.1.15 Impersonate a user (support)

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

### 8.1.16 Reset MFA

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

### 8.1.17 Remove password

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

### 8.1.18 Reset password email

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

### 8.1.19 Set password

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
| `temporary` | boolean | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/users/{id}/password/set" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "password": "Choose-a-Strong-Pass-1!",
  "temporary": true
}'
```

### 8.1.20 Set password email

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

### 8.1.21 Where the user is signed in

`GET /api/admin/v1/users/{id}/sessions`

Browser sessions at SecureOne and the applications holding a grant (no token values).

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
curl -s "$SECUREONE_URL/api/admin/v1/users/{id}/sessions" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 8.1.22 Sign the user out everywhere

`POST /api/admin/v1/users/{id}/sign-out`

Ends their browser sessions and every application's grants (refresh tokens), with a back-channel logout to the applications. The account stays active.

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
curl -s -X POST "$SECUREONE_URL/api/admin/v1/users/{id}/sign-out" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 8.1.23 Update status

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

### 8.1.24 Two-step status of a user (options without secrets, deadline, exemption)

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

### 8.1.25 Exempt a user from two-step verification until a date (reason required)

`POST /api/admin/v1/users/{id}/two-step/exempt`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`, [ExemptRequest](13-data-models.md#exemptrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
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
  "applicationId": "{applicationId}",
  "reason": "Short explanation",
  "until": "until"
}'
```

### 8.1.26 End a user's exemption (in one application, or the one for every application)

`DELETE /api/admin/v1/users/{id}/two-step/exempt`

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
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/users/{id}/two-step/exempt" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 8.1.27 Give a user more time to set up two-step verification

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

### 8.1.28 Reset a user's two-step verification (one option or all); ends their sessions

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

### 8.1.29 Unlock user

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
