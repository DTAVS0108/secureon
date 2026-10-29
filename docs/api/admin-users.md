---
title: Admin API: users
summary: User accounts across tenants: create, edit, suspend, delete, passwords, email verification, MFA reset, unlock and impersonation.
order: 10
audience: public
---

# Admin API: users

[API reference](index.md) › Admin API: users

> Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.

User accounts across tenants: create, edit, suspend, delete, passwords, email verification, MFA reset, unlock and impersonation.

In Swagger UI (`/docs`), choose the definition **Admin — tenants and users** (`/v3/api-docs/admin-tenants`).

Set the variables from [Getting started](getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [Users](#users)
  - [POST `/api/admin/v1/applications/{applicationId}/users/two-step-statuses`](#two-step-status-of-several-of-an-applications-users) — Two-step status of several of an application's users
  - [GET `/api/admin/v1/applications/{applicationId}/users/{userId}/two-step`](#two-step-status-of-an-applications-user) — Two-step status of an application's user
  - [GET `/api/admin/v1/users`](#list-users) — List users
  - [POST `/api/admin/v1/users`](#create-user) — Create user
  - [GET `/api/admin/v1/users/{id}`](#get-user) — Get user
  - [PUT `/api/admin/v1/users/{id}`](#update-user) — Update user
  - [DELETE `/api/admin/v1/users/{id}`](#delete-user) — Delete user
  - [GET `/api/admin/v1/users/{id}/attributes`](#the-users-profile-attributes) — The user's profile attributes
  - [PUT `/api/admin/v1/users/{id}/attributes`](#replace-the-users-profile-attributes) — Replace the user's profile attributes
  - [PATCH `/api/admin/v1/users/{id}/email-verification`](#update-email-verification) — Update email verification
  - [POST `/api/admin/v1/users/{id}/email/resend-verification`](#resend-email-verification) — Resend email verification
  - [POST `/api/admin/v1/users/{id}/email/verify`](#verify-email) — Verify email
  - [GET `/api/admin/v1/users/{id}/identities`](#the-users-linked-sign-in-accounts-google-saml) — The user's linked sign-in accounts (Google, SAML…)
  - [DELETE `/api/admin/v1/users/{id}/identities/{identityId}`](#unlink-a-sign-in-account) — Unlink a sign-in account
  - [POST `/api/admin/v1/users/{id}/impersonate`](#impersonate-a-user-support) — Impersonate a user (support)
  - [POST `/api/admin/v1/users/{id}/mfa/reset`](#reset-mfa) — Reset MFA
  - [POST `/api/admin/v1/users/{id}/password/remove`](#remove-password) — Remove password
  - [POST `/api/admin/v1/users/{id}/password/reset-email`](#reset-password-email) — Reset password email
  - [POST `/api/admin/v1/users/{id}/password/set`](#set-password) — Set password
  - [POST `/api/admin/v1/users/{id}/password/set-password-email`](#set-password-email) — Set password email
  - [GET `/api/admin/v1/users/{id}/sessions`](#where-the-user-is-signed-in) — Where the user is signed in
  - [POST `/api/admin/v1/users/{id}/sign-out`](#sign-the-user-out-everywhere) — Sign the user out everywhere
  - [PATCH `/api/admin/v1/users/{id}/status`](#update-status) — Update status
  - [GET `/api/admin/v1/users/{id}/two-step`](#two-step-status-of-a-user-options-without-secrets-deadline-exemption) — Two-step status of a user (options without secrets, deadline, exemption)
  - [POST `/api/admin/v1/users/{id}/two-step/exempt`](#exempt-a-user-from-two-step-verification-until-a-date-reason-required) — Exempt a user from two-step verification until a date (reason required)
  - [DELETE `/api/admin/v1/users/{id}/two-step/exempt`](#end-a-users-exemption-in-one-application-or-the-one-for-every-application) — End a user's exemption (in one application, or the one for every application)
  - [POST `/api/admin/v1/users/{id}/two-step/extend`](#give-a-user-more-time-to-set-up-two-step-verification) — Give a user more time to set up two-step verification
  - [POST `/api/admin/v1/users/{id}/two-step/reset`](#reset-a-users-two-step-verification-one-option-or-all-ends-their-sessions) — Reset a user's two-step verification (one option or all); ends their sessions
  - [POST `/api/admin/v1/users/{id}/unlock`](#unlock-user) — Unlock user

## Users

Platform-wide user directory

### Two-step status of several of an application's users

`POST /api/admin/v1/applications/{applicationId}/users/two-step-statuses`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [StatusesRequest](data-models.md#statusesrequest))

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

### Two-step status of an application's user

`GET /api/admin/v1/applications/{applicationId}/users/{userId}/two-step`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### List users

`GET /api/admin/v1/users`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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
| `200` | OK | [UserResponse](data-models.md#userresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/users" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Create user

`POST /api/admin/v1/users`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [UserCreateRequest](data-models.md#usercreaterequest))

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
| `201` | Created | [UserResponse](data-models.md#userresponse) |

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

### Get user

`GET /api/admin/v1/users/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserResponse](data-models.md#userresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/users/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update user

`PUT /api/admin/v1/users/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`, [UserUpdateRequest](data-models.md#userupdaterequest))

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
| `200` | OK | [UserResponse](data-models.md#userresponse) |

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

### Delete user

`DELETE /api/admin/v1/users/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### The user's profile attributes

`GET /api/admin/v1/users/{id}/attributes`

Sign-up fields, imported values and attributes set by an administrator.

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### Replace the user's profile attributes

`PUT /api/admin/v1/users/{id}/attributes`

Body: {"attributes": {"key": value}}. An empty value removes the attribute.

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### Update email verification

`PATCH /api/admin/v1/users/{id}/email-verification`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserResponse](data-models.md#userresponse) |

```bash
curl -s -X PATCH "$SECUREONE_URL/api/admin/v1/users/{id}/email-verification" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Resend email verification

`POST /api/admin/v1/users/{id}/email/resend-verification`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### Verify email

`POST /api/admin/v1/users/{id}/email/verify`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserResponse](data-models.md#userresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/users/{id}/email/verify" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### The user's linked sign-in accounts (Google, SAML…)

`GET /api/admin/v1/users/{id}/identities`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### Unlink a sign-in account

`DELETE /api/admin/v1/users/{id}/identities/{identityId}`

Refused (409) when it is the user's only way to sign in.

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### Impersonate a user (support)

`POST /api/admin/v1/users/{id}/impersonate`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ImpersonationTokenResponse](data-models.md#impersonationtokenresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/users/{id}/impersonate" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Reset MFA

`POST /api/admin/v1/users/{id}/mfa/reset`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### Remove password

`POST /api/admin/v1/users/{id}/password/remove`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### Reset password email

`POST /api/admin/v1/users/{id}/password/reset-email`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### Set password

`POST /api/admin/v1/users/{id}/password/set`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`, [AdminSetPasswordRequest](data-models.md#adminsetpasswordrequest))

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

### Set password email

`POST /api/admin/v1/users/{id}/password/set-password-email`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### Where the user is signed in

`GET /api/admin/v1/users/{id}/sessions`

Browser sessions at SecureOne and the applications holding a grant (no token values).

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### Sign the user out everywhere

`POST /api/admin/v1/users/{id}/sign-out`

Ends their browser sessions and every application's grants (refresh tokens), with a back-channel logout to the applications. The account stays active.

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### Update status

`PATCH /api/admin/v1/users/{id}/status`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserResponse](data-models.md#userresponse) |

```bash
curl -s -X PATCH "$SECUREONE_URL/api/admin/v1/users/{id}/status" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Two-step status of a user (options without secrets, deadline, exemption)

`GET /api/admin/v1/users/{id}/two-step`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### Exempt a user from two-step verification until a date (reason required)

`POST /api/admin/v1/users/{id}/two-step/exempt`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`, [ExemptRequest](data-models.md#exemptrequest))

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

### End a user's exemption (in one application, or the one for every application)

`DELETE /api/admin/v1/users/{id}/two-step/exempt`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### Give a user more time to set up two-step verification

`POST /api/admin/v1/users/{id}/two-step/extend`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`, [ExtendRequest](data-models.md#extendrequest))

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

### Reset a user's two-step verification (one option or all); ends their sessions

`POST /api/admin/v1/users/{id}/two-step/reset`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`, [ResetRequest](data-models.md#resetrequest))

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

### Unlock user

`POST /api/admin/v1/users/{id}/unlock`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserResponse](data-models.md#userresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/users/{id}/unlock" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```
