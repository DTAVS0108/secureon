# 11. Admin API: security and governance

[API reference](README.md) › Admin API: security and governance

> Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.

Platform operators, access review, platform roles, sign-in federation providers, service accounts, webhooks, the audit log (search and CSV export), sessions and the console overview.

In Swagger UI (`/docs`), choose the definition **Admin — security and governance** (`/v3/api-docs/admin-security`).

Set the variables from [Getting started](00-getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [11.1 Overview](#111-overview)
  - [GET `/api/admin/v1/overview`](#1111-get-overview) — Get overview
  - [GET `/api/admin/v1/tenants/{tenantId}/overview`](#1112-get-overview) — Get overview
- [11.2 Platform operators](#112-platform-operators)
  - [GET `/api/admin/v1/platform-operators`](#1121-list-platform-operators) — List platform operators
  - [POST `/api/admin/v1/platform-operators`](#1122-create-platform-operator) — Create platform operator
  - [DELETE `/api/admin/v1/platform-operators/{userId}`](#1123-delete-platform-operator) — Delete platform operator
  - [PUT `/api/admin/v1/platform-operators/{userId}/role`](#1124-update-role) — Update role
- [11.3 Access review](#113-access-review)
  - [GET `/api/admin/v1/access-review`](#1131-get-access-review) — Get access review
  - [POST `/api/admin/v1/access-review/complete`](#1132-record-a-completed-access-review-platform) — Record a completed access review (platform)
  - [GET `/api/admin/v1/tenants/{tenantId}/access-review`](#1133-get-access-review) — Get access review
  - [POST `/api/admin/v1/tenants/{tenantId}/access-review/complete`](#1134-record-a-completed-access-review-tenant) — Record a completed access review (tenant)
- [11.4 Platform RBAC](#114-platform-rbac)
  - [GET `/api/admin/v1/permissions`](#1141-list-permissions) — List permissions
  - [POST `/api/admin/v1/permissions`](#1142-create-permission) — Create permission
  - [GET `/api/admin/v1/permissions/{id}`](#1143-get-permission) — Get permission
  - [PUT `/api/admin/v1/permissions/{id}`](#1144-update-permission) — Update permission
  - [DELETE `/api/admin/v1/permissions/{id}`](#1145-delete-permission) — Delete permission
  - [GET `/api/admin/v1/roles`](#1146-list-roles) — List roles
  - [POST `/api/admin/v1/roles`](#1147-create-role) — Create role
  - [GET `/api/admin/v1/roles/{id}`](#1148-get-role) — Get role
  - [PUT `/api/admin/v1/roles/{id}`](#1149-update-role) — Update role
  - [DELETE `/api/admin/v1/roles/{id}`](#11410-delete-role) — Delete role
- [11.5 Federation](#115-federation)
  - [GET `/api/admin/v1/applications/{applicationId}/federation/providers`](#1151-list-providers) — List providers
  - [POST `/api/admin/v1/applications/{applicationId}/federation/providers`](#1152-create-provider) — Create provider
  - [PUT `/api/admin/v1/applications/{applicationId}/federation/providers/{providerId}`](#1153-update-provider) — Update provider
  - [DELETE `/api/admin/v1/applications/{applicationId}/federation/providers/{providerId}`](#1154-remove-provider) — Remove provider
  - [GET `/api/admin/v1/tenants/{tenantId}/federation/providers`](#1155-list-providers) — List providers
  - [POST `/api/admin/v1/tenants/{tenantId}/federation/providers`](#1156-create-provider) — Create provider
  - [PUT `/api/admin/v1/tenants/{tenantId}/federation/providers/{providerId}`](#1157-update-provider) — Update provider
  - [DELETE `/api/admin/v1/tenants/{tenantId}/federation/providers/{providerId}`](#1158-remove-provider) — Remove provider
- [11.6 Service accounts](#116-service-accounts)
  - [GET `/api/admin/v1/tenants/{tenantId}/service-accounts`](#1161-list-service-accounts) — List service accounts
  - [POST `/api/admin/v1/tenants/{tenantId}/service-accounts`](#1162-create-service-account) — Create service account
  - [GET `/api/admin/v1/tenants/{tenantId}/service-accounts/{id}`](#1163-get-service-account) — Get service account
  - [PUT `/api/admin/v1/tenants/{tenantId}/service-accounts/{id}`](#1164-update-service-account) — Update service account
  - [DELETE `/api/admin/v1/tenants/{tenantId}/service-accounts/{id}`](#1165-remove-service-account) — Remove service account
  - [GET `/api/admin/v1/tenants/{tenantId}/service-accounts/{id}/keys`](#1166-list-keys) — List keys
  - [POST `/api/admin/v1/tenants/{tenantId}/service-accounts/{id}/keys`](#1167-create-key) — Create key
  - [DELETE `/api/admin/v1/tenants/{tenantId}/service-accounts/{id}/keys/{keyId}`](#1168-remove-key) — Remove key
- [11.7 Webhooks](#117-webhooks)
  - [GET `/api/admin/v1/tenants/{tenantId}/webhooks`](#1171-list-webhooks) — List webhooks
  - [POST `/api/admin/v1/tenants/{tenantId}/webhooks`](#1172-create-webhook) — Create webhook
  - [GET `/api/admin/v1/tenants/{tenantId}/webhooks/events`](#1173-list-events) — List events
  - [GET `/api/admin/v1/tenants/{tenantId}/webhooks/{id}`](#1174-get-webhook) — Get webhook
  - [PUT `/api/admin/v1/tenants/{tenantId}/webhooks/{id}`](#1175-update-webhook) — Update webhook
  - [DELETE `/api/admin/v1/tenants/{tenantId}/webhooks/{id}`](#1176-remove-webhook) — Remove webhook
  - [GET `/api/admin/v1/tenants/{tenantId}/webhooks/{id}/deliveries`](#1177-list-deliveries) — List deliveries
  - [POST `/api/admin/v1/tenants/{tenantId}/webhooks/{id}/deliveries/{deliveryId}/redeliver`](#1178-redeliver) — Redeliver
  - [POST `/api/admin/v1/tenants/{tenantId}/webhooks/{id}/rotate-secret`](#1179-rotate-webhook-secret) — Rotate webhook secret
  - [POST `/api/admin/v1/tenants/{tenantId}/webhooks/{id}/test`](#11710-test-webhook) — Test webhook
- [11.8 Audit](#118-audit)
  - [GET `/api/admin/v1/audit`](#1181-search-the-audit-log) — Search the audit log
  - [GET `/api/admin/v1/audit/export`](#1182-export-the-audit-log-as-csv) — Export the audit log as CSV
- [11.9 Sessions](#119-sessions)
  - [GET `/api/admin/v1/sessions`](#1191-list-sessions) — List sessions

## 11.1 Overview

Console home: sign-ins, active users, two-factor use, security events

### 11.1.1 Get overview

`GET /api/admin/v1/overview`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/overview" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 11.1.2 Get overview

`GET /api/admin/v1/tenants/{tenantId}/overview`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

## 11.2 Platform operators

Named accounts that hold the platform super admin role

### 11.2.1 List platform operators

`GET /api/admin/v1/platform-operators`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [Row](13-data-models.md#row)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/platform-operators" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 11.2.2 Create platform operator

`POST /api/admin/v1/platform-operators`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [AddOperatorRequest](13-data-models.md#addoperatorrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | string | no |  |
| `firstName` | string | no |  |
| `lastName` | string | no |  |
| `role` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | [Row](13-data-models.md#row) |

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

### 11.2.3 Delete platform operator

`DELETE /api/admin/v1/platform-operators/{userId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 11.2.4 Update role

`PUT /api/admin/v1/platform-operators/{userId}/role`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Request body** (`application/json`, [RoleRequest](13-data-models.md#rolerequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `role` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [Row](13-data-models.md#row) |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/platform-operators/{userId}/role" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "role": "role"
}'
```

## 11.3 Access review

Who holds console and platform roles, with last use and MFA

### 11.3.1 Get access review

`GET /api/admin/v1/access-review`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [Entry](13-data-models.md#entry)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/access-review" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 11.3.2 Record a completed access review (platform)

`POST /api/admin/v1/access-review/complete`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [CompleteReviewRequest](13-data-models.md#completereviewrequest))

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

### 11.3.3 Get access review

`GET /api/admin/v1/tenants/{tenantId}/access-review`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [Entry](13-data-models.md#entry)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/access-review" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 11.3.4 Record a completed access review (tenant)

`POST /api/admin/v1/tenants/{tenantId}/access-review/complete`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Request body** (`application/json`, [CompleteReviewRequest](13-data-models.md#completereviewrequest))

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

## 11.4 Platform RBAC

Platform-wide roles and permissions

### 11.4.1 List permissions

`GET /api/admin/v1/permissions`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [PermissionResponse](13-data-models.md#permissionresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/permissions?applicationId={applicationId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 11.4.2 Create permission

`POST /api/admin/v1/permissions`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [PermissionCreateRequest](13-data-models.md#permissioncreaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | no |  |
| `key` | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | [PermissionDetailResponse](13-data-models.md#permissiondetailresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/permissions?applicationId={applicationId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "description": "Short explanation",
  "key": "orders:read"
}'
```

### 11.4.3 Get permission

`GET /api/admin/v1/permissions/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |
| `applicationId` | query | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [PermissionDetailResponse](13-data-models.md#permissiondetailresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/permissions/{id}?applicationId={applicationId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 11.4.4 Update permission

`PUT /api/admin/v1/permissions/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |
| `applicationId` | query | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [PermissionUpdateRequest](13-data-models.md#permissionupdaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | no |  |
| `key` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [PermissionDetailResponse](13-data-models.md#permissiondetailresponse) |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/permissions/{id}?applicationId={applicationId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "description": "Short explanation",
  "key": "orders:read"
}'
```

### 11.4.5 Delete permission

`DELETE /api/admin/v1/permissions/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 11.4.6 List roles

`GET /api/admin/v1/roles`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | query | string (uuid) | no | Tenant ID (UUID) |
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [RoleSummaryResponse](13-data-models.md#rolesummaryresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/roles" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 11.4.7 Create role

`POST /api/admin/v1/roles`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [RoleCreateRequest](13-data-models.md#rolecreaterequest))

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
| `201` | Created | [RoleDetailResponse](13-data-models.md#roledetailresponse) |

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

### 11.4.8 Get role

`GET /api/admin/v1/roles/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [RoleDetailResponse](13-data-models.md#roledetailresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/roles/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 11.4.9 Update role

`PUT /api/admin/v1/roles/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Request body** (`application/json`, [RoleUpdateRequest](13-data-models.md#roleupdaterequest))

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
| `200` | OK | [RoleDetailResponse](13-data-models.md#roledetailresponse) |

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

### 11.4.10 Delete role

`DELETE /api/admin/v1/roles/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

## 11.5 Federation

Configure social, OIDC and SAML identity providers

### 11.5.1 List providers

`GET /api/admin/v1/applications/{applicationId}/federation/providers`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 11.5.2 Create provider

`POST /api/admin/v1/applications/{applicationId}/federation/providers`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 11.5.3 Update provider

`PUT /api/admin/v1/applications/{applicationId}/federation/providers/{providerId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 11.5.4 Remove provider

`DELETE /api/admin/v1/applications/{applicationId}/federation/providers/{providerId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 11.5.5 List providers

`GET /api/admin/v1/tenants/{tenantId}/federation/providers`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 11.5.6 Create provider

`POST /api/admin/v1/tenants/{tenantId}/federation/providers`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 11.5.7 Update provider

`PUT /api/admin/v1/tenants/{tenantId}/federation/providers/{providerId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 11.5.8 Remove provider

`DELETE /api/admin/v1/tenants/{tenantId}/federation/providers/{providerId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

## 11.6 Service accounts

Machine-to-machine service accounts and API keys

### 11.6.1 List service accounts

`GET /api/admin/v1/tenants/{tenantId}/service-accounts`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 11.6.2 Create service account

`POST /api/admin/v1/tenants/{tenantId}/service-accounts`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 11.6.3 Get service account

`GET /api/admin/v1/tenants/{tenantId}/service-accounts/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 11.6.4 Update service account

`PUT /api/admin/v1/tenants/{tenantId}/service-accounts/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 11.6.5 Remove service account

`DELETE /api/admin/v1/tenants/{tenantId}/service-accounts/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 11.6.6 List keys

`GET /api/admin/v1/tenants/{tenantId}/service-accounts/{id}/keys`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 11.6.7 Create key

`POST /api/admin/v1/tenants/{tenantId}/service-accounts/{id}/keys`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 11.6.8 Remove key

`DELETE /api/admin/v1/tenants/{tenantId}/service-accounts/{id}/keys/{keyId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

## 11.7 Webhooks

Outbound webhook endpoints and delivery history

### 11.7.1 List webhooks

`GET /api/admin/v1/tenants/{tenantId}/webhooks`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 11.7.2 Create webhook

`POST /api/admin/v1/tenants/{tenantId}/webhooks`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 11.7.3 List events

`GET /api/admin/v1/tenants/{tenantId}/webhooks/events`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 11.7.4 Get webhook

`GET /api/admin/v1/tenants/{tenantId}/webhooks/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 11.7.5 Update webhook

`PUT /api/admin/v1/tenants/{tenantId}/webhooks/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 11.7.6 Remove webhook

`DELETE /api/admin/v1/tenants/{tenantId}/webhooks/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 11.7.7 List deliveries

`GET /api/admin/v1/tenants/{tenantId}/webhooks/{id}/deliveries`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 11.7.8 Redeliver

`POST /api/admin/v1/tenants/{tenantId}/webhooks/{id}/deliveries/{deliveryId}/redeliver`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 11.7.9 Rotate webhook secret

`POST /api/admin/v1/tenants/{tenantId}/webhooks/{id}/rotate-secret`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 11.7.10 Test webhook

`POST /api/admin/v1/tenants/{tenantId}/webhooks/{id}/test`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

## 11.8 Audit

Security and admin action audit log

### 11.8.1 Search the audit log

`GET /api/admin/v1/audit`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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
| `200` | OK | [Row](13-data-models.md#row)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/audit" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 11.8.2 Export the audit log as CSV

`GET /api/admin/v1/audit/export`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

## 11.9 Sessions

Login history and active sessions

### 11.9.1 List sessions

`GET /api/admin/v1/sessions`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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
| `200` | OK | [LoginEventResponse](13-data-models.md#logineventresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/sessions" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```
