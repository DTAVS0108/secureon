# 2. Public endpoints and first-run setup

[API reference](README.md) › Public endpoints and first-run setup

> Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.

Server information, the sign-in method catalog, documentation discovery and the one-time setup of a new installation. No authentication.

Set the variables from [Getting started](00-getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [2.1 System](#21-system)
  - [GET `/api/info`](#211-service-info) — Service info
- [2.2 Documentation](#22-documentation)
  - [GET `/api/v1/confluence`](#221-confluence-catalog) — Confluence catalog
- [2.3 First-run setup](#23-first-run-setup)
  - [POST `/api/v1/setup/complete`](#231-complete-setup) — Complete setup
  - [GET `/api/v1/setup/status`](#232-list-status) — List status
  - [POST `/api/v1/setup/verify`](#233-verify-setup) — Verify setup

## 2.1 System

Health and discovery

### 2.1.1 Service info

`GET /api/info`

Public metadata including links to OpenAPI documentation.

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/info"
```

## 2.2 Documentation

SecureOne Confluence discovery

### 2.2.1 Confluence catalog

`GET /api/v1/confluence`

Lists documentation pages by category and links to the SecureOne Confluence UI (standalone docs browser on admin-web) and session-gated content API. Draw.io diagrams render inline on doc pages.

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/confluence"
```

## 2.3 First-run setup

Creates the first platform operator on a new installation (setup code)

### 2.3.1 Complete setup

`POST /api/v1/setup/complete`

**Authentication:** None (public)

**Request body** (`application/json`, [CompleteRequest](13-data-models.md#completerequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | no |  |
| `email` | string | no |  |
| `firstName` | string | no |  |
| `lastName` | string | no |  |
| `password` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/setup/complete" \
  -H "Content-Type: application/json" \
  --data '{
  "code": "123456",
  "email": "user@example.com",
  "firstName": "Jane",
  "lastName": "Doe",
  "password": "Choose-a-Strong-Pass-1!"
}'
```

### 2.3.2 List status

`GET /api/v1/setup/status`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/setup/status"
```

### 2.3.3 Verify setup

`POST /api/v1/setup/verify`

**Authentication:** None (public)

**Request body** (`application/json`, [VerifyRequest](13-data-models.md#verifyrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | — |

```bash
curl -s -X POST "$SECUREONE_URL/api/v1/setup/verify" \
  -H "Content-Type: application/json" \
  --data '{
  "code": "123456"
}'
```
