# 10. Admin API: application console

[API reference](README.md) › Admin API: application console

> Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.

Everything under /api/admin/v1/applications/{applicationId}/…: the application's users, roles, permissions, groups, settings and logs.

Set the variables from [Getting started](00-getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [10.1 Application scope](#101-application-scope)
  - [GET `/api/admin/v1/applications/{applicationId}/settings/appearance`](#1011-get-appearance) — Get appearance
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/appearance`](#1012-update-appearance) — Update appearance
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/appearance`](#1013-remove-appearance) — Remove appearance
  - [GET `/api/admin/v1/applications/{applicationId}/settings/auth-methods`](#1014-list-auth-methods) — List auth methods
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/auth-methods`](#1015-update-auth-methods) — Update auth methods
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/auth-methods`](#1016-remove-auth-methods) — Remove auth methods
  - [GET `/api/admin/v1/applications/{applicationId}/settings/client-integration`](#1017-get-client-integration) — Get client integration
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/client-integration`](#1018-update-client-integration) — Update client integration
  - [GET `/api/admin/v1/applications/{applicationId}/settings/email`](#1019-get-email) — Get email
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/email`](#10110-update-email) — Update email
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/email`](#10111-remove-email) — Remove email
  - [GET `/api/admin/v1/applications/{applicationId}/settings/email-templates`](#10112-list-email-templates) — List email templates
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/email-templates`](#10113-update-email-templates) — Update email templates
  - [GET `/api/admin/v1/applications/{applicationId}/settings/email-templates/defaults`](#10114-list-default-email-templates) — List default email templates
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/email-templates/{templateKey}`](#10115-remove-email-template) — Remove email template
  - [POST `/api/admin/v1/applications/{applicationId}/settings/email/test`](#10116-send-a-test-email) — Send a test email
  - [GET `/api/admin/v1/applications/{applicationId}/settings/exposure`](#10117-get-exposure) — Get exposure
  - [GET `/api/admin/v1/applications/{applicationId}/settings/feature-flags`](#10118-list-feature-flags) — List feature flags
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/feature-flags`](#10119-update-feature-flags) — Update feature flags
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/feature-flags`](#10120-remove-feature-flags) — Remove feature flags
  - [GET `/api/admin/v1/applications/{applicationId}/settings/mfa-policy`](#10121-get-mfa-policy) — Get MFA policy
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/mfa-policy`](#10122-update-mfa-policy) — Update MFA policy
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/mfa-policy`](#10123-remove-mfa-policy) — Remove MFA policy
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/mfa-tab/tab-enabled`](#10124-update-tab-enabled) — Update tab enabled
  - [GET `/api/admin/v1/applications/{applicationId}/settings/mfa-tab/tab-state`](#10125-get-tab-state) — Get tab state
  - [GET `/api/admin/v1/applications/{applicationId}/settings/notifications`](#10126-list-notifications) — List notifications
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/notifications`](#10127-update-notifications) — Update notifications
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/notifications`](#10128-remove-notifications) — Remove notifications
  - [GET `/api/admin/v1/applications/{applicationId}/settings/password-policy`](#10129-get-password-policy) — Get password policy
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/password-policy`](#10130-update-password-policy) — Update password policy
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/password-policy`](#10131-remove-password-policy) — Remove password policy
  - [GET `/api/admin/v1/applications/{applicationId}/settings/policy-source/{exposureKey}`](#10132-get-policy-source) — Get policy source
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/policy-source/{exposureKey}`](#10133-update-policy-source) — Update policy source
  - [GET `/api/admin/v1/applications/{applicationId}/settings/policy-sources`](#10134-list-policy-sources) — List policy sources
  - [GET `/api/admin/v1/applications/{applicationId}/settings/public-manifest`](#10135-get-public-manifest) — Get public manifest
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/public-manifest`](#10136-update-public-manifest) — Update public manifest
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/public-manifest`](#10137-remove-public-manifest) — Remove public manifest
  - [GET `/api/admin/v1/applications/{applicationId}/settings/signup-policy`](#10138-get-signup-policy) — Get signup policy
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/signup-policy`](#10139-update-signup-policy) — Update signup policy
  - [GET `/api/admin/v1/applications/{applicationId}/settings/sms`](#10140-get-sms) — Get SMS
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/sms`](#10141-update-sms) — Update SMS
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/sms`](#10142-remove-sms) — Remove SMS
  - [POST `/api/admin/v1/applications/{applicationId}/settings/sms/test`](#10143-send-a-test-sms) — Send a test SMS
  - [GET `/api/admin/v1/applications/{applicationId}/settings/smtp`](#10144-get-smtp) — Get SMTP
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/smtp`](#10145-update-smtp) — Update SMTP
  - [GET `/api/admin/v1/applications/{applicationId}/settings/token-claims`](#10146-list-token-claims) — List token claims
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/token-claims`](#10147-update-token-claims) — Update token claims
  - [GET `/api/admin/v1/applications/{applicationId}/settings/token-policy`](#10148-get-token-policy) — Get token policy
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/token-policy`](#10149-update-token-policy) — Update token policy
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/token-policy`](#10150-remove-token-policy) — Remove token policy
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/token-policy/tab-enabled`](#10151-update-tab-enabled) — Update tab enabled
  - [GET `/api/admin/v1/applications/{applicationId}/settings/token-policy/tab-state`](#10152-get-tab-state) — Get tab state
  - [GET `/api/admin/v1/applications/{applicationId}/settings/user-directory`](#10153-get-user-directory) — Get user directory
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/user-directory`](#10154-update-user-directory) — Update user directory
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/user-directory`](#10155-remove-user-directory) — Remove user directory
  - [GET `/api/admin/v1/applications/{applicationId}/settings/verification-policy`](#10156-get-verification-policy) — Get verification policy
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/verification-policy`](#10157-update-verification-policy) — Update verification policy
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/verification-policy`](#10158-remove-verification-policy) — Remove verification policy
  - [GET `/api/admin/v1/applications/{applicationId}/settings/workspace`](#10159-get-workspace) — Get workspace
  - [GET `/api/admin/v1/applications/{applicationId}/users`](#10160-list-users) — List users
  - [GET `/api/admin/v1/applications/{applicationId}/users/export`](#10161-export-user) — Export user
  - [POST `/api/admin/v1/applications/{applicationId}/users/import`](#10162-import-users-from-a-csv-file) — Import users from a CSV file
  - [GET `/api/admin/v1/applications/{applicationId}/users/{userId}`](#10163-get-user) — Get user
  - [POST `/api/admin/v1/applications/{applicationId}/users/{userId}`](#10164-add-a-user-to-the-application) — Add a user to the application
  - [DELETE `/api/admin/v1/applications/{applicationId}/users/{userId}`](#10165-remove-a-user-from-the-application) — Remove a user from the application
  - [GET `/api/admin/v1/applications/{applicationId}/users/{userId}/attributes`](#10166-list-attributes) — List attributes
  - [PUT `/api/admin/v1/applications/{applicationId}/users/{userId}/attributes`](#10167-update-attributes) — Update attributes
  - [PUT `/api/admin/v1/applications/{applicationId}/users/{userId}/auth-methods`](#10168-update-auth-methods) — Update auth methods
  - [PATCH `/api/admin/v1/applications/{applicationId}/users/{userId}/email-verification`](#10169-update-email-verification) — Update email verification
  - [POST `/api/admin/v1/applications/{applicationId}/users/{userId}/email/resend-verification`](#10170-resend-email-verification) — Resend email verification
  - [POST `/api/admin/v1/applications/{applicationId}/users/{userId}/email/verify`](#10171-verify-email) — Verify email
  - [GET `/api/admin/v1/applications/{applicationId}/users/{userId}/identities`](#10172-list-identities) — List identities
  - [DELETE `/api/admin/v1/applications/{applicationId}/users/{userId}/identities/{identityId}`](#10173-remove-identity) — Remove identity
  - [DELETE `/api/admin/v1/applications/{applicationId}/users/{userId}/mfa/factors/{factorId}`](#10174-remove-factor) — Remove factor
  - [POST `/api/admin/v1/applications/{applicationId}/users/{userId}/mfa/methods/{methodId}/reset`](#10175-reset-one-mfa-method-of-a-user) — Reset one MFA method of a user
  - [POST `/api/admin/v1/applications/{applicationId}/users/{userId}/password/remove`](#10176-remove-password) — Remove password
  - [POST `/api/admin/v1/applications/{applicationId}/users/{userId}/password/reset-email`](#10177-reset-password-email) — Reset password email
  - [POST `/api/admin/v1/applications/{applicationId}/users/{userId}/password/set`](#10178-set-password) — Set password
  - [POST `/api/admin/v1/applications/{applicationId}/users/{userId}/password/set-password-email`](#10179-set-password-email) — Set password email
  - [GET `/api/admin/v1/applications/{applicationId}/users/{userId}/role-assignments`](#10180-list-role-assignments) — List role assignments
  - [PUT `/api/admin/v1/applications/{applicationId}/users/{userId}/role-assignments/{roleId}`](#10181-update-role-assignment) — Update role assignment
  - [DELETE `/api/admin/v1/applications/{applicationId}/users/{userId}/role-assignments/{roleId}`](#10182-remove-role-assignment) — Remove role assignment
  - [GET `/api/admin/v1/applications/{applicationId}/users/{userId}/sessions`](#10183-list-sessions) — List sessions
  - [POST `/api/admin/v1/applications/{applicationId}/users/{userId}/sign-out`](#10184-sign-out) — Sign out
- [10.2 Application RBAC](#102-application-rbac)
  - [GET `/api/admin/v1/applications/{applicationId}/groups`](#1021-list-groups) — List groups
  - [POST `/api/admin/v1/applications/{applicationId}/groups`](#1022-create-group) — Create group
  - [GET `/api/admin/v1/applications/{applicationId}/groups/{groupId}`](#1023-get-group) — Get group
  - [PUT `/api/admin/v1/applications/{applicationId}/groups/{groupId}`](#1024-update-group) — Update group
  - [DELETE `/api/admin/v1/applications/{applicationId}/groups/{groupId}`](#1025-remove-group) — Remove group
  - [GET `/api/admin/v1/applications/{applicationId}/permissions`](#1026-list-permissions) — List permissions
  - [POST `/api/admin/v1/applications/{applicationId}/permissions`](#1027-create-permission) — Create permission
  - [POST `/api/admin/v1/applications/{applicationId}/permissions/seed-defaults`](#1028-add-the-default-permissions) — Add the default permissions
  - [GET `/api/admin/v1/applications/{applicationId}/permissions/{permissionId}`](#1029-get-permission) — Get permission
  - [PUT `/api/admin/v1/applications/{applicationId}/permissions/{permissionId}`](#10210-update-permission) — Update permission
  - [DELETE `/api/admin/v1/applications/{applicationId}/permissions/{permissionId}`](#10211-remove-permission) — Remove permission
  - [GET `/api/admin/v1/applications/{applicationId}/roles`](#10212-list-roles) — List roles
  - [POST `/api/admin/v1/applications/{applicationId}/roles`](#10213-create-role) — Create role
  - [GET `/api/admin/v1/applications/{applicationId}/roles/{roleId}`](#10214-get-role) — Get role
  - [PUT `/api/admin/v1/applications/{applicationId}/roles/{roleId}`](#10215-update-role) — Update role
  - [DELETE `/api/admin/v1/applications/{applicationId}/roles/{roleId}`](#10216-remove-role) — Remove role
  - [POST `/api/admin/v1/applications/{applicationId}/roles/{roleId}/permissions`](#10217-add-a-permission-to-a-role) — Add a permission to a role
  - [DELETE `/api/admin/v1/applications/{applicationId}/roles/{roleId}/permissions/{permissionId}`](#10218-remove-a-permission-from-a-role) — Remove a permission from a role
  - [GET `/api/admin/v1/applications/{applicationId}/roles/{roleId}/users`](#10219-list-users) — List users
- [10.3 Application logs](#103-application-logs)
  - [GET `/api/admin/v1/logs`](#1031-search-application-logs) — Search application logs
  - [DELETE `/api/admin/v1/logs`](#1032-delete-application-logs) — Delete application logs
- [10.4 Application webhooks](#104-application-webhooks)
  - [GET `/api/admin/v1/applications/{applicationId}/settings/webhooks`](#1041-list-webhooks) — List webhooks
  - [POST `/api/admin/v1/applications/{applicationId}/settings/webhooks`](#1042-create-webhook) — Create webhook
  - [GET `/api/admin/v1/applications/{applicationId}/settings/webhooks/events`](#1043-list-events) — List events
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/webhooks/{id}`](#1044-update-webhook) — Update webhook
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/webhooks/{id}`](#1045-remove-webhook) — Remove webhook
  - [GET `/api/admin/v1/applications/{applicationId}/settings/webhooks/{id}/deliveries`](#1046-list-deliveries) — List deliveries
  - [POST `/api/admin/v1/applications/{applicationId}/settings/webhooks/{id}/deliveries/{deliveryId}/redeliver`](#1047-redeliver) — Redeliver
  - [POST `/api/admin/v1/applications/{applicationId}/settings/webhooks/{id}/rotate-secret`](#1048-rotate-webhook-secret) — Rotate webhook secret
  - [POST `/api/admin/v1/applications/{applicationId}/settings/webhooks/{id}/test`](#1049-test-webhook) — Test webhook
- [10.5 Application APIs](#105-application-apis)
  - [GET `/api/admin/v1/applications/{applicationId}/settings/apis`](#1051-the-applications-apis) — The application's APIs
  - [POST `/api/admin/v1/applications/{applicationId}/settings/apis`](#1052-add-an-api-name-identifier-scopes) — Add an API (name, identifier, scopes)
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/apis/{apiId}`](#1053-change-an-api) — Change an API
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/apis/{apiId}`](#1054-remove-an-api-new-tokens-stop-naming-it-tokens-already-issued-keep-it-until-they-expire) — Remove an API (new tokens stop naming it; tokens already issued keep it until they expire)
- [10.6 Application SAML apps](#106-application-saml-apps)
  - [GET `/api/admin/v1/applications/{applicationId}/settings/saml-apps`](#1061-the-saml-apps-and-the-identity-provider-they-use-entity-id-sso-url-metadata-certificate) — The SAML apps and the identity provider they use (entity ID, SSO URL, metadata, certificate)
  - [POST `/api/admin/v1/applications/{applicationId}/settings/saml-apps`](#1062-add-a-saml-app-name-entityid-acsurl-optional-slourl-nameidformat-attributes-spcertificate) — Add a SAML app (name, entityId, acsUrl; optional sloUrl, nameIdFormat, attributes, spCertificate)
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/saml-apps/{spId}`](#1063-change-a-saml-app-fields-left-out-keep-their-values) — Change a SAML app (fields left out keep their values)
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/saml-apps/{spId}`](#1064-remove-a-saml-app-it-can-no-longer-sign-users-in) — Remove a SAML app (it can no longer sign users in)
- [10.7 Application client registration](#107-application-client-registration)
  - [GET `/api/admin/v1/applications/{applicationId}/settings/client-registration`](#1071-mode-off-token-open-policies-endpoints-and-the-number-of-registered-clients) — Mode (off, token, open), policies, endpoints and the number of registered clients
  - [PUT `/api/admin/v1/applications/{applicationId}/settings/client-registration`](#1072-save-the-mode-and-policies) — Save the mode and policies
  - [GET `/api/admin/v1/applications/{applicationId}/settings/client-registration/tokens`](#1073-initial-access-tokens-values-are-shown-only-when-created) — Initial access tokens (values are shown only when created)
  - [POST `/api/admin/v1/applications/{applicationId}/settings/client-registration/tokens`](#1074-create-an-initial-access-token-label-maxuses-1-1000-expiresinhours-1-8760-the-value-is-shown-once) — Create an initial access token: label, maxUses (1-1000), expiresInHours (1-8760); the value is shown once
  - [DELETE `/api/admin/v1/applications/{applicationId}/settings/client-registration/tokens/{tokenId}`](#1075-revoke-an-initial-access-token-clients-it-registered-stay) — Revoke an initial access token (clients it registered stay)

## 10.1 Application scope

Users, settings, import/export for one application

### 10.1.1 Get appearance

`GET /api/admin/v1/applications/{applicationId}/settings/appearance`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.2 Update appearance

`PUT /api/admin/v1/applications/{applicationId}/settings/appearance`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/appearance" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.1.3 Remove appearance

`DELETE /api/admin/v1/applications/{applicationId}/settings/appearance`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.4 List auth methods

`GET /api/admin/v1/applications/{applicationId}/settings/auth-methods`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.5 Update auth methods

`PUT /api/admin/v1/applications/{applicationId}/settings/auth-methods`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.6 Remove auth methods

`DELETE /api/admin/v1/applications/{applicationId}/settings/auth-methods`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.7 Get client integration

`GET /api/admin/v1/applications/{applicationId}/settings/client-integration`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.8 Update client integration

`PUT /api/admin/v1/applications/{applicationId}/settings/client-integration`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/client-integration" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.1.9 Get email

`GET /api/admin/v1/applications/{applicationId}/settings/email`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.10 Update email

`PUT /api/admin/v1/applications/{applicationId}/settings/email`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/email" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.1.11 Remove email

`DELETE /api/admin/v1/applications/{applicationId}/settings/email`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.12 List email templates

`GET /api/admin/v1/applications/{applicationId}/settings/email-templates`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.13 Update email templates

`PUT /api/admin/v1/applications/{applicationId}/settings/email-templates`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/email-templates" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.1.14 List default email templates

`GET /api/admin/v1/applications/{applicationId}/settings/email-templates/defaults`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.15 Remove email template

`DELETE /api/admin/v1/applications/{applicationId}/settings/email-templates/{templateKey}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.16 Send a test email

`POST /api/admin/v1/applications/{applicationId}/settings/email/test`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [TestEmailRequest](13-data-models.md#testemailrequest))

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

### 10.1.17 Get exposure

`GET /api/admin/v1/applications/{applicationId}/settings/exposure`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.18 List feature flags

`GET /api/admin/v1/applications/{applicationId}/settings/feature-flags`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.19 Update feature flags

`PUT /api/admin/v1/applications/{applicationId}/settings/feature-flags`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.20 Remove feature flags

`DELETE /api/admin/v1/applications/{applicationId}/settings/feature-flags`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.21 Get MFA policy

`GET /api/admin/v1/applications/{applicationId}/settings/mfa-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.22 Update MFA policy

`PUT /api/admin/v1/applications/{applicationId}/settings/mfa-policy`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/mfa-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.1.23 Remove MFA policy

`DELETE /api/admin/v1/applications/{applicationId}/settings/mfa-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.24 Update tab enabled

`PUT /api/admin/v1/applications/{applicationId}/settings/mfa-tab/tab-enabled`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/mfa-tab/tab-enabled" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.1.25 Get tab state

`GET /api/admin/v1/applications/{applicationId}/settings/mfa-tab/tab-state`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.26 List notifications

`GET /api/admin/v1/applications/{applicationId}/settings/notifications`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.27 Update notifications

`PUT /api/admin/v1/applications/{applicationId}/settings/notifications`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/notifications" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.1.28 Remove notifications

`DELETE /api/admin/v1/applications/{applicationId}/settings/notifications`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.29 Get password policy

`GET /api/admin/v1/applications/{applicationId}/settings/password-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.30 Update password policy

`PUT /api/admin/v1/applications/{applicationId}/settings/password-policy`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/password-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.1.31 Remove password policy

`DELETE /api/admin/v1/applications/{applicationId}/settings/password-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.32 Get policy source

`GET /api/admin/v1/applications/{applicationId}/settings/policy-source/{exposureKey}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.33 Update policy source

`PUT /api/admin/v1/applications/{applicationId}/settings/policy-source/{exposureKey}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.34 List policy sources

`GET /api/admin/v1/applications/{applicationId}/settings/policy-sources`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.35 Get public manifest

`GET /api/admin/v1/applications/{applicationId}/settings/public-manifest`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.36 Update public manifest

`PUT /api/admin/v1/applications/{applicationId}/settings/public-manifest`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/public-manifest" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.1.37 Remove public manifest

`DELETE /api/admin/v1/applications/{applicationId}/settings/public-manifest`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.38 Get signup policy

`GET /api/admin/v1/applications/{applicationId}/settings/signup-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.39 Update signup policy

`PUT /api/admin/v1/applications/{applicationId}/settings/signup-policy`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/signup-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.1.40 Get SMS

`GET /api/admin/v1/applications/{applicationId}/settings/sms`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.41 Update SMS

`PUT /api/admin/v1/applications/{applicationId}/settings/sms`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/sms" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.1.42 Remove SMS

`DELETE /api/admin/v1/applications/{applicationId}/settings/sms`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.43 Send a test SMS

`POST /api/admin/v1/applications/{applicationId}/settings/sms/test`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [TestSmsRequest](13-data-models.md#testsmsrequest))

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

### 10.1.44 Get SMTP

`GET /api/admin/v1/applications/{applicationId}/settings/smtp`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.45 Update SMTP

`PUT /api/admin/v1/applications/{applicationId}/settings/smtp`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/smtp" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.1.46 List token claims

`GET /api/admin/v1/applications/{applicationId}/settings/token-claims`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.47 Update token claims

`PUT /api/admin/v1/applications/{applicationId}/settings/token-claims`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/token-claims" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.1.48 Get token policy

`GET /api/admin/v1/applications/{applicationId}/settings/token-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.49 Update token policy

`PUT /api/admin/v1/applications/{applicationId}/settings/token-policy`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/token-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.1.50 Remove token policy

`DELETE /api/admin/v1/applications/{applicationId}/settings/token-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.51 Update tab enabled

`PUT /api/admin/v1/applications/{applicationId}/settings/token-policy/tab-enabled`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/token-policy/tab-enabled" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.1.52 Get tab state

`GET /api/admin/v1/applications/{applicationId}/settings/token-policy/tab-state`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.53 Get user directory

`GET /api/admin/v1/applications/{applicationId}/settings/user-directory`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.54 Update user directory

`PUT /api/admin/v1/applications/{applicationId}/settings/user-directory`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/user-directory" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.1.55 Remove user directory

`DELETE /api/admin/v1/applications/{applicationId}/settings/user-directory`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.56 Get verification policy

`GET /api/admin/v1/applications/{applicationId}/settings/verification-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.57 Update verification policy

`PUT /api/admin/v1/applications/{applicationId}/settings/verification-policy`

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/verification-policy" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.1.58 Remove verification policy

`DELETE /api/admin/v1/applications/{applicationId}/settings/verification-policy`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.59 Get workspace

`GET /api/admin/v1/applications/{applicationId}/settings/workspace`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.60 List users

`GET /api/admin/v1/applications/{applicationId}/users`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserResponse](13-data-models.md#userresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.1.61 Export user

`GET /api/admin/v1/applications/{applicationId}/users/export`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.62 Import users from a CSV file

`POST /api/admin/v1/applications/{applicationId}/users/import`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.63 Get user

`GET /api/admin/v1/applications/{applicationId}/users/{userId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserResponse](13-data-models.md#userresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.1.64 Add a user to the application

`POST /api/admin/v1/applications/{applicationId}/users/{userId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.65 Remove a user from the application

`DELETE /api/admin/v1/applications/{applicationId}/users/{userId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.66 List attributes

`GET /api/admin/v1/applications/{applicationId}/users/{userId}/attributes`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.67 Update attributes

`PUT /api/admin/v1/applications/{applicationId}/users/{userId}/attributes`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.68 Update auth methods

`PUT /api/admin/v1/applications/{applicationId}/users/{userId}/auth-methods`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Request body** (`application/json`, [UserAuthMethodsUpdateRequest](13-data-models.md#userauthmethodsupdaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `methods` | object | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserResponse](13-data-models.md#userresponse) |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}/auth-methods" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "methods": {}
}'
```

### 10.1.69 Update email verification

`PATCH /api/admin/v1/applications/{applicationId}/users/{userId}/email-verification`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserResponse](13-data-models.md#userresponse) |

```bash
curl -s -X PATCH "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}/email-verification" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.1.70 Resend email verification

`POST /api/admin/v1/applications/{applicationId}/users/{userId}/email/resend-verification`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.71 Verify email

`POST /api/admin/v1/applications/{applicationId}/users/{userId}/email/verify`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserResponse](13-data-models.md#userresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}/email/verify" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.1.72 List identities

`GET /api/admin/v1/applications/{applicationId}/users/{userId}/identities`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.73 Remove identity

`DELETE /api/admin/v1/applications/{applicationId}/users/{userId}/identities/{identityId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.74 Remove factor

`DELETE /api/admin/v1/applications/{applicationId}/users/{userId}/mfa/factors/{factorId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.75 Reset one MFA method of a user

`POST /api/admin/v1/applications/{applicationId}/users/{userId}/mfa/methods/{methodId}/reset`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |
| `methodId` | path | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [UserResponse](13-data-models.md#userresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/users/{userId}/mfa/methods/{methodId}/reset" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.1.76 Remove password

`POST /api/admin/v1/applications/{applicationId}/users/{userId}/password/remove`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.77 Reset password email

`POST /api/admin/v1/applications/{applicationId}/users/{userId}/password/reset-email`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.78 Set password

`POST /api/admin/v1/applications/{applicationId}/users/{userId}/password/set`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `userId` | path | string (uuid) | yes | User ID (UUID) |

**Request body** (`application/json`, [AdminSetPasswordRequest](13-data-models.md#adminsetpasswordrequest))

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

### 10.1.79 Set password email

`POST /api/admin/v1/applications/{applicationId}/users/{userId}/password/set-password-email`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.80 List role assignments

`GET /api/admin/v1/applications/{applicationId}/users/{userId}/role-assignments`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.81 Update role assignment

`PUT /api/admin/v1/applications/{applicationId}/users/{userId}/role-assignments/{roleId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.82 Remove role assignment

`DELETE /api/admin/v1/applications/{applicationId}/users/{userId}/role-assignments/{roleId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.83 List sessions

`GET /api/admin/v1/applications/{applicationId}/users/{userId}/sessions`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.1.84 Sign out

`POST /api/admin/v1/applications/{applicationId}/users/{userId}/sign-out`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

## 10.2 Application RBAC

Roles and permissions for one application

### 10.2.1 List groups

`GET /api/admin/v1/applications/{applicationId}/groups`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [GroupSummaryResponse](13-data-models.md#groupsummaryresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/groups" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.2.2 Create group

`POST /api/admin/v1/applications/{applicationId}/groups`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [GroupCreateRequest](13-data-models.md#groupcreaterequest))

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
| `201` | Created | [GroupDetailResponse](13-data-models.md#groupdetailresponse) |

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
  "name": "Acme Shop",
  "roleIds": [
    "{roleId}"
  ],
  "tenantId": "{tenantId}"
}'
```

### 10.2.3 Get group

`GET /api/admin/v1/applications/{applicationId}/groups/{groupId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `groupId` | path | string (uuid) | yes | Group ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [GroupDetailResponse](13-data-models.md#groupdetailresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/groups/{groupId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.2.4 Update group

`PUT /api/admin/v1/applications/{applicationId}/groups/{groupId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `groupId` | path | string (uuid) | yes | Group ID (UUID) |

**Request body** (`application/json`, [GroupUpdateRequest](13-data-models.md#groupupdaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | no |  |
| `memberUserIds` | string (uuid)[] | no |  |
| `name` | string | yes |  |
| `roleIds` | string (uuid)[] | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [GroupDetailResponse](13-data-models.md#groupdetailresponse) |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/groups/{groupId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "description": "Short explanation",
  "memberUserIds": [
    "{memberUserId}"
  ],
  "name": "Acme Shop",
  "roleIds": [
    "{roleId}"
  ]
}'
```

### 10.2.5 Remove group

`DELETE /api/admin/v1/applications/{applicationId}/groups/{groupId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.2.6 List permissions

`GET /api/admin/v1/applications/{applicationId}/permissions`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [PermissionResponse](13-data-models.md#permissionresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/permissions" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.2.7 Create permission

`POST /api/admin/v1/applications/{applicationId}/permissions`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [PermissionCreateRequest](13-data-models.md#permissioncreaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | no |  |
| `key` | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `201` | Created | [PermissionDetailResponse](13-data-models.md#permissiondetailresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/permissions" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "description": "Short explanation",
  "key": "orders:read"
}'
```

### 10.2.8 Add the default permissions

`POST /api/admin/v1/applications/{applicationId}/permissions/seed-defaults`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [SeedPermissionsResponse](13-data-models.md#seedpermissionsresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/permissions/seed-defaults" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.2.9 Get permission

`GET /api/admin/v1/applications/{applicationId}/permissions/{permissionId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `permissionId` | path | string (uuid) | yes | Permission ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [PermissionDetailResponse](13-data-models.md#permissiondetailresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/permissions/{permissionId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.2.10 Update permission

`PUT /api/admin/v1/applications/{applicationId}/permissions/{permissionId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `permissionId` | path | string (uuid) | yes | Permission ID (UUID) |

**Request body** (`application/json`, [PermissionUpdateRequest](13-data-models.md#permissionupdaterequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | no |  |
| `key` | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [PermissionDetailResponse](13-data-models.md#permissiondetailresponse) |

```bash
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/permissions/{permissionId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "description": "Short explanation",
  "key": "orders:read"
}'
```

### 10.2.11 Remove permission

`DELETE /api/admin/v1/applications/{applicationId}/permissions/{permissionId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.2.12 List roles

`GET /api/admin/v1/applications/{applicationId}/roles`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [RoleSummaryResponse](13-data-models.md#rolesummaryresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/roles" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.2.13 Create role

`POST /api/admin/v1/applications/{applicationId}/roles`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Request body** (`application/json`, [RoleCreateRequest](13-data-models.md#rolecreaterequest))

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
| `201` | Created | [RoleDetailResponse](13-data-models.md#roledetailresponse) |

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
  "name": "Acme Shop",
  "permissionIds": [
    "{permissionId}"
  ],
  "tenantId": "{tenantId}"
}'
```

### 10.2.14 Get role

`GET /api/admin/v1/applications/{applicationId}/roles/{roleId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `roleId` | path | string (uuid) | yes | Role ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [RoleDetailResponse](13-data-models.md#roledetailresponse) |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/roles/{roleId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.2.15 Update role

`PUT /api/admin/v1/applications/{applicationId}/roles/{roleId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `roleId` | path | string (uuid) | yes | Role ID (UUID) |

**Request body** (`application/json`, [RoleUpdateRequest](13-data-models.md#roleupdaterequest))

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
| `200` | OK | [RoleDetailResponse](13-data-models.md#roledetailresponse) |

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
  "name": "Acme Shop",
  "permissionIds": [
    "{permissionId}"
  ]
}'
```

### 10.2.16 Remove role

`DELETE /api/admin/v1/applications/{applicationId}/roles/{roleId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.2.17 Add a permission to a role

`POST /api/admin/v1/applications/{applicationId}/roles/{roleId}/permissions`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `roleId` | path | string (uuid) | yes | Role ID (UUID) |

**Request body** (`application/json`, [RolePermissionPatchRequest](13-data-models.md#rolepermissionpatchrequest))

| Field | Type | Required | Description |
|---|---|---|---|
| `permissionId` | string (uuid) | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [RoleDetailResponse](13-data-models.md#roledetailresponse) |

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/roles/{roleId}/permissions" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
  "permissionId": "{permissionId}"
}'
```

### 10.2.18 Remove a permission from a role

`DELETE /api/admin/v1/applications/{applicationId}/roles/{roleId}/permissions/{permissionId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `roleId` | path | string (uuid) | yes | Role ID (UUID) |
| `permissionId` | path | string (uuid) | yes | Permission ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [RoleDetailResponse](13-data-models.md#roledetailresponse) |

```bash
curl -s -X DELETE "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/roles/{roleId}/permissions/{permissionId}" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.2.19 List users

`GET /api/admin/v1/applications/{applicationId}/roles/{roleId}/users`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |
| `roleId` | path | string (uuid) | yes | Role ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [RoleAssignedUserResponse](13-data-models.md#roleassigneduserresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/roles/{roleId}/users" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## 10.3 Application logs

CloudWatch-style searchable application logs

### 10.3.1 Search application logs

`GET /api/admin/v1/logs`

Filter by session ID, request ID, level, time range, or message text. Use sessionId from console output or sign-in history.

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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
| `200` | OK | [ApplicationLogResponse](13-data-models.md#applicationlogresponse)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/logs" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.3.2 Delete application logs

`DELETE /api/admin/v1/logs`

Delete logs matching the same filters as search. Requires a date boundary: use period (today|yesterday), date (single day), or since/until (range). Optional session, level, and message filters narrow the delete.

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

## 10.4 Application webhooks

Webhook endpoints that receive one application's events

### 10.4.1 List webhooks

`GET /api/admin/v1/applications/{applicationId}/settings/webhooks`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.4.2 Create webhook

`POST /api/admin/v1/applications/{applicationId}/settings/webhooks`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.4.3 List events

`GET /api/admin/v1/applications/{applicationId}/settings/webhooks/events`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.4.4 Update webhook

`PUT /api/admin/v1/applications/{applicationId}/settings/webhooks/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.4.5 Remove webhook

`DELETE /api/admin/v1/applications/{applicationId}/settings/webhooks/{id}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.4.6 List deliveries

`GET /api/admin/v1/applications/{applicationId}/settings/webhooks/{id}/deliveries`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.4.7 Redeliver

`POST /api/admin/v1/applications/{applicationId}/settings/webhooks/{id}/deliveries/{deliveryId}/redeliver`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.4.8 Rotate webhook secret

`POST /api/admin/v1/applications/{applicationId}/settings/webhooks/{id}/rotate-secret`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.4.9 Test webhook

`POST /api/admin/v1/applications/{applicationId}/settings/webhooks/{id}/test`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

## 10.5 Application APIs

APIs (resource servers) an application's access tokens are for

### 10.5.1 The application's APIs

`GET /api/admin/v1/applications/{applicationId}/settings/apis`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.5.2 Add an API (name, identifier, scopes)

`POST /api/admin/v1/applications/{applicationId}/settings/apis`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.5.3 Change an API

`PUT /api/admin/v1/applications/{applicationId}/settings/apis/{apiId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.5.4 Remove an API (new tokens stop naming it; tokens already issued keep it until they expire)

`DELETE /api/admin/v1/applications/{applicationId}/settings/apis/{apiId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

## 10.6 Application SAML apps

SAML service providers signing in through SecureOne

### 10.6.1 The SAML apps and the identity provider they use (entity ID, SSO URL, metadata, certificate)

`GET /api/admin/v1/applications/{applicationId}/settings/saml-apps`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.6.2 Add a SAML app (name, entityId, acsUrl; optional sloUrl, nameIdFormat, attributes, spCertificate)

`POST /api/admin/v1/applications/{applicationId}/settings/saml-apps`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.6.3 Change a SAML app (fields left out keep their values)

`PUT /api/admin/v1/applications/{applicationId}/settings/saml-apps/{spId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.6.4 Remove a SAML app (it can no longer sign users in)

`DELETE /api/admin/v1/applications/{applicationId}/settings/saml-apps/{spId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

## 10.7 Application client registration

Dynamic client registration settings and initial access tokens

### 10.7.1 Mode (off, token, open), policies, endpoints and the number of registered clients

`GET /api/admin/v1/applications/{applicationId}/settings/client-registration`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.7.2 Save the mode and policies

`PUT /api/admin/v1/applications/{applicationId}/settings/client-registration`

mode: off | token (an initial access token is needed) | open (no token; the tenant issuer's endpoint, one application per tenant). allowedScopes, allowedGrantTypes (authorization_code, refresh_token, client_credentials; not with open), trustedHosts (redirect URI hosts, *.example.com allowed; empty: any), maxClients, unusedDays (0: keep unused clients).

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
curl -s -X PUT "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/client-registration" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.7.3 Initial access tokens (values are shown only when created)

`GET /api/admin/v1/applications/{applicationId}/settings/client-registration/tokens`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `applicationId` | path | string (uuid) | yes | Application ID (UUID) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | [InitialToken](13-data-models.md#initialtoken)[] |

```bash
curl -s "$SECUREONE_URL/api/admin/v1/applications/{applicationId}/settings/client-registration/tokens" \
  -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

### 10.7.4 Create an initial access token: label, maxUses (1-1000), expiresInHours (1-8760); the value is shown once

`POST /api/admin/v1/applications/{applicationId}/settings/client-registration/tokens`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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

### 10.7.5 Revoke an initial access token (clients it registered stay)

`DELETE /api/admin/v1/applications/{applicationId}/settings/client-registration/tokens/{tokenId}`

**Authentication:** Console session token (`Authorization: SecureOne-Admin-Session …`), see [Getting started](00-getting-started.md#admin-api-get-a-console-session-token)

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
