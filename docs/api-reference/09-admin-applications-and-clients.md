# 9. Admin API: applications, OAuth clients and change approvals

[API reference](README.md) › Admin API: applications, OAuth clients and change approvals

> Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.

Application products, their OAuth clients, and changes that wait for a second admin (deleting an OAuth client returns 202 with a change request; another admin approves it).

Set the variables from [Getting started](00-getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [9.1 Applications](#91-applications)
  - [GET `/api/admin/v1/applications`](#911-list-applications) — List applications
  - [POST `/api/admin/v1/applications`](#912-create-application) — Create application
  - [GET `/api/admin/v1/applications/{id}`](#913-get-application) — Get application
  - [PUT `/api/admin/v1/applications/{id}`](#914-update-application) — Update application
  - [DELETE `/api/admin/v1/applications/{id}`](#915-delete-application) — Delete application
  - [GET `/api/admin/v1/applications/{id}/delete-request`](#916-get-delete-request) — Get delete request
  - [POST `/api/admin/v1/applications/{id}/isolate`](#917-isolate-application) — Isolate application
- [9.2 OAuth clients](#92-oauth-clients)
  - [GET `/api/admin/v1/oauth-clients`](#921-list-oauth-clients) — List OAuth clients
  - [POST `/api/admin/v1/oauth-clients`](#922-create-oauth-client) — Create OAuth client
  - [GET `/api/admin/v1/oauth-clients/{id}`](#923-get-oauth-client) — Get OAuth client
  - [PUT `/api/admin/v1/oauth-clients/{id}`](#924-update-oauth-client) — Update OAuth client
  - [DELETE `/api/admin/v1/oauth-clients/{id}`](#925-request-deletion-of-an-oauth-client-needs-approval) — Request deletion of an OAuth client (needs approval)
  - [GET `/api/admin/v1/oauth-clients/{id}/delete-request`](#926-get-the-pending-delete-request-of-an-oauth-client) — Get the pending delete request of an OAuth client
  - [POST `/api/admin/v1/oauth-clients/{id}/rotate-secret`](#927-rotate-oauth-client-secret) — Rotate OAuth client secret
- [9.3 Change approvals](#93-change-approvals)
  - [GET `/api/admin/v1/change-requests`](#931-list-change-requests-every-tenant) — List change requests (every tenant)
  - [POST `/api/admin/v1/change-requests/{id}/approve`](#932-approve-change-request) — Approve change request
  - [POST `/api/admin/v1/change-requests/{id}/reject`](#933-reject-change-request) — Reject change request
  - [POST `/api/admin/v1/change-requests/{id}/withdraw`](#934-withdraw-change-request) — Withdraw change request
  - [GET `/api/admin/v1/tenants/{tenantId}/change-requests`](#935-list-change-requests-one-tenant) — List change requests (one tenant)
  - [GET `/api/admin/v1/tenants/{tenantId}/two-factor/review`](#936-two-factor-authentication-review-for-one-tenant) — Two Factor Authentication review for one tenant
  - [GET `/api/admin/v1/two-factor/review`](#937-two-factor-authentication-review-for-every-tenant-platform-operators) — Two Factor Authentication review for every tenant (platform operators)

## 9.1 Applications

Register and manage application products (console scope)

### 9.1.1 List applications

`GET /api/admin/v1/applications`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | query | string (uuid) | no | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ApplicationResponse](13-data-models.md#applicationresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 9.1.2 Create application

`POST /api/admin/v1/applications`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [ApplicationCreateRequest](13-data-models.md#applicationcreaterequest))

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
| `201` | Created | [ApplicationResponse](13-data-models.md#applicationresponse) |

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

### 9.1.3 Get application

`GET /api/admin/v1/applications/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ApplicationResponse](13-data-models.md#applicationresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 9.1.4 Update application

`PUT /api/admin/v1/applications/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`, [ApplicationUpdateRequest](13-data-models.md#applicationupdaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | no |  |
| `name` | string | yes |  |
| `status` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ApplicationResponse](13-data-models.md#applicationresponse) |

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

### 9.1.5 Delete application

`DELETE /api/admin/v1/applications/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 9.1.6 Get delete request

`GET /api/admin/v1/applications/{id}/delete-request`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ChangeRequestView](13-data-models.md#changerequestview) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{id}/delete-request" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 9.1.7 Isolate application

`POST /api/admin/v1/applications/{id}/isolate`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ApplicationResponse](13-data-models.md#applicationresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{id}/isolate" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## 9.2 OAuth clients

Register and manage OAuth/OIDC client credentials

### 9.2.1 List OAuth clients

`GET /api/admin/v1/oauth-clients`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | query | string (uuid) | no | Tenant ID (UUID) |
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [OAuthClientResponse](13-data-models.md#oauthclientresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/oauth-clients" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 9.2.2 Create OAuth client

`POST /api/admin/v1/oauth-clients`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [OAuthClientCreateRequest](13-data-models.md#oauthclientcreaterequest))

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
| `201` | Created | [OAuthClientCreateResult](13-data-models.md#oauthclientcreateresult) |

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

### 9.2.3 Get OAuth client

`GET /api/admin/v1/oauth-clients/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [OAuthClientResponse](13-data-models.md#oauthclientresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/oauth-clients/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 9.2.4 Update OAuth client

`PUT /api/admin/v1/oauth-clients/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`, [OAuthClientUpdateRequest](13-data-models.md#oauthclientupdaterequest))

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
| `200` | OK | [OAuthClientResponse](13-data-models.md#oauthclientresponse) |

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

### 9.2.5 Request deletion of an OAuth client (needs approval)

`DELETE /api/admin/v1/oauth-clients/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |
| `reason` | query | string | no | Why the change is needed (shown to approvers) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `202` | Accepted | [ChangeRequestView](13-data-models.md#changerequestview) |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/oauth-clients/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 9.2.6 Get the pending delete request of an OAuth client

`GET /api/admin/v1/oauth-clients/{id}/delete-request`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ChangeRequestView](13-data-models.md#changerequestview) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/oauth-clients/{id}/delete-request" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 9.2.7 Rotate OAuth client secret

`POST /api/admin/v1/oauth-clients/{id}/rotate-secret`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |
| `keepOldHours` | query | integer (int32) | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [OAuthClientSecretResponse](13-data-models.md#oauthclientsecretresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/oauth-clients/{id}/rotate-secret" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## 9.3 Change approvals

Two Factor Authentication review

### 9.3.1 List change requests (every tenant)

`GET /api/admin/v1/change-requests`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ChangeRequestView](13-data-models.md#changerequestview)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/change-requests" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 9.3.2 Approve change request

`POST /api/admin/v1/change-requests/{id}/approve`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`, [DecisionRequest](13-data-models.md#decisionrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `note` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ChangeRequestView](13-data-models.md#changerequestview) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/change-requests/{id}/approve" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "note": "Short explanation"
}'
```

### 9.3.3 Reject change request

`POST /api/admin/v1/change-requests/{id}/reject`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`, [DecisionRequest](13-data-models.md#decisionrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `note` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ChangeRequestView](13-data-models.md#changerequestview) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/change-requests/{id}/reject" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "note": "Short explanation"
}'
```

### 9.3.4 Withdraw change request

`POST /api/admin/v1/change-requests/{id}/withdraw`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ChangeRequestView](13-data-models.md#changerequestview) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/change-requests/{id}/withdraw" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 9.3.5 List change requests (one tenant)

`GET /api/admin/v1/tenants/{tenantId}/change-requests`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ChangeRequestView](13-data-models.md#changerequestview)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/change-requests" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 9.3.6 Two Factor Authentication review for one tenant

`GET /api/admin/v1/tenants/{tenantId}/two-factor/review`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | path | string (uuid) | yes | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [Review](13-data-models.md#review) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/tenants/{tenantId}/two-factor/review" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 9.3.7 Two Factor Authentication review for every tenant (platform operators)

`GET /api/admin/v1/two-factor/review`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [Review](13-data-models.md#review) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/two-factor/review" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```
