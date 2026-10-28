# 12. Admin API: platform settings and configuration as code

[API reference](README.md) › Admin API: platform settings and configuration as code

> Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.

Platform-wide settings (sign-in methods, password, MFA and token policies, email, SMS, appearance, feature flags, attack protection, log retention and streaming) and configuration files (export, preview, apply).

In Swagger UI (`/docs`), choose the definition **Admin — platform settings** (`/v3/api-docs/admin-settings`).

Set the variables from [Getting started](00-getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [12.1 Platform settings](#121-platform-settings)
  - [GET `/api/admin/v1/settings/app-exposure`](#1211-get-app-exposure) — Get app exposure
  - [PUT `/api/admin/v1/settings/app-exposure`](#1212-update-app-exposure) — Update app exposure
  - [GET `/api/admin/v1/settings/app-exposure/exceptions`](#1213-list-exceptions) — List exceptions
  - [PUT `/api/admin/v1/settings/app-exposure/exceptions/{applicationId}`](#1214-update-exception) — Update exception
  - [GET `/api/admin/v1/settings/appearance`](#1215-get-appearance) — Get appearance
  - [PUT `/api/admin/v1/settings/appearance`](#1216-update-appearance) — Update appearance
  - [GET `/api/admin/v1/settings/auth-methods`](#1217-list-auth-methods) — List auth methods
  - [PUT `/api/admin/v1/settings/auth-methods`](#1218-update-auth-methods) — Update auth methods
  - [GET `/api/admin/v1/settings/email`](#1219-get-email) — Get email
  - [PUT `/api/admin/v1/settings/email`](#12110-update-email) — Update email
  - [GET `/api/admin/v1/settings/feature-flags`](#12111-list-feature-flags) — List feature flags
  - [PUT `/api/admin/v1/settings/feature-flags`](#12112-update-feature-flags) — Update feature flags
  - [GET `/api/admin/v1/settings/mfa-policy`](#12113-get-mfa-policy) — Get MFA policy
  - [PUT `/api/admin/v1/settings/mfa-policy`](#12114-update-mfa-policy) — Update MFA policy
  - [GET `/api/admin/v1/settings/notifications`](#12115-list-notifications) — List notifications
  - [PUT `/api/admin/v1/settings/notifications`](#12116-update-notifications) — Update notifications
  - [GET `/api/admin/v1/settings/password-policy`](#12117-get-password-policy) — Get password policy
  - [PUT `/api/admin/v1/settings/password-policy`](#12118-update-password-policy) — Update password policy
  - [GET `/api/admin/v1/settings/platform-features`](#12119-list-platform-features) — List platform features
  - [PUT `/api/admin/v1/settings/platform-features`](#12120-update-platform-features) — Update platform features
  - [GET `/api/admin/v1/settings/sms`](#12121-get-sms) — Get SMS
  - [PUT `/api/admin/v1/settings/sms`](#12122-update-sms) — Update SMS
  - [POST `/api/admin/v1/settings/sms/test`](#12123-send-a-test-sms) — Send a test SMS
  - [GET `/api/admin/v1/settings/smtp`](#12124-get-smtp) — Get SMTP
  - [PUT `/api/admin/v1/settings/smtp`](#12125-update-smtp) — Update SMTP
  - [GET `/api/admin/v1/settings/token-policy`](#12126-get-token-policy) — Get token policy
  - [PUT `/api/admin/v1/settings/token-policy`](#12127-update-token-policy) — Update token policy
  - [GET `/api/admin/v1/settings/verification-policy`](#12128-get-verification-policy) — Get verification policy
  - [PUT `/api/admin/v1/settings/verification-policy`](#12129-update-verification-policy) — Update verification policy
- [12.2 Attack protection](#122-attack-protection)
  - [GET `/api/admin/v1/settings/attack-protection`](#1221-get-attack-protection) — Get attack protection
  - [PUT `/api/admin/v1/settings/attack-protection`](#1222-update-attack-protection) — Update attack protection
- [12.3 Log streams](#123-log-streams)
  - [GET `/api/admin/v1/settings/log-streams`](#1231-list-log-streams) — List log streams
  - [PUT `/api/admin/v1/settings/log-streams`](#1232-update-log-streams) — Update log streams
  - [POST `/api/admin/v1/settings/log-streams/{streamId}/test`](#1233-send-a-test-entry-to-a-log-stream) — Send a test entry to a log stream
- [12.4 Configuration as code](#124-configuration-as-code)
  - [POST `/api/admin/v1/config/import`](#1241-preview-or-apply-a-configuration-file) — Preview or apply a configuration file
  - [GET `/api/admin/v1/config/platform/export`](#1242-export-the-platform-configuration) — Export the platform configuration
  - [GET `/api/admin/v1/config/tenants/{slug}/export`](#1243-export-a-tenants-configuration) — Export a tenant's configuration
- [12.5 Signing keys](#125-signing-keys)
  - [GET `/api/admin/v1/settings/signing-keys`](#1251-list-signing-keys) — List signing keys
  - [POST `/api/admin/v1/settings/signing-keys/retire-previous`](#1252-create-retire-previou) — Create retire previou
  - [POST `/api/admin/v1/settings/signing-keys/rotate`](#1253-rotate) — Rotate

## 12.1 Platform settings

Platform defaults, exposure to applications, SMTP

### 12.1.1 Get app exposure

`GET /api/admin/v1/settings/app-exposure`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/app-exposure" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.1.2 Update app exposure

`PUT /api/admin/v1/settings/app-exposure`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/app-exposure" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.1.3 List exceptions

`GET /api/admin/v1/settings/app-exposure/exceptions`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/app-exposure/exceptions" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.1.4 Update exception

`PUT /api/admin/v1/settings/app-exposure/exceptions/{applicationId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 12.1.5 Get appearance

`GET /api/admin/v1/settings/appearance`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/appearance" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.1.6 Update appearance

`PUT /api/admin/v1/settings/appearance`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/appearance" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.1.7 List auth methods

`GET /api/admin/v1/settings/auth-methods`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/auth-methods" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.1.8 Update auth methods

`PUT /api/admin/v1/settings/auth-methods`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 12.1.9 Get email

`GET /api/admin/v1/settings/email`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/email" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.1.10 Update email

`PUT /api/admin/v1/settings/email`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/email" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.1.11 List feature flags

`GET /api/admin/v1/settings/feature-flags`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/feature-flags" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.1.12 Update feature flags

`PUT /api/admin/v1/settings/feature-flags`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 12.1.13 Get MFA policy

`GET /api/admin/v1/settings/mfa-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/mfa-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.1.14 Update MFA policy

`PUT /api/admin/v1/settings/mfa-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/mfa-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.1.15 List notifications

`GET /api/admin/v1/settings/notifications`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/notifications" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.1.16 Update notifications

`PUT /api/admin/v1/settings/notifications`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/notifications" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.1.17 Get password policy

`GET /api/admin/v1/settings/password-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/password-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.1.18 Update password policy

`PUT /api/admin/v1/settings/password-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/password-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.1.19 List platform features

`GET /api/admin/v1/settings/platform-features`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/platform-features" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.1.20 Update platform features

`PUT /api/admin/v1/settings/platform-features`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/platform-features" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.1.21 Get SMS

`GET /api/admin/v1/settings/sms`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/sms" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.1.22 Update SMS

`PUT /api/admin/v1/settings/sms`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/sms" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.1.23 Send a test SMS

`POST /api/admin/v1/settings/sms/test`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`, [TestSmsRequest](13-data-models.md#testsmsrequest))

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

### 12.1.24 Get SMTP

`GET /api/admin/v1/settings/smtp`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/smtp" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.1.25 Update SMTP

`PUT /api/admin/v1/settings/smtp`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/smtp" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.1.26 Get token policy

`GET /api/admin/v1/settings/token-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/token-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.1.27 Update token policy

`PUT /api/admin/v1/settings/token-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/token-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.1.28 Get verification policy

`GET /api/admin/v1/settings/verification-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/verification-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.1.29 Update verification policy

`PUT /api/admin/v1/settings/verification-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/verification-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## 12.2 Attack protection

Bot protection (CAPTCHA) and IP rules

### 12.2.1 Get attack protection

`GET /api/admin/v1/settings/attack-protection`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/attack-protection" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.2.2 Update attack protection

`PUT /api/admin/v1/settings/attack-protection`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/attack-protection" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## 12.3 Log streams

Audit log streaming to a SIEM and log retention

### 12.3.1 List log streams

`GET /api/admin/v1/settings/log-streams`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/log-streams" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.3.2 Update log streams

`PUT /api/admin/v1/settings/log-streams`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/settings/log-streams" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.3.3 Send a test entry to a log stream

`POST /api/admin/v1/settings/log-streams/{streamId}/test`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

## 12.4 Configuration as code

Export a tenant to a file; preview and apply a file

### 12.4.1 Preview or apply a configuration file

`POST /api/admin/v1/config/import`

Body: the YAML or JSON file. dryRun=true (default) only reports what would change. prune=true also deletes what the file does not list. 422 when the file has problems or applying stopped early.

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `dryRun` | query | boolean | no |  |
| `prune` | query | boolean | no |  |

**Request body** (`application/json`): string

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [Result](13-data-models.md#result) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/config/import" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.4.2 Export the platform configuration

`GET /api/admin/v1/config/platform/export`

SMTP, SMS and SCIM defaults; YAML or format=json.

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 12.4.3 Export a tenant's configuration

`GET /api/admin/v1/config/tenants/{slug}/export`

YAML by default; format=json for JSON.

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

## 12.5 Signing keys

Rotate the OAuth/OIDC token signing key

### 12.5.1 List signing keys

`GET /api/admin/v1/settings/signing-keys`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/settings/signing-keys" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.5.2 Create retire previou

`POST /api/admin/v1/settings/signing-keys/retire-previous`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/settings/signing-keys/retire-previous" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 12.5.3 Rotate

`POST /api/admin/v1/settings/signing-keys/rotate`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/settings/signing-keys/rotate" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```
