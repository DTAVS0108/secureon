# 1. OAuth 2.1 and OpenID Connect

[API reference](README.md) › OAuth 2.1 and OpenID Connect

> Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by hand: change the controllers and regenerate.

The authorization server protocol: discovery, authorize, token, revoke, introspect, JWKS, UserInfo, device flow and OAuth extensions. Most apps only need discovery, authorize, token and UserInfo; an OIDC library calls them for you.

Set the variables from [Getting started](00-getting-started.md) first (`SECUREONE_URL`, and the token the endpoint needs). Replace `{placeholders}` with real values.

## Contents

- [1.1 OAuth 2.1 / OIDC](#11-oauth-21--oidc)
  - [GET `/.well-known/oauth-authorization-server`](#111-authorization-server-metadata-rfc-8414) — Authorization server metadata (RFC 8414)
  - [GET `/.well-known/oauth-protected-resource`](#112-protected-resource-metadata-rfc-9728) — Protected resource metadata (RFC 9728)
  - [GET `/.well-known/openid-configuration`](#113-openid-provider-metadata) — OpenID Provider metadata
  - [GET `/oauth2/authorize`](#114-authorization-endpoint-authorization-code--pkce) — Authorization endpoint (authorization code + PKCE)
  - [POST `/oauth2/device_authorization`](#115-device-authorization-rfc-8628) — Device authorization (RFC 8628)
  - [POST `/oauth2/introspect`](#116-token-introspection-rfc-7662) — Token introspection (RFC 7662)
  - [GET `/oauth2/jwks`](#117-json-web-key-set) — JSON Web Key Set
  - [POST `/oauth2/revoke`](#118-token-revocation-rfc-7009) — Token revocation (RFC 7009)
  - [POST `/oauth2/token`](#119-token-endpoint) — Token endpoint
  - [GET `/userinfo`](#1110-oidc-userinfo) — OIDC UserInfo
- [1.2 Consent-page-controller](#12-consent-page-controller)
  - [GET `/oauth2/consent`](#121-consent-screen-hosted-page-third-party-clients) — Consent screen (hosted page, third-party clients)
- [1.3 SAML identity provider](#13-saml-identity-provider)
  - [GET `/saml2/idp/resume`](#131-after-sign-in-the-response-for-the-waiting-sso-posted-to-the-sp) — After sign-in: the Response for the waiting SSO (POSTed to the SP)
  - [GET `/saml2/idp/{slug}/init`](#132-idp-initiated-sign-in-to-a-service-provider) — IdP-initiated sign-in to a service provider
  - [GET `/saml2/idp/{slug}/metadata`](#133-the-tenant-idps-saml-metadata-entity-id-sso-and-slo-endpoints-signing-certificate) — The tenant IdP's SAML metadata (entity ID, SSO and SLO endpoints, signing certificate)
  - [GET `/saml2/idp/{slug}/slo`](#134-single-logout-an-sps-logoutrequest-http-redirect-or-http-post) — Single logout: an SP's LogoutRequest (HTTP-Redirect or HTTP-POST)
  - [POST `/saml2/idp/{slug}/slo`](#135-single-logout-an-sps-logoutrequest-http-redirect-or-http-post) — Single logout: an SP's LogoutRequest (HTTP-Redirect or HTTP-POST)
  - [GET `/saml2/idp/{slug}/sso`](#136-single-sign-on-an-sps-authnrequest-http-redirect-get-http-post-form-post) — Single sign-on: an SP's AuthnRequest (HTTP-Redirect: GET, HTTP-POST: form POST)
  - [POST `/saml2/idp/{slug}/sso`](#137-single-sign-on-an-sps-authnrequest-http-redirect-get-http-post-form-post) — Single sign-on: an SP's AuthnRequest (HTTP-Redirect: GET, HTTP-POST: form POST)
- [1.4 Custom domains](#14-custom-domains)
  - [GET `/.well-known/secureone-domain-verification`](#141-on-a-registered-domain-its-verification-token-shows-the-domain-points-at-secureone) — On a registered domain: its verification token (shows the domain points at SecureOne)
  - [GET `/api/v1/domains/check`](#142-whether-a-domain-is-a-verified-custom-domain-200-or-not-404) — Whether a domain is a verified custom domain (200) or not (404)

## 1.1 OAuth 2.1 / OIDC

### 1.1.1 Authorization server metadata (RFC 8414)

`GET /.well-known/oauth-authorization-server`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | Authorization server metadata JSON | object |

```bash
curl -s "$SECUREONE_URL/.well-known/oauth-authorization-server"
```

### 1.1.2 Protected resource metadata (RFC 9728)

`GET /.well-known/oauth-protected-resource`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | Authorization servers, JWKS URI, bearer methods | object |

```bash
curl -s "$SECUREONE_URL/.well-known/oauth-protected-resource"
```

### 1.1.3 OpenID Provider metadata

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

### 1.1.4 Authorization endpoint (authorization code + PKCE)

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

### 1.1.5 Device authorization (RFC 8628)

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

### 1.1.6 Token introspection (RFC 7662)

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

### 1.1.7 JSON Web Key Set

`GET /oauth2/jwks`

**Authentication:** None (public)

**Responses**

| Status | Meaning | Body |
|---|---|---|
| `200` | JWKS document | object |

```bash
curl -s "$SECUREONE_URL/oauth2/jwks"
```

### 1.1.8 Token revocation (RFC 7009)

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

### 1.1.9 Token endpoint

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

### 1.1.10 OIDC UserInfo

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

## 1.2 Consent-page-controller

### 1.2.1 Consent screen (hosted page, third-party clients)

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

## 1.3 SAML identity provider

SecureOne as a SAML 2.0 IdP for service providers

### 1.3.1 After sign-in: the Response for the waiting SSO (POSTed to the SP)

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

### 1.3.2 IdP-initiated sign-in to a service provider

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

### 1.3.3 The tenant IdP's SAML metadata (entity ID, SSO and SLO endpoints, signing certificate)

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

### 1.3.4 Single logout: an SP's LogoutRequest (HTTP-Redirect or HTTP-POST)

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

### 1.3.5 Single logout: an SP's LogoutRequest (HTTP-Redirect or HTTP-POST)

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

### 1.3.6 Single sign-on: an SP's AuthnRequest (HTTP-Redirect: GET, HTTP-POST: form POST)

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

### 1.3.7 Single sign-on: an SP's AuthnRequest (HTTP-Redirect: GET, HTTP-POST: form POST)

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
