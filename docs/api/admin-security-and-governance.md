---
title: Admin API: security and governance
summary: Platform operators, access review, platform roles, sign-in federation providers, service accounts, webhooks, the audit log (search and CSV export), sessions and the console overview.
order: 13
audience: public
---

# Admin API: security and governance

[API reference](index.md) › Admin API: security and governance

> Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.

Platform operators, access review, platform roles, sign-in federation providers, service accounts, webhooks, the audit log (search and CSV export), sessions and the console overview.

In Swagger UI (`/docs`), choose the definition **Admin — security and governance** (`/v3/api-docs/admin-security`).

Set the variables from [Getting started](getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [Overview](#overview)
  - [GET `/api/admin/v1/overview`](#get-overview) — Get overview
  - [GET `/api/admin/v1/tenants/{tenantId}/overview`](#get-overview-1) — Get overview
- [Platform operators](#platform-operators)
  - [GET `/api/admin/v1/platform-operators`](#list-platform-operators) — List platform operators
  - [POST `/api/admin/v1/platform-operators`](#create-platform-operator) — Create platform operator
  - [DELETE `/api/admin/v1/platform-operators/{userId}`](#delete-platform-operator) — Delete platform operator
  - [PUT `/api/admin/v1/platform-operators/{userId}/role`](#update-role) — Update role
- [Access review](#access-review)
  - [GET `/api/admin/v1/access-review`](#get-access-review) — Get access review
  - [POST `/api/admin/v1/access-review/complete`](#record-a-completed-access-review-platform) — Record a completed access review (platform)
  - [GET `/api/admin/v1/tenants/{tenantId}/access-review`](#get-access-review-1) — Get access review
  - [POST `/api/admin/v1/tenants/{tenantId}/access-review/complete`](#record-a-completed-access-review-tenant) — Record a completed access review (tenant)
- [Platform RBAC](#platform-rbac)
  - [GET `/api/admin/v1/permissions`](#list-permissions) — List permissions
  - [POST `/api/admin/v1/permissions`](#create-permission) — Create permission
  - [GET `/api/admin/v1/permissions/{id}`](#get-permission) — Get permission
  - [PUT `/api/admin/v1/permissions/{id}`](#update-permission) — Update permission
  - [DELETE `/api/admin/v1/permissions/{id}`](#delete-permission) — Delete permission
  - [GET `/api/admin/v1/roles`](#list-roles) — List roles
  - [POST `/api/admin/v1/roles`](#create-role) — Create role
  - [GET `/api/admin/v1/roles/{id}`](#get-role) — Get role
  - [PUT `/api/admin/v1/roles/{id}`](#update-role-1) — Update role
  - [DELETE `/api/admin/v1/roles/{id}`](#delete-role) — Delete role
- [Federation](#federation)
  - [GET `/api/admin/v1/applications/{applicationId}/federation/providers`](#list-providers) — List providers
  - [POST `/api/admin/v1/applications/{applicationId}/federation/providers`](#create-provider) — Create provider
  - [PUT `/api/admin/v1/applications/{applicationId}/federation/providers/{providerId}`](#update-provider) — Update provider
  - [DELETE `/api/admin/v1/applications/{applicationId}/federation/providers/{providerId}`](#remove-provider) — Remove provider
  - [GET `/api/admin/v1/tenants/{tenantId}/federation/providers`](#list-providers-1) — List providers
  - [POST `/api/admin/v1/tenants/{tenantId}/federation/providers`](#create-provider-1) — Create provider
  - [PUT `/api/admin/v1/tenants/{tenantId}/federation/providers/{providerId}`](#update-provider-1) — Update provider
  - [DELETE `/api/admin/v1/tenants/{tenantId}/federation/providers/{providerId}`](#remove-provider-1) — Remove provider
- [Service accounts](#service-accounts)
  - [GET `/api/admin/v1/tenants/{tenantId}/service-accounts`](#list-service-accounts) — List service accounts
  - [POST `/api/admin/v1/tenants/{tenantId}/service-accounts`](#create-service-account) — Create service account
  - [GET `/api/admin/v1/tenants/{tenantId}/service-accounts/{id}`](#get-service-account) — Get service account
  - [PUT `/api/admin/v1/tenants/{tenantId}/service-accounts/{id}`](#update-service-account) — Update service account
  - [DELETE `/api/admin/v1/tenants/{tenantId}/service-accounts/{id}`](#remove-service-account) — Remove service account
  - [GET `/api/admin/v1/tenants/{tenantId}/service-accounts/{id}/keys`](#list-keys) — List keys
  - [POST `/api/admin/v1/tenants/{tenantId}/service-accounts/{id}/keys`](#create-key) — Create key
  - [DELETE `/api/admin/v1/tenants/{tenantId}/service-accounts/{id}/keys/{keyId}`](#remove-key) — Remove key
- [Webhooks](#webhooks)
  - [GET `/api/admin/v1/tenants/{tenantId}/webhooks`](#list-webhooks) — List webhooks
  - [POST `/api/admin/v1/tenants/{tenantId}/webhooks`](#create-webhook) — Create webhook
  - [GET `/api/admin/v1/tenants/{tenantId}/webhooks/events`](#list-events) — List events
  - [GET `/api/admin/v1/tenants/{tenantId}/webhooks/{id}`](#get-webhook) — Get webhook
  - [PUT `/api/admin/v1/tenants/{tenantId}/webhooks/{id}`](#update-webhook) — Update webhook
  - [DELETE `/api/admin/v1/tenants/{tenantId}/webhooks/{id}`](#remove-webhook) — Remove webhook
  - [GET `/api/admin/v1/tenants/{tenantId}/webhooks/{id}/deliveries`](#list-deliveries) — List deliveries
  - [POST `/api/admin/v1/tenants/{tenantId}/webhooks/{id}/deliveries/{deliveryId}/redeliver`](#redeliver) — Redeliver
  - [POST `/api/admin/v1/tenants/{tenantId}/webhooks/{id}/rotate-secret`](#rotate-webhook-secret) — Rotate webhook secret
  - [POST `/api/admin/v1/tenants/{tenantId}/webhooks/{id}/test`](#test-webhook) — Test webhook
- [Audit](#audit)
  - [GET `/api/admin/v1/audit`](#search-the-audit-log) — Search the audit log
  - [GET `/api/admin/v1/audit/export`](#export-the-audit-log-as-csv) — Export the audit log as CSV
- [Sessions](#sessions)
  - [GET `/api/admin/v1/sessions`](#list-sessions) — List sessions

## Overview

Console home: sign-ins, active users, two-factor use, security events

### Get overview

`GET /api/admin/v1/overview`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/overview" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Get overview

`GET /api/admin/v1/tenants/{tenantId}/overview`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/overview" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## Platform operators

Named accounts that hold the platform super admin role

### List platform operators

`GET /api/admin/v1/platform-operators`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [Row](data-models.md#row)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/platform-operators" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Create platform operator

`POST /api/admin/v1/platform-operators`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [AddOperatorRequest](data-models.md#addoperatorrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | string | no |  |
| `firstName` | string | no |  |
| `lastName` | string | no |  |
| `role` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | [Row](data-models.md#row) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/platform-operators" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "email": "user@example.com",
  "firstName": "First",
  "lastName": "Last",
  "role": "role"
}'
```

### Delete platform operator

`DELETE /api/admin/v1/platform-operators/{userId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/platform-operators/{userId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update role

`PUT /api/admin/v1/platform-operators/{userId}/role`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Request body** (`application/json`, [RoleRequest](data-models.md#rolerequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `role` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [Row](data-models.md#row) |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/platform-operators/{userId}/role" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "role": "role"
}'
```

## Access review

Who holds console and platform roles, with last use and MFA

### Get access review

`GET /api/admin/v1/access-review`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [Entry](data-models.md#entry)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/access-review" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Record a completed access review (platform)

`POST /api/admin/v1/access-review/complete`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [CompleteReviewRequest](data-models.md#completereviewrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `entriesReviewed` | integer (int32) | no |  |
| `note` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/access-review/complete" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "entriesReviewed": 10,
  "note": "Short explanation"
}'
```

### Get access review

`GET /api/admin/v1/tenants/{tenantId}/access-review`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [Entry](data-models.md#entry)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/access-review" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Record a completed access review (tenant)

`POST /api/admin/v1/tenants/{tenantId}/access-review/complete`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Request body** (`application/json`, [CompleteReviewRequest](data-models.md#completereviewrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `entriesReviewed` | integer (int32) | no |  |
| `note` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/access-review/complete" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "entriesReviewed": 10,
  "note": "Short explanation"
}'
```

## Platform RBAC

Platform-wide roles and permissions

### List permissions

`GET /api/admin/v1/permissions`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [PermissionResponse](data-models.md#permissionresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/permissions?applicationId={applicationId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Create permission

`POST /api/admin/v1/permissions`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [PermissionCreateRequest](data-models.md#permissioncreaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | no |  |
| `key` | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | [PermissionDetailResponse](data-models.md#permissiondetailresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/permissions?applicationId={applicationId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "description": "Short explanation",
  "key": "orders:read"
}'
```

### Get permission

`GET /api/admin/v1/permissions/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |
| `applicationId` | query | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [PermissionDetailResponse](data-models.md#permissiondetailresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/permissions/{id}?applicationId={applicationId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update permission

`PUT /api/admin/v1/permissions/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |
| `applicationId` | query | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [PermissionUpdateRequest](data-models.md#permissionupdaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | no |  |
| `key` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [PermissionDetailResponse](data-models.md#permissiondetailresponse) |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/permissions/{id}?applicationId={applicationId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "description": "Short explanation",
  "key": "orders:read"
}'
```

### Delete permission

`DELETE /api/admin/v1/permissions/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |
| `applicationId` | query | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/permissions/{id}?applicationId={applicationId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List roles

`GET /api/admin/v1/roles`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | query | string (uuid) | no | Tenant ID (UUID) |
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [RoleSummaryResponse](data-models.md#rolesummaryresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/roles" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Create role

`POST /api/admin/v1/roles`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [RoleCreateRequest](data-models.md#rolecreaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | yes |  |
| `childRoleIds` | string (uuid)[] | no |  |
| `description` | string | no |  |
| `isComposite` | boolean | no |  |
| `name` | string | yes |  |
| `permissionIds` | string (uuid)[] | no |  |
| `tenantId` | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | [RoleDetailResponse](data-models.md#roledetailresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/roles" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "applicationId": "{applicationId}",
  "childRoleIds": [
    "{childRoleId}"
  ],
  "description": "Short explanation",
  "isComposite": true,
  "name": "Tenant A Shop",
  "permissionIds": [
    "{permissionId}"
  ],
  "tenantId": "{tenantId}"
}'
```

### Get role

`GET /api/admin/v1/roles/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [RoleDetailResponse](data-models.md#roledetailresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/roles/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update role

`PUT /api/admin/v1/roles/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Request body** (`application/json`, [RoleUpdateRequest](data-models.md#roleupdaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `childRoleIds` | string (uuid)[] | no |  |
| `description` | string | no |  |
| `isComposite` | boolean | no |  |
| `name` | string | yes |  |
| `permissionIds` | string (uuid)[] | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [RoleDetailResponse](data-models.md#roledetailresponse) |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/roles/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "childRoleIds": [
    "{childRoleId}"
  ],
  "description": "Short explanation",
  "isComposite": true,
  "name": "Tenant A Shop",
  "permissionIds": [
    "{permissionId}"
  ]
}'
```

### Delete role

`DELETE /api/admin/v1/roles/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/roles/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## Federation

Configure social, OIDC and SAML identity providers

### List providers

`GET /api/admin/v1/applications/{applicationId}/federation/providers`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/federation/providers" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Create provider

`POST /api/admin/v1/applications/{applicationId}/federation/providers`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/federation/providers" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update provider

`PUT /api/admin/v1/applications/{applicationId}/federation/providers/{providerId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `providerId` | path | string (uuid) | yes | Identity provider ID (UUID) |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/federation/providers/{providerId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Remove provider

`DELETE /api/admin/v1/applications/{applicationId}/federation/providers/{providerId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `providerId` | path | string (uuid) | yes | Identity provider ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/federation/providers/{providerId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List providers

`GET /api/admin/v1/tenants/{tenantId}/federation/providers`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/federation/providers" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Create provider

`POST /api/admin/v1/tenants/{tenantId}/federation/providers`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/federation/providers" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update provider

`PUT /api/admin/v1/tenants/{tenantId}/federation/providers/{providerId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `providerId` | path | string (uuid) | yes | Identity provider ID (UUID) |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/federation/providers/{providerId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Remove provider

`DELETE /api/admin/v1/tenants/{tenantId}/federation/providers/{providerId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `providerId` | path | string (uuid) | yes | Identity provider ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/federation/providers/{providerId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## Service accounts

Machine-to-machine service accounts and API keys

### List service accounts

`GET /api/admin/v1/tenants/{tenantId}/service-accounts`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/service-accounts" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Create service account

`POST /api/admin/v1/tenants/{tenantId}/service-accounts`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/service-accounts" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Get service account

`GET /api/admin/v1/tenants/{tenantId}/service-accounts/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/service-accounts/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update service account

`PUT /api/admin/v1/tenants/{tenantId}/service-accounts/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/service-accounts/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Remove service account

`DELETE /api/admin/v1/tenants/{tenantId}/service-accounts/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/service-accounts/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List keys

`GET /api/admin/v1/tenants/{tenantId}/service-accounts/{id}/keys`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/service-accounts/{id}/keys" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Create key

`POST /api/admin/v1/tenants/{tenantId}/service-accounts/{id}/keys`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/service-accounts/{id}/keys" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Remove key

`DELETE /api/admin/v1/tenants/{tenantId}/service-accounts/{id}/keys/{keyId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `id` | path | string (uuid) | yes | ID of the item (UUID) |
| `keyId` | path | string (uuid) | yes | API key ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/service-accounts/{id}/keys/{keyId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## Webhooks

Outbound webhook endpoints and delivery history

### List webhooks

`GET /api/admin/v1/tenants/{tenantId}/webhooks`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/webhooks" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Create webhook

`POST /api/admin/v1/tenants/{tenantId}/webhooks`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/webhooks" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List events

`GET /api/admin/v1/tenants/{tenantId}/webhooks/events`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/webhooks/events" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Get webhook

`GET /api/admin/v1/tenants/{tenantId}/webhooks/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/webhooks/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update webhook

`PUT /api/admin/v1/tenants/{tenantId}/webhooks/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/webhooks/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Remove webhook

`DELETE /api/admin/v1/tenants/{tenantId}/webhooks/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/webhooks/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List deliveries

`GET /api/admin/v1/tenants/{tenantId}/webhooks/{id}/deliveries`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/webhooks/{id}/deliveries" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Redeliver

`POST /api/admin/v1/tenants/{tenantId}/webhooks/{id}/deliveries/{deliveryId}/redeliver`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `id` | path | string (uuid) | yes | ID of the item (UUID) |
| `deliveryId` | path | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/webhooks/{id}/deliveries/{deliveryId}/redeliver" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Rotate webhook secret

`POST /api/admin/v1/tenants/{tenantId}/webhooks/{id}/rotate-secret`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `id` | path | string (uuid) | yes | ID of the item (UUID) |
| `keepOldHours` | query | integer (int32) | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/webhooks/{id}/rotate-secret" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Test webhook

`POST /api/admin/v1/tenants/{tenantId}/webhooks/{id}/test`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/webhooks/{id}/test" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## Audit

Security and admin action audit log

### Search the audit log

`GET /api/admin/v1/audit`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |
| `tenantId` | query | string (uuid) | no | Tenant ID (UUID) |
| `from` | query | string (date-time) | no |  |
| `to` | query | string (date-time) | no |  |
| `action` | query | string | no |  |
| `actor` | query | string | no |  |
| `result` | query | string | no |  |
| `limit` | query | integer (int32) | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [Row](data-models.md#row)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/audit" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Export the audit log as CSV

`GET /api/admin/v1/audit/export`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |
| `tenantId` | query | string (uuid) | no | Tenant ID (UUID) |
| `from` | query | string (date-time) | no |  |
| `to` | query | string (date-time) | no |  |
| `action` | query | string | no |  |
| `actor` | query | string | no |  |
| `result` | query | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | — |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/audit/export" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## Sessions

Login history and active sessions

### List sessions

`GET /api/admin/v1/sessions`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `userId` | query | string (uuid) | no | User ID (UUID) |
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |
| `tenantId` | query | string (uuid) | no | Tenant ID (UUID) |
| `limit` | query | integer (int32) | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [LoginEventResponse](data-models.md#logineventresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/sessions" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```
