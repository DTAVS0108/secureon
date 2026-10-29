---
title: Getting started with the API
summary: Shell setup, credentials, admin session token, errors
order: 2
audience: public
---

# Getting started with the API

[API reference](index.md) › Getting started

Everything the reference sections assume: where the API is, how to authenticate each kind of call, and what
errors look like. Every command on this page was run against a SecureOne server.

## Set up your shell

The curl commands use shell variables, so you set your values once. They are written for **bash or zsh**
(Linux, macOS, WSL, **Git Bash on Windows**). In PowerShell use `curl.exe`, put the line breaks on one line
(or use `` ` ``) and set variables with `$env:SECUREONE_URL = "…"`.

```bash
export SECUREONE_URL=$SECUREONE_URL   # your server, without a trailing slash

# Only the ones you need:
export CLIENT_ID=my-app                      # OAuth client ID (Platform admin › OAuth clients)
export CLIENT_SECRET='…'                     # confidential clients only; shown once when created or rotated
export ACCESS_TOKEN='…'                      # from the token endpoint (OAuth 2.1, OpenID Connect and SAML)
export ADMIN_TOKEN='…'                       # console session token (below)
export API_KEY='sk_…'                        # service account API key
export SCIM_TOKEN='…'                        # tenant SCIM token
```

`jq` is handy for reading responses (`… | jq .`) but not required.

## Which credential does a call need?

Each endpoint states its **Authentication**. There are six kinds:

| Kind | Header or option | Used by | How to get it |
|------|------------------|---------|---------------|
| None (public) | — | Discovery, sign-in and sign-up, password reset, manifests | — |
| Access token | `-H "Authorization: Bearer $ACCESS_TOKEN"` | UserInfo, account profile and privacy, your own APIs | [Token endpoint](oauth-oidc.md) (authorization code, refresh, client credentials) |
| Client authentication | `-u "$CLIENT_ID:$CLIENT_SECRET"` | Token, revoke, introspect for confidential clients | The client secret from the OAuth client's page |
| Session cookie | `-c cookies.txt` to save, `-b cookies.txt` to send | Native sign-in, then account MFA and sessions | A sign-in call in [Application sign-in and sign-up](application-sign-in.md) |
| API key / SCIM token | `-H "Authorization: Bearer $API_KEY"` / `$SCIM_TOKEN` | [Machine-to-machine and SCIM](machine-and-scim.md) | Tenant › Service accounts / SCIM in the console |
| Console session token | `-H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"` | Every admin API (`/api/admin/v1/…`, the *Admin API* sections) | Sign in with password (and authenticator code when required) (below) |

## Admin API: get a console session token

Admin APIs accept only a console session token, never a password alone, so automation signs in the same way a
person does: password, then (when console two-step sign-in is on) an authenticator code. Use a dedicated operator
account for scripts, and keep its password and authenticator secret in your secret store.

**Step 1: password.** Platform operators use the tenant slug `platform`; tenant operators use their tenant's slug.

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/auth/login" \
  -H "Content-Type: application/json" \
  --data '{"tenantSlug": "platform", "emailOrUsername": "admin@example.com", "password": "…"}'
```

The answer is one of:

- the **session** at once (same shape as at the end of step 2) when console two-step sign-in is off, the default
  outside production (`SECUREONE_ADMIN_MFA_REQUIRED` overrides): skip to step 3;
- **403** with `"passwordChangeRequired": true` and a `changeToken` when the password is temporary (the bootstrap
  admin's first sign-in). Set a new one, then sign in again:

  ```bash
  curl -s -X POST "$SECUREONE_URL/api/v1/account/password/reset" \
    -H "Content-Type: application/json" \
    --data '{"token": "{changeToken}", "password": "…"}'
  ```

- a **second step**:

```json
{ "mfaRequired": true, "mfaToken": "nAODpe…", "methods": ["totp", "recovery_code"], "expiresIn": 300 }
```

or, the first time (no authenticator yet), an enrolment: add `secret` (or scan `qrCodeDataUrl`) to an
authenticator app.

```json
{ "mfaEnrollmentRequired": true, "mfaToken": "…", "factorId": "a6c91e02-…", "secret": "4V7CCCL5…", "otpauthUri": "otpauth://…", "expiresIn": 300 }
```

**Step 2: authenticator code** (within 5 minutes). Usually:

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/auth/mfa/verify" \
  -H "Content-Type: application/json" \
  --data '{"mfaToken": "{mfaToken}", "code": "123456"}'
# A recovery code instead: {"mfaToken": "{mfaToken}", "recoveryCode": "735718171"}
```

First time only, confirm the new authenticator instead (the answer also contains your recovery codes, keep them):

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/auth/mfa/enroll/confirm" \
  -H "Content-Type: application/json" \
  --data '{"mfaToken": "{mfaToken}", "factorId": "{factorId}", "code": "123456"}'
```

Both return the session:

```json
{ "ok": true, "principal": "platform:admin@example.com", "platformSuperAdmin": true,
  "sessionToken": "mPBt5w5YkHn_…", "expiresIn": 28800, "idleTimeout": 1800 }
```

**Step 3: use it.**

```bash
export ADMIN_TOKEN='mPBt5w5YkHn_…'
curl -s "$SECUREONE_URL/api/admin/v1/context" -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

The token lasts `expiresIn` seconds (8 hours by default) and ends after `idleTimeout` seconds (30 minutes) without a
call. Sign out when the script is done:

```bash
curl -s -X POST "$SECUREONE_URL/api/admin/v1/auth/logout" -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

What a token may do depends on the account's role, exactly as in the console: platform operators everything,
tenant super admins their tenant, tenant and application admins their applications. A call outside the role
returns **403**.

Platform operators can add `-H "X-Act-As-Email: someone@tenant.com"` to see what another operator sees (support);
it never grants more than that person has.

### The whole flow as one script

```bash
#!/usr/bin/env bash
set -euo pipefail
: "${SECUREONE_URL:?}" "${ADMIN_EMAIL:?}" "${ADMIN_PASSWORD:?}" "${TOTP_CODE:?}"
MFA_TOKEN=$(curl -s -X POST "$SECUREONE_URL/api/admin/v1/auth/login" -H "Content-Type: application/json" \
  --data "{\"tenantSlug\":\"platform\",\"emailOrUsername\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" | jq -r .mfaToken)
ADMIN_TOKEN=$(curl -s -X POST "$SECUREONE_URL/api/admin/v1/auth/mfa/verify" -H "Content-Type: application/json" \
  --data "{\"mfaToken\":\"$MFA_TOKEN\",\"code\":\"$TOTP_CODE\"}" | jq -r .sessionToken)
curl -s "$SECUREONE_URL/api/admin/v1/tenants" -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN" | jq '.[].slug'
curl -s -X POST "$SECUREONE_URL/api/admin/v1/auth/logout" -H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"
```

## App integration: get an access token

Apps normally let an OIDC library do this. To try it by hand, with a confidential client and the client
credentials grant (machine-to-machine):

```bash
curl -s -X POST "$SECUREONE_URL/oauth2/token" \
  -u "$CLIENT_ID:$CLIENT_SECRET" \
  --data-urlencode "grant_type=client_credentials" \
  --data-urlencode "scope=api.read"
# {"access_token":"eyJraWQiOi…","scope":"api.read","token_type":"Bearer","expires_in":3599}
```

Check a token (introspection):

```bash
curl -s -X POST "$SECUREONE_URL/oauth2/introspect" -u "$CLIENT_ID:$CLIENT_SECRET" --data-urlencode "token=$ACCESS_TOKEN"
# {"active":true,"sub":"billing-job","scope":"api.read","iss":"$SECUREONE_URL",…}
```

User sign-in (authorization code + PKCE, refresh) is in [OAuth 2.1, OpenID Connect and SAML › Token endpoint](oauth-oidc.md).

## Changes that need approval

Some admin calls do not change anything at once. They return **202 Accepted** with a *change request*, and
another admin approves it ([Admin API: applications and OAuth clients](admin-applications-and-clients.md) › Change approvals). Today: deleting an OAuth client. The body tells you what
happens next:

```json
{ "id": "80ba0e88-…", "action": "oauth_client.delete", "status": "PENDING", "requestedBy": "admin@example.com", "mine": true, "canDecide": false }
```

## Errors

Errors from the API use RFC 9457 *problem details*; `detail` says what went wrong:

```json
{ "type": "about:blank", "title": "Forbidden", "status": 403, "detail": "Platform settings require the platform operator account" }
```

A missing or expired credential is refused before the API runs, with a shorter body:

```json
{ "timestamp": "2026-10-20T00:41:08.291Z", "status": 401, "error": "Unauthorized", "path": "/api/admin/v1/settings/smtp" }
```

| Status | Meaning | What to do |
|--------|---------|-----------|
| 400 | The request is invalid (missing field, wrong format, rule not met). | Read `detail`; fix the request. |
| 401 | No credential, or it expired (console token idle or signed out). | Sign in again. |
| 403 | Signed in, but this account may not do this. | Use an account with the role, or ask an admin. |
| 404 | Nothing with that ID, or a feature that is switched off (for example Configuration as code). | Check the ID; check Platform settings. |
| 409 | Conflicts with the current state (duplicate client ID, request already decided). | Reload and try again. |
| 429 | Too many attempts (sign-in, OTP, token). | Wait a minute. |
| 503 | A required service is not set up (for example SMTP for an email). | Configure it, or ask the platform operator. |

## Swagger UI and the OpenAPI document

The same API is described as OpenAPI 3 and can be tried in Swagger UI:

| What | Where |
|------|-------|
| Swagger UI | `$SECUREONE_URL/swagger-ui/index.html` (or `$SECUREONE_URL/docs`) |
| OpenAPI JSON, everything | `$SECUREONE_URL/v3/api-docs/all` |
| OpenAPI JSON by category | `/v3/api-docs/public`, `/auth`, `/oauth-oidc`, `/account`, `/machine`, `/admin-sign-in`, `/admin-tenants`, `/admin-applications`, `/admin-security`, `/admin-settings` ([categories](swagger-and-openapi.md#categories)) |

They are **off in production** (the image's default `SECUREONE_ENV`) until you set `SECUREONE_SWAGGER_ENABLED=true`
on the server, and on in development. They only describe the API; every call still needs its normal credential. In Swagger UI,
sign in with the auth calls above, then **Authorize** with `SecureOne-Admin-Session <sessionToken>`.

```bash
curl -s -o /dev/null -w "%{http_code}\n" "$SECUREONE_URL/v3/api-docs/all"   # 200 when on, 404 when off
```
