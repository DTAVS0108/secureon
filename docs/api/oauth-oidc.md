---
title: OAuth 2.1, OpenID Connect and SAML
summary: The authorization server protocol: discovery, authorize, token, revoke, introspect, JWKS, UserInfo, device flow and OAuth extensions.
order: 5
audience: public
---

# OAuth 2.1, OpenID Connect and SAML

[API reference](index.md) › OAuth 2.1, OpenID Connect and SAML

<!-- developer -->
Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.
<!-- /developer -->

The authorization server protocol: discovery, authorize, token, revoke, introspect, JWKS, UserInfo, device flow and OAuth extensions. Most apps only need discovery, authorize, token and UserInfo; an OIDC library calls them for you.

In Swagger UI (`/docs`), choose the definition **OAuth 2.1 / OIDC and SAML** (`/v3/api-docs/oauth-oidc`).

Set the variables from [Getting started](getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [OAuth 2.1 / OIDC](#oauth-21--oidc)
  - [GET `/.well-known/oauth-authorization-server`](#authorization-server-metadata-rfc-8414) — Authorization server metadata (RFC 8414)
  - [GET `/.well-known/oauth-protected-resource`](#protected-resource-metadata-rfc-9728) — Protected resource metadata (RFC 9728)
  - [GET `/.well-known/openid-configuration`](#openid-provider-metadata) — OpenID Provider metadata
  - [GET `/oauth2/authorize`](#authorization-endpoint-authorization-code--pkce) — Authorization endpoint (authorization code + PKCE)
  - [POST `/oauth2/device_authorization`](#device-authorization-rfc-8628) — Device authorization (RFC 8628)
  - [POST `/oauth2/introspect`](#token-introspection-rfc-7662) — Token introspection (RFC 7662)
  - [GET `/oauth2/jwks`](#json-web-key-set) — JSON Web Key Set
  - [POST `/oauth2/revoke`](#token-revocation-rfc-7009) — Token revocation (RFC 7009)
  - [POST `/oauth2/token`](#token-endpoint) — Token endpoint
  - [GET `/userinfo`](#oidc-userinfo) — OIDC UserInfo
- [OAuth 2.1 / OIDC — consent](#oauth-21--oidc--consent)
  - [GET `/oauth2/consent`](#consent-screen-hosted-page-third-party-clients) — Consent screen (hosted page, third-party clients)
- [Client registration](#client-registration)
  - [POST `/connect/register`](#register-a-client-rfc-7591) — Register a client (RFC 7591)
  - [POST `/oauth2/register`](#register-a-client-rfc-7591-1) — Register a client (RFC 7591)
  - [GET `/oauth2/register/{clientId}`](#read-a-registration-rfc-7592-the-registration-access-token-is-replaced) — Read a registration (RFC 7592; the registration access token is replaced)
  - [PUT `/oauth2/register/{clientId}`](#replace-a-registrations-metadata-rfc-7592-the-registration-access-token-is-replaced) — Replace a registration's metadata (RFC 7592; the registration access token is replaced)
  - [DELETE `/oauth2/register/{clientId}`](#delete-a-registration-rfc-7592-the-client-stops-working) — Delete a registration (RFC 7592): the client stops working
- [SAML identity provider](#saml-identity-provider)
  - [GET `/saml2/idp/resume`](#after-sign-in-the-response-for-the-waiting-sso-posted-to-the-sp) — After sign-in: the Response for the waiting SSO (POSTed to the SP)
  - [GET `/saml2/idp/{slug}/init`](#idp-initiated-sign-in-to-a-service-provider) — IdP-initiated sign-in to a service provider
  - [GET `/saml2/idp/{slug}/metadata`](#the-tenant-idps-saml-metadata-entity-id-sso-and-slo-endpoints-signing-certificate) — The tenant IdP's SAML metadata (entity ID, SSO and SLO endpoints, signing certificate)
  - [GET `/saml2/idp/{slug}/slo`](#single-logout-an-sps-logoutrequest-http-redirect-or-http-post) — Single logout: an SP's LogoutRequest (HTTP-Redirect or HTTP-POST)
  - [POST `/saml2/idp/{slug}/slo`](#single-logout-an-sps-logoutrequest-http-redirect-or-http-post-1) — Single logout: an SP's LogoutRequest (HTTP-Redirect or HTTP-POST)
  - [GET `/saml2/idp/{slug}/sso`](#single-sign-on-an-sps-authnrequest-http-redirect-get-http-post-form-post) — Single sign-on: an SP's AuthnRequest (HTTP-Redirect: GET, HTTP-POST: form POST)
  - [POST `/saml2/idp/{slug}/sso`](#single-sign-on-an-sps-authnrequest-http-redirect-get-http-post-form-post-1) — Single sign-on: an SP's AuthnRequest (HTTP-Redirect: GET, HTTP-POST: form POST)

## OAuth 2.1 / OIDC

### Authorization server metadata (RFC 8414)

`GET /.well-known/oauth-authorization-server`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | Authorization server metadata JSON | object |

```bash
curl -s "$SECUREONE_URL/.well-known/oauth-authorization-server"
```

### Protected resource metadata (RFC 9728)

`GET /.well-known/oauth-protected-resource`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | Authorization servers, JWKS URI, bearer methods | object |

```bash
curl -s "$SECUREONE_URL/.well-known/oauth-protected-resource"
```

### OpenID Provider metadata

`GET /.well-known/openid-configuration`

OIDC discovery document (issuer, endpoints, supported scopes and grant types).

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OpenID configuration JSON | object |

```bash
curl -s "$SECUREONE_URL/.well-known/openid-configuration"
```

### Authorization endpoint (authorization code + PKCE)

`GET /oauth2/authorize`

Browser redirect flow. Requires an authenticated end-user session. Use `response_type=code`, `client_id`, `redirect_uri`, `scope`, and PKCE `code_challenge` / `code_challenge_method=S256`.

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `response_type` | query | string | yes |  |
| `client_id` | query | string | yes |  |
| `redirect_uri` | query | string | yes |  |
| `scope` | query | string | no |  |
| `state` | query | string | no |  |
| `code_challenge` | query | string | no |  |
| `code_challenge_method` | query | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `302` | Redirect to client with `code` or error | — |

```bash
# Opened in the browser by your app (an OIDC library builds this URL). curl only shows the redirect:
curl -s -o /dev/null -w "%{http_code} %{redirect_url}\n" \
  "$SECUREONE_URL/oauth2/authorize?response_type=code&client_id=$CLIENT_ID&redirect_uri=http%3A%2F%2Flocalhost%3A5173%2Fcallback&scope=openid%20profile%20email&state={state}&code_challenge={pkce_challenge}&code_challenge_method=S256"
```

### Device authorization (RFC 8628)

`POST /oauth2/device_authorization`

For TVs / CLIs: returns device_code, user_code, verification_uri (/account/device.html), verification_uri_complete, expires_in and interval. Public device clients send client_id only. The openid scope is not allowed.

**Authentication:** Client authentication: `-u client_id:client_secret` for confidential clients, `client_id` in the body for public clients

**Request body** (`application/x-www-form-urlencoded`)

| Field | Type | Required | Description |
|---|---|---|---|
| `client_id` | string | no | Client with the device_code grant |
| `scope` | string | no | e.g. profile email |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | Device and user codes | object |

```bash
curl -s -X POST "$SECUREONE_URL/oauth2/device_authorization" \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "scope=openid profile"
```

### Token introspection (RFC 7662)

`POST /oauth2/introspect`

**Authentication:** Client authentication: `-u client_id:client_secret` for confidential clients, `client_id` in the body for public clients

**Request body** (`application/x-www-form-urlencoded`)

| Field | Type | Required | Description |
|---|---|---|---|
| `token` | string | no | Token to introspect |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | Active flag and token metadata | object |

```bash
curl -s -X POST "$SECUREONE_URL/oauth2/introspect" \
  -u "$CLIENT_ID:$CLIENT_SECRET" \
  --data-urlencode "token=$ACCESS_TOKEN"
```

### JSON Web Key Set

`GET /oauth2/jwks`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | JWKS document | object |

```bash
curl -s "$SECUREONE_URL/oauth2/jwks"
```

### Token revocation (RFC 7009)

`POST /oauth2/revoke`

**Authentication:** Client authentication: `-u client_id:client_secret` for confidential clients, `client_id` in the body for public clients

**Request body** (`application/x-www-form-urlencoded`)

| Field | Type | Required | Description |
|---|---|---|---|
| `token` | string | no | Access or refresh token |
| `token_type_hint` | string | no | access_token \| refresh_token |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | Revoked (or unknown token) | — |

```bash
curl -s -X POST "$SECUREONE_URL/oauth2/revoke" \
  --data-urlencode "token={refresh_token}" \
  --data-urlencode "token_type_hint=refresh_token" \
  --data-urlencode "client_id=$CLIENT_ID"
# Confidential clients authenticate instead: -u "$CLIENT_ID:$CLIENT_SECRET"
```

### Token endpoint

`POST /oauth2/token`

Exchange credentials for tokens. Supports `authorization_code`, `refresh_token`, `client_credentials`, `urn:ietf:params:oauth:grant-type:device_code` (RFC 8628 polling: `authorization_pending`, `slow_down`, `access_denied`, `expired_token`) and token exchange. Client auth: HTTP Basic (`client_id` / `client_secret`) or `client_secret_post`.

**Authentication:** Client authentication: `-u client_id:client_secret` for confidential clients, `client_id` in the body for public clients

**Request body** (`application/x-www-form-urlencoded`)

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | no | Authorization code (authorization_code grant) |
| `code_verifier` | string | no | PKCE verifier |
| `grant_type` | string | no | authorization_code \| refresh_token \| client_credentials \| urn:ietf:params:oauth:grant-type:device_code |
| `redirect_uri` | string | no | Must match authorize request |
| `refresh_token` | string | no | Refresh token (refresh_token grant) |
| `scope` | string | no | Requested scopes |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | Token response | object |

```bash
# 1. Authorization code + PKCE (public client: SPA, mobile, desktop)
curl -s -X POST "$SECUREONE_URL/oauth2/token" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code={authorization_code}" \
  --data-urlencode "redirect_uri=http://localhost:5173/callback" \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "code_verifier={pkce_verifier}"

# 2. Authorization code (confidential client: server-side web app)
curl -s -X POST "$SECUREONE_URL/oauth2/token" \
  -u "$CLIENT_ID:$CLIENT_SECRET" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code={authorization_code}" \
  --data-urlencode "redirect_uri=http://localhost:3000/auth/callback"

# 3. Refresh tokens
curl -s -X POST "$SECUREONE_URL/oauth2/token" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token={refresh_token}" \
  --data-urlencode "client_id=$CLIENT_ID"

# 4. Machine-to-machine (client credentials)
curl -s -X POST "$SECUREONE_URL/oauth2/token" \
  -u "$CLIENT_ID:$CLIENT_SECRET" \
  --data-urlencode "grant_type=client_credentials" \
  --data-urlencode "scope={scope}"

# 5. Device flow: poll with the device_code from /oauth2/device_authorization
curl -s -X POST "$SECUREONE_URL/oauth2/token" \
  --data-urlencode "grant_type=urn:ietf:params:oauth:grant-type:device_code" \
  --data-urlencode "device_code={device_code}" \
  --data-urlencode "client_id=$CLIENT_ID"
```

### OIDC UserInfo

`GET /userinfo`

**Authentication:** Access token (`Authorization: Bearer …`) from the token endpoint

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | Claims about the authenticated subject | — |

```bash
curl -s "$SECUREONE_URL/userinfo" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

## OAuth 2.1 / OIDC — consent

The consent screen shown to users for third-party clients

### Consent screen (hosted page, third-party clients)

`GET /oauth2/consent`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `client_id` | query | string | yes |  |
| `scope` | query | string | yes |  |
| `state` | query | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | string |

```bash
curl -s "$SECUREONE_URL/oauth2/consent?client_id={client_id}&scope={scope}&state={state}"
```

## Client registration

Apps register themselves as OAuth clients (RFC 7591, RFC 7592)

### Register a client (RFC 7591)

`POST /connect/register`

Authorization: Bearer <initial access token>, unless the organization takes open registrations (then on its own issuer only). The application's policies decide what may be asked for; the response has client_id, client_secret (confidential clients), registration_access_token and registration_client_uri.

**Authentication:** None (public)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/connect/register"
```

### Register a client (RFC 7591)

`POST /oauth2/register`

Authorization: Bearer <initial access token>, unless the organization takes open registrations (then on its own issuer only). The application's policies decide what may be asked for; the response has client_id, client_secret (confidential clients), registration_access_token and registration_client_uri.

**Authentication:** None (public)

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X POST "$SECUREONE_URL/oauth2/register"
```

### Read a registration (RFC 7592; the registration access token is replaced)

`GET /oauth2/register/{clientId}`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `clientId` | path | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s "$SECUREONE_URL/oauth2/register/{clientId}"
```

### Replace a registration's metadata (RFC 7592; the registration access token is replaced)

`PUT /oauth2/register/{clientId}`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `clientId` | path | string | yes |  |

**Request body** (`application/json`): object

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | object |

```bash
curl -s -X PUT "$SECUREONE_URL/oauth2/register/{clientId}"
```

### Delete a registration (RFC 7592): the client stops working

`DELETE /oauth2/register/{clientId}`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `clientId` | path | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | — |

```bash
curl -s -X DELETE "$SECUREONE_URL/oauth2/register/{clientId}"
```

## SAML identity provider

SecureOne as a SAML 2.0 IdP for service providers

### After sign-in: the Response for the waiting SSO (POSTed to the SP)

`GET /saml2/idp/resume`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `ref` | query | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | — |

```bash
curl -s "$SECUREONE_URL/saml2/idp/resume?ref={ref}"
```

### IdP-initiated sign-in to a service provider

`GET /saml2/idp/{slug}/init`

sp: the SP's entity ID (or its id). The user signs in if needed; the Response has no InResponseTo.

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `slug` | path | string | yes |  |
| `sp` | query | string | yes |  |
| `RelayState` | query | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | — |

```bash
curl -s "$SECUREONE_URL/saml2/idp/{slug}/init?sp={sp}"
```

### The tenant IdP's SAML metadata (entity ID, SSO and SLO endpoints, signing certificate)

`GET /saml2/idp/{slug}/metadata`

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `slug` | path | string | yes |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | string |

```bash
curl -s "$SECUREONE_URL/saml2/idp/{slug}/metadata"
```

### Single logout: an SP's LogoutRequest (HTTP-Redirect or HTTP-POST)

`GET /saml2/idp/{slug}/slo`

Signs this browser out of SecureOne (apps with back-channel logout are told) and POSTs a signed LogoutResponse to the SP's SLO URL.

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `slug` | path | string | yes |  |
| `SAMLRequest` | query | string | no |  |
| `SAMLResponse` | query | string | no |  |
| `RelayState` | query | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | — |

```bash
curl -s "$SECUREONE_URL/saml2/idp/{slug}/slo"
```

### Single logout: an SP's LogoutRequest (HTTP-Redirect or HTTP-POST)

`POST /saml2/idp/{slug}/slo`

Signs this browser out of SecureOne (apps with back-channel logout are told) and POSTs a signed LogoutResponse to the SP's SLO URL.

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `slug` | path | string | yes |  |
| `SAMLRequest` | query | string | no |  |
| `SAMLResponse` | query | string | no |  |
| `RelayState` | query | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | — |

```bash
curl -s -X POST "$SECUREONE_URL/saml2/idp/{slug}/slo"
```

### Single sign-on: an SP's AuthnRequest (HTTP-Redirect: GET, HTTP-POST: form POST)

`GET /saml2/idp/{slug}/sso`

The user signs in if needed; the signed Response is POSTed to the SP's registered ACS URL.

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `slug` | path | string | yes |  |
| `SAMLRequest` | query | string | no |  |
| `RelayState` | query | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | — |

```bash
curl -s "$SECUREONE_URL/saml2/idp/{slug}/sso"
```

### Single sign-on: an SP's AuthnRequest (HTTP-Redirect: GET, HTTP-POST: form POST)

`POST /saml2/idp/{slug}/sso`

The user signs in if needed; the signed Response is POSTed to the SP's registered ACS URL.

**Authentication:** None (public)

**Parameters**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| `slug` | path | string | yes |  |
| `SAMLRequest` | query | string | no |  |
| `RelayState` | query | string | no |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | OK | — |

```bash
curl -s -X POST "$SECUREONE_URL/saml2/idp/{slug}/sso"
```
