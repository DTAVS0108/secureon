---
title: Admin API: applications and OAuth clients
summary: Application products, their OAuth clients, and changes that wait for a second admin (deleting an OAuth client returns 202 with a change request; another admin approves it).
order: 11
audience: public
---

# Admin API: applications and OAuth clients

[API reference](index.md) › Admin API: applications and OAuth clients

> Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.

Application products, their OAuth clients, and changes that wait for a second admin (deleting an OAuth client returns 202 with a change request; another admin approves it).

In Swagger UI (`/docs`), choose the definition **Admin — applications and clients** (`/v3/api-docs/admin-applications`).

Set the variables from [Getting started](getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [Applications](#applications)
  - [GET `/api/admin/v1/applications`](#list-applications) — List applications
  - [POST `/api/admin/v1/applications`](#create-application) — Create application
  - [GET `/api/admin/v1/applications/{id}`](#get-application) — Get application
  - [PUT `/api/admin/v1/applications/{id}`](#update-application) — Update application
  - [DELETE `/api/admin/v1/applications/{id}`](#delete-application) — Delete application
  - [GET `/api/admin/v1/applications/{id}/delete-request`](#get-delete-request) — Get delete request
  - [POST `/api/admin/v1/applications/{id}/isolate`](#isolate-application) — Isolate application
- [OAuth clients](#oauth-clients)
  - [GET `/api/admin/v1/oauth-clients`](#list-oauth-clients) — List OAuth clients
  - [POST `/api/admin/v1/oauth-clients`](#create-oauth-client) — Create OAuth client
  - [GET `/api/admin/v1/oauth-clients/{id}`](#get-oauth-client) — Get OAuth client
  - [PUT `/api/admin/v1/oauth-clients/{id}`](#update-oauth-client) — Update OAuth client
  - [DELETE `/api/admin/v1/oauth-clients/{id}`](#request-deletion-of-an-oauth-client-needs-approval) — Request deletion of an OAuth client (needs approval)
  - [GET `/api/admin/v1/oauth-clients/{id}/delete-request`](#get-the-pending-delete-request-of-an-oauth-client) — Get the pending delete request of an OAuth client
  - [POST `/api/admin/v1/oauth-clients/{id}/rotate-secret`](#rotate-oauth-client-secret) — Rotate OAuth client secret
- [Change approvals](#change-approvals)
  - [GET `/api/admin/v1/change-requests`](#list-change-requests-every-tenant) — List change requests (every tenant)
  - [POST `/api/admin/v1/change-requests/{id}/approve`](#approve-change-request) — Approve change request
  - [POST `/api/admin/v1/change-requests/{id}/reject`](#reject-change-request) — Reject change request
  - [POST `/api/admin/v1/change-requests/{id}/withdraw`](#withdraw-change-request) — Withdraw change request
  - [GET `/api/admin/v1/tenants/{tenantId}/change-requests`](#list-change-requests-one-tenant) — List change requests (one tenant)
  - [GET `/api/admin/v1/tenants/{tenantId}/two-factor/review`](#two-factor-authentication-review-for-one-tenant) — Two Factor Authentication review for one tenant
  - [GET `/api/admin/v1/two-factor/review`](#two-factor-authentication-review-for-every-tenant-platform-operators) — Two Factor Authentication review for every tenant (platform operators)

## Applications

Register and manage application products (console scope)

### List applications

`GET /api/admin/v1/applications`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | query | string (uuid) | no | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ApplicationResponse](data-models.md#applicationresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Create application

`POST /api/admin/v1/applications`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [ApplicationCreateRequest](data-models.md#applicationcreaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | no |  |
| `name` | string | yes |  |
| `slug` | string | no |  |
| `status` | string | no |  |
| `tenantId` | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | [ApplicationResponse](data-models.md#applicationresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "description": "Short explanation",
  "name": "Tenant A Shop",
  "slug": "tenant-a",
  "status": "ACTIVE",
  "tenantId": "{tenantId}"
}'
```

### Get application

`GET /api/admin/v1/applications/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ApplicationResponse](data-models.md#applicationresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update application

`PUT /api/admin/v1/applications/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`, [ApplicationUpdateRequest](data-models.md#applicationupdaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | no |  |
| `name` | string | yes |  |
| `status` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ApplicationResponse](data-models.md#applicationresponse) |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "description": "Short explanation",
  "name": "Tenant A Shop",
  "status": "ACTIVE"
}'
```

### Delete application

`DELETE /api/admin/v1/applications/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |
| `confirm` | query | string | no |  |
| `reason` | query | string | no | Why the change is needed (shown to approvers) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Get delete request

`GET /api/admin/v1/applications/{id}/delete-request`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ChangeRequestView](data-models.md#changerequestview) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{id}/delete-request" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Isolate application

`POST /api/admin/v1/applications/{id}/isolate`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ApplicationResponse](data-models.md#applicationresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{id}/isolate" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## OAuth clients

Register and manage OAuth/OIDC client credentials

### List OAuth clients

`GET /api/admin/v1/oauth-clients`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | query | string (uuid) | no | Tenant ID (UUID) |
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [OAuthClientResponse](data-models.md#oauthclientresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/oauth-clients" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Create OAuth client

`POST /api/admin/v1/oauth-clients`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [OAuthClientCreateRequest](data-models.md#oauthclientcreaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `applicationName` | string | no |  |
| `backchannelLogoutUri` | string | no |  |
| `clientId` | string | no |  |
| `consentRequired` | boolean | no |  |
| `grantTypes` | string[] | no |  |
| `jwks` | object | no |  |
| `jwksUri` | string | no |  |
| `pkceRequired` | boolean | no |  |
| `postLogoutRedirectUris` | string[] | no |  |
| `redirectUris` | string[] | no |  |
| `scopes` | string[] | no |  |
| `setupFramework` | string | no |  |
| `status` | string | no |  |
| `subjectType` | string | no |  |
| `tenantId` | string (uuid) | no |  |
| `tokenEndpointAuthMethod` | string | no |  |
| `type` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | [OAuthClientCreateResult](data-models.md#oauthclientcreateresult) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/oauth-clients" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "applicationId": "{applicationId}",
  "applicationName": "Tenant A Shop",
  "backchannelLogoutUri": "https://app.example.com",
  "clientId": "{clientId}",
  "consentRequired": true,
  "grantTypes": [
    "authorization_code",
    "refresh_token"
  ],
  "jwks": {},
  "jwksUri": "https://app.example.com",
  "pkceRequired": true,
  "postLogoutRedirectUris": [
    "http://localhost:5173/"
  ],
  "redirectUris": [
    "http://localhost:5173/callback"
  ],
  "scopes": [
    "openid",
    "profile",
    "email"
  ],
  "setupFramework": "react",
  "status": "ACTIVE",
  "subjectType": "public",
  "tenantId": "{tenantId}",
  "tokenEndpointAuthMethod": "none",
  "type": "spa"
}'
```

### Get OAuth client

`GET /api/admin/v1/oauth-clients/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [OAuthClientResponse](data-models.md#oauthclientresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/oauth-clients/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update OAuth client

`PUT /api/admin/v1/oauth-clients/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`, [OAuthClientUpdateRequest](data-models.md#oauthclientupdaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `backchannelLogoutUri` | string | no |  |
| `consentRequired` | boolean | no |  |
| `grantTypes` | string[] | no |  |
| `jwks` | object | no |  |
| `jwksUri` | string | no |  |
| `pkceRequired` | boolean | no |  |
| `postLogoutRedirectUris` | string[] | no |  |
| `redirectUris` | string[] | no |  |
| `scopes` | string[] | no |  |
| `setupFramework` | string | no |  |
| `status` | string | no |  |
| `subjectType` | string | no |  |
| `tokenEndpointAuthMethod` | string | no |  |
| `type` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [OAuthClientResponse](data-models.md#oauthclientresponse) |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/oauth-clients/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "backchannelLogoutUri": "https://app.example.com",
  "consentRequired": true,
  "grantTypes": [
    "authorization_code",
    "refresh_token"
  ],
  "jwks": {},
  "jwksUri": "https://app.example.com",
  "pkceRequired": true,
  "postLogoutRedirectUris": [
    "http://localhost:5173/"
  ],
  "redirectUris": [
    "http://localhost:5173/callback"
  ],
  "scopes": [
    "openid",
    "profile",
    "email"
  ],
  "setupFramework": "react",
  "status": "ACTIVE",
  "subjectType": "public",
  "tokenEndpointAuthMethod": "none",
  "type": "spa"
}'
```

### Request deletion of an OAuth client (needs approval)

`DELETE /api/admin/v1/oauth-clients/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |
| `reason` | query | string | no | Why the change is needed (shown to approvers) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `202` | Accepted | [ChangeRequestView](data-models.md#changerequestview) |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/oauth-clients/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Get the pending delete request of an OAuth client

`GET /api/admin/v1/oauth-clients/{id}/delete-request`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ChangeRequestView](data-models.md#changerequestview) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/oauth-clients/{id}/delete-request" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Rotate OAuth client secret

`POST /api/admin/v1/oauth-clients/{id}/rotate-secret`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |
| `keepOldHours` | query | integer (int32) | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [OAuthClientSecretResponse](data-models.md#oauthclientsecretresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/oauth-clients/{id}/rotate-secret" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## Change approvals

High-impact changes that wait for a second admin

### List change requests (every tenant)

`GET /api/admin/v1/change-requests`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ChangeRequestView](data-models.md#changerequestview)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/change-requests" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Approve change request

`POST /api/admin/v1/change-requests/{id}/approve`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`, [DecisionRequest](data-models.md#decisionrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `note` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ChangeRequestView](data-models.md#changerequestview) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/change-requests/{id}/approve" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "note": "Short explanation"
}'
```

### Reject change request

`POST /api/admin/v1/change-requests/{id}/reject`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`, [DecisionRequest](data-models.md#decisionrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `note` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ChangeRequestView](data-models.md#changerequestview) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/change-requests/{id}/reject" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "note": "Short explanation"
}'
```

### Withdraw change request

`POST /api/admin/v1/change-requests/{id}/withdraw`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ChangeRequestView](data-models.md#changerequestview) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/change-requests/{id}/withdraw" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List change requests (one tenant)

`GET /api/admin/v1/tenants/{tenantId}/change-requests`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ChangeRequestView](data-models.md#changerequestview)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/change-requests" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Two Factor Authentication review for one tenant

`GET /api/admin/v1/tenants/{tenantId}/two-factor/review`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [Review](data-models.md#review) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/two-factor/review" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Two Factor Authentication review for every tenant (platform operators)

`GET /api/admin/v1/two-factor/review`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [Review](data-models.md#review) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/two-factor/review" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```
