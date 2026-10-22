# 12. Admin API: platform settings and configuration as code

[API reference](README.md) › Admin API: platform settings and configuration as code

> Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.

Platform-wide settings (sign-in methods, password, MFA and token policies, email, SMS, appearance, feature flags) and configuration files (export, preview, apply).

Set the variables from [Getting started](00-getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [12.1 Platform settings](#121-platform-settings)
  - [GET `/api/admin/v1/settings/app-exposure`](#1211-get-app-exposure) — Get app exposure
  - [PUT `/api/admin/v1/settings/app-exposure`](#1212-update-app-exposure) — Update app exposure
  - [GET `/api/admin/v1/settings/appearance`](#1213-get-appearance) — Get appearance
  - [PUT `/api/admin/v1/settings/appearance`](#1214-update-appearance) — Update appearance
  - [GET `/api/admin/v1/settings/auth-methods`](#1215-list-auth-methods) — List auth methods
  - [PUT `/api/admin/v1/settings/auth-methods`](#1216-update-auth-methods) — Update auth methods
  - [GET `/api/admin/v1/settings/email`](#1217-get-email) — Get email
  - [PUT `/api/admin/v1/settings/email`](#1218-update-email) — Update email
  - [GET `/api/admin/v1/settings/feature-flags`](#1219-list-feature-flags) — List feature flags
  - [PUT `/api/admin/v1/settings/feature-flags`](#12110-update-feature-flags) — Update feature flags
  - [GET `/api/admin/v1/settings/mfa-policy`](#12111-get-mfa-policy) — Get MFA policy
  - [PUT `/api/admin/v1/settings/mfa-policy`](#12112-update-mfa-policy) — Update MFA policy
  - [GET `/api/admin/v1/settings/notifications`](#12113-list-notifications) — List notifications
  - [PUT `/api/admin/v1/settings/notifications`](#12114-update-notifications) — Update notifications
  - [GET `/api/admin/v1/settings/password-policy`](#12115-get-password-policy) — Get password policy
  - [PUT `/api/admin/v1/settings/password-policy`](#12116-update-password-policy) — Update password policy
  - [GET `/api/admin/v1/settings/platform-features`](#12117-list-platform-features) — List platform features
  - [PUT `/api/admin/v1/settings/platform-features`](#12118-update-platform-features) — Update platform features
  - [GET `/api/admin/v1/settings/sms`](#12119-get-sms) — Get SMS
  - [PUT `/api/admin/v1/settings/sms`](#12120-update-sms) — Update SMS
  - [POST `/api/admin/v1/settings/sms/test`](#12121-send-a-test-sms) — Send a test SMS
  - [GET `/api/admin/v1/settings/smtp`](#12122-get-smtp) — Get SMTP
  - [PUT `/api/admin/v1/settings/smtp`](#12123-update-smtp) — Update SMTP
  - [GET `/api/admin/v1/settings/token-policy`](#12124-get-token-policy) — Get token policy
  - [PUT `/api/admin/v1/settings/token-policy`](#12125-update-token-policy) — Update token policy
  - [GET `/api/admin/v1/settings/verification-policy`](#12126-get-verification-policy) — Get verification policy
  - [PUT `/api/admin/v1/settings/verification-policy`](#12127-update-verification-policy) — Update verification policy
- [12.2 Configuration as code](#122-configuration-as-code)
  - [POST `/api/admin/v1/config/import`](#1221-preview-or-apply-a-configuration-file) — Preview or apply a configuration file
  - [GET `/api/admin/v1/config/platform/export`](#1222-export-the-platform-configuration) — Export the platform configuration
  - [GET `/api/admin/v1/config/tenants/{slug}/export`](#1223-export-a-tenants-configuration) — Export a tenant's configuration

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

### 12.1.3 Get appearance

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

### 12.1.4 Update appearance

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

### 12.1.5 List auth methods

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

### 12.1.6 Update auth methods

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

### 12.1.7 Get email

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

### 12.1.8 Update email

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

### 12.1.9 List feature flags

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

### 12.1.10 Update feature flags

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

### 12.1.11 Get MFA policy

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

### 12.1.12 Update MFA policy

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

### 12.1.13 List notifications

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

### 12.1.14 Update notifications

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

### 12.1.15 Get password policy

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

### 12.1.16 Update password policy

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

### 12.1.17 List platform features

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

### 12.1.18 Update platform features

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

### 12.1.19 Get SMS

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

### 12.1.20 Update SMS

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

### 12.1.21 Send a test SMS

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

### 12.1.22 Get SMTP

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

### 12.1.23 Update SMTP

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

### 12.1.24 Get token policy

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

### 12.1.25 Update token policy

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

### 12.1.26 Get verification policy

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

### 12.1.27 Update verification policy

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

## 12.2 Configuration as code

Export a tenant to a file; preview and apply a file

### 12.2.1 Preview or apply a configuration file

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

### 12.2.2 Export the platform configuration

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

### 12.2.3 Export a tenant's configuration

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
