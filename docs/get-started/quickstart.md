---
title: Quickstart
summary: SecureOne running on your machine in about five minutes
order: 2
audience: public
---

# Quickstart

This starts SecureOne with its database, cache and a mail catcher on one machine. It is meant for trying SecureOne,
not for production.

## What you need

- Docker Desktop (or Docker Engine with Compose) with about 4 GB of memory for the build
- A copy of the SecureOne repository

## 1. Start everything

From the repository root:

```bash
docker compose -f deploy/docker-compose.quickstart.yml up --build
```

The first build takes several minutes. SecureOne is ready when the log shows `Started AuthServerApplication`.

## 2. Sign in to the console

Open `/admin` on the quickstart address (on your own machine that is `http://localhost:9000/admin`) and choose
**Platform operator**:

| Field | Value |
|---|---|
| Email | `admin@example.com` |
| Password | `Quickstart-Admin-2026!` (or the value of `QUICKSTART_ADMIN_PASSWORD`) |

The first sign-in asks you to choose your own password.

## 3. Connect an application

In the console, choose **Connect an app**, pick the kind of application (web, single-page, mobile, back-end or
TV / command line) and follow the steps. **Test sign-in** shows the whole sign-in flow before you write any code.

Emails that SecureOne sends (verification, password reset) appear in the mail catcher at port `8025`.

## Next steps

- [Key concepts](concepts.md): tenants, applications, clients, roles
- [Integration guide](../integrate/integration-guide.md): connect your real application
- [Deployment](../install/deployment.md): run SecureOne for real, with your own database, secrets and domain

> [!WARNING]
> The quickstart uses development mode and throw-away passwords. Never expose it to the internet.
