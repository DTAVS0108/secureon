---
title: Public endpoints and setup
summary: Server information, the one-time setup of a new installation, documentation discovery, custom-domain checks and the CAPTCHA settings the sign-in pages use.
order: 3
audience: public
---

# Public endpoints and setup

[API reference](index.md) › Public endpoints and setup

> Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.

Server information, the one-time setup of a new installation, documentation discovery, custom-domain checks and the CAPTCHA settings the sign-in pages use. No authentication.

In Swagger UI (`/docs`), choose the definition **Public** (`/v3/api-docs/public`).

Set the variables from [Getting started](getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [System](#system)
  - [GET `/api/info`](#service-info) — Service info
- [First-run setup](#first-run-setup)
  - [POST `/api/v1/setup/complete`](#complete-setup) — Complete setup
  - [GET `/api/v1/setup/status`](#list-status) — List status
  - [POST `/api/v1/setup/verify`](#verify-setup) — Verify setup
- [Documentation](#documentation)
  - [GET `/api/v1/documentation`](#documentation-catalog) — Documentation catalog
- [Custom domains](#custom-domains)
  - [GET `/.well-known/secureone-domain-verification`](#on-a-registered-domain-its-verification-token-shows-the-domain-points-at-secureone) — On a registered domain: its verification token (shows the domain points at SecureOne)
  - [GET `/api/v1/domains/check`](#whether-a-domain-is-a-verified-custom-domain-200-or-not-404) — Whether a domain is a verified custom domain (200) or not (404)
- [Bot protection](#bot-protection)
  - [GET `/api/v1/attack-protection`](#get-the-captcha-settings-for-hosted-pages-public) — Get the CAPTCHA settings for hosted pages (public)

## System

Health and discovery

### Service info

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

## First-run setup

Creates the first platform operator on a new installation (setup code)

### Complete setup

`POST /api/v1/setup/complete`

**Authentication:** None (public)

**Request body** (`application/json`, [CompleteRequest](data-models.md#completerequest))

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
  "firstName": "First",
  "lastName": "Last",
  "password": "Choose-a-Strong-Pass-1!"
}'
```

### List status

`GET /api/v1/setup/status`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/setup/status"
```

### Verify setup

`POST /api/v1/setup/verify`

**Authentication:** None (public)

**Request body** (`application/json`, [VerifyRequest](data-models.md#verifyrequest))

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

## Documentation

SecureOne Documentation discovery

### Documentation catalog

`GET /api/v1/documentation`

Lists documentation pages by category and links to the SecureOne Documentation UI (standalone docs browser on admin-web) and session-gated content API. Diagrams render inline on doc pages.

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/documentation"
```

## Custom domains

Domain verification and the on-demand TLS check

### On a registered domain: its verification token (shows the domain points at SecureOne)

`GET /.well-known/secureone-domain-verification`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | string |

```bash
curl -s "$SECUREONE_URL/.well-known/secureone-domain-verification"
```

### Whether a domain is a verified custom domain (200) or not (404)

`GET /api/v1/domains/check`

For a reverse proxy that issues certificates on demand (Caddy on_demand_tls ask): it gets a certificate only for verified domains.

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `domain` | query | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | — |

```bash
curl -s "$SECUREONE_URL/api/v1/domains/check?domain={domain}"
```

## Bot protection

Which CAPTCHA the sign-in pages show, and where (no secrets)

### Get the CAPTCHA settings for hosted pages (public)

`GET /api/v1/attack-protection`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/attack-protection"
```
