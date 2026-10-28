# 5. Machine-to-machine and SCIM provisioning

[API reference](README.md) › Machine-to-machine and SCIM provisioning

> Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.

Calls made by other systems: service accounts with API keys, and SCIM 2.0 user and group provisioning from an identity provider.

In Swagger UI (`/docs`), choose the definition **Machine-to-machine and SCIM** (`/v3/api-docs/machine`).

Set the variables from [Getting started](00-getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [5.1 Machine-to-machine (API keys)](#51-machine-to-machine-api-keys)
  - [GET `/api/v1/m2m/health`](#511-check-the-api-key-works) — Check the API key works
  - [GET `/api/v1/m2m/tenant`](#512-get-my-tenant-service-account) — Get my tenant (service account)
  - [GET `/api/v1/m2m/whoami`](#513-who-am-i-service-account) — Who am I (service account)
- [5.2 SCIM 2.0 provisioning](#52-scim-20-provisioning)
  - [GET `/scim/v2/Groups`](#521-list-groups) — List groups
  - [POST `/scim/v2/Groups`](#522-create-group) — Create group
  - [GET `/scim/v2/Groups/{id}`](#523-get-group) — Get group
  - [PUT `/scim/v2/Groups/{id}`](#524-update-group) — Update group
  - [PATCH `/scim/v2/Groups/{id}`](#525-update-group) — Update group
  - [DELETE `/scim/v2/Groups/{id}`](#526-delete-group) — Delete group
  - [GET `/scim/v2/ServiceProviderConfig`](#527-get-service-provider-config) — Get service provider config
  - [GET `/scim/v2/Users`](#528-list-users) — List users
  - [POST `/scim/v2/Users`](#529-create-user) — Create user
  - [GET `/scim/v2/Users/{id}`](#5210-get-user) — Get user
  - [PUT `/scim/v2/Users/{id}`](#5211-update-user) — Update user
  - [PATCH `/scim/v2/Users/{id}`](#5212-update-user) — Update user
  - [DELETE `/scim/v2/Users/{id}`](#5213-delete-user) — Delete user

## 5.1 Machine-to-machine (API keys)

Service account API keys: who am I, my tenant, health

### 5.1.1 Check the API key works

`GET /api/v1/m2m/health`

**Authentication:** Service account API key (`Authorization: Bearer sk_…`)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/m2m/health" \
  -H "Authorization: Bearer $API_KEY"
```

### 5.1.2 Get my tenant (service account)

`GET /api/v1/m2m/tenant`

**Authentication:** Service account API key (`Authorization: Bearer sk_…`)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/m2m/tenant" \
  -H "Authorization: Bearer $API_KEY"
```

### 5.1.3 Who am I (service account)

`GET /api/v1/m2m/whoami`

**Authentication:** Service account API key (`Authorization: Bearer sk_…`)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/m2m/whoami" \
  -H "Authorization: Bearer $API_KEY"
```

## 5.2 SCIM 2.0 provisioning

Users and groups provisioned by an identity provider (SCIM bearer token)

### 5.2.1 List groups

`GET /scim/v2/Groups`

**Authentication:** Tenant SCIM token (`Authorization: Bearer …`)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | query | string (uuid) | no | Tenant ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/scim/v2/Groups" \
  -H "Authorization: Bearer $SCIM_TOKEN"
```

### 5.2.2 Create group

`POST /scim/v2/Groups`

**Authentication:** Tenant SCIM token (`Authorization: Bearer …`)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/scim/v2/Groups" \
  -H "Authorization: Bearer $SCIM_TOKEN"
```

### 5.2.3 Get group

`GET /scim/v2/Groups/{id}`

**Authentication:** Tenant SCIM token (`Authorization: Bearer …`)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/scim/v2/Groups/{id}" \
  -H "Authorization: Bearer $SCIM_TOKEN"
```

### 5.2.4 Update group

`PUT /scim/v2/Groups/{id}`

**Authentication:** Tenant SCIM token (`Authorization: Bearer …`)

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
curl -s -X PUT "$SECUREONE_URL/scim/v2/Groups/{id}" \
  -H "Authorization: Bearer $SCIM_TOKEN"
```

### 5.2.5 Update group

`PATCH /scim/v2/Groups/{id}`

**Authentication:** Tenant SCIM token (`Authorization: Bearer …`)

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
curl -s -X PATCH "$SECUREONE_URL/scim/v2/Groups/{id}" \
  -H "Authorization: Bearer $SCIM_TOKEN"
```

### 5.2.6 Delete group

`DELETE /scim/v2/Groups/{id}`

**Authentication:** Tenant SCIM token (`Authorization: Bearer …`)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/scim/v2/Groups/{id}" \
  -H "Authorization: Bearer $SCIM_TOKEN"
```

### 5.2.7 Get service provider config

`GET /scim/v2/ServiceProviderConfig`

**Authentication:** Tenant SCIM token (`Authorization: Bearer …`)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/scim/v2/ServiceProviderConfig" \
  -H "Authorization: Bearer $SCIM_TOKEN"
```

### 5.2.8 List users

`GET /scim/v2/Users`

**Authentication:** Tenant SCIM token (`Authorization: Bearer …`)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `tenantId` | query | string (uuid) | no | Tenant ID (UUID) |
| `filter` | query | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/scim/v2/Users" \
  -H "Authorization: Bearer $SCIM_TOKEN"
```

### 5.2.9 Create user

`POST /scim/v2/Users`

**Authentication:** Tenant SCIM token (`Authorization: Bearer …`)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/scim/v2/Users" \
  -H "Authorization: Bearer $SCIM_TOKEN"
```

### 5.2.10 Get user

`GET /scim/v2/Users/{id}`

**Authentication:** Tenant SCIM token (`Authorization: Bearer …`)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/scim/v2/Users/{id}" \
  -H "Authorization: Bearer $SCIM_TOKEN"
```

### 5.2.11 Update user

`PUT /scim/v2/Users/{id}`

**Authentication:** Tenant SCIM token (`Authorization: Bearer …`)

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
curl -s -X PUT "$SECUREONE_URL/scim/v2/Users/{id}" \
  -H "Authorization: Bearer $SCIM_TOKEN"
```

### 5.2.12 Update user

`PATCH /scim/v2/Users/{id}`

**Authentication:** Tenant SCIM token (`Authorization: Bearer …`)

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
curl -s -X PATCH "$SECUREONE_URL/scim/v2/Users/{id}" \
  -H "Authorization: Bearer $SCIM_TOKEN"
```

### 5.2.13 Delete user

`DELETE /scim/v2/Users/{id}`

**Authentication:** Tenant SCIM token (`Authorization: Bearer …`)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/scim/v2/Users/{id}" \
  -H "Authorization: Bearer $SCIM_TOKEN"
```
