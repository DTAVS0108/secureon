# 7. Admin API: tenants

[API reference](README.md) › Admin API: tenants

> Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.

Tenants and everything inside one: the tenant workspace (roster, application access), tenant roles, console roles and access, access requests and the tenant's SCIM token.

Set the variables from [Getting started](00-getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [7.1 Tenants](#71-tenants)
  - [GET `/api/admin/v1/tenants`](#711-list-tenants) — List tenants
  - [POST `/api/admin/v1/tenants`](#712-create-tenant) — Create tenant
  - [GET `/api/admin/v1/tenants/{id}`](#713-get-tenant) — Get tenant
  - [PUT `/api/admin/v1/tenants/{id}`](#714-update-tenant) — Update tenant
  - [DELETE `/api/admin/v1/tenants/{id}`](#715-delete-tenant) — Delete tenant
  - [GET `/api/admin/v1/tenants/{tenantId}/admin-operators`](#716-list-admin-operators) — List admin operators
  - [POST `/api/admin/v1/tenants/{tenantId}/admin-operators/{userId}/applications/{applicationId}`](#717-make-a-user-tenant-admin-of-an-application) — Make a user tenant admin of an application
  - [DELETE `/api/admin/v1/tenants/{tenantId}/admin-operators/{userId}/applications/{applicationId}`](#718-remove-a-users-tenant-admin-role-for-an-application) — Remove a user's tenant admin role for an application
  - [GET `/api/admin/v1/tenants/{tenantId}/two-step-policy`](#719-effective-tenant-defaults-the-tenants-own-overrides-and-locks-and-the-platforms-locks) — Effective tenant defaults, the tenant's own overrides and locks, and the platform's locks
  - [PUT `/api/admin/v1/tenants/{tenantId}/two-step-policy`](#7110-save-the-tenants-defaults-and-locks-weaker-values-or-removed-locks-wait-for-a-platform-operator) — Save the tenant's defaults and locks (weaker values or removed locks wait for a platform operator)
  - [DELETE `/api/admin/v1/tenants/{tenantId}/two-step-policy`](#7111-back-to-the-platform-policy-waits-for-a-platform-operator-when-that-is-weaker) — Back to the platform policy (waits for a platform operator when that is weaker)
- [7.2 Tenant workspace](#72-tenant-workspace)
  - [GET `/api/admin/v1/tenant-workspace`](#721-get-tenant-workspace) — Get tenant workspace
  - [GET `/api/admin/v1/tenant-workspace/admin-operators`](#722-list-admin-operators) — List admin operators
  - [GET `/api/admin/v1/tenant-workspace/applications/{applicationId}/importable-users`](#723-list-importable-users) — List importable users
  - [POST `/api/admin/v1/tenant-workspace/users/roster/bulk`](#724-bulk-roster) — Bulk roster
  - [POST `/api/admin/v1/tenant-workspace/users/{userId}/applications/{applicationId}`](#725-give-a-user-access-to-an-application) — Give a user access to an application
  - [DELETE `/api/admin/v1/tenant-workspace/users/{userId}/applications/{applicationId}`](#726-remove-a-users-access-to-an-application) — Remove a user's access to an application
  - [PUT `/api/admin/v1/tenant-workspace/users/{userId}/applications/{applicationId}/roles`](#727-update-roles) — Update roles
  - [POST `/api/admin/v1/tenant-workspace/users/{userId}/roster`](#728-roster) — Roster
  - [DELETE `/api/admin/v1/tenant-workspace/users/{userId}/roster`](#729-remove-roster) — Remove roster
  - [POST `/api/admin/v1/tenant-workspace/users/{userId}/tenant-admin/{applicationId}`](#7210-make-a-user-tenant-admin-of-an-application) — Make a user tenant admin of an application
  - [DELETE `/api/admin/v1/tenant-workspace/users/{userId}/tenant-admin/{applicationId}`](#7211-remove-a-users-tenant-admin-role) — Remove a user's tenant admin role
- [7.3 Tenant RBAC](#73-tenant-rbac)
  - [GET `/api/admin/v1/tenants/{tenantId}/tenant-rbac/permissions`](#731-list-permissions) — List permissions
  - [POST `/api/admin/v1/tenants/{tenantId}/tenant-rbac/permissions`](#732-create-permission) — Create permission
  - [DELETE `/api/admin/v1/tenants/{tenantId}/tenant-rbac/permissions/{permissionId}`](#733-remove-permission) — Remove permission
  - [GET `/api/admin/v1/tenants/{tenantId}/tenant-rbac/roles`](#734-list-roles) — List roles
  - [POST `/api/admin/v1/tenants/{tenantId}/tenant-rbac/roles`](#735-create-role) — Create role
  - [GET `/api/admin/v1/tenants/{tenantId}/tenant-rbac/roles/{roleId}`](#736-get-role) — Get role
  - [PUT `/api/admin/v1/tenants/{tenantId}/tenant-rbac/roles/{roleId}`](#737-update-role) — Update role
  - [DELETE `/api/admin/v1/tenants/{tenantId}/tenant-rbac/roles/{roleId}`](#738-remove-role) — Remove role
  - [GET `/api/admin/v1/tenants/{tenantId}/tenant-rbac/users/{userId}/role-assignments`](#739-list-role-assignments) — List role assignments
  - [PUT `/api/admin/v1/tenants/{tenantId}/tenant-rbac/users/{userId}/role-assignments`](#7310-update-role-assignments) — Update role assignments
- [7.4 Tenant console roles](#74-tenant-console-roles)
  - [GET `/api/admin/v1/tenants/{tenantId}/console-roles`](#741-list-console-roles) — List console roles
  - [PUT `/api/admin/v1/tenants/{tenantId}/console-roles/{roleKey}/features`](#742-update-features) — Update features
- [7.5 Console access](#75-console-access)
  - [GET `/api/admin/v1/tenants/{tenantId}/console-access`](#751-list-console-access) — List console access
  - [POST `/api/admin/v1/tenants/{tenantId}/console-access`](#752-create-console-access) — Create console access
  - [DELETE `/api/admin/v1/tenants/{tenantId}/console-access/{assignmentId}`](#753-remove-console-access) — Remove console access
- [7.6 Console capabilities](#76-console-capabilities)
  - [GET `/api/admin/v1/tenants/{tenantId}/users/{userId}/console-capabilities`](#761-list-console-capabilities) — List console capabilities
  - [PUT `/api/admin/v1/tenants/{tenantId}/users/{userId}/console-capabilities`](#762-update-console-capabilities) — Update console capabilities
  - [GET `/api/admin/v1/tenants/{tenantId}/users/{userId}/console-capabilities/catalog`](#763-get-catalog) — Get catalog
- [7.7 Access requests](#77-access-requests)
  - [GET `/api/admin/v1/tenants/{tenantId}/access-requests`](#771-list-access-requests) — List access requests
  - [POST `/api/admin/v1/tenants/{tenantId}/access-requests`](#772-create-access-request) — Create access request
  - [POST `/api/admin/v1/tenants/{tenantId}/access-requests/{id}/approve`](#773-approve-access-request) — Approve access request
  - [POST `/api/admin/v1/tenants/{tenantId}/access-requests/{id}/deny`](#774-deny) — Deny
- [7.8 Tenant SCIM](#78-tenant-scim)
  - [GET `/api/admin/v1/tenants/{tenantId}/scim`](#781-get-scim) — Get SCIM
  - [POST `/api/admin/v1/tenants/{tenantId}/scim/token`](#782-create-the-tenants-scim-token) — Create the tenant's SCIM token
  - [DELETE `/api/admin/v1/tenants/{tenantId}/scim/token`](#783-revoke-the-tenants-scim-token) — Revoke the tenant's SCIM token
- [7.9 SCIM](#79-scim)
  - [GET `/api/admin/v1/settings/scim`](#791-get-scim) — Get SCIM
  - [PUT `/api/admin/v1/settings/scim`](#792-update-scim) — Update SCIM
- [7.10 Deletion requests](#710-deletion-requests)
  - [GET `/api/admin/v1/tenants/{tenantId}/deletion-requests`](#7101-list-deletion-requests) — List deletion requests
  - [POST `/api/admin/v1/tenants/{tenantId}/deletion-requests/{userId}/cancel`](#7102-cancel) — Cancel
  - [POST `/api/admin/v1/tenants/{tenantId}/deletion-requests/{userId}/complete`](#7103-complete-deletion-request) — Complete deletion request

## 7.1 Tenants

Multi-tenant organizations

### 7.1.1 List tenants

`GET /api/admin/v1/tenants`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [TenantResponse](13-data-models.md#tenantresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.1.2 Create tenant

`POST /api/admin/v1/tenants`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [TenantCreateRequest](13-data-models.md#tenantcreaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | yes |  |
| `plan` | string | no |  |
| `slug` | string | no |  |
| `status` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | [TenantResponse](13-data-models.md#tenantresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenants" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "name": "Acme Shop",
  "plan": "enterprise",
  "slug": "acme",
  "status": "ACTIVE"
}'
```

### 7.1.3 Get tenant

`GET /api/admin/v1/tenants/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [TenantResponse](13-data-models.md#tenantresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.1.4 Update tenant

`PUT /api/admin/v1/tenants/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`, [TenantUpdateRequest](13-data-models.md#tenantupdaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | yes |  |
| `plan` | string | no |  |
| `slug` | string | no |  |
| `status` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [TenantResponse](13-data-models.md#tenantresponse) |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/tenants/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "name": "Acme Shop",
  "plan": "enterprise",
  "slug": "acme",
  "status": "ACTIVE"
}'
```

### 7.1.5 Delete tenant

`DELETE /api/admin/v1/tenants/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |
| `confirm` | query | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/tenants/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.1.6 List admin operators

`GET /api/admin/v1/tenants/{tenantId}/admin-operators`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [TenantAdminOperator](13-data-models.md#tenantadminoperator)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/admin-operators" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.1.7 Make a user tenant admin of an application

`POST /api/admin/v1/tenants/{tenantId}/admin-operators/{userId}/applications/{applicationId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/admin-operators/{userId}/applications/{applicationId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.1.8 Remove a user's tenant admin role for an application

`DELETE /api/admin/v1/tenants/{tenantId}/admin-operators/{userId}/applications/{applicationId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/admin-operators/{userId}/applications/{applicationId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.1.9 Effective tenant defaults, the tenant's own overrides and locks, and the platform's locks

`GET /api/admin/v1/tenants/{tenantId}/two-step-policy`

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
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/two-step-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.1.10 Save the tenant's defaults and locks (weaker values or removed locks wait for a platform operator)

`PUT /api/admin/v1/tenants/{tenantId}/two-step-policy`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/two-step-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.1.11 Back to the platform policy (waits for a platform operator when that is weaker)

`DELETE /api/admin/v1/tenants/{tenantId}/two-step-policy`

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
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/two-step-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## 7.2 Tenant workspace

Tenant operator workspace (users, application access)

### 7.2.1 Get tenant workspace

`GET /api/admin/v1/tenant-workspace`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | query | string (uuid) | no | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [TenantWorkspaceResponse](13-data-models.md#tenantworkspaceresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenant-workspace" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.2.2 List admin operators

`GET /api/admin/v1/tenant-workspace/admin-operators`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | query | string (uuid) | yes | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [TenantAdminOperator](13-data-models.md#tenantadminoperator)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenant-workspace/admin-operators?tenantId={tenantId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.2.3 List importable users

`GET /api/admin/v1/tenant-workspace/applications/{applicationId}/importable-users`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | query | string (uuid) | no | Tenant ID (UUID) |
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [TenantWorkspaceUser](13-data-models.md#tenantworkspaceuser)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenant-workspace/applications/{applicationId}/importable-users" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.2.4 Bulk roster

`POST /api/admin/v1/tenant-workspace/users/roster/bulk`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | query | string (uuid) | no | Tenant ID (UUID) |
| `applicationId` | query | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [BulkRosterImportRequest](13-data-models.md#bulkrosterimportrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `userIds` | string (uuid)[] | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenant-workspace/users/roster/bulk?applicationId={applicationId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "userIds": [
    "{userId}"
  ]
}'
```

### 7.2.5 Give a user access to an application

`POST /api/admin/v1/tenant-workspace/users/{userId}/applications/{applicationId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | query | string (uuid) | no | Tenant ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenant-workspace/users/{userId}/applications/{applicationId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.2.6 Remove a user's access to an application

`DELETE /api/admin/v1/tenant-workspace/users/{userId}/applications/{applicationId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | query | string (uuid) | no | Tenant ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/tenant-workspace/users/{userId}/applications/{applicationId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.2.7 Update roles

`PUT /api/admin/v1/tenant-workspace/users/{userId}/applications/{applicationId}/roles`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | query | string (uuid) | yes | Tenant ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [UpdateApplicationRolesRequest](13-data-models.md#updateapplicationrolesrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `roleIds` | string (uuid)[] | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/tenant-workspace/users/{userId}/applications/{applicationId}/roles?tenantId={tenantId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "roleIds": [
    "{roleId}"
  ]
}'
```

### 7.2.8 Roster

`POST /api/admin/v1/tenant-workspace/users/{userId}/roster`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | query | string (uuid) | no | Tenant ID (UUID) |
| `applicationId` | query | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenant-workspace/users/{userId}/roster?applicationId={applicationId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.2.9 Remove roster

`DELETE /api/admin/v1/tenant-workspace/users/{userId}/roster`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | query | string (uuid) | no | Tenant ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/tenant-workspace/users/{userId}/roster" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.2.10 Make a user tenant admin of an application

`POST /api/admin/v1/tenant-workspace/users/{userId}/tenant-admin/{applicationId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `userId` | path | string (uuid) | yes | User ID (UUID) |
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenant-workspace/users/{userId}/tenant-admin/{applicationId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.2.11 Remove a user's tenant admin role

`DELETE /api/admin/v1/tenant-workspace/users/{userId}/tenant-admin/{applicationId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `userId` | path | string (uuid) | yes | User ID (UUID) |
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/tenant-workspace/users/{userId}/tenant-admin/{applicationId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## 7.3 Tenant RBAC

Tenant-scoped roles and permissions (platform governance)

### 7.3.1 List permissions

`GET /api/admin/v1/tenants/{tenantId}/tenant-rbac/permissions`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [TenantPermissionResponse](13-data-models.md#tenantpermissionresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/tenant-rbac/permissions" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.3.2 Create permission

`POST /api/admin/v1/tenants/{tenantId}/tenant-rbac/permissions`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Request body** (`application/json`, [TenantPermissionCreateRequest](13-data-models.md#tenantpermissioncreaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | no |  |
| `key` | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | [TenantPermissionResponse](13-data-models.md#tenantpermissionresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/tenant-rbac/permissions" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "description": "Short explanation",
  "key": "orders:read"
}'
```

### 7.3.3 Remove permission

`DELETE /api/admin/v1/tenants/{tenantId}/tenant-rbac/permissions/{permissionId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `permissionId` | path | string (uuid) | yes | Permission ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/tenant-rbac/permissions/{permissionId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.3.4 List roles

`GET /api/admin/v1/tenants/{tenantId}/tenant-rbac/roles`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `customOnly` | query | boolean | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [TenantRoleSummaryResponse](13-data-models.md#tenantrolesummaryresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/tenant-rbac/roles" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.3.5 Create role

`POST /api/admin/v1/tenants/{tenantId}/tenant-rbac/roles`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Request body** (`application/json`, [TenantRoleCreateRequest](13-data-models.md#tenantrolecreaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationIds` | string (uuid)[] | no |  |
| `description` | string | no |  |
| `name` | string | yes |  |
| `permissionIds` | string (uuid)[] | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | [TenantRoleDetailResponse](13-data-models.md#tenantroledetailresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/tenant-rbac/roles" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "applicationIds": [
    "{applicationId}"
  ],
  "description": "Short explanation",
  "name": "Acme Shop",
  "permissionIds": [
    "{permissionId}"
  ]
}'
```

### 7.3.6 Get role

`GET /api/admin/v1/tenants/{tenantId}/tenant-rbac/roles/{roleId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `roleId` | path | string (uuid) | yes | Role ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [TenantRoleDetailResponse](13-data-models.md#tenantroledetailresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/tenant-rbac/roles/{roleId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.3.7 Update role

`PUT /api/admin/v1/tenants/{tenantId}/tenant-rbac/roles/{roleId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `roleId` | path | string (uuid) | yes | Role ID (UUID) |

**Request body** (`application/json`, [TenantRoleUpdateRequest](13-data-models.md#tenantroleupdaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationIds` | string (uuid)[] | no |  |
| `description` | string | no |  |
| `name` | string | yes |  |
| `permissionIds` | string (uuid)[] | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [TenantRoleDetailResponse](13-data-models.md#tenantroledetailresponse) |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/tenant-rbac/roles/{roleId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "applicationIds": [
    "{applicationId}"
  ],
  "description": "Short explanation",
  "name": "Acme Shop",
  "permissionIds": [
    "{permissionId}"
  ]
}'
```

### 7.3.8 Remove role

`DELETE /api/admin/v1/tenants/{tenantId}/tenant-rbac/roles/{roleId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `roleId` | path | string (uuid) | yes | Role ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/tenant-rbac/roles/{roleId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.3.9 List role assignments

`GET /api/admin/v1/tenants/{tenantId}/tenant-rbac/users/{userId}/role-assignments`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserTenantRoleAssignmentsResponse](13-data-models.md#usertenantroleassignmentsresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/tenant-rbac/users/{userId}/role-assignments" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.3.10 Update role assignments

`PUT /api/admin/v1/tenants/{tenantId}/tenant-rbac/users/{userId}/role-assignments`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Request body** (`application/json`, [ReplaceUserTenantRolesRequest](13-data-models.md#replaceusertenantrolesrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `roleIds` | string (uuid)[] | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserTenantRoleAssignmentsResponse](13-data-models.md#usertenantroleassignmentsresponse) |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/tenant-rbac/users/{userId}/role-assignments" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "roleIds": [
    "{roleId}"
  ]
}'
```

## 7.4 Tenant console roles

Console operator roles and default permissions for a tenant

### 7.4.1 List console roles

`GET /api/admin/v1/tenants/{tenantId}/console-roles`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [TenantConsoleRolesCatalogResponse](13-data-models.md#tenantconsolerolescatalogresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/console-roles" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.4.2 Update features

`PUT /api/admin/v1/tenants/{tenantId}/console-roles/{roleKey}/features`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `roleKey` | path | string | yes |  |

**Request body** (`application/json`, [UpdateConsoleRoleFeaturesRequest](13-data-models.md#updateconsolerolefeaturesrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `features` | string[] | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | string[] |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/console-roles/{roleKey}/features" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "features": [
    "feature"
  ]
}'
```

## 7.5 Console access

Who may sign in to SecureOne Admin and at which scope

### 7.5.1 List console access

`GET /api/admin/v1/tenants/{tenantId}/console-access`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ConsoleAccessAssignment](13-data-models.md#consoleaccessassignment)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/console-access" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.5.2 Create console access

`POST /api/admin/v1/tenants/{tenantId}/console-access`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Request body** (`application/json`, [GrantConsoleAccessRequest](13-data-models.md#grantconsoleaccessrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `roleType` | string | no |  |
| `userId` | string (uuid) | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ConsoleAccessAssignment](13-data-models.md#consoleaccessassignment) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/console-access" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "applicationId": "{applicationId}",
  "roleType": "custom",
  "userId": "{userId}"
}'
```

### 7.5.3 Remove console access

`DELETE /api/admin/v1/tenants/{tenantId}/console-access/{assignmentId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `assignmentId` | path | string (uuid) | yes | Console access assignment ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/console-access/{assignmentId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## 7.6 Console capabilities

Feature matrix and per-user overrides for tenant console operators

### 7.6.1 List console capabilities

`GET /api/admin/v1/tenants/{tenantId}/users/{userId}/console-capabilities`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserConsoleCapabilities](13-data-models.md#userconsolecapabilities) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/users/{userId}/console-capabilities" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.6.2 Update console capabilities

`PUT /api/admin/v1/tenants/{tenantId}/users/{userId}/console-capabilities`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Request body** (`application/json`, [UpdateOverridesRequest](13-data-models.md#updateoverridesrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `overrides` | [FeatureOverride](13-data-models.md#featureoverride)[] | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserConsoleCapabilities](13-data-models.md#userconsolecapabilities) |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/users/{userId}/console-capabilities" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "overrides": [
    {
      "effect": "allow",
      "featureKey": "key"
    }
  ]
}'
```

### 7.6.3 Get catalog

`GET /api/admin/v1/tenants/{tenantId}/users/{userId}/console-capabilities/catalog`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/users/{userId}/console-capabilities/catalog" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## 7.7 Access requests

Identity governance access request workflow (stub)

### 7.7.1 List access requests

`GET /api/admin/v1/tenants/{tenantId}/access-requests`

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
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/access-requests" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.7.2 Create access request

`POST /api/admin/v1/tenants/{tenantId}/access-requests`

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
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/access-requests" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.7.3 Approve access request

`POST /api/admin/v1/tenants/{tenantId}/access-requests/{id}/approve`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `id` | path | string (uuid) | yes | ID of the item (UUID) |
| `reviewerId` | query | string (uuid) | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/access-requests/{id}/approve" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.7.4 Deny

`POST /api/admin/v1/tenants/{tenantId}/access-requests/{id}/deny`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `id` | path | string (uuid) | yes | ID of the item (UUID) |
| `reviewerId` | query | string (uuid) | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/access-requests/{id}/deny" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## 7.8 Tenant SCIM

SCIM 2.0 provisioning token for one tenant

### 7.8.1 Get SCIM

`GET /api/admin/v1/tenants/{tenantId}/scim`

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
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/scim" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.8.2 Create the tenant's SCIM token

`POST /api/admin/v1/tenants/{tenantId}/scim/token`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/scim/token" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.8.3 Revoke the tenant's SCIM token

`DELETE /api/admin/v1/tenants/{tenantId}/scim/token`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/scim/token" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## 7.9 SCIM

Platform SCIM provisioning settings

### 7.9.1 Get SCIM

`GET /api/admin/v1/settings/scim`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/scim" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.9.2 Update SCIM

`PUT /api/admin/v1/settings/scim`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/scim" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## 7.10 Deletion requests

Users who asked for their account to be deleted

### 7.10.1 List deletion requests

`GET /api/admin/v1/tenants/{tenantId}/deletion-requests`

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
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/deletion-requests" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.10.2 Cancel

`POST /api/admin/v1/tenants/{tenantId}/deletion-requests/{userId}/cancel`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/deletion-requests/{userId}/cancel" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 7.10.3 Complete deletion request

`POST /api/admin/v1/tenants/{tenantId}/deletion-requests/{userId}/complete`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/deletion-requests/{userId}/complete" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```
