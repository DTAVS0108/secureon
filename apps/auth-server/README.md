# auth-server

Spring Boot application: **Spring Authorization Server** (OAuth 2.1 / OIDC), admin APIs, account APIs, and Flyway-managed PostgreSQL schema.

## Prerequisites

- JDK 21+
- PostgreSQL 16+ and Redis 7+ (see `deploy/docker-compose.yml`)

## Run

```bash
./gradlew bootRun          # Windows: .\gradlew bootRun
```

- **http://localhost:9000** — API + OAuth endpoints
- **http://localhost:9000/docs** — Swagger UI
- **GET /api/info** — service metadata (`confluence`, `confluenceApi`, `confluenceDiscovery` URLs)

Dev admin API auth: HTTP Basic `admin` / `admin` (`SECUREONE_DEV_USER` / `SECUREONE_DEV_PASSWORD`).

## Configuration

`src/main/resources/application.yml` — key settings:

| Setting | Purpose |
|---------|---------|
| `spring.datasource.url` | JDBC with `currentSchema=platform` |
| `spring.flyway.locations` | `classpath:db/migration/postgresql` |
| `secureone.issuer` | OIDC issuer URL |
| `secureone.admin-web-url` | Base URL for Confluence links in `/api/info` and `/api/v1/confluence` (default `http://localhost:3001`) |
| `secureone.otp.pepper` | Server secret for OTP code hashing (`SECUREONE_OTP_PEPPER`) |

Environment overrides: `SECUREONE_DB_URL`, `SECUREONE_REDIS_URL`, `SECUREONE_ISSUER_URL`, `SECUREONE_ADMIN_WEB_URL`, `SECUREONE_PORT`, `SECUREONE_OTP_PEPPER`.

Sessions and OTP challenges use **Redis** (`spring.session.store-type: redis`).

## Database

### Platform migrations (Flyway)

```
src/main/resources/db/migration/postgresql/
├── V1__…sql … V45__platform_schema_oauth_client.sql
├── V46__otp_verification_and_phone.sql
└── R__z_repair_catalog.sql
```

History table: **`public.flyway_schema_history`** (not in `platform`).

### Per-application schemas (runtime)

```
src/main/resources/db/migration/application-schema/V1__app_core.sql
```

Provisioned by `ApplicationSchemaProvisioner` when an application is registered or isolated.

See [Database strategy](../../docs/06-database.md) and [Applications & OAuth clients](../../docs/15-applications-and-oauth-clients.md).

## Key packages

| Package | Responsibility |
|---------|----------------|
| `com.secureone.auth.config` | Security, `ApplicationSchemaFilter`, OpenAPI |
| `com.secureone.auth.application` | Application entity, schema provision/routing/migrate |
| `com.secureone.auth.admin.application` | Application product admin API |
| `com.secureone.auth.admin.oauth` | OAuth client admin API |
| `com.secureone.auth.oauth` | `RegisteredClientRepository` from `platform.oauth_client` |

## Build & test

```bash
./gradlew compileJava test
./gradlew flywayMigrate    # optional; bootRun migrates automatically
```

## Related docs

- [Installation](../../docs/09-installation.md)
- [Environment & operations](../../docs/16-environment-setup-and-operations.md)
- [Verification & OTP](../../docs/19-verification-and-otp.md)
- [API documentation](../../docs/12-api-documentation.md)
- [SecureOne Confluence](../../docs/README.md#secureone-confluence-in-app-ui) (in-app docs on admin-web)
- [ADR 0010 — Platform and app schemas](../../docs/decisions/0010-platform-and-app-schemas.md)
