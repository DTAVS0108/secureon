# 1. Public endpoints and first-run setup

[API reference](README.md) › Public endpoints and first-run setup

> Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.

Server information, the one-time setup of a new installation, documentation discovery, custom-domain checks and the CAPTCHA settings the sign-in pages use. No authentication.

In Swagger UI (`/docs`), choose the definition **Public** (`/v3/api-docs/public`).

Set the variables from [Getting started](00-getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [1.1 System](#11-system)
  - [GET `/api/info`](#111-service-info) — Service info
- [1.2 First-run setup](#12-first-run-setup)
  - [POST `/api/v1/setup/complete`](#121-complete-setup) — Complete setup
  - [GET `/api/v1/setup/status`](#122-list-status) — List status
  - [POST `/api/v1/setup/verify`](#123-verify-setup) — Verify setup
- [1.3 Documentation](#13-documentation)
  - [GET `/api/v1/documentation`](#131-documentation-catalog) — Documentation catalog
- [1.4 Custom domains](#14-custom-domains)
  - [GET `/.well-known/secureone-domain-verification`](#141-on-a-registered-domain-its-verification-token-shows-the-domain-points-at-secureone) — On a registered domain: its verification token (shows the domain points at SecureOne)
  - [GET `/api/v1/domains/check`](#142-whether-a-domain-is-a-verified-custom-domain-200-or-not-404) — Whether a domain is a verified custom domain (200) or not (404)
- [1.5 Bot protection](#15-bot-protection)
  - [GET `/api/v1/attack-protection`](#151-get-the-captcha-settings-for-hosted-pages-public) — Get the CAPTCHA settings for hosted pages (public)

## 1.1 System

Health and discovery

### 1.1.1 Service info

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

## 1.2 First-run setup

Creates the first platform operator on a new installation (setup code)

### 1.2.1 Complete setup

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
  "firstName": "First",
  "lastName": "Last",
  "password": "Choose-a-Strong-Pass-1!"
}'
```

### 1.2.2 List status

`GET /api/v1/setup/status`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/setup/status"
```

### 1.2.3 Verify setup

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

## 1.3 Documentation

SecureOne Documentation discovery

### 1.3.1 Documentation catalog

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

## 1.4 Custom domains

Domain verification and the on-demand TLS check

### 1.4.1 On a registered domain: its verification token (shows the domain points at SecureOne)

`GET /.well-known/secureone-domain-verification`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | string |

```bash
curl -s "$SECUREONE_URL/.well-known/secureone-domain-verification"
```

### 1.4.2 Whether a domain is a verified custom domain (200) or not (404)

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

## 1.5 Bot protection

Which CAPTCHA the sign-in pages show, and where (no secrets)

### 1.5.1 Get the CAPTCHA settings for hosted pages (public)

`GET /api/v1/attack-protection`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/v1/attack-protection"
```
