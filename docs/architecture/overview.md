---
title: How SecureOne fits together
summary: The parts of SecureOne, how a sign-in works, and how it runs on one server or many
order: 1
audience: public
---

# How SecureOne fits together

SecureOne sits between your users and your applications. Applications send users to SecureOne to sign in (or call
its sign-in APIs), and get back standard tokens that say who the user is and what they may do.

## The parts

```mermaid
flowchart LR
    users(["People signing in"])
    apps(["Your applications"])
    admins(["Your administrators"])

    subgraph so["SecureOne"]
        signin["Sign-in and APIs<br/>OAuth 2.1 · OpenID Connect · SAML · SCIM"]
        console["Admin console<br/>/admin"]
    end

    db[("PostgreSQL<br/>data that must last")]
    cache[("Redis<br/>sessions and codes")]

    users --> signin
    apps --> signin
    admins --> console
    console --> signin
    signin --> db
    signin --> cache
```

| Part | What it does |
|---|---|
| **SecureOne** | One container. Signs people in, issues tokens, serves the sign-in pages and the APIs, and hosts the admin console at `/admin`. |
| **PostgreSQL** | Keeps tenants, users, applications, roles, settings and the audit log. Back it up. |
| **Redis** | Keeps short-lived state: sessions, one-time codes and rate limits. It needs no backup. |

## How a sign-in works

```mermaid
sequenceDiagram
    participant U as User
    participant A as Your application
    participant S as SecureOne
    U->>A: Opens the application
    A->>S: Sends the user to sign in
    S->>U: Sign-in page (password, passkey or code)
    U->>S: Signs in, and the second step if required
    S->>A: Returns to the application with a one-time code
    A->>S: Exchanges the code for tokens
    S-->>A: ID token and access token (with roles and permissions)
    A->>U: Signed in
```

Your application never sees the user's password. It checks the roles and permissions in the access token to decide
what the user may do. See [Hosted and native sign-in](../integrate/hosted-and-native-sign-in.md) for applications
that draw their own sign-in screens.

## How things are organised

```mermaid
flowchart TB
    platform["SecureOne installation"] --> t1["Tenant: an organisation"]
    platform --> t2["Another tenant"]
    t1 --> app1["Application"]
    t1 --> app2["Application"]
    t1 --> people["Users and groups"]
    app1 --> clients["OAuth clients<br/>web, mobile, services"]
    app1 --> roles["Roles and permissions"]
    people -. "given roles in" .-> app1
```

Each tenant's users and data are kept apart from every other tenant's. See [Key concepts](../get-started/concepts.md).

## Running it

```mermaid
flowchart LR
    subgraph one["One server"]
        p1["HTTPS proxy"] --> s1["SecureOne"]
        s1 --> d1[("PostgreSQL")]
        s1 --> r1[("Redis")]
    end
    subgraph many["Several instances"]
        lb["Load balancer (HTTPS)"] --> sa["SecureOne"]
        lb --> sb["SecureOne"]
        sa --> d2[("PostgreSQL")]
        sb --> d2
        sa --> r2[("Redis")]
        sb --> r2
    end
```

Start with one server. For more traffic or no downtime during updates, run several SecureOne containers behind a load
balancer: they share PostgreSQL and Redis, so any of them can serve any request. See [Deployment](../install/deployment.md).
