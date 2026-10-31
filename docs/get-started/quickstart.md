---
title: Quickstart
summary: SecureOne running on one machine in about five minutes, from the published image
order: 2
audience: public
---

# Quickstart

This starts SecureOne with its database and cache on one machine, from the published image. It is meant for trying
SecureOne, not for production: see [Deployment](../install/deployment.md) for that.

## What you need

- Docker with Compose
- Port 9000 free on the machine

## 1. Create a Compose file

Save this as `docker-compose.yml` in an empty folder:

```yaml
name: secureone-trial

services:
  secureone:
    image: dmtavs0108/secureone:1.1.0
    ports:
      - "9000:9000"
    environment:
      SECUREONE_ENV: development          # trial only: allows http and shows email links in the log
      SECUREONE_ISSUER_URL: http://localhost:9000
      SECUREONE_PUBLIC_BASE_URL: http://localhost:9000
      SECUREONE_DB_URL: jdbc:postgresql://postgres:5432/secureone?currentSchema=platform
      SECUREONE_DB_USERNAME: secureone
      SECUREONE_DB_PASSWORD: trial-only
      SECUREONE_REDIS_URL: redis://redis:6379
      SECUREONE_MAIL_LOG_WHEN_UNAVAILABLE: "true"
      SECUREONE_BOOTSTRAP_ADMIN_EMAIL: admin@example.com
      SECUREONE_BOOTSTRAP_ADMIN_PASSWORD: Trial-Admin-2026!
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_started

  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: secureone
      POSTGRES_USER: secureone
      POSTGRES_PASSWORD: trial-only
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U secureone -d secureone"]
      interval: 5s
      retries: 20

  redis:
    image: redis:7-alpine

volumes:
  pgdata:
```

## 2. Start it

```bash
docker compose up -d
docker compose logs -f secureone     # ready when you see "Started AuthServerApplication"
```

## 3. Sign in to the console

Open `http://localhost:9000/admin` and choose **Platform operator**:

| Field | Value |
|---|---|
| Email | `admin@example.com` |
| Password | `Trial-Admin-2026!` |

The first sign-in asks you to choose your own password.

## 4. Connect an application

In the console, choose **Connect an app**, pick the kind of application (web, single-page, mobile, back-end or
TV / command line) and follow the steps. **Test sign-in** shows the whole sign-in flow before you write any code.

Without an SMTP server, emails that SecureOne would send (verification, password reset) appear as links in the log:
`docker compose logs secureone`. Add your SMTP server under **Settings › Notifications** when you want real email.

## Next steps

- [Key concepts](concepts.md): tenants, applications, clients, roles
- [Integration guide](../integrate/integration-guide.md): connect your real application
- [Deployment](../install/deployment.md): run SecureOne for real, with your own database, secrets and domain

> [!WARNING]
> This trial uses development mode and fixed passwords. Never expose it to the internet. Remove it with
> `docker compose down -v`.
