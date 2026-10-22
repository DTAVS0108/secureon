# 11. Admin API: security and governance

[API reference](README.md) › Admin API: security and governance

> Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.

Platform operators, access review, platform roles, sign-in federation providers, service accounts, webhooks, the audit log and sessions.

Set the variables from [Getting started](00-getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [11.1 Platform operators](#111-platform-operators)
  - [GET `/api/admin/v1/platform-operators`](#1111-list-platform-operators) — List platform operators
  - [POST `/api/admin/v1/platform-operators`](#1112-create-platform-operator) — Create platform operator
  - [DELETE `/api/admin/v1/platform-operators/{userId}`](#1113-delete-platform-operator) — Delete platform operator
- [11.2 Access review](#112-access-review)
  - [GET `/api/admin/v1/access-review`](#1121-get-access-review) — Get access review
  - [POST `/api/admin/v1/access-review/complete`](#1122-record-a-completed-access-review-platform) — Record a completed access review (platform)
  - [GET `/api/admin/v1/tenants/{tenantId}/access-review`](#1123-get-access-review) — Get access review
  - [POST `/api/admin/v1/tenants/{tenantId}/access-review/complete`](#1124-record-a-completed-access-review-tenant) — Record a completed access review (tenant)
- [11.3 Platform RBAC](#113-platform-rbac)
  - [GET `/api/admin/v1/permissions`](#1131-list-permissions) — List permissions
  - [POST `/api/admin/v1/permissions`](#1132-create-permission) — Create permission
  - [GET `/api/admin/v1/permissions/{id}`](#1133-get-permission) — Get permission
  - [PUT `/api/admin/v1/permissions/{id}`](#1134-update-permission) — Update permission
  - [DELETE `/api/admin/v1/permissions/{id}`](#1135-delete-permission) — Delete permission
  - [GET `/api/admin/v1/roles`](#1136-list-roles) — List roles
  - [POST `/api/admin/v1/roles`](#1137-create-role) — Create role
  - [GET `/api/admin/v1/roles/{id}`](#1138-get-role) — Get role
  - [PUT `/api/admin/v1/roles/{id}`](#1139-update-role) — Update role
  - [DELETE `/api/admin/v1/roles/{id}`](#11310-delete-role) — Delete role
- [11.4 Federation](#114-federation)
  - [GET `/api/admin/v1/applications/{applicationId}/federation/providers`](#1141-list-providers) — List providers
  - [POST `/api/admin/v1/applications/{applicationId}/federation/providers`](#1142-create-provider) — Create provider
  - [PUT `/api/admin/v1/applications/{applicationId}/federation/providers/{providerId}`](#1143-update-provider) — Update provider
  - [DELETE `/api/admin/v1/applications/{applicationId}/federation/providers/{providerId}`](#1144-remove-provider) — Remove provider
  - [GET `/api/admin/v1/tenants/{tenantId}/federation/providers`](#1145-list-providers) — List providers
  - [POST `/api/admin/v1/tenants/{tenantId}/federation/providers`](#1146-create-provider) — Create provider
  - [PUT `/api/admin/v1/tenants/{tenantId}/federation/providers/{providerId}`](#1147-update-provider) — Update provider
  - [DELETE `/api/admin/v1/tenants/{tenantId}/federation/providers/{providerId}`](#1148-remove-provider) — Remove provider
- [11.5 Service accounts](#115-service-accounts)
  - [GET `/api/admin/v1/tenants/{tenantId}/service-accounts`](#1151-list-service-accounts) — List service accounts
  - [POST `/api/admin/v1/tenants/{tenantId}/service-accounts`](#1152-create-service-account) — Create service account
  - [GET `/api/admin/v1/tenants/{tenantId}/service-accounts/{id}`](#1153-get-service-account) — Get service account
  - [PUT `/api/admin/v1/tenants/{tenantId}/service-accounts/{id}`](#1154-update-service-account) — Update service account
  - [DELETE `/api/admin/v1/tenants/{tenantId}/service-accounts/{id}`](#1155-remove-service-account) — Remove service account
  - [GET `/api/admin/v1/tenants/{tenantId}/service-accounts/{id}/keys`](#1156-list-keys) — List keys
  - [POST `/api/admin/v1/tenants/{tenantId}/service-accounts/{id}/keys`](#1157-create-key) — Create key
  - [DELETE `/api/admin/v1/tenants/{tenantId}/service-accounts/{id}/keys/{keyId}`](#1158-remove-key) — Remove key
- [11.6 Webhooks](#116-webhooks)
  - [GET `/api/admin/v1/tenants/{tenantId}/webhooks`](#1161-list-webhooks) — List webhooks
  - [POST `/api/admin/v1/tenants/{tenantId}/webhooks`](#1162-create-webhook) — Create webhook
  - [GET `/api/admin/v1/tenants/{tenantId}/webhooks/{id}`](#1163-get-webhook) — Get webhook
  - [PUT `/api/admin/v1/tenants/{tenantId}/webhooks/{id}`](#1164-update-webhook) — Update webhook
  - [DELETE `/api/admin/v1/tenants/{tenantId}/webhooks/{id}`](#1165-remove-webhook) — Remove webhook
  - [GET `/api/admin/v1/tenants/{tenantId}/webhooks/{id}/deliveries`](#1166-list-deliveries) — List deliveries
- [11.7 Audit](#117-audit)
  - [GET `/api/admin/v1/audit`](#1171-search-the-audit-log) — Search the audit log
- [11.8 Sessions](#118-sessions)
  - [GET `/api/admin/v1/sessions`](#1181-list-sessions) — List sessions

## 11.1 Platform operators

Named accounts that hold the platform super admin role

### 11.1.1 List platform operators

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

### 11.1.2 Create platform operator

`POST /api/admin/v1/platform-operators`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [AddOperatorRequest](13-data-models.md#addoperatorrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | string | no |  |
| `firstName` | string | no |  |
| `lastName` | string | no |  |

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
  "firstName": "Jane",
  "lastName": "Doe"
}'
```

### 11.1.3 Delete platform operator

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

## 11.2 Access review

Who holds console and platform roles, with last use and MFA

### 11.2.1 Get access review

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

### 11.2.2 Record a completed access review (platform)

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

### 11.2.3 Get access review

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

### 11.2.4 Record a completed access review (tenant)

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

## 11.3 Platform RBAC

Platform-wide roles and permissions

### 11.3.1 List permissions

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

### 11.3.2 Create permission

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

### 11.3.3 Get permission

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

### 11.3.4 Update permission

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

### 11.3.5 Delete permission

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

### 11.3.6 List roles

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

### 11.3.7 Create role

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
  "name": "Acme Shop",
  "permissionIds": [
    "{permissionId}"
  ],
  "tenantId": "{tenantId}"
}'
```

### 11.3.8 Get role

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

### 11.3.9 Update role

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
  "name": "Acme Shop",
  "permissionIds": [
    "{permissionId}"
  ]
}'
```

### 11.3.10 Delete role

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

## 11.4 Federation

Configure Google, GitHub, and OIDC identity providers

### 11.4.1 List providers

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

### 11.4.2 Create provider

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

### 11.4.3 Update provider

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

### 11.4.4 Remove provider

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

### 11.4.5 List providers

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

### 11.4.6 Create provider

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

### 11.4.7 Update provider

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

### 11.4.8 Remove provider

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

## 11.5 Service accounts

Machine-to-machine service accounts and API keys

### 11.5.1 List service accounts

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

### 11.5.2 Create service account

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

### 11.5.3 Get service account

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

### 11.5.4 Update service account

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

### 11.5.5 Remove service account

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

### 11.5.6 List keys

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

### 11.5.7 Create key

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

### 11.5.8 Remove key

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

## 11.6 Webhooks

Outbound webhook endpoints and delivery history

### 11.6.1 List webhooks

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

### 11.6.2 Create webhook

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

### 11.6.3 Get webhook

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

### 11.6.4 Update webhook

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

### 11.6.5 Remove webhook

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

### 11.6.6 List deliveries

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

## 11.7 Audit

Security and admin action audit log

### 11.7.1 Search the audit log

`GET /api/admin/v1/audit`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |
| `tenantId` | query | string (uuid) | no | Tenant ID (UUID) |
| `limit` | query | integer (int32) | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [AuditEventResponse](13-data-models.md#auditeventresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/audit" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## 11.8 Sessions

Login history and active sessions

### 11.8.1 List sessions

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
