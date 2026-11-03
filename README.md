<p align="center"><img src="apps/admin-web/public/brand/secureone-logo.svg" alt="SecureOne" width="300"></p>

# SecureOne

> A centralized, multi-tenant **Identity and Access Management (IAM)** platform: sign-in, single sign-on, MFA, users, roles and permissions for all your applications, from one place.

SecureOne ships as **one Docker image**: the authorization server (Spring Boot + Spring Authorization Server) with the admin console built in at `/admin`. It needs PostgreSQL and Redis. Apps connect with standard OAuth 2.1 / OpenID Connect.

| | |
|---|---|
| Current release | **0.1.0** |
| Image | [`dmtavs0108/secureone`](https://hub.docker.com/r/dmtavs0108/secureone) (`0.1.0`, `latest`; Intel/AMD x86-64) |
| Helm chart | `helm repo add secureone https://secureone-website.vercel.app/charts` (chart `0.1.0`) |
| Website and user guide | https://secureone-website.vercel.app |
| One-click templates | [DTAVS0108/secureone-deploy](https://github.com/DTAVS0108/secureone-deploy) (public; templates only) |

---

## What SecureOne provides

- **Multi-tenant**: tenants → applications, each application with its own database schema and PostgreSQL row-level security
- **Standards**: OAuth 2.1 and OpenID Connect, PKCE, PAR, token exchange, device sign-in for TVs and CLIs (RFC 8628), discovery metadata (RFC 8414 / RFC 9728)
- **Sign-in**: hosted sign-in pages or your own screens; passwords, email/SMS one-time codes, magic links, passkeys
- **MFA**: passkeys (WebAuthn/FIDO2), authenticator apps (TOTP), email/SMS codes, recovery codes; required for console operators in production
- **Single sign-on**: Google, GitHub, any OIDC provider and SAML 2.0 identity providers, with single logout; SecureOne is also a SAML identity provider for your apps
- **Authorization**: roles, permissions and groups per application, included in tokens
- **Directory and automation**: SCIM 2.0, webhooks, service accounts with API keys
- **Governance**: access requests, access reviews, audit log, sign-in history
- **Admin console**: tenants, applications, users, roles, settings; *Connect an app* wizard with setup code for 15 frameworks; role-scoped access for tenant and application admins
- **Operations**: configuration as code, automatic database upgrades, health and Prometheus metrics, JSON logs
- **Security**: production mode refuses unsafe settings; stored secrets encrypted with a key kept outside the database; signing-key rotation, rate limits, password policies, breach checks

---

## Install

You set the public address, the database and Redis. SecureOne creates its own secrets on the first start (the
encryption key in its volume, the signing key and code pepper encrypted in the database), waits for PostgreSQL and
Redis, and prints a **setup code** in its log. Open `https://<your address>/admin/setup`, enter the code and the
wizard checks the installation, sets up email, shows the encryption key to back up and creates the first operator.

| You want to… | Use |
|---|---|
| One server with Docker | [Docker Compose](#docker-compose) |
| Your own PostgreSQL and Redis | [docker run](#docker-run) |
| Kubernetes | [Helm](#kubernetes-helm) |
| Render, Portainer, Unraid, DigitalOcean | [One-click templates](#one-click-templates) |
| AWS, Azure, Google Cloud | The website's [Deploy](https://secureone-website.vercel.app/deploy/) page (Terraform modules in [`deploy/terraform`](deploy/terraform/README.md), not tested on real accounts yet) |
| Work on the code | [From source](#from-source-development) |

**Requirements**: Docker 24+ (or Kubernetes 1.27+), PostgreSQL 16+, Redis 7+, about 1 GB of memory per container
(2 GB on cloud services), and https in front of port 9000 (a TLS reverse proxy or load balancer).

### Docker Compose

```yaml
name: secureone
services:
  secureone:
    image: dmtavs0108/secureone:0.1.0
    ports: ["127.0.0.1:9000:9000"]          # your HTTPS proxy forwards to this port
    environment:
      SECUREONE_PUBLIC_BASE_URL: https://id.example.com
      SECUREONE_DB_URL: jdbc:postgresql://postgres:5432/secureone?currentSchema=platform
      SECUREONE_DB_USERNAME: secureone
      SECUREONE_DB_PASSWORD: choose-a-database-password
      SECUREONE_REDIS_URL: redis://redis:6379
    volumes: ["secureone-data:/var/lib/secureone"]   # the encryption key lives here: back it up
    restart: unless-stopped
  postgres:
    image: postgres:16-alpine
    environment: {POSTGRES_DB: secureone, POSTGRES_USER: secureone, POSTGRES_PASSWORD: choose-a-database-password}
    volumes: ["pgdata:/var/lib/postgresql/data"]
    restart: unless-stopped
  redis:
    image: redis:7-alpine
    restart: unless-stopped
volumes: {secureone-data: {}, pgdata: {}}
```

```bash
docker compose up -d && docker compose logs -f secureone     # wait for the setup code
```

With secrets as files and a daily backup container instead: [`deploy/docker-compose.example.yml`](deploy/docker-compose.example.yml).

### docker run

```bash
docker run -d --name secureone --restart unless-stopped \
  -p 127.0.0.1:9000:9000 -v secureone-data:/var/lib/secureone \
  -e SECUREONE_PUBLIC_BASE_URL=https://id.example.com \
  -e "SECUREONE_DB_URL=jdbc:postgresql://db.example.internal:5432/secureone?currentSchema=platform" \
  -e SECUREONE_DB_USERNAME=secureone -e SECUREONE_DB_PASSWORD='…' \
  -e SECUREONE_REDIS_URL=redis://redis.example.internal:6379 \
  dmtavs0108/secureone:0.1.0
```

### Kubernetes (Helm)

```bash
helm repo add secureone https://secureone-website.vercel.app/charts && helm repo update
helm install secureone secureone/secureone -n secureone --create-namespace -f secureone-values.yaml
```

`secureone-values.yaml` holds `publicUrl`, `database.url/username/password`, `redis.url` and `ingress`. The chart
creates its Secret (with a random encryption key) once and keeps it on upgrade and uninstall, runs a migration job
before the pods, and starts two replicas. Details: [Install on Kubernetes](docs/install/kubernetes.md).

### One-click templates

The public repository [DTAVS0108/secureone-deploy](https://github.com/DTAVS0108/secureone-deploy) holds ready-made
templates that start SecureOne with its database and Redis, every setting filled in:

| Platform | Template | How |
|---|---|---|
| Render | `render.yaml` | [Deploy to Render](https://render.com/deploy?repo=https://github.com/DTAVS0108/secureone-deploy): only asks for the public address; Render generates the encryption key |
| Portainer | `portainer/templates.json`, `portainer/docker-compose.yml` | App Templates URL, or paste the Compose file as a stack |
| Unraid | `unraid/secureone.xml` | Community Apps (after submission at ca.unraid.net) |
| DigitalOcean | `digitalocean/app.yaml` | `doctl apps create --spec app.yaml` |
| Railway | built in Railway's dashboard | settings in [deploy/README.md](deploy/README.md#one-click-templates) |

**Why a separate repository:** these platforms read templates from a public Git repository, and this one stays
private. `secureone-deploy` holds only the templates and their README (no source code, no secrets). Its source of
truth is [`deploy/templates/`](deploy/templates/) here: each template names the image version, so with every release
update the version there, copy the folder into `secureone-deploy` and push it. User guide:
[Hosting platforms](docs/install/hosting-platforms.md).

### From source (development)

```bash
docker compose -f deploy/docker-compose.yml up -d        # PostgreSQL, Redis, MailHog (http://localhost:8025)
cd apps/auth-server && ./gradlew bootRun                  # http://localhost:9000 (Java 25)
cd apps/admin-web && pnpm install && pnpm dev            # console on :3001, served at http://localhost:9000/admin
```

Development mode (`SECUREONE_ENV=development`) allows plain http and the emergency account `admin` / `admin`. A
five-minute trial with a mail catcher: [Quickstart](docs/get-started/quickstart.md). Build the image with
`docker build -f deploy/Dockerfile -t secureone:dev .` (give Docker about 4 GB of memory).

---

## Settings

Every setting is read when the container starts: an environment variable, or a file of the same name in
`/run/secrets` (Docker and Kubernetes secrets). If something required is missing, the container stops and the log
names the setting.

| Setting | Required | Notes |
|---|---|---|
| `SECUREONE_PUBLIC_BASE_URL` | always | The address users open, `https://` in production; the token issuer follows it |
| `SECUREONE_DB_URL` | always | `jdbc:postgresql://host:5432/db?currentSchema=platform`, or a provider's `postgres://user:password@host:port/db` URL |
| `SECUREONE_DB_USERNAME`, `SECUREONE_DB_PASSWORD` | always, unless in a `postgres://` URL | Owner of the empty database; needs `CREATEROLE` on the first start |
| `SECUREONE_REDIS_URL` | always | `redis://host:6379`, or `rediss://:password@host:6380` |
| Volume at `/var/lib/secureone` | production | Where SecureOne creates its encryption key; or set `SECUREONE_ENCRYPTION_KEY` (32 random bytes, base64) |
| `SECUREONE_TRUSTED_PROXIES` | behind a proxy | Your proxy's address or range, so client addresses are trusted |
| `SECUREONE_SMTP_*` | optional | Email; usually set in the setup wizard |
| `SECUREONE_ENV` | optional | `production` (image default) or `development` |

All settings: [Deployment › All settings](docs/install/deployment.md#all-settings). Publish port **9000**; keep
**9001** (health, metrics) internal.

**Upgrade**: back up the database, change the image tag, start it; the database upgrades itself. **Back up** the
database and, separately, the encryption key: without the key, a database backup cannot be used.

---

## Connect your first app

1. In the console: **Connect an app**, pick the kind of app (web, single-page, mobile, desktop, backend, TV/CLI) and framework.
2. Copy the generated setup code; try **Test sign-in** before writing any code.

Guide: [Integration guide](docs/integrate/integration-guide.md).

---

## Documentation and website

`docs/` is the single source for two readers:

| Where | For | Content |
|---|---|---|
| Console, `/admin/documentation` (and `/docs` in the image) | Operators of an installation | Production **and** development (quickstart, development mode, local examples). The release image leaves out developer pages and blocks |
| Website, https://secureone-website.vercel.app | People running SecureOne in production | Production only: pages with `website: false` and `<!-- not on website -->` blocks are left out; `check-production.mjs` fails the build otherwise |

| Space | What's inside |
|---|---|
| [Get started](docs/get-started/index.md) | What SecureOne is, key concepts, operator first steps |
| [Install and operate](docs/install/index.md) | Installation, Kubernetes, hosting platforms, every setting, upgrades, runbooks |
| [Integrate applications](docs/integrate/index.md) | Integration guide, hosted or native sign-in, OAuth clients, roles and access |
| [Admin console guide](docs/guide/index.md) | Every console page, button and field |
| [API reference](docs/api/index.md) | Every endpoint with curl, Swagger UI and OpenAPI |
| [Architecture](docs/architecture/index.md) | How SecureOne fits together, standards, security |
| [Developer](docs/developer/index.md) | Data model, database, migrations, decision records, [docs and website](docs/developer/documentation-and-website.md) |

Releasing (image, chart, website, templates): [deploy/README.md](deploy/README.md).

---

## Repository layout

```
secureone/
├── apps/
│   ├── auth-server/            # Spring Boot + Spring Authorization Server (Java 25)
│   ├── admin-web/              # Next.js console, served by the auth-server at /admin
│   └── website/                # public website and user guide (Astro + Starlight), chart repo at /charts
├── deploy/
│   ├── Dockerfile              # the single SecureOne image
│   ├── helm/secureone/         # Kubernetes chart (migration job, generated Secret)
│   ├── templates/              # one-click templates = content of DTAVS0108/secureone-deploy
│   ├── terraform/              # AWS, Google Cloud, Azure modules (not tested on real accounts yet)
│   ├── docker-compose.example.yml   # single server with secret files and daily backups
│   ├── docker-compose.quickstart.yml # trial with a mail catcher (development mode)
│   └── docker-compose.yml      # development: PostgreSQL, Redis, MailHog
├── docs/                       # documentation (console and website)
└── scripts/                    # release.sh (build | chart | publish), docs tools
```

## Stack

Java 25 · Spring Boot 4 · Spring Authorization Server · PostgreSQL 16 (Flyway, row-level security) · Redis 7 · Next.js 16 · TypeScript · Tailwind · Astro. Details: [Tech stack](docs/architecture/tech-stack.md).
