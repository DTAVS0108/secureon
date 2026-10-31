---
title: Admin API: platform settings
summary: Platform-wide settings (sign-in methods, password, MFA and token policies, email, SMS, appearance, feature flags, attack protection, log retention and streaming) and configuration files (export, preview, apply).
order: 14
audience: public
---

# Admin API: platform settings

[API reference](index.md) › Admin API: platform settings

<!-- developer -->
Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.
<!-- /developer -->

Platform-wide settings (sign-in methods, password, MFA and token policies, email, SMS, appearance, feature flags, attack protection, log retention and streaming) and configuration files (export, preview, apply).

In Swagger UI (`/docs`), choose the definition **Admin — platform settings** (`/v3/api-docs/admin-settings`).

Set the variables from [Getting started](getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [Platform settings](#platform-settings)
  - [GET `/api/admin/v1/settings/app-exposure`](#get-app-exposure) — Get app exposure
  - [PUT `/api/admin/v1/settings/app-exposure`](#update-app-exposure) — Update app exposure
  - [GET `/api/admin/v1/settings/app-exposure/exceptions`](#list-exceptions) — List exceptions
  - [PUT `/api/admin/v1/settings/app-exposure/exceptions/{applicationId}`](#update-exception) — Update exception
  - [GET `/api/admin/v1/settings/appearance`](#get-appearance) — Get appearance
  - [PUT `/api/admin/v1/settings/appearance`](#update-appearance) — Update appearance
  - [GET `/api/admin/v1/settings/auth-methods`](#list-auth-methods) — List auth methods
  - [PUT `/api/admin/v1/settings/auth-methods`](#update-auth-methods) — Update auth methods
  - [GET `/api/admin/v1/settings/email`](#get-email) — Get email
  - [PUT `/api/admin/v1/settings/email`](#update-email) — Update email
  - [GET `/api/admin/v1/settings/feature-flags`](#list-feature-flags) — List feature flags
  - [PUT `/api/admin/v1/settings/feature-flags`](#update-feature-flags) — Update feature flags
  - [GET `/api/admin/v1/settings/mfa-policy`](#get-mfa-policy) — Get MFA policy
  - [PUT `/api/admin/v1/settings/mfa-policy`](#update-mfa-policy) — Update MFA policy
  - [GET `/api/admin/v1/settings/notifications`](#list-notifications) — List notifications
  - [PUT `/api/admin/v1/settings/notifications`](#update-notifications) — Update notifications
  - [GET `/api/admin/v1/settings/password-policy`](#get-password-policy) — Get password policy
  - [PUT `/api/admin/v1/settings/password-policy`](#update-password-policy) — Update password policy
  - [GET `/api/admin/v1/settings/platform-features`](#list-platform-features) — List platform features
  - [PUT `/api/admin/v1/settings/platform-features`](#update-platform-features) — Update platform features
  - [GET `/api/admin/v1/settings/sms`](#get-sms) — Get SMS
  - [PUT `/api/admin/v1/settings/sms`](#update-sms) — Update SMS
  - [POST `/api/admin/v1/settings/sms/test`](#send-a-test-sms) — Send a test SMS
  - [GET `/api/admin/v1/settings/smtp`](#get-smtp) — Get SMTP
  - [PUT `/api/admin/v1/settings/smtp`](#update-smtp) — Update SMTP
  - [GET `/api/admin/v1/settings/token-policy`](#get-token-policy) — Get token policy
  - [PUT `/api/admin/v1/settings/token-policy`](#update-token-policy) — Update token policy
  - [GET `/api/admin/v1/settings/verification-policy`](#get-verification-policy) — Get verification policy
  - [PUT `/api/admin/v1/settings/verification-policy`](#update-verification-policy) — Update verification policy
- [Attack protection](#attack-protection)
  - [GET `/api/admin/v1/settings/attack-protection`](#get-attack-protection) — Get attack protection
  - [PUT `/api/admin/v1/settings/attack-protection`](#update-attack-protection) — Update attack protection
- [Log streams](#log-streams)
  - [GET `/api/admin/v1/settings/log-streams`](#list-log-streams) — List log streams
  - [PUT `/api/admin/v1/settings/log-streams`](#update-log-streams) — Update log streams
  - [POST `/api/admin/v1/settings/log-streams/{streamId}/test`](#send-a-test-entry-to-a-log-stream) — Send a test entry to a log stream
- [Configuration as code](#configuration-as-code)
  - [POST `/api/admin/v1/config/import`](#preview-or-apply-a-configuration-file) — Preview or apply a configuration file
  - [GET `/api/admin/v1/config/platform/export`](#export-the-platform-configuration) — Export the platform configuration
  - [GET `/api/admin/v1/config/tenants/{slug}/export`](#export-a-tenants-configuration) — Export a tenant's configuration
- [Signing keys](#signing-keys)
  - [GET `/api/admin/v1/settings/signing-keys`](#list-signing-keys) — List signing keys
  - [POST `/api/admin/v1/settings/signing-keys/retire-previous`](#create-retire-previou) — Create retire previou
  - [POST `/api/admin/v1/settings/signing-keys/rotate`](#rotate) — Rotate

## Platform settings

Platform defaults, exposure to applications, SMTP

### Get app exposure

`GET /api/admin/v1/settings/app-exposure`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/app-exposure" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update app exposure

`PUT /api/admin/v1/settings/app-exposure`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/app-exposure" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List exceptions

`GET /api/admin/v1/settings/app-exposure/exceptions`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/app-exposure/exceptions" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update exception

`PUT /api/admin/v1/settings/app-exposure/exceptions/{applicationId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/app-exposure/exceptions/{applicationId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Get appearance

`GET /api/admin/v1/settings/appearance`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/appearance" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update appearance

`PUT /api/admin/v1/settings/appearance`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/appearance" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List auth methods

`GET /api/admin/v1/settings/auth-methods`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/auth-methods" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update auth methods

`PUT /api/admin/v1/settings/auth-methods`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object[]

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/auth-methods" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '[
  {}
]'
```

### Get email

`GET /api/admin/v1/settings/email`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/email" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update email

`PUT /api/admin/v1/settings/email`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/email" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List feature flags

`GET /api/admin/v1/settings/feature-flags`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/feature-flags" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update feature flags

`PUT /api/admin/v1/settings/feature-flags`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object[]

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/feature-flags" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '[
  {}
]'
```

### Get MFA policy

`GET /api/admin/v1/settings/mfa-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/mfa-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update MFA policy

`PUT /api/admin/v1/settings/mfa-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/mfa-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List notifications

`GET /api/admin/v1/settings/notifications`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/notifications" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update notifications

`PUT /api/admin/v1/settings/notifications`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/notifications" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Get password policy

`GET /api/admin/v1/settings/password-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/password-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update password policy

`PUT /api/admin/v1/settings/password-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/password-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List platform features

`GET /api/admin/v1/settings/platform-features`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/platform-features" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update platform features

`PUT /api/admin/v1/settings/platform-features`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/platform-features" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Get SMS

`GET /api/admin/v1/settings/sms`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/sms" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update SMS

`PUT /api/admin/v1/settings/sms`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/sms" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Send a test SMS

`POST /api/admin/v1/settings/sms/test`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [TestSmsRequest](data-models.md#testsmsrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `to` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/settings/sms/test" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "to": "you@example.com"
}'
```

### Get SMTP

`GET /api/admin/v1/settings/smtp`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/smtp" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update SMTP

`PUT /api/admin/v1/settings/smtp`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/smtp" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Get token policy

`GET /api/admin/v1/settings/token-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/token-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update token policy

`PUT /api/admin/v1/settings/token-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/token-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Get verification policy

`GET /api/admin/v1/settings/verification-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/verification-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update verification policy

`PUT /api/admin/v1/settings/verification-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/verification-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## Attack protection

Bot protection (CAPTCHA) and IP rules

### Get attack protection

`GET /api/admin/v1/settings/attack-protection`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/attack-protection" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update attack protection

`PUT /api/admin/v1/settings/attack-protection`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/attack-protection" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## Log streams

Audit log streaming to a SIEM and log retention

### List log streams

`GET /api/admin/v1/settings/log-streams`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/log-streams" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update log streams

`PUT /api/admin/v1/settings/log-streams`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/log-streams" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Send a test entry to a log stream

`POST /api/admin/v1/settings/log-streams/{streamId}/test`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `streamId` | path | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/settings/log-streams/{streamId}/test" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## Configuration as code

Export a tenant to a file; preview and apply a file

### Preview or apply a configuration file

`POST /api/admin/v1/config/import`

Body: the YAML or JSON file. dryRun=true (default) only reports what would change. prune=true also deletes what the file does not list. 422 when the file has problems or applying stopped early.

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `dryRun` | query | boolean | no |  |
| `prune` | query | boolean | no |  |

**Request body** (`application/json`): string

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [Result](data-models.md#result) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/config/import" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Export the platform configuration

`GET /api/admin/v1/config/platform/export`

SMTP, SMS and SCIM defaults; YAML or format=json.

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `format` | query | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | string |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/config/platform/export" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Export a tenant's configuration

`GET /api/admin/v1/config/tenants/{slug}/export`

YAML by default; format=json for JSON.

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `slug` | path | string | yes |  |
| `format` | query | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | string |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/config/tenants/{slug}/export" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## Signing keys

Rotate the OAuth/OIDC token signing key

### List signing keys

`GET /api/admin/v1/settings/signing-keys`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/signing-keys" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Create retire previou

`POST /api/admin/v1/settings/signing-keys/retire-previous`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/settings/signing-keys/retire-previous" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Rotate

`POST /api/admin/v1/settings/signing-keys/rotate`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/settings/signing-keys/rotate" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```
