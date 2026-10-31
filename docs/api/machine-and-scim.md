---
title: Machine-to-machine and SCIM
summary: Calls made by other systems: service accounts with API keys, and SCIM 2.0 user and group provisioning from an identity provider.
order: 7
audience: public
---

# Machine-to-machine and SCIM

[API reference](index.md) › Machine-to-machine and SCIM

<!-- developer -->
Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.
<!-- /developer -->

Calls made by other systems: service accounts with API keys, and SCIM 2.0 user and group provisioning from an identity provider.

In Swagger UI (`/docs`), choose the definition **Machine-to-machine and SCIM** (`/v3/api-docs/machine`).

Set the variables from [Getting started](getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [Machine-to-machine (API keys)](#machine-to-machine-api-keys)
  - [GET `/api/v1/m2m/health`](#check-the-api-key-works) — Check the API key works
  - [GET `/api/v1/m2m/tenant`](#get-my-tenant-service-account) — Get my tenant (service account)
  - [GET `/api/v1/m2m/whoami`](#who-am-i-service-account) — Who am I (service account)
- [SCIM 2.0 provisioning](#scim-20-provisioning)
  - [GET `/scim/v2/Groups`](#list-groups) — List groups
  - [POST `/scim/v2/Groups`](#create-group) — Create group
  - [GET `/scim/v2/Groups/{id}`](#get-group) — Get group
  - [PUT `/scim/v2/Groups/{id}`](#update-group) — Update group
  - [PATCH `/scim/v2/Groups/{id}`](#update-group-1) — Update group
  - [DELETE `/scim/v2/Groups/{id}`](#delete-group) — Delete group
  - [GET `/scim/v2/ServiceProviderConfig`](#get-service-provider-config) — Get service provider config
  - [GET `/scim/v2/Users`](#list-users) — List users
  - [POST `/scim/v2/Users`](#create-user) — Create user
  - [GET `/scim/v2/Users/{id}`](#get-user) — Get user
  - [PUT `/scim/v2/Users/{id}`](#update-user) — Update user
  - [PATCH `/scim/v2/Users/{id}`](#update-user-1) — Update user
  - [DELETE `/scim/v2/Users/{id}`](#delete-user) — Delete user

## Machine-to-machine (API keys)

Service account API keys: who am I, my tenant, health

### Check the API key works

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

### Get my tenant (service account)

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

### Who am I (service account)

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

## SCIM 2.0 provisioning

Users and groups provisioned by an identity provider (SCIM bearer token)

### List groups

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

### Create group

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

### Get group

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

### Update group

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

### Update group

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

### Delete group

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

### Get service provider config

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

### List users

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

### Create user

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

### Get user

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

### Update user

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

### Update user

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

### Delete user

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
