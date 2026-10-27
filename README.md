# SecureOne

> A centralized, multi-tenant **Identity and Access Management (IAM)** platform: sign-in, single sign-on, MFA, users, roles and permissions for all your applications, from one place.

SecureOne ships as **one Docker image**: the authorization server (Spring Boot + Spring Authorization Server) with the admin console built in at `/admin`. It needs PostgreSQL and Redis. Apps connect with standard OAuth 2.1 / OpenID Connect.

- Image: [`dmtavs0108/secureone`](https://hub.docker.com/r/dmtavs0108/secureone) (current: `0.3.1`)
- Everything is configured when the container is created (environment variables or secret files), including the first admin.

---

## What SecureOne provides

- **Multi-tenant**: tenants → applications, each application with its own database schema and PostgreSQL row-level security
- **Standards**: OAuth 2.1 and OpenID Connect, PKCE, PAR, token exchange, device sign-in for TVs and CLIs (RFC 8628), discovery metadata (RFC 8414 / RFC 9728)
- **Sign-in**: hosted sign-in pages or your own screens; passwords, email/SMS one-time codes, magic links, passkeys
- **MFA**: passkeys (WebAuthn/FIDO2), authenticator apps (TOTP), email/SMS codes, recovery codes; can be required for console operators (`SECUREONE_ADMIN_MFA_REQUIRED=true`)
- **Single sign-on**: Google, GitHub, any OIDC provider and SAML 2.0 identity providers, with single logout
- **Authorization**: roles, permissions and groups per application, included in tokens
- **Directory and automation**: SCIM 2.0, webhooks, service accounts with API keys
- **Governance**: access requests, access reviews, audit log, sign-in history
- **Admin console**: tenants, applications, users, roles, settings; *Connect an app* wizard with setup code for 15 frameworks; role-scoped access for tenant and application admins
- **Operations**: configuration as code (export, preview, apply tenant files), automatic database upgrades, health and Prometheus metrics, JSON logs
- **Security**: client secrets and stored credentials hashed or encrypted, signing-key rotation, rate limits, password policies, breach checks

---

## Installation

Choose one:

| You want to… | Use |
|---|---|
| Try it in a few minutes | [A. Docker run](#a-docker-run) |
| Run it on a server (with backups) | [B. Docker Compose](#b-docker-compose-recommended-for-a-single-server) |
| Run it on Kubernetes | [C. Helm](#c-kubernetes-helm) |
| Work on the code | [D. From source](#d-from-source-development) |

**Requirements**: Docker 24+ (or Kubernetes 1.27+), PostgreSQL 16+, Redis 7+. A container needs about 1 GB of memory. Production needs https: put a TLS reverse proxy or load balancer in front of port 9000.

### A. Docker run

Creates a private network, PostgreSQL, Redis and SecureOne, with the first admin. PowerShell (on Linux or macOS replace the trailing `` ` `` with `\`):

```powershell
docker network create secureone-net

docker run -d --name secureone-db --network secureone-net --restart unless-stopped `
  -e POSTGRES_DB=secureone -e POSTGRES_USER=secureone -e POSTGRES_PASSWORD=Db-Pass-2026-change-me `
  -v secureone-pgdata:/var/lib/postgresql/data postgres:16-alpine

docker run -d --name secureone-cache --network secureone-net --restart unless-stopped `
  -v secureone-redisdata:/data redis:7-alpine redis-server --appendonly yes

docker run -d --name secureone --network secureone-net --restart unless-stopped -p 9000:9000 `
  -e SECUREONE_ENV=development `
  -e SECUREONE_ISSUER_URL=http://localhost:9000 `
  -e SECUREONE_PUBLIC_BASE_URL=http://localhost:9000 `
  -e "SECUREONE_DB_URL=jdbc:postgresql://secureone-db:5432/secureone?currentSchema=platform" `
  -e SECUREONE_DB_USERNAME=secureone `
  -e SECUREONE_DB_PASSWORD=Db-Pass-2026-change-me `
  -e SECUREONE_REDIS_URL=redis://secureone-cache:6379 `
  -e SECUREONE_BOOTSTRAP_ADMIN_EMAIL=owner@example.com `
  -e "SECUREONE_BOOTSTRAP_ADMIN_PASSWORD=Owner-Pass-2026!x" `
  dmtavs0108/secureone:latest
```

After about 30 seconds (`docker logs secureone` shows *First platform operator created*), open **http://localhost:9000/admin**, choose **Platform operator** and sign in as `owner@example.com`.

This is development mode (plain http, example passwords). For production see the settings below.

**Build your own image, update a running container or roll back:** see [deploy/README.md](deploy/README.md).

### B. Docker Compose (recommended for a single server)

[`deploy/docker-compose.example.yml`](deploy/docker-compose.example.yml) runs SecureOne, PostgreSQL, Redis (persistent, with a password) and a **daily database backup**, with all secrets as files.

```bash
mkdir secureone && cd secureone
# copy deploy/docker-compose.example.yml and deploy/generate-secrets.sh here, then:
sh generate-secrets.sh owner@example.com          # creates ./secrets (7 files); keep a copy elsewhere
ADMIN_EMAIL=owner@example.com PUBLIC_URL=https://id.example.com \
  docker compose -f docker-compose.example.yml up -d
```

Sign in at `https://id.example.com/admin` with the password in `secrets/admin_password`. Backups are written to `./backups`. To try it locally over http, add `SECUREONE_ENV=development PUBLIC_URL=http://localhost:9000`.

For a quick evaluation with a mail catcher instead: `docker compose -f deploy/docker-compose.quickstart.yml up --build` (builds the image from source, admin `admin@example.com` / `Quickstart-Admin-2026!`, mail at http://localhost:8025).

### C. Kubernetes (Helm)

```bash
kubectl create secret generic secureone \
  --from-literal=SECUREONE_DB_USERNAME=secureone --from-literal=SECUREONE_DB_PASSWORD='…' \
  --from-literal=SECUREONE_ENCRYPTION_KEY="base64:$(openssl rand -base64 32)" \
  --from-literal=SECUREONE_OTP_PEPPER="$(openssl rand -hex 32)" \
  --from-file=SECUREONE_JWK_PRIVATE_KEY_PEM=jwk.pem \
  --from-literal=SECUREONE_BOOTSTRAP_ADMIN_EMAIL=owner@example.com \
  --from-literal=SECUREONE_BOOTSTRAP_ADMIN_PASSWORD='…'

helm upgrade --install secureone deploy/helm/secureone \
  --set image.repository=dmtavs0108/secureone --set image.tag=0.3.1 \
  --set env.SECUREONE_ISSUER_URL=https://id.example.com --set env.SECUREONE_PUBLIC_BASE_URL=https://id.example.com \
  --set env.SECUREONE_DB_URL='jdbc:postgresql://postgres:5432/secureone?currentSchema=platform' \
  --set env.SECUREONE_REDIS_URL=redis://redis:6379 \
  --set ingress.enabled=true --set ingress.host=id.example.com
```

A pre-install / pre-upgrade job upgrades the database before the pods start. PostgreSQL and Redis are not part of the chart (use managed services or their own charts). Details: [docs/24](docs/24-deployment-image-env-and-enterprise.md).

### D. From source (development)

```bash
docker compose -f deploy/docker-compose.yml up -d        # PostgreSQL, Redis, MailHog (http://localhost:8025)
cd apps/auth-server && ./gradlew bootRun                  # http://localhost:9000 (Java 25)
cd apps/admin-web && pnpm install && pnpm dev            # console on :3001, served at http://localhost:9000/admin
```

Development mode allows the emergency account `admin` / `admin` (Emergency access link). Build the image yourself with `docker build -f deploy/Dockerfile -t secureone:dev .` (give Docker about 4 GB of memory).

---

## Settings

Every setting is read when the container starts: an environment variable, or a file of the same name in `/run/secrets` (Docker and Kubernetes secrets). If something required is missing, the container stops and the log names the setting.

| Setting | Required | Notes |
|---|---|---|
| `SECUREONE_DB_URL`, `SECUREONE_DB_USERNAME`, `SECUREONE_DB_PASSWORD` | always | PostgreSQL 16+; keep `?currentSchema=platform`. The user needs `CREATEROLE` on the first start |
| `SECUREONE_REDIS_URL` | always | `redis://host:6379`, or `rediss://:password@host:6380` |
| `SECUREONE_ISSUER_URL`, `SECUREONE_PUBLIC_BASE_URL` | always | Public address; `https://` in production |
| `SECUREONE_ENCRYPTION_KEY` | production | `echo "base64:$(openssl rand -base64 32)"`; back it up separately |
| `SECUREONE_OTP_PEPPER` | production | `openssl rand -hex 32` |
| `SECUREONE_JWK_PRIVATE_KEY_PEM` | production | `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048`; best as a secret file |
| `SECUREONE_BOOTSTRAP_ADMIN_EMAIL`, `SECUREONE_BOOTSTRAP_ADMIN_PASSWORD` | first start | Creates the first admin once; ignored afterwards. Without them, use the setup code from the log at `/admin/setup` |
| `SECUREONE_SMTP_HOST`, `_PORT`, `_USERNAME`, `_PASSWORD` | recommended | Email (can also be set in the console) |
| `SECUREONE_CONFIG_IMPORT` | optional | Folder of tenant configuration files applied at every start |
| `SECUREONE_ENV` | optional | `production` (image default) or `development` |

All settings with defaults: [docs/24 "All settings"](docs/24-deployment-image-env-and-enterprise.md#all-settings). Ports: publish **9000**; keep **9001** (health, metrics) internal.

**Upgrade**: back up the database, change the image tag, start it; the database upgrades itself. **Backups**: database dumps plus the secrets (above all the encryption key). See [docs/26](docs/26-configuration-as-code-and-upgrades.md) and [docs/24 "Backups"](docs/24-deployment-image-env-and-enterprise.md#backups).

---

## Connect your first app

1. In the console: **Connect an app**, pick the kind of app (web, single-page, mobile, backend, TV/CLI) and framework.
2. Copy the generated setup code; try **Test sign-in** before writing any code.
3. Or start from a [sample app](samples/README.md): React SPA, Next.js, Express API, device-code CLI.

Guide: [docs/23 Application integration](docs/23-application-integration-guide.md).

---

## Documentation

**Start here:** [docs/README.md](docs/README.md). The same docs are in the console at `/admin/confluence`.

| # | Document | What's inside |
|---|---|---|
| 01 | [Overview](docs/01-overview.md) | Vision, goals, glossary |
| 03 | [Architecture](docs/03-architecture.md) | Components, flows, deployment |
| 05 | [Auth standards](docs/05-auth-standards.md) | OAuth 2.1 / OIDC, tokens, MFA |
| 07 | [Security](docs/07-security.md) | Security practices |
| 08 | [Roadmap](docs/08-roadmap.md) | Phased delivery |
| 15 | [Applications & OAuth clients](docs/15-applications-and-oauth-clients.md) | Applications vs clients, per-app schemas |
| 22 | [Operator getting started](docs/22-operator-getting-started.md) | Day-to-day operation |
| 23 | [Application integration](docs/23-application-integration-guide.md) | Connecting apps, errors explained |
| **24** | **[Deployment](docs/24-deployment-image-env-and-enterprise.md)** | **Image, all settings, backups, Redis, Helm** |
| 25 | [Roles and access](docs/25-roles-and-access.md) | Who sees and can do what |
| **26** | **[Configuration as code and upgrades](docs/26-configuration-as-code-and-upgrades.md)** | **Tenant files, upgrades, rollback** |
| — | [Build, update and run the container](deploy/README.md) | Build the image, update or roll back a container, start a new installation |
| — | [User guide](docs/user-guide/README.md) | Every console page, button and field |
| — | [Runbooks](docs/runbooks/) | Deploy, rollback, incidents, key rotation |
| — | [Decision records](docs/decisions/README.md) | ADRs |

---

## Repository layout

```
secureone/
├── apps/
│   ├── auth-server/            # Spring Boot + Spring Authorization Server (Java 25)
│   └── admin-web/              # Next.js console, served by the auth-server at /admin
├── deploy/
│   ├── Dockerfile              # the single SecureOne image
│   ├── docker-compose.example.yml   # production-style single server (secrets, backups)
│   ├── generate-secrets.sh     # creates the secrets for the example
│   ├── docker-compose.quickstart.yml  # evaluation with MailHog
│   ├── docker-compose.yml      # development: PostgreSQL, Redis, MailHog
│   └── helm/secureone/         # Kubernetes chart (with migration job)
├── samples/                    # React SPA, Next.js, Express API, device-code CLI
├── docs/                       # documentation (also in the console)
└── scripts/                    # development helpers
```

## Stack

Java 25 · Spring Boot 4 · Spring Authorization Server · PostgreSQL 16 (Flyway, row-level security) · Redis 7 · Next.js 16 · TypeScript · Tailwind. Details: [docs/02](docs/02-tech-stack.md).
