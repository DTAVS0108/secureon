---
title: Architecture internals
summary: Components, frameworks, request flows, data layers and deployment details
order: 1
audience: internal
---

# Architecture internals

## System components

| Component | Tech | Responsibility |
|---|---|---|
| **auth-server** | Spring Boot + Spring Authorization Server | OIDC/OAuth endpoints, token issuance, JWKS, user/tenant/role APIs, admin APIs |
| **admin-web** | Next.js | Admin console and **SecureOne Documentation** (in-app docs), served by the auth-server at **`/admin`** from the same container (one image, one port); hosted sign-in pages are served by the auth-server itself |
| **docs-site** | Next.js/Nextra + Scalar | Public developer docs + OpenAPI rendering (planned) |
| **sdk-js** | TypeScript | Client SDK for integrating applications |
| **PostgreSQL** | RDBMS | Durable system of record |
| **Redis** | Cache | Sessions, refresh-token store + revocation, rate limiting, MFA challenges |
| **Secrets manager** | KMS / Vault | Signing keys, TOTP seed encryption keys, client secrets |

The `auth-server` is **stateless** (no in-memory session state) so it scales horizontally; all shared state lives in Redis and the database.

**Packaging:** one image (`deploy/Dockerfile`). The auth-server is the only public listener (port 9000); it starts the console's Node.js server on `127.0.0.1` inside the container and forwards `/admin` to it (`ConsoleProxyFilter`, `ConsoleProcess`), passing the client address so console sign-ins are rate-limited and audited per client. Settings are environment variables read at start-up, so one image serves every installation.

## Logical architecture

> Editable source: [`diagrams/logical-architecture.drawio`](../diagrams/logical-architecture.drawio)

```mermaid
flowchart TB
    subgraph clients["Clients"]
        U["End Users / Browser"]
        APP["Integrated Apps<br/>(hosted or native UI)"]
        M["Machines (M2M)"]
        DEV["Devices (TV / CLI)<br/>device_code grant"]
    end

    subgraph ui["admin-web (Next.js)"]
        LOGIN["Hosted pages: login, signup,<br/>device, account, MFA"]
        ADMIN["Admin Dashboard"]
    end

    subgraph auth["auth-server (Spring Boot)"]
        SAS["Spring Authorization Server<br/>OAuth2 / OIDC engine"]
        API["Tenant / User / Role APIs"]
        POL["PolicyEvaluator (RBAC)"]
        REPO["Repository layer (JPA)"]
        FED["Federation: OIDC + SAML 2.0 SP<br/>(SSO, SLO, IdP-initiated)"]
    end

    IDPS[["External IdPs<br/>Google, GitHub, OIDC,<br/>SAML 2.0 company IdPs"]]

    PG[("PostgreSQL")]
    REDIS[("Redis")]
    KMS[["Secrets Manager / KMS<br/>(encryption key, JWT + SAML SP keys)"]]

    U --> LOGIN
    APP -- "OIDC redirects" --> LOGIN
    APP --> SAS
    M --> SAS
    DEV -. "device_code" .-> SAS
    LOGIN --> SAS
    LOGIN --> FED
    FED <-- "SSO / SLO" --> IDPS
    ADMIN --> API
    SAS --> POL
    API --> POL
    POL --> REPO
    REPO --> PG
    SAS --> REDIS
    SAS --> KMS

    classDef client fill:#dae8fc,stroke:#6c8ebf,color:#000;
    classDef uic fill:#d5e8d4,stroke:#82b366,color:#000;
    classDef authc fill:#ffe6cc,stroke:#d79b00,color:#000;
    classDef store fill:#f8cecc,stroke:#b85450,color:#000;
    classDef sec fill:#e1d5e7,stroke:#9673a6,color:#000;
    class U,APP,M,DEV client;
    class LOGIN,ADMIN uic;
    class SAS,API,POL,REPO,FED authc;
    class PG,REDIS store;
    class KMS,IDPS sec;
```

## Core request flows

### Authorization Code + PKCE (web / SPA / mobile login)

> Editable source: [`diagrams/auth-flow-authorization-code-pkce.drawio`](../diagrams/auth-flow-authorization-code-pkce.drawio)

```mermaid
sequenceDiagram
    actor User
    participant App as Integrated App
    participant Auth as auth-server (SAS)
    participant Login as Hosted Login UI

    App->>Auth: 1. /oauth2/authorize (PKCE challenge)
    Auth->>Login: 2. redirect to login
    User->>Login: 3. credentials + MFA
    Login->>Auth: 4. authenticated
    Auth-->>App: 5. authorization code (redirect)
    App->>Auth: 6. /oauth2/token (code + PKCE verifier)
    Auth-->>App: 7. ID + access + refresh tokens
    App->>Auth: 8. GET /oauth2/jwks
    Auth-->>App: 9. JWKS public keys
    Note over App: 10. validate JWT signature
```

### Client Credentials (machine-to-machine)

> Editable source: [`diagrams/auth-flow-client-credentials.drawio`](../diagrams/auth-flow-client-credentials.drawio)

```mermaid
sequenceDiagram
    participant SA as Service / Machine
    participant Auth as auth-server (SAS)

    SA->>Auth: 1. /oauth2/token (client_credentials, client_id+secret / API key)
    Note over Auth: 2. resolve service-account roles / permissions
    Auth-->>SA: 3. access token (JWT)
```

### Refresh with rotation

> Editable source: [`diagrams/auth-flow-refresh-rotation.drawio`](../diagrams/auth-flow-refresh-rotation.drawio)

```mermaid
sequenceDiagram
    participant App
    participant Auth as auth-server (SAS)

    App->>Auth: 1. /oauth2/token (refresh_token)
    Note over Auth: 2. validate + check family for reuse
    alt token reuse detected
        Auth-->>App: 3a. revoke entire family + error (invalid_grant)
    else valid
        Auth-->>App: 4. rotate: new access + refresh token
    end
```

Full standards detail: [Auth Standards](auth-standards.md).

### Hosted vs native sign-in (the "Authentication UI" setting)

> Editable source: [`diagrams/auth-flow-hosted-vs-native.drawio`](../diagrams/auth-flow-hosted-vs-native.drawio) — two pages (hosted, native)

```mermaid
sequenceDiagram
    autonumber
    participant B as Browser
    participant APP as Your app
    participant AS as auth-server
    APP->>AS: GET /api/v1/applications/{id} (manifest: authUiMode, methods)
    alt authUiMode = hosted
        APP->>AS: 302 /oauth2/authorize (PKCE)
        AS-->>B: /login.html (branded for the app)
        B->>AS: credentials / OTP / passkey / SSO (+ MFA)
        AS-->>APP: redirect_uri?code&state
    else authUiMode = native
        B->>APP: credentials in the app's own form
        APP->>AS: POST …/auth/session/login (cookie)
        APP->>AS: /oauth2/authorize (prompt=none, PKCE)
        AS-->>APP: redirect_uri?code
    end
    APP->>AS: POST /oauth2/token (code + verifier) → tokens
```

### Device sign-in (RFC 8628)

> Editable source: [`diagrams/auth-flow-device-code.drawio`](../diagrams/auth-flow-device-code.drawio)

```mermaid
sequenceDiagram
    autonumber
    participant D as Device (TV / CLI)
    participant AS as auth-server
    participant P as Phone (device page)
    D->>AS: POST /oauth2/device_authorization (client_id, scope)
    AS-->>D: device_code, user_code, verification_uri, interval=5
    loop every 5 s
        D->>AS: POST /oauth2/token (device_code)
        AS-->>D: authorization_pending | slow_down
    end
    P->>AS: lookup code → app name
    P->>AS: /oauth2/device_verification (sign in if needed, tenant + app check)
    AS-->>P: Allow / Deny (consent, POST with state)
    P->>AS: Allow
    D->>AS: POST /oauth2/token (device_code)
    AS-->>D: access + refresh token (code single use)
```

### SAML 2.0 SSO and single logout

> Editable source: [`diagrams/auth-flow-saml.drawio`](../diagrams/auth-flow-saml.drawio) — three pages (SP-initiated, IdP-initiated, single logout)

```mermaid
sequenceDiagram
    autonumber
    participant B as Browser
    participant AS as auth-server (SP)
    participant IDP as Company IdP
    B->>AS: Continue with Corp SSO (/federation/{alias}/start)
    AS-->>B: signed AuthnRequest (stored by RelayState)
    B->>IDP: SAMLRequest + RelayState
    IDP-->>B: sign in
    B->>AS: POST /federation/saml/acs/{providerId} (SAMLResponse)
    Note over AS: decrypt · signature · audience · InResponseTo · replay cache
    AS-->>B: session → back to /oauth2/authorize → app gets code
    B->>AS: /connect/logout (app signs out)
    AS-->>B: signed LogoutRequest → IdP
    IDP-->>B: LogoutResponse → /federation/saml/slo/{providerId}
    AS-->>B: post_logout_redirect_uri?state
```

## Application schema isolation

IAM data for each application product can live in a **dedicated PostgreSQL schema** (derived from the application slug). Shared platform data (tenants, application registry, `oauth_client`, user accounts) stays in the **`platform`** schema.

```mermaid
flowchart LR
    subgraph db["PostgreSQL database: secureone"]
        direction LR
        platform["<b>platform</b><br/>tenants, applications, OAuth clients,<br/>users, governance, migration history"]
        app["<b>one schema per application</b><br/>its roles, permissions, settings,<br/>groups and logs"]
    end
    platform -. "application registry" .-> app
```

- **New applications** — schema provisioned automatically on register.
- **Legacy applications** — `schema_name = NULL`; IAM remains in `platform.*` until **isolate** (super-admin).
- **Routing** — `search_path` set at transaction begin for app-scoped API paths and OAuth `client_id` resolution.

See [Applications & OAuth clients](../integrate/applications-and-oauth-clients.md) and [Database Strategy](../developer/database.md).

## Multi-tenancy & isolation

With PostgreSQL committed, isolation is enforced in **layers (defense in depth)**:

1. **Mandatory `tenant_id`** on every tenant-scoped table.
2. **Hibernate `@Filter`** auto-enabled per request from the authenticated principal's tenant, so no query can omit the predicate.
3. **PostgreSQL Row-Level Security (RLS)** policies keyed on a per-connection `app.tenant_id` setting — a database-enforced backstop even if app code has a bug.
4. **Automated cross-tenant isolation tests** in CI (read tenant B as tenant A → must fail).

Tenant resolution strategy is pluggable to allow, later, schema-per-tenant or database-per-tenant for enterprise customers. MVP uses **shared database, shared schema, `tenant_id` discriminator** + RLS. See the layered diagram in [Database Strategy](../developer/database.md#tenant-isolation-defense-in-depth) ([editable source](../diagrams/tenant-isolation.drawio)).

## Trust tiers

| Tier | Stored as | Scope |
|---|---|---|
| Platform admin (super-admin) | separate `platform_admin` table | All tenants; manages the deployment |
| Tenant admin | `user_account.type = ADMIN` | One tenant |
| End user | `user_account.type = USER` | One tenant, per-app roles |
| Service account | `service_account` | M2M within a tenant |

Platform admins are deliberately decoupled from any tenant so a tenant-scoped bug cannot escalate to platform control.

## Deployment topology

### MVP (cheap, fast)
- Docker containers via **Docker Compose** or a PaaS (Render / Fly.io / Railway).
- Managed PostgreSQL + managed Redis.
- **Caddy / Traefik** for TLS termination + reverse proxy.

> Editable source: [`diagrams/deployment-mvp.drawio`](../diagrams/deployment-mvp.drawio)

```mermaid
flowchart LR
    NET(["Internet"]) --> PROXY["Caddy / Traefik<br/>(TLS)"]
    PROXY --> AUTH["SecureOne container<br/>auth-server (Spring Boot)<br/>+ console at /admin (Next.js)"]
    AUTH --> PG[("PostgreSQL<br/>managed")]
    AUTH --> REDIS[("Redis<br/>managed")]

    classDef sec fill:#e1d5e7,stroke:#9673a6,color:#000;
    classDef authc fill:#ffe6cc,stroke:#d79b00,color:#000;
    classDef uic fill:#d5e8d4,stroke:#82b366,color:#000;
    classDef store fill:#f8cecc,stroke:#b85450,color:#000;
    class PROXY sec;
    class AUTH authc;
    class PG,REDIS store;
```

### Production (scale)

> Editable source: [`diagrams/deployment-production.drawio`](../diagrams/deployment-production.drawio)

```mermaid
flowchart TB
    NET(["Internet"]) --> CDN["CDN / WAF<br/>(static UI/docs, DDoS/WAF)"]
    CDN --> LB["Load Balancer<br/>(TLS, HSTS)"]

    subgraph tier["Stateless tier (Kubernetes, horizontally scaled)"]
        A1["SecureOne pod<br/>auth-server + /admin console"]
        A2["SecureOne pod<br/>auth-server + /admin console"]
    end

    LB --> A1
    LB --> A2
    A1 --> PG[("PostgreSQL primary<br/>HA + replicas, PgBouncer")]
    A2 --> PG
    A1 --> REDIS[("Redis cluster<br/>sessions, revoke, rate-limit")]
    A2 --> REDIS
    A2 --> KMS[["Secrets Manager / KMS<br/>(encryption key, JWT + SAML SP keys)"]]
    PG --> SIEM["Audit archive (object storage) → SIEM"]
    tier -. "outbound" .-> EG["SAML / OIDC IdPs (metadata, SSO, SLO)<br/>SMTP, SMS gateways"]

    classDef sec fill:#e1d5e7,stroke:#9673a6,color:#000;
    classDef authc fill:#ffe6cc,stroke:#d79b00,color:#000;
    classDef uic fill:#d5e8d4,stroke:#82b366,color:#000;
    classDef store fill:#f8cecc,stroke:#b85450,color:#000;
    classDef note fill:#fff2cc,stroke:#d6b656,color:#000;
    class CDN,LB,KMS,EG sec;
    class A1,A2 authc;
    class PG,REDIS store;
    class SIEM note;
```

- **Kubernetes + Helm** when Compose is outgrown; the Helm chart doubles as the self-host distribution.
- DB connection pooling (e.g. **PgBouncer** for Postgres) and read replicas for read-heavy admin/audit queries.
- Aggressively cache JWKS, discovery docs, and tenant config.
- Signing keys/secrets always in a KMS/Vault in production — never env files. The auth-server reads `SECUREONE_ENCRYPTION_KEY`, `SECUREONE_JWK_PRIVATE_KEY_PEM` and `SECUREONE_SAML_SP_KEY_PEM`/`_CERT_PEM` from the secret store; keys it generates itself are stored encrypted and created once per cluster (Redis lock).
- All flow state (sessions, SAML requests, device polling, rate limits) is in Redis, so any replica can serve any step.

See [Security](security.md) for hardening and [Installation](../install/installation.md) for setup.
