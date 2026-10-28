# 16 â€” Environment Setup & Operations

End-to-end guide for **running SecureOne locally**, configuring environment variables, understanding **Flyway migrations**, **startup bootstrap**, and **scheduled background jobs**.

For a shorter first-time walkthrough, see [09 â€” Installation](09-installation.md). For database layout, see [06 â€” Database Strategy](06-database.md).

---

## What runs where

| Component | Port (dev) | Role |
|-----------|------------|------|
| **PostgreSQL** | `5432` | System of record (`platform` + per-app schemas) |
| **Redis** | `6379` | Sessions, cache, rate limits |
| **MailHog** | SMTP `1025`, UI `8025` | Catches outbound email in dev |
| **auth-server** | `9000` | OAuth/OIDC, admin APIs, Flyway, schedulers; serves the console at **`/admin`** |
| **admin-web** (`next dev`) | `3001` (internal) | Admin console + SecureOne Documentation; open it at http://localhost:9000/admin (or http://localhost:3001/admin for hot reload) |

---

## Docker & Compose files (reference)

Source files live under **`deploy/`**. In local dev, **`docker-compose.yml` runs only infrastructure** (Postgres, Redis, MailHog). **auth-server** and **admin-web** are started on the host by `scripts/start-all.ps1` (Gradle + Node), and the auth-server forwards `/admin` to the console dev server. For production there is **one image** (`deploy/Dockerfile`) containing both.

```
deploy/
├── docker-compose.yml             ← dev: Postgres, Redis, MailHog
├── docker-compose.quickstart.yml  ← evaluation: the SecureOne image + Postgres, Redis, MailHog
├── Dockerfile                     ← the single SecureOne image (auth-server + console at /admin)
└── helm/secureone                 ← Kubernetes chart (one Deployment, Service, Ingress)
.dockerignore                      ← allow-list for the image build (repo root)
```

### `deploy/docker-compose.yml` (development infrastructure)

Starts data-plane services only. Persistent Postgres data uses the **`pgdata`** named volume.

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: secureone-postgres
    environment:
      POSTGRES_DB: secureone
      POSTGRES_USER: secureone
      POSTGRES_PASSWORD: secureone
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U secureone -d secureone"]
      interval: 5s
      timeout: 5s
      retries: 10

  redis:
    image: redis:7-alpine
    container_name: secureone-redis
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 5s
      retries: 10

  # Catches outbound email in dev â€” web UI at http://localhost:8025
  mailhog:
    image: mailhog/mailhog:latest
    container_name: secureone-mailhog
    ports:
      - "1025:1025"
      - "8025:8025"

volumes:
  pgdata:
```

| Service | Container name | Host ports | Purpose |
|---------|----------------|------------|---------|
| `postgres` | `secureone-postgres` | `5432` | Database (`secureone` / `secureone` / `secureone`) |
| `redis` | `secureone-redis` | `6379` | Cache & sessions |
| `mailhog` | `secureone-mailhog` | `1025` (SMTP), `8025` (UI) | Dev email capture |

**Commands:**

```bash
# Start
docker compose -f deploy/docker-compose.yml up -d

# Status / logs
docker compose -f deploy/docker-compose.yml ps
docker compose -f deploy/docker-compose.yml logs -f postgres

# Stop (keep data)
docker compose -f deploy/docker-compose.yml down

# Stop and wipe Postgres volume (fresh DB)
docker compose -f deploy/docker-compose.yml down -v
```

### `deploy/Dockerfile` (the single image)

Three stages: the console is built as a Next.js standalone server (Node 22), the auth-server as a Spring Boot jar (JDK 25, unpacked), and both go into one JRE 25 Alpine image with Node.js and `tini`. The last stage records a JVM start-up cache (`app.aot`) by starting the application once against a temporary PostgreSQL + Redis that exist only during that build step. Health and metrics are on port 9001 (`SECUREONE_MANAGEMENT_PORT`). At start-up the auth-server runs the console on `127.0.0.1:3001` and serves it at **`/admin`**; only port **9000** is exposed. It runs as UID 10001. Flyway runs on container start (same as `bootRun`). Nothing environment-specific is baked in: URLs, environment and secrets are read at start-up.

**Build & run** (after compose is up):

```bash
docker build -f deploy/Dockerfile -t secureone/secureone .

docker run --rm -p 9000:9000 \
  -e SPRING_PROFILES_ACTIVE=dev -e SECUREONE_ENV=development \
  -e SECUREONE_DB_URL=jdbc:postgresql://host.docker.internal:5432/secureone?currentSchema=platform \
  -e SECUREONE_DB_USERNAME=secureone \
  -e SECUREONE_DB_PASSWORD=secureone \
  -e SECUREONE_REDIS_URL=redis://host.docker.internal:6379 \
  -e SECUREONE_ISSUER_URL=http://localhost:9000 \
  -e SECUREONE_PUBLIC_BASE_URL=http://localhost:9000 \
  secureone/secureone
# http://localhost:9000/admin
```

> On Linux use `host.docker.internal` via `--add-host=host.docker.internal:host-gateway` or point at the compose service network.

The image defaults to `SPRING_PROFILES_ACTIVE=prod` / `SECUREONE_ENV=production`, which refuses to start without production settings (https URLs, encryption key, OTP pepper, signing key; see [24 — Deployment](24-deployment-image-env-and-enterprise.md#production-caveats)). The example above switches to development for a local try-out.

### `deploy/docker-compose.quickstart.yml` (evaluation)

```bash
docker compose -f deploy/docker-compose.quickstart.yml up --build
# http://localhost:9000/admin (admin / admin) · MailHog http://localhost:8025
```

Builds the image and runs it with its own Postgres, Redis and MailHog, in development mode. Not for production.

### Dev vs containerized

| Mode | Infrastructure | auth-server | Admin console |
|------|----------------|-------------|---------------|
| **Local dev (default)** | Docker Compose | Host (`gradlew bootRun`) | Host `next dev` on :3001, served at http://localhost:9000/admin (`SECUREONE_CONSOLE_UPSTREAM_URL`) |
| **Container image** | Docker Compose (or managed DB/Redis) | `deploy/Dockerfile` | Same container, started by the auth-server (`SECUREONE_CONSOLE_DIR`) |
---

## Configuration files (reference)

Source-of-truth paths in the monorepo. Copy values into environment variables or local env files as needed.

```
secureone/
├── .env.example                              ← production-oriented env template (repo root)
├── pnpm-workspace.yaml                       ← JS monorepo workspaces
├── package.json                              ← root scripts (turbo / pnpm)
├── apps/auth-server/
│   ├── build.gradle.kts                      ← Java 25, Spring Boot, Flyway deps
│   └── src/main/resources/application.yml    ← auth-server defaults + scheduler cron
└── apps/admin-web/
    ├── package.json                          ← Next.js scripts (dev :3001, served under /admin)
    ├── next.config.ts
    └── .env.local.example                    ← copy to .env.local
```

### `apps/auth-server/src/main/resources/application.yml`

```yaml
spring:
  application:
    name: auth-server

  datasource:
    url: ${SECUREONE_DB_URL:jdbc:postgresql://localhost:5432/secureone?currentSchema=platform}
    username: ${SECUREONE_DB_USERNAME:secureone}
    password: ${SECUREONE_DB_PASSWORD:secureone}

  jpa:
    open-in-view: false
    hibernate:
      # Flyway owns the schema. Tighten to "validate" once entities fully match migrations.
      ddl-auto: none
    properties:
      hibernate.jdbc.time_zone: UTC
      hibernate.format_sql: true

  flyway:
    enabled: true
    locations: classpath:db/migration/postgresql

  data:
    redis:
      url: ${SECUREONE_REDIS_URL:redis://localhost:6379}

  # SMTP is configured in Platform settings â†’ Notifications (stored in DB). Optional env override for local MailHog.
  mail:
    host: ${SECUREONE_SMTP_HOST:}
    port: ${SECUREONE_SMTP_PORT:1025}
    username: ${SECUREONE_SMTP_USERNAME:}
    password: ${SECUREONE_SMTP_PASSWORD:}
    properties:
      mail.smtp.auth: ${SECUREONE_SMTP_AUTH:false}
      mail.smtp.starttls.enable: ${SECUREONE_SMTP_STARTTLS:false}
      mail.smtp.connectiontimeout: 10000
      mail.smtp.timeout: 10000

  # Dev login user (replace with real user store in Phase 1)
  security:
    user:
      name: ${SECUREONE_DEV_USER:admin}
      password: ${SECUREONE_DEV_PASSWORD:admin}
    oauth2:
      authorizationserver:
        issuer: ${SECUREONE_ISSUER_URL:http://localhost:9000}
        # OAuth clients are loaded from application records (admin Applications API).

server:
  port: ${SECUREONE_PORT:9000}

secureone:
  public-base-url: ${SECUREONE_PUBLIC_BASE_URL:http://localhost:9000}
  admin-web-url: ${SECUREONE_ADMIN_WEB_URL:${secureone.public-base-url}/admin}
  # When MailHog/SMTP is down, log verification/reset links to the auth-server console (dev only).
  mail:
    log-links-when-smtp-unavailable: ${SECUREONE_MAIL_LOG_WHEN_UNAVAILABLE:true}
  password-expiry:
    scheduler-enabled: ${SECUREONE_PASSWORD_EXPIRY_SCHEDULER:true}
    check-cron: ${SECUREONE_PASSWORD_EXPIRY_CRON:0 0 */6 * * *}

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true
  health:
    mail:
      enabled: false

info:
  app:
    name: SecureOne Auth Server
    description: Centralized IAM platform â€” authorization server
    version: 0.0.1-SNAPSHOT

springdoc:
  api-docs:
    enabled: true
    path: /v3/api-docs
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
    # Required when disable-swagger-default-url is true â€” springdoc 3.x may omit urls[] from swagger-config.
    url: /v3/api-docs/all
    urls-primary-name: all
    disable-swagger-default-url: true
    operations-sorter: method
    tags-sorter: alpha
    display-request-duration: true
    try-it-out-enabled: true
    persist-authorization: true
    doc-expansion: list
    groups-order: ASC
    config-url: /v3/api-docs/swagger-config
  show-actuator: false
  writer-with-order-by-keys: true
```

### Repo root `.env.example`

Production-oriented template (not all vars are used in minimal local dev — see `application.yml` defaults).

```dotenv
# ---- Database (PostgreSQL) ----
SECUREONE_DB_URL=jdbc:postgresql://localhost:5432/secureone
SECUREONE_DB_USERNAME=secureone
SECUREONE_DB_PASSWORD=change-me

# ---- Redis ----
SECUREONE_REDIS_URL=redis://localhost:6379

# ---- OTP (signup/login verification codes) ----
SECUREONE_OTP_PEPPER=change-me-in-production

# ---- OIDC / JWT ----
SECUREONE_ISSUER_URL=http://localhost:9000
SECUREONE_JWT_KEY_SOURCE=local          # local | kms | vault

# ---- Secret encryption (stored secrets + generated signing keys) ----
# AES-256 key kept OUTSIDE the database: encrypts SMTP/SMS credentials, IdP client secrets and the
# JWT / SAML private keys SecureOne generates. Generate: echo "base64:$(openssl rand -base64 32)"
# Blank = development fallback (key stored in the database, warning at startup). A malformed value
# stops startup. Production: set it from your secret manager and back it up separately from the DB.
SECUREONE_ENCRYPTION_KEY=

# ---- SAML 2.0 SSO (optional) ----
# SP signing/decryption key (PKCS#8 PEM) + self-signed certificate published in SP metadata.
# Blank = generated on first SAML use and stored encrypted. One-line env files: use \n for line breaks.
SECUREONE_SAML_SP_KEY_PEM=
SECUREONE_SAML_SP_CERT_PEM=

# ---- Email (SMTP) ----
# Dev default: MailHog via docker compose (localhost:1025, UI http://localhost:8025).
# Most providers need an app password when the account uses two-step sign-in.
SECUREONE_SMTP_HOST=smtp.example.com
SECUREONE_SMTP_PORT=587
SECUREONE_SMTP_USERNAME=you@example.com
SECUREONE_SMTP_PASSWORD=your-app-password
SECUREONE_SMTP_AUTH=true
SECUREONE_SMTP_STARTTLS=true
SECUREONE_SMTP_SSL_TRUST=smtp.example.com
# Set false when using real SMTP so failures surface instead of logging to console
SECUREONE_MAIL_LOG_WHEN_UNAVAILABLE=true

# ---- First-run setup ----
# Optional: fixed setup code for /admin/setup (otherwise printed in the log until the first operator exists)
# SECUREONE_SETUP_CODE=
```

### `apps/admin-web/.env.local.example`

Copy to `apps/admin-web/.env.local` for local dev.

```dotenv
# Base URL of the SecureOne authorization server (apps/auth-server).
# Copy this file to .env.local and adjust as needed.
SECUREONE_PUBLIC_BASE_URL=http://localhost:9000

# Server-side Basic auth for admin API calls (must match auth-server dev user).
SECUREONE_DEV_USER=admin
SECUREONE_DEV_PASSWORD=admin
```

### `apps/admin-web/next.config.ts`

```typescript
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
};

export default nextConfig;
```

### `apps/admin-web/package.json`

```json
{
  "name": "admin-web",
  "version": "0.1.0",
  "private": true,
  "scripts": {
    "dev": "next dev --port 3001",
    "build": "next build",
    "start": "next start",
    "lint": "eslint"
  },
  "dependencies": {
    "next": "16.2.6",
    "react": "19.2.4",
    "react-dom": "19.2.4",
    "react-markdown": "^10.1.0",
    "remark-gfm": "^4.0.1"
  },
  "devDependencies": {
    "@tailwindcss/postcss": "^4",
    "@types/node": "^20",
    "@types/react": "^19",
    "@types/react-dom": "^19",
    "eslint": "^9",
    "eslint-config-next": "16.2.6",
    "tailwindcss": "^4",
    "typescript": "^5"
  }
}
```

### Root `package.json` + `pnpm-workspace.yaml`

```json
{
  "name": "secureone",
  "version": "0.0.1",
  "private": true,
  "description": "SecureOne â€” centralized Identity & Access Management platform (JS workspaces).",
  "packageManager": "pnpm@9.15.9",
  "engines": {
    "node": ">=20"
  },
  "scripts": {
    "dev": "turbo run dev",
    "build": "turbo run build",
    "lint": "turbo run lint",
    "start": "turbo run start",
    "admin-web": "pnpm --filter admin-web"
  },
  "devDependencies": {
    "turbo": "^2.5.0"
  }
}
```

```yaml
packages:
  - "apps/*"
  - "packages/*"
```

### `apps/auth-server/build.gradle.kts`

```kotlin
plugins {
	java
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.secureone"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
	// OpenSAML (used by Spring Security's SAML 2.0 service provider) is published only here.
	maven("https://build.shibboleth.net/maven/releases/") {
		content {
			includeGroupByRegex("org[.]opensaml.*")
			includeGroupByRegex("net[.]shibboleth.*")
		}
	}
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("io.micrometer:micrometer-registry-prometheus")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-authorization-server")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.security:spring-security-saml2-service-provider")
	implementation("org.opensaml:opensaml-saml-api")
	implementation("org.opensaml:opensaml-saml-impl")
	// Self-signed SAML SP signing certificate (already on the classpath through OpenSAML).
	implementation("org.bouncycastle:bcpkix-jdk18on:1.77")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("com.googlecode.libphonenumber:libphonenumber:8.13.50")
	implementation("software.amazon.awssdk:sns:2.29.51")
	implementation("dev.samstevens.totp:totp:1.7.1")
	implementation("com.yubico:webauthn-server-core:2.5.2")
	implementation("org.springframework.session:spring-session-data-redis")
	compileOnly("org.projectlombok:lombok")
	runtimeOnly("org.postgresql:postgresql")
	annotationProcessor("org.projectlombok:lombok")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-authorization-server-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.withType<JavaCompile> {
	options.compilerArgs.add("-parameters")
}
```

> The Shibboleth repository serves only OpenSAML (`org.opensaml`, `net.shibboleth`) for SAML SSO; builds need access to it (or a mirror). BouncyCastle `bcpkix` creates the self-signed SAML SP certificate.

| File | Purpose |
|------|---------|
| `application.yml` | Spring Boot + Flyway + Redis + scheduler cron + Swagger |
| `.env.example` | Full production env var catalog |
| `.env.local.example` | admin-web → auth-server URL + dev Basic auth |
| `build.gradle.kts` | JDK 25 toolchain, Flyway, Spring Authorization Server, Spring Security SAML2 SP (OpenSAML 5), WebAuthn, TOTP, AWS SNS |

---

## Quick start (Windows)From the repo root, one script starts Docker, auth-server, and admin-web:

```powershell
.\scripts\start-all.ps1
```

| Flag | Purpose |
|------|---------|
| `-SkipDocker` | Postgres/Redis/MailHog already running elsewhere |
| `-SkipInstall` | Skip `pnpm install` |
| `-NoWait` | Do not wait for HTTP health checks |

**Stop everything:**

```powershell
.\scripts\stop-all.ps1
```

**Fresh empty database** (wipes Docker volume, restarts stack):

```powershell
.\scripts\reset-database.ps1
```

Logs: `.local/logs/` Â· PIDs: `.local/pids/`

---

## Manual setup

### 1. Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 21+ | auth-server |
| Node.js | 20+ | admin-web |
| pnpm | via corepack | Monorepo JS (`pnpm install` at root) |
| Docker + Compose | latest | Postgres, Redis, MailHog |

### 2. Start infrastructure

See [Docker & Compose files](#docker--compose-files-reference) for the full `deploy/docker-compose.yml` and Dockerfiles.

```bash
docker compose -f deploy/docker-compose.yml up -d
```

Containers: `secureone-postgres`, `secureone-redis`, `secureone-mailhog`.

Default DB credentials: user **`secureone`**, password **`secureone`**, database **`secureone`**.

### 3. Configure auth-server

Set environment variables or rely on defaults in `apps/auth-server/src/main/resources/application.yml`:

```dotenv
# Database
SECUREONE_DB_URL=jdbc:postgresql://localhost:5432/secureone?currentSchema=platform
SECUREONE_DB_USERNAME=secureone
SECUREONE_DB_PASSWORD=secureone

# Cache
SECUREONE_REDIS_URL=redis://localhost:6379

# OIDC / public URLs
SECUREONE_ISSUER_URL=http://localhost:9000
SECUREONE_PUBLIC_BASE_URL=http://localhost:9000
SECUREONE_ADMIN_WEB_URL=http://localhost:9000/admin
SECUREONE_PORT=9000

# Break-glass platform account (console sign-in with empty tenant slug; see doc 25)
SECUREONE_DEV_USER=admin
SECUREONE_DEV_PASSWORD=admin
# Production: off unless in an incident; password must be a bcrypt hash
SECUREONE_BREAK_GLASS_ENABLED=false

# Console sign-in and sessions
# Console MFA and API docs follow SECUREONE_ENV: production = MFA on, Swagger off; development = the opposite.
# Set these only to override that default.
# SECUREONE_ADMIN_MFA_REQUIRED=true
# SECUREONE_SWAGGER_ENABLED=false
SECUREONE_ADMIN_SESSION_IDLE=30m
SECUREONE_ADMIN_SESSION_MAX=8h
SECUREONE_BREAK_GLASS_SESSION_MAX=1h

# Scheduled jobs (optional overrides)
SECUREONE_PASSWORD_EXPIRY_SCHEDULER=true
SECUREONE_PASSWORD_EXPIRY_CRON=0 0 */6 * * *

# Email (dev â€” MailHog)
SECUREONE_SMTP_HOST=localhost
SECUREONE_SMTP_PORT=1025
SECUREONE_MAIL_LOG_WHEN_UNAVAILABLE=true
```

### 4. Start auth-server

```bash
cd apps/auth-server
./gradlew bootRun          # Windows: .\gradlew bootRun
```

On startup:

1. **Flyway** applies pending platform migrations (see [Migrations](#database-migrations-flyway) below).
2. **Bootstrap beans** re-seed catalog rows if tables were truncated (see [Startup bootstrap](#startup-bootstrap)).
3. **Schedulers** register if enabled (see [Scheduled jobs](#scheduled-jobs)).

Verify: `GET http://localhost:9000/api/info` â†’ `"status":"UP"`.

### 5. Configure and start admin-web

```bash
cd apps/admin-web
cp .env.local.example .env.local   # if missing
npm install                        # or pnpm --filter admin-web install from repo root
npm run dev                        # listens on :3001 (see package.json)
```

`apps/admin-web/.env.local`:

```dotenv
SECUREONE_PUBLIC_BASE_URL=http://localhost:9000
SECUREONE_DEV_USER=admin
SECUREONE_DEV_PASSWORD=admin
```

Open **http://localhost:9000/admin** (or http://localhost:3001/admin directly) · sign in **`admin` / `admin`** (no tenant slug).
---

## Database migrations (Flyway)

SecureOne uses **two migration paths**:

| Path | When | Where |
|------|------|-------|
| **Platform migrations** | Before every auth-server start | `db/migration/postgresql/` via Flyway |
| **Application schema migrations** | Register an application; upgrade existing ones at start | `db/migration/application-schema/` |

The platform schema starts from a single `V1__baseline.sql` (complete schema, row-level security, the `secureone_app` role and built-in reference rows; no sample data). Later changes are `V2__…`, `V3__…`. History: `platform.flyway_schema_history`.

`SECUREONE_DB_MIGRATE` picks the mode: `auto` (default, migrate then start), `only` (migrate and exit, for an upgrade job) or `validate` (refuse to start while anything is pending).

See [18 — Flyway migration files](18-flyway-migration-files.md) for the files and how to add one, and [17 — Database schemas & tables](17-database-schemas-and-tables.md) for the tables.

### Start fresh (development)

```powershell
.\scripts\reset-database.ps1
```

Drops the local Postgres volume and starts again; the setup wizard then creates the first operator.

---

## Startup bootstrap

These run on **`ApplicationReadyEvent`** â€” they are **not** cron jobs. They repair or seed data after migrations or manual table truncation.

| Component | Class | What it does |
|-----------|-------|--------------|
| Platform settings | `PlatformSettingsBootstrap` | Ensures default `platform_setting` rows (SMTP, auth methods, password policy, feature flags, â€¦) |
| Catalog repair | `CatalogRepairBootstrap` | Re-seeds tenant RBAC catalog + application default roles/permissions for every existing tenant/app |
| Tenant create | `TenantRbacBootstrapService` | Seeds tenant-scoped permissions/roles when a **new tenant** is created |
| Application create | `RbacBootstrapService` | Seeds default roles/permissions when a **new application** is registered |
| Auth catalog repair | `AuthCatalogRepairService` | Marks shipped sign-in methods as implemented (e.g. `m_oidc`, `m_saml`) without re-running Flyway |
| Signing keys | `OAuth2SigningKeyService`, `SamlSpCredentialService` | Load keys from env, else from `platform_setting` (encrypted); create once if missing, under a Redis lock so replicas agree. Older plaintext keys are re-encrypted in place |
| Encryption key check | `SecretProtector` | Fails startup on a malformed `SECUREONE_ENCRYPTION_KEY`; warns when it is not set |

**Scheduling is separate** â€” see below. Bootstrap only runs once per process start (and on explicit create flows).

---

## Scheduled jobs

Scheduling is enabled on the auth-server main class:

```java
@EnableScheduling
public class AuthServerApplication { â€¦ }
```

### Password expiry notifications (`PasswordExpiryScheduler`)

The only production scheduled job today. Sends **password expiring soon** and **password expired â†’ reset** emails based on `user_credential.expires_at`.

| Item | Detail |
|------|--------|
| Class | `com.secureone.auth.account.PasswordExpiryScheduler` |
| Service | `PasswordExpiryService.processScheduledNotifications()` |
| Default cron | `0 0 */6 * * *` â€” every 6 hours (00:00, 06:00, 12:00, 18:00 server time) |
| Spring cron | **6 fields:** `second minute hour day-of-month month day-of-week` |

**Configuration** (`application.yml`):

```yaml
secureone:
  password-expiry:
    scheduler-enabled: true
    check-cron: "0 0 */6 * * *"
```

| Setting | Env variable | Default |
|---------|--------------|---------|
| Enable/disable job | `SECUREONE_PASSWORD_EXPIRY_SCHEDULER` | `true` |
| Cron expression | `SECUREONE_PASSWORD_EXPIRY_CRON` | `0 0 */6 * * *` |

**Disable in dev or tests:**

```dotenv
SECUREONE_PASSWORD_EXPIRY_SCHEDULER=false
```

**Example cron overrides:**

| Expression | Meaning |
|------------|---------|
| `0 0 */6 * * *` | Every 6 hours (default) |
| `0 0 9 * * *` | Daily at 09:00 |
| `0 */30 * * * *` | Every 30 minutes (testing only) |

There is **no admin UI** for the scheduler â€” configure via YAML or environment variables only.

**Logging:** search auth-server logs for `PasswordExpiryScheduler` or `PasswordExpiryService`.

Full behaviour (warning windows, idempotency, multi-replica): [10 â€” Settings governance Â§ Scheduled job](10-settings-governance.md#scheduled-job-passwordexpiryscheduler).

### Future schedulers

Additional background jobs (audit shipping, session cleanup, token rotation sweeps) are planned on the roadmap. New jobs should follow the same pattern: `@Component` + `@Scheduled` + `@ConditionalOnProperty` + documented env overrides.

---

## Maintenance scripts

| Script | Purpose |
|--------|---------|
| `scripts/start-all.ps1` | Docker + auth-server + admin-web |
| `scripts/stop-all.ps1` | Stop background services (`-KeepDocker` leaves Postgres running) |
| `scripts/reset-database.ps1` | `docker compose down -v` + fresh start |

---

## Health checks

| Check | Command / URL |
|-------|----------------|
| Auth server up | `GET http://localhost:9000/api/info` |
| First-run setup | While no platform operator exists, the log prints a setup code for `/admin/setup` (`SECUREONE_SETUP_CODE` fixes it for automated installs) |
| Actuator health | `GET http://localhost:9000/actuator/health` in development; in the image it moves to the management port `9001` (`SECUREONE_MANAGEMENT_PORT`) |
| OIDC discovery | `GET http://localhost:9000/.well-known/openid-configuration` |
| Admin UI | `http://localhost:9000/admin` |
| MailHog | `http://localhost:8025` |
| Flyway history | `SELECT version, description, success FROM platform.flyway_schema_history ORDER BY installed_rank;` |
| App schemas | `SELECT name, schema_name FROM platform.application;` |

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| Flyway fails on boot | Partial migration, duplicate objects | Check `flyway_schema_history`; dev: `reset-database.ps1` |
| Empty permissions/roles | Catalog tables truncated | Restart auth-server (the startup bootstrap re-seeds the catalog) |
| auth-server won't start (port) | Process still on `:9000` | `stop-all.ps1` or kill Java process |
| admin-web can't reach API | Wrong `SECUREONE_PUBLIC_BASE_URL` / `SECUREONE_AUTH_SERVER_INTERNAL_URL` | Set to `http://localhost:9000` in `apps/admin-web/.env.local` |
| http://localhost:9000/admin returns 404 | auth-server started without `SECUREONE_CONSOLE_UPSTREAM_URL` | Use `scripts/start-all.ps1`, or set it to `http://localhost:3001` |
| No expiry emails | Scheduler disabled or SMTP off | Check `SECUREONE_PASSWORD_EXPIRY_SCHEDULER`, MailHog, Settings â†’ Notifications |
| Scheduler logs nothing | No credentials with `expires_at` set | Set password policy expiry in platform/app settings |

---

## Production notes (summary)

- Run **one auth-server instance** with schedulers enabled, or accept duplicate cron ticks across replicas (password expiry job is idempotent per credential).
- Set `SECUREONE_MAIL_LOG_WHEN_UNAVAILABLE=false` and configure real SMTP.
- Set `SECUREONE_ENCRYPTION_KEY`, `SECUREONE_JWK_PRIVATE_KEY_PEM` and (for SAML) `SECUREONE_SAML_SP_KEY_PEM` / `_CERT_PEM` from a secrets manager; see [24 — Deployment](24-deployment-image-env-and-enterprise.md#a2--generate-secrets).
- Several replicas are fine for sign-in flows: SAML request/logout state, device-code polling, sessions and rate limits are in Redis.
- Reverse proxy: forward `X-Forwarded-*`, and do not add CORS rules on `/api/v1/auth/federation/saml/acs/*` and `/saml/slo/*` (IdP form POSTs).
- Use managed Postgres + Redis, TLS termination, secrets manager for `SECUREONE_DEV_PASSWORD` replacement.
- Pin Flyway migrations in release artifacts; run `flywayMigrate` in deploy pipeline before or during rolling restarts.

See [07 â€” Security](07-security.md) and [03 â€” Architecture Â§ Deployment](03-architecture.md).

---

## Related documents

| Topic | Document |
|-------|----------|
| Short install walkthrough | [09 â€” Installation](09-installation.md) |
| Flyway layout & schemas | [06 â€” Database Strategy](06-database.md) |
| Application schema provision | [15 â€” Applications & OAuth clients](15-applications-and-oauth-clients.md) |
| Password expiry scheduler detail | [10 â€” Settings governance](10-settings-governance.md) |
| Docker Compose & image | `deploy/docker-compose.yml`, `deploy/docker-compose.quickstart.yml`, `deploy/Dockerfile`, `.dockerignore` |
| Configuration files (full) | `application.yml`, `.env.example`, `.env.local.example`, `build.gradle.kts`, `package.json`, `next.config.ts` |
| Flyway migration files | [18 — Flyway migration files](18-flyway-migration-files.md) |
| Schemas, tables & FK map | [17 — Database schemas & tables](17-database-schemas-and-tables.md) |
| auth-server config | [apps/auth-server/README.md](../apps/auth-server/README.md) |
| admin-web config | [apps/admin-web/README.md](../apps/admin-web/README.md) |
