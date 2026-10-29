---
title: Admin API: application console
summary: Everything under /api/admin/v1/applications/{applicationId}/…: the application's users, roles, permissions, groups, settings and logs.
order: 12
audience: public
---

# Admin API: application console

[API reference](index.md) › Admin API: application console

> Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.

Everything under /api/admin/v1/applications/{applicationId}/…: the application's users, roles, permissions, groups, settings and logs.

In Swagger UI (`/docs`), choose the definition **Admin — applications and clients** (`/v3/api-docs/admin-applications`).

Set the variables from [Getting started](getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [Application scope](#application-scope)
  - [GET `/api/admin/v1/applications/{applicationId}/settings/appearance`](#get-appearance) — Get appearance
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/appearance`](#update-appearance) — Update appearance
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/appearance`](#remove-appearance) — Remove appearance
  - [GET `/api/admin/v1/applications/{applicationId}/settings/auth-methods`](#list-auth-methods) — List auth methods
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/auth-methods`](#update-auth-methods) — Update auth methods
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/auth-methods`](#remove-auth-methods) — Remove auth methods
  - [GET `/api/admin/v1/applications/{applicationId}/settings/client-integration`](#get-client-integration) — Get client integration
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/client-integration`](#update-client-integration) — Update client integration
  - [GET `/api/admin/v1/applications/{applicationId}/settings/email`](#get-email) — Get email
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/email`](#update-email) — Update email
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/email`](#remove-email) — Remove email
  - [GET `/api/admin/v1/applications/{applicationId}/settings/email-templates`](#list-email-templates) — List email templates
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/email-templates`](#update-email-templates) — Update email templates
  - [GET `/api/admin/v1/applications/{applicationId}/settings/email-templates/defaults`](#list-default-email-templates) — List default email templates
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/email-templates/{templateKey}`](#remove-email-template) — Remove email template
  - [POST `/api/admin/v1/applications/{applicationId}/settings/email/test`](#send-a-test-email) — Send a test email
  - [GET `/api/admin/v1/applications/{applicationId}/settings/exposure`](#get-exposure) — Get exposure
  - [GET `/api/admin/v1/applications/{applicationId}/settings/feature-flags`](#list-feature-flags) — List feature flags
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/feature-flags`](#update-feature-flags) — Update feature flags
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/feature-flags`](#remove-feature-flags) — Remove feature flags
  - [GET `/api/admin/v1/applications/{applicationId}/settings/mfa-policy`](#get-mfa-policy) — Get MFA policy
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/mfa-policy`](#update-mfa-policy) — Update MFA policy
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/mfa-policy`](#remove-mfa-policy) — Remove MFA policy
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/mfa-tab/tab-enabled`](#update-tab-enabled) — Update tab enabled
  - [GET `/api/admin/v1/applications/{applicationId}/settings/mfa-tab/tab-state`](#get-tab-state) — Get tab state
  - [GET `/api/admin/v1/applications/{applicationId}/settings/notifications`](#list-notifications) — List notifications
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/notifications`](#update-notifications) — Update notifications
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/notifications`](#remove-notifications) — Remove notifications
  - [GET `/api/admin/v1/applications/{applicationId}/settings/password-policy`](#get-password-policy) — Get password policy
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/password-policy`](#update-password-policy) — Update password policy
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/password-policy`](#remove-password-policy) — Remove password policy
  - [GET `/api/admin/v1/applications/{applicationId}/settings/policy-source/{exposureKey}`](#get-policy-source) — Get policy source
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/policy-source/{exposureKey}`](#update-policy-source) — Update policy source
  - [GET `/api/admin/v1/applications/{applicationId}/settings/policy-sources`](#list-policy-sources) — List policy sources
  - [GET `/api/admin/v1/applications/{applicationId}/settings/public-manifest`](#get-public-manifest) — Get public manifest
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/public-manifest`](#update-public-manifest) — Update public manifest
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/public-manifest`](#remove-public-manifest) — Remove public manifest
  - [GET `/api/admin/v1/applications/{applicationId}/settings/signup-policy`](#get-signup-policy) — Get signup policy
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/signup-policy`](#update-signup-policy) — Update signup policy
  - [GET `/api/admin/v1/applications/{applicationId}/settings/sms`](#get-sms) — Get SMS
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/sms`](#update-sms) — Update SMS
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/sms`](#remove-sms) — Remove SMS
  - [POST `/api/admin/v1/applications/{applicationId}/settings/sms/test`](#send-a-test-sms) — Send a test SMS
  - [GET `/api/admin/v1/applications/{applicationId}/settings/smtp`](#get-smtp) — Get SMTP
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/smtp`](#update-smtp) — Update SMTP
  - [GET `/api/admin/v1/applications/{applicationId}/settings/token-claims`](#list-token-claims) — List token claims
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/token-claims`](#update-token-claims) — Update token claims
  - [GET `/api/admin/v1/applications/{applicationId}/settings/token-policy`](#get-token-policy) — Get token policy
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/token-policy`](#update-token-policy) — Update token policy
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/token-policy`](#remove-token-policy) — Remove token policy
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/token-policy/tab-enabled`](#update-tab-enabled-1) — Update tab enabled
  - [GET `/api/admin/v1/applications/{applicationId}/settings/token-policy/tab-state`](#get-tab-state-1) — Get tab state
  - [GET `/api/admin/v1/applications/{applicationId}/settings/user-directory`](#get-user-directory) — Get user directory
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/user-directory`](#update-user-directory) — Update user directory
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/user-directory`](#remove-user-directory) — Remove user directory
  - [GET `/api/admin/v1/applications/{applicationId}/settings/verification-policy`](#get-verification-policy) — Get verification policy
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/verification-policy`](#update-verification-policy) — Update verification policy
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/verification-policy`](#remove-verification-policy) — Remove verification policy
  - [GET `/api/admin/v1/applications/{applicationId}/settings/workspace`](#get-workspace) — Get workspace
  - [GET `/api/admin/v1/applications/{applicationId}/users`](#list-users) — List users
  - [GET `/api/admin/v1/applications/{applicationId}/users/export`](#export-user) — Export user
  - [POST `/api/admin/v1/applications/{applicationId}/users/import`](#import-users-from-a-csv-file) — Import users from a CSV file
  - [GET `/api/admin/v1/applications/{applicationId}/users/{userId}`](#get-user) — Get user
  - [POST `/api/admin/v1/applications/{applicationId}/users/{userId}`](#add-a-user-to-the-application) — Add a user to the application
  - [DELETE `/api/admin/v1/applications/{applicationId}/users/{userId}`](#remove-a-user-from-the-application) — Remove a user from the application
  - [GET `/api/admin/v1/applications/{applicationId}/users/{userId}/attributes`](#list-attributes) — List attributes
  - [PUT `/api/admin/v1/applications/{applicationId}/users/{userId}/attributes`](#update-attributes) — Update attributes
  - [PUT `/api/admin/v1/applications/{applicationId}/users/{userId}/auth-methods`](#update-auth-methods-1) — Update auth methods
  - [PATCH `/api/admin/v1/applications/{applicationId}/users/{userId}/email-verification`](#update-email-verification) — Update email verification
  - [POST `/api/admin/v1/applications/{applicationId}/users/{userId}/email/resend-verification`](#resend-email-verification) — Resend email verification
  - [POST `/api/admin/v1/applications/{applicationId}/users/{userId}/email/verify`](#verify-email) — Verify email
  - [GET `/api/admin/v1/applications/{applicationId}/users/{userId}/identities`](#list-identities) — List identities
  - [DELETE `/api/admin/v1/applications/{applicationId}/users/{userId}/identities/{identityId}`](#remove-identity) — Remove identity
  - [DELETE `/api/admin/v1/applications/{applicationId}/users/{userId}/mfa/factors/{factorId}`](#remove-factor) — Remove factor
  - [POST `/api/admin/v1/applications/{applicationId}/users/{userId}/mfa/methods/{methodId}/reset`](#reset-one-mfa-method-of-a-user) — Reset one MFA method of a user
  - [POST `/api/admin/v1/applications/{applicationId}/users/{userId}/password/remove`](#remove-password) — Remove password
  - [POST `/api/admin/v1/applications/{applicationId}/users/{userId}/password/reset-email`](#reset-password-email) — Reset password email
  - [POST `/api/admin/v1/applications/{applicationId}/users/{userId}/password/set`](#set-password) — Set password
  - [POST `/api/admin/v1/applications/{applicationId}/users/{userId}/password/set-password-email`](#set-password-email) — Set password email
  - [GET `/api/admin/v1/applications/{applicationId}/users/{userId}/role-assignments`](#list-role-assignments) — List role assignments
  - [PUT `/api/admin/v1/applications/{applicationId}/users/{userId}/role-assignments/{roleId}`](#update-role-assignment) — Update role assignment
  - [DELETE `/api/admin/v1/applications/{applicationId}/users/{userId}/role-assignments/{roleId}`](#remove-role-assignment) — Remove role assignment
  - [GET `/api/admin/v1/applications/{applicationId}/users/{userId}/sessions`](#list-sessions) — List sessions
  - [POST `/api/admin/v1/applications/{applicationId}/users/{userId}/sign-out`](#sign-out) — Sign out
- [Application RBAC](#application-rbac)
  - [GET `/api/admin/v1/applications/{applicationId}/groups`](#list-groups) — List groups
  - [POST `/api/admin/v1/applications/{applicationId}/groups`](#create-group) — Create group
  - [GET `/api/admin/v1/applications/{applicationId}/groups/{groupId}`](#get-group) — Get group
  - [PUT `/api/admin/v1/applications/{applicationId}/groups/{groupId}`](#update-group) — Update group
  - [DELETE `/api/admin/v1/applications/{applicationId}/groups/{groupId}`](#remove-group) — Remove group
  - [GET `/api/admin/v1/applications/{applicationId}/permissions`](#list-permissions) — List permissions
  - [POST `/api/admin/v1/applications/{applicationId}/permissions`](#create-permission) — Create permission
  - [POST `/api/admin/v1/applications/{applicationId}/permissions/seed-defaults`](#add-the-default-permissions) — Add the default permissions
  - [GET `/api/admin/v1/applications/{applicationId}/permissions/{permissionId}`](#get-permission) — Get permission
  - [PUT `/api/admin/v1/applications/{applicationId}/permissions/{permissionId}`](#update-permission) — Update permission
  - [DELETE `/api/admin/v1/applications/{applicationId}/permissions/{permissionId}`](#remove-permission) — Remove permission
  - [GET `/api/admin/v1/applications/{applicationId}/roles`](#list-roles) — List roles
  - [POST `/api/admin/v1/applications/{applicationId}/roles`](#create-role) — Create role
  - [GET `/api/admin/v1/applications/{applicationId}/roles/{roleId}`](#get-role) — Get role
  - [PUT `/api/admin/v1/applications/{applicationId}/roles/{roleId}`](#update-role) — Update role
  - [DELETE `/api/admin/v1/applications/{applicationId}/roles/{roleId}`](#remove-role) — Remove role
  - [POST `/api/admin/v1/applications/{applicationId}/roles/{roleId}/permissions`](#add-a-permission-to-a-role) — Add a permission to a role
  - [DELETE `/api/admin/v1/applications/{applicationId}/roles/{roleId}/permissions/{permissionId}`](#remove-a-permission-from-a-role) — Remove a permission from a role
  - [GET `/api/admin/v1/applications/{applicationId}/roles/{roleId}/users`](#list-users-1) — List users
- [Application logs](#application-logs)
  - [GET `/api/admin/v1/logs`](#search-application-logs) — Search application logs
  - [DELETE `/api/admin/v1/logs`](#delete-application-logs) — Delete application logs
- [Application webhooks](#application-webhooks)
  - [GET `/api/admin/v1/applications/{applicationId}/settings/webhooks`](#list-webhooks) — List webhooks
  - [POST `/api/admin/v1/applications/{applicationId}/settings/webhooks`](#create-webhook) — Create webhook
  - [GET `/api/admin/v1/applications/{applicationId}/settings/webhooks/events`](#list-events) — List events
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/webhooks/{id}`](#update-webhook) — Update webhook
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/webhooks/{id}`](#remove-webhook) — Remove webhook
  - [GET `/api/admin/v1/applications/{applicationId}/settings/webhooks/{id}/deliveries`](#list-deliveries) — List deliveries
  - [POST `/api/admin/v1/applications/{applicationId}/settings/webhooks/{id}/deliveries/{deliveryId}/redeliver`](#redeliver) — Redeliver
  - [POST `/api/admin/v1/applications/{applicationId}/settings/webhooks/{id}/rotate-secret`](#rotate-webhook-secret) — Rotate webhook secret
  - [POST `/api/admin/v1/applications/{applicationId}/settings/webhooks/{id}/test`](#test-webhook) — Test webhook
- [Application APIs](#application-apis)
  - [GET `/api/admin/v1/applications/{applicationId}/settings/apis`](#the-applications-apis) — The application's APIs
  - [POST `/api/admin/v1/applications/{applicationId}/settings/apis`](#add-an-api-name-identifier-scopes) — Add an API (name, identifier, scopes)
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/apis/{apiId}`](#change-an-api) — Change an API
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/apis/{apiId}`](#remove-an-api-new-tokens-stop-naming-it-tokens-already-issued-keep-it-until-they-expire) — Remove an API (new tokens stop naming it; tokens already issued keep it until they expire)
- [Application SAML apps](#application-saml-apps)
  - [GET `/api/admin/v1/applications/{applicationId}/settings/saml-apps`](#the-saml-apps-and-the-identity-provider-they-use-entity-id-sso-url-metadata-certificate) — The SAML apps and the identity provider they use (entity ID, SSO URL, metadata, certificate)
  - [POST `/api/admin/v1/applications/{applicationId}/settings/saml-apps`](#add-a-saml-app-name-entityid-acsurl-optional-slourl-nameidformat-attributes-spcertificate) — Add a SAML app (name, entityId, acsUrl; optional sloUrl, nameIdFormat, attributes, spCertificate)
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/saml-apps/{spId}`](#change-a-saml-app-fields-left-out-keep-their-values) — Change a SAML app (fields left out keep their values)
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/saml-apps/{spId}`](#remove-a-saml-app-it-can-no-longer-sign-users-in) — Remove a SAML app (it can no longer sign users in)
- [Application client registration](#application-client-registration)
  - [GET `/api/admin/v1/applications/{applicationId}/settings/client-registration`](#mode-off-token-open-policies-endpoints-and-the-number-of-registered-clients) — Mode (off, token, open), policies, endpoints and the number of registered clients
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/client-registration`](#save-the-mode-and-policies) — Save the mode and policies
  - [GET `/api/admin/v1/applications/{applicationId}/settings/client-registration/tokens`](#initial-access-tokens-values-are-shown-only-when-created) — Initial access tokens (values are shown only when created)
  - [POST `/api/admin/v1/applications/{applicationId}/settings/client-registration/tokens`](#create-an-initial-access-token-label-maxuses-1-1000-expiresinhours-1-8760-the-value-is-shown-once) — Create an initial access token: label, maxUses (1-1000), expiresInHours (1-8760); the value is shown once
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/client-registration/tokens/{tokenId}`](#revoke-an-initial-access-token-clients-it-registered-stay) — Revoke an initial access token (clients it registered stay)

## Application scope

Users, settings, import/export for one application

### Get appearance

`GET /api/admin/v1/applications/{applicationId}/settings/appearance`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/appearance" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update appearance

`PUT /api/admin/v1/applications/{applicationId}/settings/appearance`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/appearance" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Remove appearance

`DELETE /api/admin/v1/applications/{applicationId}/settings/appearance`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/appearance" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List auth methods

`GET /api/admin/v1/applications/{applicationId}/settings/auth-methods`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/auth-methods" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update auth methods

`PUT /api/admin/v1/applications/{applicationId}/settings/auth-methods`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`): object[]

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/auth-methods" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '[
  {}
]'
```

### Remove auth methods

`DELETE /api/admin/v1/applications/{applicationId}/settings/auth-methods`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/auth-methods" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Get client integration

`GET /api/admin/v1/applications/{applicationId}/settings/client-integration`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/client-integration" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update client integration

`PUT /api/admin/v1/applications/{applicationId}/settings/client-integration`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/client-integration" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Get email

`GET /api/admin/v1/applications/{applicationId}/settings/email`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/email" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update email

`PUT /api/admin/v1/applications/{applicationId}/settings/email`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/email" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Remove email

`DELETE /api/admin/v1/applications/{applicationId}/settings/email`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/email" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List email templates

`GET /api/admin/v1/applications/{applicationId}/settings/email-templates`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/email-templates" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update email templates

`PUT /api/admin/v1/applications/{applicationId}/settings/email-templates`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/email-templates" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List default email templates

`GET /api/admin/v1/applications/{applicationId}/settings/email-templates/defaults`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/email-templates/defaults" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Remove email template

`DELETE /api/admin/v1/applications/{applicationId}/settings/email-templates/{templateKey}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `templateKey` | path | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/email-templates/{templateKey}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Send a test email

`POST /api/admin/v1/applications/{applicationId}/settings/email/test`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [TestEmailRequest](data-models.md#testemailrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `bcc` | string[] | no |  |
| `cc` | string[] | no |  |
| `customData` | object | no |  |
| `templateKey` | string | no |  |
| `to` | string (email) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/email/test" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "bcc": [
    "bcc"
  ],
  "cc": [
    "cc"
  ],
  "customData": {},
  "templateKey": "key",
  "to": "you@example.com"
}'
```

### Get exposure

`GET /api/admin/v1/applications/{applicationId}/settings/exposure`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/exposure" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List feature flags

`GET /api/admin/v1/applications/{applicationId}/settings/feature-flags`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/feature-flags" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update feature flags

`PUT /api/admin/v1/applications/{applicationId}/settings/feature-flags`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`): object[]

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/feature-flags" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '[
  {}
]'
```

### Remove feature flags

`DELETE /api/admin/v1/applications/{applicationId}/settings/feature-flags`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/feature-flags" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Get MFA policy

`GET /api/admin/v1/applications/{applicationId}/settings/mfa-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/mfa-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update MFA policy

`PUT /api/admin/v1/applications/{applicationId}/settings/mfa-policy`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/mfa-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Remove MFA policy

`DELETE /api/admin/v1/applications/{applicationId}/settings/mfa-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/mfa-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update tab enabled

`PUT /api/admin/v1/applications/{applicationId}/settings/mfa-tab/tab-enabled`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/mfa-tab/tab-enabled" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Get tab state

`GET /api/admin/v1/applications/{applicationId}/settings/mfa-tab/tab-state`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/mfa-tab/tab-state" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List notifications

`GET /api/admin/v1/applications/{applicationId}/settings/notifications`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/notifications" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update notifications

`PUT /api/admin/v1/applications/{applicationId}/settings/notifications`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/notifications" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Remove notifications

`DELETE /api/admin/v1/applications/{applicationId}/settings/notifications`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/notifications" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Get password policy

`GET /api/admin/v1/applications/{applicationId}/settings/password-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/password-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update password policy

`PUT /api/admin/v1/applications/{applicationId}/settings/password-policy`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/password-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Remove password policy

`DELETE /api/admin/v1/applications/{applicationId}/settings/password-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/password-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Get policy source

`GET /api/admin/v1/applications/{applicationId}/settings/policy-source/{exposureKey}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `exposureKey` | path | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/policy-source/{exposureKey}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update policy source

`PUT /api/admin/v1/applications/{applicationId}/settings/policy-source/{exposureKey}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `exposureKey` | path | string | yes |  |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/policy-source/{exposureKey}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List policy sources

`GET /api/admin/v1/applications/{applicationId}/settings/policy-sources`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/policy-sources" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Get public manifest

`GET /api/admin/v1/applications/{applicationId}/settings/public-manifest`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/public-manifest" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update public manifest

`PUT /api/admin/v1/applications/{applicationId}/settings/public-manifest`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/public-manifest" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Remove public manifest

`DELETE /api/admin/v1/applications/{applicationId}/settings/public-manifest`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/public-manifest" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Get signup policy

`GET /api/admin/v1/applications/{applicationId}/settings/signup-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/signup-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update signup policy

`PUT /api/admin/v1/applications/{applicationId}/settings/signup-policy`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/signup-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Get SMS

`GET /api/admin/v1/applications/{applicationId}/settings/sms`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/sms" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update SMS

`PUT /api/admin/v1/applications/{applicationId}/settings/sms`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/sms" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Remove SMS

`DELETE /api/admin/v1/applications/{applicationId}/settings/sms`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/sms" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Send a test SMS

`POST /api/admin/v1/applications/{applicationId}/settings/sms/test`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [TestSmsRequest](data-models.md#testsmsrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `to` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/sms/test" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "to": "you@example.com"
}'
```

### Get SMTP

`GET /api/admin/v1/applications/{applicationId}/settings/smtp`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/smtp" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update SMTP

`PUT /api/admin/v1/applications/{applicationId}/settings/smtp`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/smtp" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List token claims

`GET /api/admin/v1/applications/{applicationId}/settings/token-claims`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/token-claims" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update token claims

`PUT /api/admin/v1/applications/{applicationId}/settings/token-claims`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/token-claims" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Get token policy

`GET /api/admin/v1/applications/{applicationId}/settings/token-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/token-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update token policy

`PUT /api/admin/v1/applications/{applicationId}/settings/token-policy`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/token-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Remove token policy

`DELETE /api/admin/v1/applications/{applicationId}/settings/token-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/token-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update tab enabled

`PUT /api/admin/v1/applications/{applicationId}/settings/token-policy/tab-enabled`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/token-policy/tab-enabled" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Get tab state

`GET /api/admin/v1/applications/{applicationId}/settings/token-policy/tab-state`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/token-policy/tab-state" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Get user directory

`GET /api/admin/v1/applications/{applicationId}/settings/user-directory`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/user-directory" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update user directory

`PUT /api/admin/v1/applications/{applicationId}/settings/user-directory`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/user-directory" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Remove user directory

`DELETE /api/admin/v1/applications/{applicationId}/settings/user-directory`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/user-directory" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Get verification policy

`GET /api/admin/v1/applications/{applicationId}/settings/verification-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/verification-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update verification policy

`PUT /api/admin/v1/applications/{applicationId}/settings/verification-policy`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/verification-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Remove verification policy

`DELETE /api/admin/v1/applications/{applicationId}/settings/verification-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/verification-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Get workspace

`GET /api/admin/v1/applications/{applicationId}/settings/workspace`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/workspace" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List users

`GET /api/admin/v1/applications/{applicationId}/users`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserResponse](data-models.md#userresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Export user

`GET /api/admin/v1/applications/{applicationId}/users/export`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `format` | query | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | string |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/export" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Import users from a CSV file

`POST /api/admin/v1/applications/{applicationId}/users/import`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `source` | query | string | no |  |

**Request body** (`multipart/form-data`)

| Field | Type | Required | Description |
|---|---|---|---|
| `file` | string (binary) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/import" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -F "file=@users.csv"
```

### Get user

`GET /api/admin/v1/applications/{applicationId}/users/{userId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserResponse](data-models.md#userresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Add a user to the application

`POST /api/admin/v1/applications/{applicationId}/users/{userId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Remove a user from the application

`DELETE /api/admin/v1/applications/{applicationId}/users/{userId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List attributes

`GET /api/admin/v1/applications/{applicationId}/users/{userId}/attributes`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}/attributes" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update attributes

`PUT /api/admin/v1/applications/{applicationId}/users/{userId}/attributes`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}/attributes" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update auth methods

`PUT /api/admin/v1/applications/{applicationId}/users/{userId}/auth-methods`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Request body** (`application/json`, [UserAuthMethodsUpdateRequest](data-models.md#userauthmethodsupdaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `methods` | object | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserResponse](data-models.md#userresponse) |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}/auth-methods" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "methods": {}
}'
```

### Update email verification

`PATCH /api/admin/v1/applications/{applicationId}/users/{userId}/email-verification`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserResponse](data-models.md#userresponse) |

```bash
curl -s -X PATCH "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}/email-verification" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Resend email verification

`POST /api/admin/v1/applications/{applicationId}/users/{userId}/email/resend-verification`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `202` | Accepted | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}/email/resend-verification" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Verify email

`POST /api/admin/v1/applications/{applicationId}/users/{userId}/email/verify`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserResponse](data-models.md#userresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}/email/verify" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List identities

`GET /api/admin/v1/applications/{applicationId}/users/{userId}/identities`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}/identities" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Remove identity

`DELETE /api/admin/v1/applications/{applicationId}/users/{userId}/identities/{identityId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |
| `identityId` | path | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}/identities/{identityId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Remove factor

`DELETE /api/admin/v1/applications/{applicationId}/users/{userId}/mfa/factors/{factorId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |
| `factorId` | path | string (uuid) | yes | MFA factor ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}/mfa/factors/{factorId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Reset one MFA method of a user

`POST /api/admin/v1/applications/{applicationId}/users/{userId}/mfa/methods/{methodId}/reset`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |
| `methodId` | path | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserResponse](data-models.md#userresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}/mfa/methods/{methodId}/reset" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Remove password

`POST /api/admin/v1/applications/{applicationId}/users/{userId}/password/remove`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}/password/remove" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Reset password email

`POST /api/admin/v1/applications/{applicationId}/users/{userId}/password/reset-email`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `202` | Accepted | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}/password/reset-email" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Set password

`POST /api/admin/v1/applications/{applicationId}/users/{userId}/password/set`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Request body** (`application/json`, [AdminSetPasswordRequest](data-models.md#adminsetpasswordrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `password` | string | yes |  |
| `temporary` | boolean | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}/password/set" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "password": "Choose-a-Strong-Pass-1!",
  "temporary": true
}'
```

### Set password email

`POST /api/admin/v1/applications/{applicationId}/users/{userId}/password/set-password-email`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `202` | Accepted | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}/password/set-password-email" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List role assignments

`GET /api/admin/v1/applications/{applicationId}/users/{userId}/role-assignments`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}/role-assignments" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update role assignment

`PUT /api/admin/v1/applications/{applicationId}/users/{userId}/role-assignments/{roleId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |
| `roleId` | path | string (uuid) | yes | Role ID (UUID) |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}/role-assignments/{roleId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Remove role assignment

`DELETE /api/admin/v1/applications/{applicationId}/users/{userId}/role-assignments/{roleId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |
| `roleId` | path | string (uuid) | yes | Role ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}/role-assignments/{roleId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List sessions

`GET /api/admin/v1/applications/{applicationId}/users/{userId}/sessions`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}/sessions" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Sign out

`POST /api/admin/v1/applications/{applicationId}/users/{userId}/sign-out`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}/sign-out" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## Application RBAC

Roles and permissions for one application

### List groups

`GET /api/admin/v1/applications/{applicationId}/groups`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [GroupSummaryResponse](data-models.md#groupsummaryresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/groups" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Create group

`POST /api/admin/v1/applications/{applicationId}/groups`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [GroupCreateRequest](data-models.md#groupcreaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | yes |  |
| `description` | string | no |  |
| `memberUserIds` | string (uuid)[] | no |  |
| `name` | string | yes |  |
| `roleIds` | string (uuid)[] | no |  |
| `tenantId` | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | [GroupDetailResponse](data-models.md#groupdetailresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/groups" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "applicationId": "{applicationId}",
  "description": "Short explanation",
  "memberUserIds": [
    "{memberUserId}"
  ],
  "name": "Tenant A Shop",
  "roleIds": [
    "{roleId}"
  ],
  "tenantId": "{tenantId}"
}'
```

### Get group

`GET /api/admin/v1/applications/{applicationId}/groups/{groupId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `groupId` | path | string (uuid) | yes | Group ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [GroupDetailResponse](data-models.md#groupdetailresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/groups/{groupId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update group

`PUT /api/admin/v1/applications/{applicationId}/groups/{groupId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `groupId` | path | string (uuid) | yes | Group ID (UUID) |

**Request body** (`application/json`, [GroupUpdateRequest](data-models.md#groupupdaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | no |  |
| `memberUserIds` | string (uuid)[] | no |  |
| `name` | string | yes |  |
| `roleIds` | string (uuid)[] | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [GroupDetailResponse](data-models.md#groupdetailresponse) |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/groups/{groupId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "description": "Short explanation",
  "memberUserIds": [
    "{memberUserId}"
  ],
  "name": "Tenant A Shop",
  "roleIds": [
    "{roleId}"
  ]
}'
```

### Remove group

`DELETE /api/admin/v1/applications/{applicationId}/groups/{groupId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `groupId` | path | string (uuid) | yes | Group ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/groups/{groupId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List permissions

`GET /api/admin/v1/applications/{applicationId}/permissions`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [PermissionResponse](data-models.md#permissionresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/permissions" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Create permission

`POST /api/admin/v1/applications/{applicationId}/permissions`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [PermissionCreateRequest](data-models.md#permissioncreaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | no |  |
| `key` | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | [PermissionDetailResponse](data-models.md#permissiondetailresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/permissions" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "description": "Short explanation",
  "key": "orders:read"
}'
```

### Add the default permissions

`POST /api/admin/v1/applications/{applicationId}/permissions/seed-defaults`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [SeedPermissionsResponse](data-models.md#seedpermissionsresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/permissions/seed-defaults" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Get permission

`GET /api/admin/v1/applications/{applicationId}/permissions/{permissionId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `permissionId` | path | string (uuid) | yes | Permission ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [PermissionDetailResponse](data-models.md#permissiondetailresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/permissions/{permissionId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update permission

`PUT /api/admin/v1/applications/{applicationId}/permissions/{permissionId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `permissionId` | path | string (uuid) | yes | Permission ID (UUID) |

**Request body** (`application/json`, [PermissionUpdateRequest](data-models.md#permissionupdaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | no |  |
| `key` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [PermissionDetailResponse](data-models.md#permissiondetailresponse) |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/permissions/{permissionId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "description": "Short explanation",
  "key": "orders:read"
}'
```

### Remove permission

`DELETE /api/admin/v1/applications/{applicationId}/permissions/{permissionId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `permissionId` | path | string (uuid) | yes | Permission ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/permissions/{permissionId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List roles

`GET /api/admin/v1/applications/{applicationId}/roles`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [RoleSummaryResponse](data-models.md#rolesummaryresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/roles" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Create role

`POST /api/admin/v1/applications/{applicationId}/roles`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [RoleCreateRequest](data-models.md#rolecreaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | yes |  |
| `childRoleIds` | string (uuid)[] | no |  |
| `description` | string | no |  |
| `isComposite` | boolean | no |  |
| `name` | string | yes |  |
| `permissionIds` | string (uuid)[] | no |  |
| `tenantId` | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | [RoleDetailResponse](data-models.md#roledetailresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/roles" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "applicationId": "{applicationId}",
  "childRoleIds": [
    "{childRoleId}"
  ],
  "description": "Short explanation",
  "isComposite": true,
  "name": "Tenant A Shop",
  "permissionIds": [
    "{permissionId}"
  ],
  "tenantId": "{tenantId}"
}'
```

### Get role

`GET /api/admin/v1/applications/{applicationId}/roles/{roleId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `roleId` | path | string (uuid) | yes | Role ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [RoleDetailResponse](data-models.md#roledetailresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/roles/{roleId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update role

`PUT /api/admin/v1/applications/{applicationId}/roles/{roleId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `roleId` | path | string (uuid) | yes | Role ID (UUID) |

**Request body** (`application/json`, [RoleUpdateRequest](data-models.md#roleupdaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `childRoleIds` | string (uuid)[] | no |  |
| `description` | string | no |  |
| `isComposite` | boolean | no |  |
| `name` | string | yes |  |
| `permissionIds` | string (uuid)[] | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [RoleDetailResponse](data-models.md#roledetailresponse) |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/roles/{roleId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "childRoleIds": [
    "{childRoleId}"
  ],
  "description": "Short explanation",
  "isComposite": true,
  "name": "Tenant A Shop",
  "permissionIds": [
    "{permissionId}"
  ]
}'
```

### Remove role

`DELETE /api/admin/v1/applications/{applicationId}/roles/{roleId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `roleId` | path | string (uuid) | yes | Role ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/roles/{roleId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Add a permission to a role

`POST /api/admin/v1/applications/{applicationId}/roles/{roleId}/permissions`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `roleId` | path | string (uuid) | yes | Role ID (UUID) |

**Request body** (`application/json`, [RolePermissionPatchRequest](data-models.md#rolepermissionpatchrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `permissionId` | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [RoleDetailResponse](data-models.md#roledetailresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/roles/{roleId}/permissions" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "permissionId": "{permissionId}"
}'
```

### Remove a permission from a role

`DELETE /api/admin/v1/applications/{applicationId}/roles/{roleId}/permissions/{permissionId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `roleId` | path | string (uuid) | yes | Role ID (UUID) |
| `permissionId` | path | string (uuid) | yes | Permission ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [RoleDetailResponse](data-models.md#roledetailresponse) |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/roles/{roleId}/permissions/{permissionId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List users

`GET /api/admin/v1/applications/{applicationId}/roles/{roleId}/users`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `roleId` | path | string (uuid) | yes | Role ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [RoleAssignedUserResponse](data-models.md#roleassigneduserresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/roles/{roleId}/users" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## Application logs

CloudWatch-style searchable application logs

### Search application logs

`GET /api/admin/v1/logs`

Filter by session ID, request ID, level, time range, or message text. Use sessionId from console output or sign-in history.

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `sessionId` | query | string | no |  |
| `requestId` | query | string | no |  |
| `level` | query | string | no |  |
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |
| `tenantId` | query | string (uuid) | no | Tenant ID (UUID) |
| `since` | query | string (date-time) | no |  |
| `until` | query | string (date-time) | no |  |
| `search` | query | string | no | Text to search for |
| `includeUnscoped` | query | boolean | no |  |
| `limit` | query | integer (int32) | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [ApplicationLogResponse](data-models.md#applicationlogresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/logs" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Delete application logs

`DELETE /api/admin/v1/logs`

Delete logs matching the same filters as search. Requires a date boundary: use period (today|yesterday), date (single day), or since/until (range). Optional session, level, and message filters narrow the delete.

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `sessionId` | query | string | no |  |
| `requestId` | query | string | no |  |
| `level` | query | string | no |  |
| `applicationId` | query | string (uuid) | no | Application ID (UUID) |
| `tenantId` | query | string (uuid) | no | Tenant ID (UUID) |
| `since` | query | string (date-time) | no |  |
| `until` | query | string (date-time) | no |  |
| `search` | query | string | no | Text to search for |
| `includeUnscoped` | query | boolean | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/logs" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## Application webhooks

Webhook endpoints that receive one application's events

### List webhooks

`GET /api/admin/v1/applications/{applicationId}/settings/webhooks`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/webhooks" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Create webhook

`POST /api/admin/v1/applications/{applicationId}/settings/webhooks`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/webhooks" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List events

`GET /api/admin/v1/applications/{applicationId}/settings/webhooks/events`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/webhooks/events" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Update webhook

`PUT /api/admin/v1/applications/{applicationId}/settings/webhooks/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/webhooks/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Remove webhook

`DELETE /api/admin/v1/applications/{applicationId}/settings/webhooks/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/webhooks/{id}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### List deliveries

`GET /api/admin/v1/applications/{applicationId}/settings/webhooks/{id}/deliveries`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/webhooks/{id}/deliveries" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Redeliver

`POST /api/admin/v1/applications/{applicationId}/settings/webhooks/{id}/deliveries/{deliveryId}/redeliver`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `id` | path | string (uuid) | yes | ID of the item (UUID) |
| `deliveryId` | path | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/webhooks/{id}/deliveries/{deliveryId}/redeliver" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Rotate webhook secret

`POST /api/admin/v1/applications/{applicationId}/settings/webhooks/{id}/rotate-secret`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `id` | path | string (uuid) | yes | ID of the item (UUID) |
| `keepOldHours` | query | integer (int32) | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/webhooks/{id}/rotate-secret" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Test webhook

`POST /api/admin/v1/applications/{applicationId}/settings/webhooks/{id}/test`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `id` | path | string (uuid) | yes | ID of the item (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/webhooks/{id}/test" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## Application APIs

APIs (resource servers) an application's access tokens are for

### The application's APIs

`GET /api/admin/v1/applications/{applicationId}/settings/apis`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/apis" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Add an API (name, identifier, scopes)

`POST /api/admin/v1/applications/{applicationId}/settings/apis`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/apis" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Change an API

`PUT /api/admin/v1/applications/{applicationId}/settings/apis/{apiId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `apiId` | path | string (uuid) | yes |  |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/apis/{apiId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Remove an API (new tokens stop naming it; tokens already issued keep it until they expire)

`DELETE /api/admin/v1/applications/{applicationId}/settings/apis/{apiId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `apiId` | path | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/apis/{apiId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## Application SAML apps

SAML service providers signing in through SecureOne

### The SAML apps and the identity provider they use (entity ID, SSO URL, metadata, certificate)

`GET /api/admin/v1/applications/{applicationId}/settings/saml-apps`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/saml-apps" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Add a SAML app (name, entityId, acsUrl; optional sloUrl, nameIdFormat, attributes, spCertificate)

`POST /api/admin/v1/applications/{applicationId}/settings/saml-apps`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/saml-apps" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Change a SAML app (fields left out keep their values)

`PUT /api/admin/v1/applications/{applicationId}/settings/saml-apps/{spId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `spId` | path | string (uuid) | yes |  |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/saml-apps/{spId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Remove a SAML app (it can no longer sign users in)

`DELETE /api/admin/v1/applications/{applicationId}/settings/saml-apps/{spId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `spId` | path | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/saml-apps/{spId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## Application client registration

Dynamic client registration settings and initial access tokens

### Mode (off, token, open), policies, endpoints and the number of registered clients

`GET /api/admin/v1/applications/{applicationId}/settings/client-registration`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/client-registration" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Save the mode and policies

`PUT /api/admin/v1/applications/{applicationId}/settings/client-registration`

mode: off | token (an initial access token is needed) | open (no token; the tenant issuer's endpoint, one application per tenant). allowedScopes, allowedGrantTypes (authorization_code, refresh_token, client_credentials; not with open), trustedHosts (redirect URI hosts, *.example.com allowed; empty: any), maxClients, unusedDays (0: keep unused clients).

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/client-registration" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Initial access tokens (values are shown only when created)

`GET /api/admin/v1/applications/{applicationId}/settings/client-registration/tokens`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [InitialToken](data-models.md#initialtoken)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/client-registration/tokens" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Create an initial access token: label, maxUses (1-1000), expiresInHours (1-8760); the value is shown once

`POST /api/admin/v1/applications/{applicationId}/settings/client-registration/tokens`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | object |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/client-registration/tokens" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### Revoke an initial access token (clients it registered stay)

`DELETE /api/admin/v1/applications/{applicationId}/settings/client-registration/tokens/{tokenId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `tokenId` | path | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `204` | No Content | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/client-registration/tokens/{tokenId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```
