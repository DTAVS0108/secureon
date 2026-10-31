---
title: Admin API: tenants
summary: Tenants and everything inside one: the tenant workspace (roster, application access), tenant roles, console roles and access, access requests and the tenant's SCIM token.
order: 9
audience: public
---

# Admin API: tenants

[API reference](index.md) › Admin API: tenants

<!-- developer -->
Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.
<!-- /developer -->

Tenants and everything inside one: the tenant workspace (roster, application access), tenant roles, console roles and access, access requests and the tenant's SCIM token.

In Swagger UI (`/docs`), choose the definition **Admin — tenants and users** (`/v3/api-docs/admin-tenants`).

Set the variables from [Getting started](getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [Tenants](#tenants)
  - [GET `/api/admin/v1/tenants`](#list-tenants) — List tenants
  - [POST `/api/admin/v1/tenants`](#create-tenant) — Create tenant
  - [GET `/api/admin/v1/tenants/{id}`](#get-tenant) — Get tenant
  - [PUT `/api/admin/v1/tenants/{id}`](#update-tenant) — Update tenant
  - [DELETE `/api/admin/v1/tenants/{id}`](#delete-tenant) — Delete tenant
  - [GET `/api/admin/v1/tenants/{tenantId}/admin-operators`](#list-admin-operators) — List admin operators
  - [POST `/api/admin/v1/tenants/{tenantId}/admin-operators/{userId}/applications/{applicationId}`](#make-a-user-tenant-admin-of-an-application) — Make a user tenant admin of an application
  - [DELETE `/api/admin/v1/tenants/{tenantId}/admin-operators/{userId}/applications/{applicationId}`](#remove-a-users-tenant-admin-role-for-an-application) — Remove a user's tenant admin role for an application
  - [GET `/api/admin/v1/tenants/{tenantId}/two-step-policy`](#effective-tenant-defaults-the-tenants-own-overrides-and-locks-and-the-platforms-locks) — Effective tenant defaults, the tenant's own overrides and locks, and the platform's locks
  - [PUT `/api/admin/v1/tenants/{tenantId}/two-step-policy`](#save-the-tenants-defaults-and-locks-weaker-values-or-removed-locks-wait-for-a-platform-operator) — Save the tenant's defaults and locks (weaker values or removed locks wait for a platform operator)
  - [DELETE `/api/admin/v1/tenants/{tenantId}/two-step-policy`](#back-to-the-platform-policy-waits-for-a-platform-operator-when-that-is-weaker) — Back to the platform policy (waits for a platform operator when that is weaker)
- [Tenant workspace](#tenant-workspace)
  - [GET `/api/admin/v1/tenant-workspace`](#get-tenant-workspace) — Get tenant workspace
  - [GET `/api/admin/v1/tenant-workspace/admin-operators`](#list-admin-operators-1) — List admin operators
  - [GET `/api/admin/v1/tenant-workspace/applications/{applicationId}/importable-users`](#list-importable-users) — List importable users
  - [POST `/api/admin/v1/tenant-workspace/users/roster/bulk`](#bulk-roster) — Bulk roster
  - [POST `/api/admin/v1/tenant-workspace/users/{userId}/applications/{applicationId}`](#give-a-user-access-to-an-application) — Give a user access to an application
  - [DELETE `/api/admin/v1/tenant-workspace/users/{userId}/applications/{applicationId}`](#remove-a-users-access-to-an-application) — Remove a user's access to an application
  - [PUT `/api/admin/v1/tenant-workspace/users/{userId}/applications/{applicationId}/roles`](#update-roles) — Update roles
  - [POST `/api/admin/v1/tenant-workspace/users/{userId}/roster`](#roster) — Roster
  - [DELETE `/api/admin/v1/tenant-workspace/users/{userId}/roster`](#remove-roster) — Remove roster
  - [POST `/api/admin/v1/tenant-workspace/users/{userId}/tenant-admin/{applicationId}`](#make-a-user-tenant-admin-of-an-application-1) — Make a user tenant admin of an application
  - [DELETE `/api/admin/v1/tenant-workspace/users/{userId}/tenant-admin/{applicationId}`](#remove-a-users-tenant-admin-role) — Remove a user's tenant admin role
- [Tenant RBAC](#tenant-rbac)
  - [GET `/api/admin/v1/tenants/{tenantId}/tenant-rbac/permissions`](#list-permissions) — List permissions
  - [POST `/api/admin/v1/tenants/{tenantId}/tenant-rbac/permissions`](#create-permission) — Create permission
  - [DELETE `/api/admin/v1/tenants/{tenantId}/tenant-rbac/permissions/{permissionId}`](#remove-permission) — Remove permission
  - [GET `/api/admin/v1/tenants/{tenantId}/tenant-rbac/roles`](#list-roles) — List roles
  - [POST `/api/admin/v1/tenants/{tenantId}/tenant-rbac/roles`](#create-role) — Create role
  - [GET `/api/admin/v1/tenants/{tenantId}/tenant-rbac/roles/{roleId}`](#get-role) — Get role
  - [PUT `/api/admin/v1/tenants/{tenantId}/tenant-rbac/roles/{roleId}`](#update-role) — Update role
  - [DELETE `/api/admin/v1/tenants/{tenantId}/tenant-rbac/roles/{roleId}`](#remove-role) — Remove role
  - [GET `/api/admin/v1/tenants/{tenantId}/tenant-rbac/users/{userId}/role-assignments`](#list-role-assignments) — List role assignments
  - [PUT `/api/admin/v1/tenants/{tenantId}/tenant-rbac/users/{userId}/role-assignments`](#update-role-assignments) — Update role assignments
- [Tenant console roles](#tenant-console-roles)
  - [GET `/api/admin/v1/tenants/{tenantId}/console-roles`](#list-console-roles) — List console roles
  - [PUT `/api/admin/v1/tenants/{tenantId}/console-roles/{roleKey}/features`](#update-features) — Update features
- [Console access](#console-access)
  - [GET `/api/admin/v1/tenants/{tenantId}/console-access`](#list-console-access) — List console access
  - [POST `/api/admin/v1/tenants/{tenantId}/console-access`](#create-console-access) — Create console access
  - [DELETE `/api/admin/v1/tenants/{tenantId}/console-access/{assignmentId}`](#remove-console-access) — Remove console access
- [Console capabilities](#console-capabilities)
  - [GET `/api/admin/v1/tenants/{tenantId}/users/{userId}/console-capabilities`](#list-console-capabilities) — List console capabilities
  - [PUT `/api/admin/v1/tenants/{tenantId}/users/{userId}/console-capabilities`](#update-console-capabilities) — Update console capabilities
  - [GET `/api/admin/v1/tenants/{tenantId}/users/{userId}/console-capabilities/catalog`](#get-catalog) — Get catalog
- [Access requests](#access-requests)
  - [GET `/api/admin/v1/tenants/{tenantId}/access-requests`](#list-access-requests) — List access requests
  - [POST `/api/admin/v1/tenants/{tenantId}/access-requests`](#create-access-request) — Create access request
  - [POST `/api/admin/v1/tenants/{tenantId}/access-requests/{id}/approve`](#approve-access-request) — Approve access request
  - [POST `/api/admin/v1/tenants/{tenantId}/access-requests/{id}/deny`](#deny) — Deny
- [Tenant SCIM](#tenant-scim)
  - [GET `/api/admin/v1/tenants/{tenantId}/scim`](#get-scim) — Get SCIM
  - [POST `/api/admin/v1/tenants/{tenantId}/scim/token`](#create-the-tenants-scim-token) — Create the tenant's SCIM token
  - [DELETE `/api/admin/v1/tenants/{tenantId}/scim/token`](#revoke-the-tenants-scim-token) — Revoke the tenant's SCIM token
- [SCIM](#scim)
  - [GET `/api/admin/v1/settings/scim`](#get-scim-1) — Get SCIM
  - [PUT `/api/admin/v1/settings/scim`](#update-scim) — Update SCIM
- [Deletion requests](#deletion-requests)
  - [GET `/api/admin/v1/tenants/{tenantId}/deletion-requests`](#list-deletion-requests) — List deletion requests
  - [POST `/api/admin/v1/tenants/{tenantId}/deletion-requests/{userId}/cancel`](#cancel) — Cancel
  - [POST `/api/admin/v1/tenants/{tenantId}/deletion-requests/{userId}/complete`](#complete-deletion-request) — Complete deletion request
- [Tenant issuer and domains](#tenant-issuer-and-domains)
  - [GET `/api/admin/v1/tenants/{tenantId}/issuer`](#the-tenants-issuers-issuer-mode-and-custom-domains) — The tenant's issuers, issuer mode and custom domains
  - [PUT `/api/admin/v1/tenants/{tenantId}/issuer`](#issuer-mode-shared-the-shared-issuer-and-the-tenants-own-or-tenant-its-own-issuers-only) — Issuer mode: 'shared' (the shared issuer and the tenant's own) or 'tenant' (its own issuers only)
  - [POST `/api/admin/v1/tenants/{tenantId}/issuer/domains`](#register-a-custom-domain-pending-until-verified) — Register a custom domain (pending until verified)
  - [DELETE `/api/admin/v1/tenants/{tenantId}/issuer/domains/{domainId}`](#remove-a-custom-domain-it-stops-being-served-at-once) — Remove a custom domain (it stops being served at once)
  - [POST `/api/admin/v1/tenants/{tenantId}/issuer/domains/{domainId}/verify`](#check-a-custom-domain) — Check a custom domain

## Tenants

Two-step verification defaults of a tenant

### List tenants

`GET /api/admin/v1/tenants`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [TenantResponse](data-models.md#tenantresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Create tenant

`POST /api/admin/v1/tenants`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [TenantCreateRequest](data-models.md#tenantcreaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | yes |  |
| `plan` | string | no |  |
| `slug` | string | no |  |
| `status` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | [TenantResponse](data-models.md#tenantresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenants" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "name": "Tenant A Shop",
  "plan": "enterprise",
  "slug": "tenant-a",
  "status": "ACTIVE"
}'
```

### Get tenant

`GET /api/admin/v1/tenants/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [TenantResponse](data-models.md#tenantresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update tenant

`PUT /api/admin/v1/tenants/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`, [TenantUpdateRequest](data-models.md#tenantupdaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | yes |  |
| `plan` | string | no |  |
| `slug` | string | no |  |
| `status` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [TenantResponse](data-models.md#tenantresponse) |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/tenants/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "name": "Tenant A Shop",
  "plan": "enterprise",
  "slug": "tenant-a",
  "status": "ACTIVE"
}'
```

### Delete tenant

`DELETE /api/admin/v1/tenants/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### List admin operators

`GET /api/admin/v1/tenants/{tenantId}/admin-operators`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [TenantAdminOperator](data-models.md#tenantadminoperator)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/admin-operators" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Make a user tenant admin of an application

`POST /api/admin/v1/tenants/{tenantId}/admin-operators/{userId}/applications/{applicationId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### Remove a user's tenant admin role for an application

`DELETE /api/admin/v1/tenants/{tenantId}/admin-operators/{userId}/applications/{applicationId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### Effective tenant defaults, the tenant's own overrides and locks, and the platform's locks

`GET /api/admin/v1/tenants/{tenantId}/two-step-policy`

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
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/two-step-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Save the tenant's defaults and locks (weaker values or removed locks wait for a platform operator)

`PUT /api/admin/v1/tenants/{tenantId}/two-step-policy`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/two-step-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Back to the platform policy (waits for a platform operator when that is weaker)

`DELETE /api/admin/v1/tenants/{tenantId}/two-step-policy`

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
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/two-step-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## Tenant workspace

Tenant operator workspace (users, application access)

### Get tenant workspace

`GET /api/admin/v1/tenant-workspace`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | query | string (uuid) | no | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [TenantWorkspaceResponse](data-models.md#tenantworkspaceresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenant-workspace" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List admin operators

`GET /api/admin/v1/tenant-workspace/admin-operators`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | query | string (uuid) | yes | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [TenantAdminOperator](data-models.md#tenantadminoperator)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenant-workspace/admin-operators?tenantId={tenantId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List importable users

`GET /api/admin/v1/tenant-workspace/applications/{applicationId}/importable-users`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | query | string (uuid) | no | Tenant ID (UUID) |
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [TenantWorkspaceUser](data-models.md#tenantworkspaceuser)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenant-workspace/applications/{applicationId}/importable-users" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Bulk roster

`POST /api/admin/v1/tenant-workspace/users/roster/bulk`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | query | string (uuid) | no | Tenant ID (UUID) |
| `applicationId` | query | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [BulkRosterImportRequest](data-models.md#bulkrosterimportrequest))

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

### Give a user access to an application

`POST /api/admin/v1/tenant-workspace/users/{userId}/applications/{applicationId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### Remove a user's access to an application

`DELETE /api/admin/v1/tenant-workspace/users/{userId}/applications/{applicationId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### Update roles

`PUT /api/admin/v1/tenant-workspace/users/{userId}/applications/{applicationId}/roles`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | query | string (uuid) | yes | Tenant ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [UpdateApplicationRolesRequest](data-models.md#updateapplicationrolesrequest))

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

### Roster

`POST /api/admin/v1/tenant-workspace/users/{userId}/roster`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### Remove roster

`DELETE /api/admin/v1/tenant-workspace/users/{userId}/roster`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### Make a user tenant admin of an application

`POST /api/admin/v1/tenant-workspace/users/{userId}/tenant-admin/{applicationId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### Remove a user's tenant admin role

`DELETE /api/admin/v1/tenant-workspace/users/{userId}/tenant-admin/{applicationId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

## Tenant RBAC

Tenant-scoped roles and permissions (platform governance)

### List permissions

`GET /api/admin/v1/tenants/{tenantId}/tenant-rbac/permissions`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [TenantPermissionResponse](data-models.md#tenantpermissionresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/tenant-rbac/permissions" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Create permission

`POST /api/admin/v1/tenants/{tenantId}/tenant-rbac/permissions`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Request body** (`application/json`, [TenantPermissionCreateRequest](data-models.md#tenantpermissioncreaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | no |  |
| `key` | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | [TenantPermissionResponse](data-models.md#tenantpermissionresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/tenant-rbac/permissions" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "description": "Short explanation",
  "key": "orders:read"
}'
```

### Remove permission

`DELETE /api/admin/v1/tenants/{tenantId}/tenant-rbac/permissions/{permissionId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### List roles

`GET /api/admin/v1/tenants/{tenantId}/tenant-rbac/roles`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `customOnly` | query | boolean | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [TenantRoleSummaryResponse](data-models.md#tenantrolesummaryresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/tenant-rbac/roles" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Create role

`POST /api/admin/v1/tenants/{tenantId}/tenant-rbac/roles`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Request body** (`application/json`, [TenantRoleCreateRequest](data-models.md#tenantrolecreaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationIds` | string (uuid)[] | no |  |
| `description` | string | no |  |
| `name` | string | yes |  |
| `permissionIds` | string (uuid)[] | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | [TenantRoleDetailResponse](data-models.md#tenantroledetailresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/tenant-rbac/roles" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "applicationIds": [
    "{applicationId}"
  ],
  "description": "Short explanation",
  "name": "Tenant A Shop",
  "permissionIds": [
    "{permissionId}"
  ]
}'
```

### Get role

`GET /api/admin/v1/tenants/{tenantId}/tenant-rbac/roles/{roleId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `roleId` | path | string (uuid) | yes | Role ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [TenantRoleDetailResponse](data-models.md#tenantroledetailresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/tenant-rbac/roles/{roleId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update role

`PUT /api/admin/v1/tenants/{tenantId}/tenant-rbac/roles/{roleId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `roleId` | path | string (uuid) | yes | Role ID (UUID) |

**Request body** (`application/json`, [TenantRoleUpdateRequest](data-models.md#tenantroleupdaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationIds` | string (uuid)[] | no |  |
| `description` | string | no |  |
| `name` | string | yes |  |
| `permissionIds` | string (uuid)[] | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [TenantRoleDetailResponse](data-models.md#tenantroledetailresponse) |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/tenant-rbac/roles/{roleId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "applicationIds": [
    "{applicationId}"
  ],
  "description": "Short explanation",
  "name": "Tenant A Shop",
  "permissionIds": [
    "{permissionId}"
  ]
}'
```

### Remove role

`DELETE /api/admin/v1/tenants/{tenantId}/tenant-rbac/roles/{roleId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### List role assignments

`GET /api/admin/v1/tenants/{tenantId}/tenant-rbac/users/{userId}/role-assignments`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserTenantRoleAssignmentsResponse](data-models.md#usertenantroleassignmentsresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/tenant-rbac/users/{userId}/role-assignments" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update role assignments

`PUT /api/admin/v1/tenants/{tenantId}/tenant-rbac/users/{userId}/role-assignments`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Request body** (`application/json`, [ReplaceUserTenantRolesRequest](data-models.md#replaceusertenantrolesrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `roleIds` | string (uuid)[] | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserTenantRoleAssignmentsResponse](data-models.md#usertenantroleassignmentsresponse) |

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

## Tenant console roles

Console operator roles and default permissions for a tenant

### List console roles

`GET /api/admin/v1/tenants/{tenantId}/console-roles`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [TenantConsoleRolesCatalogResponse](data-models.md#tenantconsolerolescatalogresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/console-roles" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update features

`PUT /api/admin/v1/tenants/{tenantId}/console-roles/{roleKey}/features`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `roleKey` | path | string | yes |  |

**Request body** (`application/json`, [UpdateConsoleRoleFeaturesRequest](data-models.md#updateconsolerolefeaturesrequest))

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

## Console access

Who may sign in to SecureOne Admin and at which scope

### List console access

`GET /api/admin/v1/tenants/{tenantId}/console-access`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ConsoleAccessAssignment](data-models.md#consoleaccessassignment)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/console-access" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Create console access

`POST /api/admin/v1/tenants/{tenantId}/console-access`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Request body** (`application/json`, [GrantConsoleAccessRequest](data-models.md#grantconsoleaccessrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `roleType` | string | no |  |
| `userId` | string (uuid) | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ConsoleAccessAssignment](data-models.md#consoleaccessassignment) |

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

### Remove console access

`DELETE /api/admin/v1/tenants/{tenantId}/console-access/{assignmentId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

## Console capabilities

Feature matrix and per-user overrides for tenant console operators

### List console capabilities

`GET /api/admin/v1/tenants/{tenantId}/users/{userId}/console-capabilities`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserConsoleCapabilities](data-models.md#userconsolecapabilities) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/users/{userId}/console-capabilities" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update console capabilities

`PUT /api/admin/v1/tenants/{tenantId}/users/{userId}/console-capabilities`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Request body** (`application/json`, [UpdateOverridesRequest](data-models.md#updateoverridesrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `overrides` | [FeatureOverride](data-models.md#featureoverride)[] | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserConsoleCapabilities](data-models.md#userconsolecapabilities) |

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

### Get catalog

`GET /api/admin/v1/tenants/{tenantId}/users/{userId}/console-capabilities/catalog`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

## Access requests

Identity governance access request workflow (stub)

### List access requests

`GET /api/admin/v1/tenants/{tenantId}/access-requests`

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
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/access-requests" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Create access request

`POST /api/admin/v1/tenants/{tenantId}/access-requests`

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
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/access-requests" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Approve access request

`POST /api/admin/v1/tenants/{tenantId}/access-requests/{id}/approve`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### Deny

`POST /api/admin/v1/tenants/{tenantId}/access-requests/{id}/deny`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

## Tenant SCIM

SCIM 2.0 provisioning token for one tenant

### Get SCIM

`GET /api/admin/v1/tenants/{tenantId}/scim`

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
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/scim" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Create the tenant's SCIM token

`POST /api/admin/v1/tenants/{tenantId}/scim/token`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### Revoke the tenant's SCIM token

`DELETE /api/admin/v1/tenants/{tenantId}/scim/token`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

## SCIM

Platform SCIM provisioning settings

### Get SCIM

`GET /api/admin/v1/settings/scim`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/scim" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update SCIM

`PUT /api/admin/v1/settings/scim`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/scim" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## Deletion requests

Users who asked for their account to be deleted

### List deletion requests

`GET /api/admin/v1/tenants/{tenantId}/deletion-requests`

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
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/deletion-requests" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Cancel

`POST /api/admin/v1/tenants/{tenantId}/deletion-requests/{userId}/cancel`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

### Complete deletion request

`POST /api/admin/v1/tenants/{tenantId}/deletion-requests/{userId}/complete`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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

## Tenant issuer and domains

Issuer per tenant and custom domains

### The tenant's issuers, issuer mode and custom domains

`GET /api/admin/v1/tenants/{tenantId}/issuer`

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
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/issuer" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Issuer mode: 'shared' (the shared issuer and the tenant's own) or 'tenant' (its own issuers only)

`PUT /api/admin/v1/tenants/{tenantId}/issuer`

With 'tenant', the tenant's apps must use their tenant issuer: sign-ins and tokens on the shared issuer are refused.

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/issuer" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Register a custom domain (pending until verified)

`POST /api/admin/v1/tenants/{tenantId}/issuer/domains`

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
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/issuer/domains" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Remove a custom domain (it stops being served at once)

`DELETE /api/admin/v1/tenants/{tenantId}/issuer/domains/{domainId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `domainId` | path | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/issuer/domains/{domainId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Check a custom domain

`POST /api/admin/v1/tenants/{tenantId}/issuer/domains/{domainId}/verify`

Verified when the TXT record _secureone-challenge.<domain> holds the token, or the domain already points at SecureOne. Otherwise lastError says what is missing.

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |
| `domainId` | path | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/issuer/domains/{domainId}/verify" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```
