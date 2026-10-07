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
| **auth-server** | `9000` | OAuth/OIDC, admin APIs, Flyway, schedulers |
| **admin-web** | `3001` | Admin console + SecureOne Confluence |

---

## Docker & Compose files (reference)

Source files live under **`deploy/`**. In local dev, **`docker-compose.yml` runs only infrastructure** (Postgres, Redis, MailHog). **auth-server** and **admin-web** are started on the host by `scripts/start-all.ps1` (Gradle + Node). The Dockerfiles below are for building app images (CI/production).

```
deploy/
â”œâ”€â”€ docker-compose.yml       â† dev: Postgres, Redis, MailHog
â”œâ”€â”€ Dockerfile.auth-server   â† build auth-server JAR image
â””â”€â”€ Dockerfile.admin-web     â† build admin-web image (bundles docs/ for Confluence)
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

### `deploy/Dockerfile.auth-server`

Multi-stage build: Gradle `bootJar` â†’ JRE runtime on port **9000**. Flyway runs on container start (same as `bootRun`).

```dockerfile
# SecureOne auth-server â€” production-style image
# Build (from repo root):
#   docker build -f deploy/Dockerfile.auth-server -t secureone/auth-server apps/auth-server

FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew ./
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY src src
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN adduser -D -h /app secureone
USER secureone
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
EXPOSE 9000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

**Build & run** (after compose is up):

```bash
docker build -f deploy/Dockerfile.auth-server -t secureone/auth-server apps/auth-server

docker run --rm -p 9000:9000 \
  -e SECUREONE_DB_URL=jdbc:postgresql://host.docker.internal:5432/secureone?currentSchema=platform \
  -e SECUREONE_DB_USERNAME=secureone \
  -e SECUREONE_DB_PASSWORD=secureone \
  -e SECUREONE_REDIS_URL=redis://host.docker.internal:6379 \
  -e SECUREONE_ISSUER_URL=http://localhost:9000 \
  secureone/auth-server
```

> On Linux use `host.docker.internal` via `--add-host=host.docker.internal:host-gateway` or point at the compose service network.

### `deploy/Dockerfile.admin-web`

Multi-stage Next.js build. Copies **`docs/`** into the image so SecureOne Confluence can read markdown at `/app/docs` (matches `../../docs` from the app working directory).

```dockerfile
# SecureOne admin-web â€” production-style image (includes docs/ for Confluence)
# Build (from repo root):
#   docker build -f deploy/Dockerfile.admin-web -t secureone/admin-web .

FROM node:20-alpine AS build
WORKDIR /app
COPY apps/admin-web/package.json apps/admin-web/package-lock.json ./apps/admin-web/
WORKDIR /app/apps/admin-web
RUN npm ci
COPY apps/admin-web ./
COPY docs /app/docs
RUN npm run build

FROM node:20-alpine AS run
WORKDIR /app/apps/admin-web
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
RUN adduser -D -h /app secureone
USER secureone
COPY --from=build /app/apps/admin-web/package.json ./
COPY --from=build /app/apps/admin-web/package-lock.json ./
COPY --from=build /app/apps/admin-web/node_modules ./node_modules
COPY --from=build /app/apps/admin-web/.next ./.next
COPY --from=build /app/apps/admin-web/public ./public
COPY --from=build /app/docs /app/docs
EXPOSE 3001
CMD ["npm", "start", "--", "-p", "3001"]
```

**Build & run:**

```bash
docker build -f deploy/Dockerfile.admin-web -t secureone/admin-web .

docker run --rm -p 3001:3001 \
  -e NEXT_PUBLIC_AUTH_SERVER_URL=http://host.docker.internal:9000 \
  -e SECUREONE_DEV_USER=admin \
  -e SECUREONE_DEV_PASSWORD=admin \
  secureone/admin-web
```

### Dev vs containerized apps

| Mode | Infrastructure | auth-server | admin-web |
|------|----------------|-------------|-----------|
| **Local dev (default)** | Docker Compose | Host (`gradlew bootRun`) | Host (`npm run dev` / `start-all.ps1`) |
| **Container images** | Docker Compose (or managed DB/Redis) | `Dockerfile.auth-server` | `Dockerfile.admin-web` |
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
    ├── package.json                          ← Next.js scripts (dev :3001)
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
  admin-web-url: ${SECUREONE_ADMIN_WEB_URL:http://localhost:3001}
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

# ---- Secret encryption (MFA seeds, client secrets) ----
# PROD: use a KMS-managed key, NOT a static value
SECUREONE_ENCRYPTION_KEY=base64:replace-with-32-byte-key

# ---- Email (SMTP) ----
# Dev default: MailHog via docker compose (localhost:1025, UI http://localhost:8025).
# Real Gmail: use an App Password (Google Account â†’ Security â†’ 2-Step Verification â†’ App passwords).
SECUREONE_SMTP_HOST=smtp.gmail.com
SECUREONE_SMTP_PORT=587
SECUREONE_SMTP_USERNAME=your@gmail.com
SECUREONE_SMTP_PASSWORD=your-app-password
SECUREONE_SMTP_AUTH=true
SECUREONE_SMTP_STARTTLS=true
SECUREONE_SMTP_SSL_TRUST=smtp.gmail.com
# Set false when using real SMTP so failures surface instead of logging to console
SECUREONE_MAIL_LOG_WHEN_UNAVAILABLE=true

# ---- First-run bootstrap super-admin ----
SECUREONE_BOOTSTRAP_ADMIN_EMAIL=admin@example.com
SECUREONE_BOOTSTRAP_ADMIN_PASSWORD=change-me-now
```

### `apps/admin-web/.env.local.example`

Copy to `apps/admin-web/.env.local` for local dev.

```dotenv
# Base URL of the SecureOne authorization server (apps/auth-server).
# Copy this file to .env.local and adjust as needed.
NEXT_PUBLIC_AUTH_SERVER_URL=http://localhost:9000

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
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-authorization-server")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
	implementation("org.flywaydb:flyway-database-postgresql")
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
```

| File | Purpose |
|------|---------|
| `application.yml` | Spring Boot + Flyway + Redis + scheduler cron + Swagger |
| `.env.example` | Full production env var catalog |
| `.env.local.example` | admin-web → auth-server URL + dev Basic auth |
| `build.gradle.kts` | JDK 25 toolchain, Flyway, Spring Authorization Server |

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
SECUREONE_ADMIN_WEB_URL=http://localhost:3001
SECUREONE_PORT=9000

# Break-glass platform account (console sign-in with empty tenant slug; see doc 25)
SECUREONE_DEV_USER=admin
SECUREONE_DEV_PASSWORD=admin
# Production: off unless in an incident; password must be a bcrypt hash
SECUREONE_BREAK_GLASS_ENABLED=false

# Console sign-in and sessions
SECUREONE_ADMIN_MFA_REQUIRED=true
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
NEXT_PUBLIC_AUTH_SERVER_URL=http://localhost:9000
SECUREONE_DEV_USER=admin
SECUREONE_DEV_PASSWORD=admin
```

Open **http://localhost:3001** Â· sign in **`admin` / `admin`** (no tenant slug).
---

## Database migrations (Flyway)

SecureOne uses **two migration paths**:

| Path | When | Where |
|------|------|-------|
| **Platform migrations** | Every auth-server boot | `db/migration/postgresql/` via Flyway |
| **Application schema DDL** | Register or isolate an application | `db/migration/application-schema/V1__app_core.sql` at runtime |

`
apps/auth-server/src/main/resources/db/migration/
├── postgresql/                         ← Flyway versioned + repeatable scripts
│   ├── V1__….sql … V45__platform_schema_oauth_client.sql
│   └── R__z_repair_catalog.sql         ← idempotent catalog repair (repeatable)
└── application-schema/
    └── V1__app_core.sql                ← template DDL (not Flyway-versioned per app)
`

**Flyway config** (from `application.yml`): `spring.flyway.enabled: true`, `locations: classpath:db/migration/postgresql`, `hibernate.ddl-auto: none`.

**History table:** `public.flyway_schema_history` — stays in `public`, not moved to `platform`.

**Run migrations without starting the full app** (optional):

`ash
cd apps/auth-server
./gradlew flywayMigrate
`

> `bootRun` already migrates automatically.

### Migration index (V1–V45 + repeatable)

| Version | File |
|---------|------|
| V1 | `V1__core_schema.sql` | core schema |
| V10 | `V10__app_settings_exposure.sql` | app settings exposure |
| V11 | `V11__rbac_permissions_seed.sql` | rbac permissions seed |
| V12 | `V12__default_permissions_all_apps.sql` | default permissions all apps |
| V13 | `V13__user_directory_import_export.sql` | user directory import export |
| V14 | `V14__public_application_manifest.sql` | public application manifest |
| V15 | `V15__token_policy.sql` | token policy |
| V16 | `V16__signup_default_member_role.sql` | signup default member role |
| V17 | `V17__external_test_application.sql` | external test application |
| V18 | `V18__application_logs.sql` | application logs |
| V19 | `V19__smtp_email_templates.sql` | smtp email templates |
| V2 | `V2__seed_dev_data.sql` | seed dev data |
| V20 | `V20__user_credential_one_current.sql` | user credential one current |
| V21 | `V21__restore_dev_tenant_admin.sql` | restore dev tenant admin |
| V22 | `V22__dev_reset_acme_operator_password.sql` | dev reset acme operator password |
| V23 | `V23__admin_console_access.sql` | admin console access |
| V24 | `V24__dedupe_admin_console_access.sql` | dedupe admin console access |
| V25 | `V25__remove_dev_seed_data.sql` | remove dev seed data |
| V26 | `V26__purge_dev_sample_rows.sql` | purge dev sample rows |
| V27 | `V27__finalize_dev_data_removal.sql` | finalize dev data removal |
| V28 | `V28__seed_default_roles_all_apps.sql` | seed default roles all apps |
| V29 | `V29__tenant_user_roster.sql` | tenant user roster |
| V3 | `V3__platform_settings_and_app_config.sql` | platform settings and app config |
| V30 | `V30__repair_invited_application_members.sql` | repair invited application members |
| V31 | `V31__tenant_roster_metadata.sql` | tenant roster metadata |
| V32 | `V32__admin_console_feature_overrides.sql` | admin console feature overrides |
| V33 | `V33__cleanup_orphan_console_roster_rows.sql` | cleanup orphan console roster rows |
| V34 | `V34__tenant_rbac.sql` | tenant rbac |
| V35 | `V35__seed_tenant_rbac_existing.sql` | seed tenant rbac existing |
| V36 | `V36__tenant_console_role_features.sql` | tenant console role features |
| V37 | `V37__mark_tenant_rbac_system_roles.sql` | mark tenant rbac system roles |
| V38 | `V38__platform_notifications_independent.sql` | platform notifications independent |
| V39 | `V39__user_credential_expires_at.sql` | user credential expires at |
| V4 | `V4__seed_roles_and_admin_assignments.sql` | seed roles and admin assignments |
| V40 | `V40__password_expiry_notifications.sql` | password expiry notifications |
| V41 | `V41__trim_feature_flag_catalog.sql` | trim feature flag catalog |
| V42 | `V42__application_rbac_groups.sql` | application rbac groups |
| V43 | `V43__tenant_console_groups.sql` | tenant console groups |
| V44 | `V44__reseed_platform_settings.sql` | reseed platform settings |
| V45 | `V45__platform_schema_oauth_client.sql` | platform schema oauth client |
| V5 | `V5__email_tokens_and_user_notifications.sql` | email tokens and user notifications |
| V6 | `V6__auth_settings_mfa_and_magic_link.sql` | auth settings mfa and magic link |
| V7 | `V7__dev_password_credentials.sql` | dev password credentials |
| V8 | `V8__application_scoped_users_and_settings.sql` | application scoped users and settings |
| V9 | `V9__application_scope_audit_sessions_access.sql` | application scope audit sessions access |
| R (repeatable) | `R__z_repair_catalog.sql` | Idempotent catalog repair |
| App template | `application-schema/V1__app_core.sql` | Per-app schema DDL (runtime) |

### Application schema provisioning

When you **register an application** or **isolate** a legacy app, `ApplicationSchemaProvisioner` executes `V1__app_core.sql` inside a new PostgreSQL schema. Tracked in `platform.application_schema.flyway_version` as `V1__app_core`, not in `flyway_schema_history`.

See [15 — Applications & OAuth clients](15-applications-and-oauth-clients.md).

### After pulling new migrations

1. Stop auth-server (or let it restart).
2. Start auth-server — Flyway applies new `V*.sql` scripts.
3. If migration fails, inspect `public.flyway_schema_history` and auth-server logs.
4. For a broken dev DB, use `.\scripts\reset-database.ps1` or repair manually.

### Catalog repair without full reset

```powershell
.\scripts\reseed-catalog.ps1
```

Runs `R__z_repair_catalog.sql` against Postgres, then restart auth-server.

### Related database documents

| Topic | Document |
|-------|----------|
| Schemas, tables & connections | [17 — Database schemas & tables](17-database-schemas-and-tables.md) |
| Full Flyway SQL (V1–V45) | [18 — Flyway migration files](18-flyway-migration-files.md) |

---

## Startup bootstrap

These run on **`ApplicationReadyEvent`** â€” they are **not** cron jobs. They repair or seed data after migrations or manual table truncation.

| Component | Class | What it does |
|-----------|-------|--------------|
| Platform settings | `PlatformSettingsBootstrap` | Ensures default `platform_setting` rows (SMTP, auth methods, password policy, feature flags, â€¦) |
| Catalog repair | `CatalogRepairBootstrap` | Re-seeds tenant RBAC catalog + application default roles/permissions for every existing tenant/app |
| Tenant create | `TenantRbacBootstrapService` | Seeds tenant-scoped permissions/roles when a **new tenant** is created |
| Application create | `RbacBootstrapService` | Seeds default roles/permissions when a **new application** is registered |

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
| `scripts/reseed-catalog.ps1` | Re-apply `R__z_repair_catalog.sql` without wiping users |
| `scripts/test-auth-e2e.ps1` | Auth smoke tests |
| `scripts/test-external-client.ps1` | External test client OAuth flow |
| `scripts/test-oauth-token.ps1` | Token endpoint check |

---

## Health checks

| Check | Command / URL |
|-------|----------------|
| Auth server up | `GET http://localhost:9000/api/info` |
| Actuator health | `GET http://localhost:9000/actuator/health` |
| OIDC discovery | `GET http://localhost:9000/.well-known/openid-configuration` |
| Admin UI | `http://localhost:3001` |
| MailHog | `http://localhost:8025` |
| Flyway history | `SELECT version, description, success FROM public.flyway_schema_history ORDER BY installed_rank;` |
| App schemas | `SELECT name, schema_name FROM platform.application;` |

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| Flyway fails on boot | Partial migration, duplicate objects | Check `flyway_schema_history`; dev: `reset-database.ps1` |
| Empty permissions/roles | Catalog tables truncated | `reseed-catalog.ps1` + restart auth-server |
| auth-server won't start (port) | Process still on `:9000` | `stop-all.ps1` or kill Java process |
| admin-web can't reach API | Wrong `NEXT_PUBLIC_AUTH_SERVER_URL` | Set to `http://localhost:9000` in `.env.local` |
| No expiry emails | Scheduler disabled or SMTP off | Check `SECUREONE_PASSWORD_EXPIRY_SCHEDULER`, MailHog, Settings â†’ Notifications |
| Scheduler logs nothing | No credentials with `expires_at` set | Set password policy expiry in platform/app settings |

---

## Production notes (summary)

- Run **one auth-server instance** with schedulers enabled, or accept duplicate cron ticks across replicas (password expiry job is idempotent per credential).
- Set `SECUREONE_MAIL_LOG_WHEN_UNAVAILABLE=false` and configure real SMTP.
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
| Docker Compose & Dockerfiles | `deploy/docker-compose.yml`, `deploy/Dockerfile.auth-server`, `deploy/Dockerfile.admin-web` |
| Configuration files (full) | `application.yml`, `.env.example`, `.env.local.example`, `build.gradle.kts`, `package.json`, `next.config.ts` |
| Full Flyway SQL (V1–V45) | [18 — Flyway migration files](18-flyway-migration-files.md) |
| Schemas, tables & FK map | [17 — Database schemas & tables](17-database-schemas-and-tables.md) |
| auth-server config | [apps/auth-server/README.md](../apps/auth-server/README.md) |
| admin-web config | [apps/admin-web/README.md](../apps/admin-web/README.md) |
