---
title: Database strategy
summary: Platform and application schemas
order: 2
audience: internal
---

# Database strategy

## Goal

Run on **PostgreSQL**, configured at install time, while keeping the data layer **abstracted behind repository interfaces** so a different engine could be added later without rewriting domain logic.

> **Current decision: PostgreSQL only.** See [ADR 0002](decisions/database-postgresql.md).

## Physical layout (implemented)

```mermaid
flowchart TB
    subgraph db["PostgreSQL database: secureone"]
        direction TB
        subgraph platform["platform schema"]
            p1["flyway_schema_history"]
            p2["tenant · application · oauth_client · application_schema"]
            p3["user_account · user_credential · email_token · mfa_factor"]
            p4["tenant_rbac* · admin_console_* · platform_setting · audit_log …"]
            p5["legacy application IAM<br/>(application.schema_name IS NULL)"]
        end
        subgraph app["one schema per isolated application ({app_slug})"]
            a1["permission · role · role_permission · role_composite · user_role"]
            a2["user_application · application_setting"]
            a3["rbac_group · rbac_group_role · rbac_group_member"]
            a4["application_log"]
        end
    end
    p2 -. "schema_name" .-> app
```

Migration **V45** moves shared tables from `public` → `platform` and introduces `oauth_client` + `application.schema_name`. Per-app DDL is applied at runtime from `db/migration/application-schema/V1__app_core.sql`.

Full guide: [Applications & OAuth clients](../integrate/applications-and-oauth-clients.md) · ADR: [0010](decisions/platform-and-app-schemas.md).

## Why relational, why Postgres

IAM data is deeply relational. PostgreSQL adds:

- **Row-Level Security (RLS)** — tenant-isolation backstop (where enabled).
- **`jsonb`** — indexable flexible metadata.
- **Schemas** — isolate per-application IAM without separate databases.

## Layering: ORM **and** Repository

> Editable source: [`diagrams/data-layer-layering.drawio`](../diagrams/data-layer-layering.drawio)

```mermaid
flowchart TB
    DOM["Domain / Services"] -- "depends on" --> PORT["Repository Interfaces (ports)"]
    PORT -- "implemented by" --> IMPL["JPA / Hibernate Repository Impls"]
    IMPL --> HIB["Hibernate + search_path routing"]
    HIB --> PG[("PostgreSQL<br/>platform + app schemas")]
```

- **ORM = JPA/Hibernate** on PostgreSQL.
- **Repository pattern** — domain depends on interfaces, not Hibernate.
- **Schema routing** — `ApplicationSchemaJpaTransactionManager` sets `SET LOCAL search_path` at transaction begin from `ApplicationSchemaContext` (set by `ApplicationSchemaFilter` for app-scoped requests).

### Connection defaults

```dotenv
SECUREONE_DB_URL=jdbc:postgresql://localhost:5432/secureone?currentSchema=platform
```

- JDBC `currentSchema=platform` — default connection schema for platform tables.
- **`hibernate.default_schema` is not set** — app-scoped entities rely on `search_path` (`{app_schema}, platform`).

## Conventions (enforced)

| Concern | Rule |
|---|---|
| IDs | UUID, `@JdbcTypeCode(SqlTypes.UUID)` |
| Enums | `VARCHAR` + `@Enumerated(STRING)` |
| JSON | `@JdbcTypeCode(SqlTypes.JSON)` → `jsonb` |
| Timestamps | UTC (`timestamptz`) |
| Email | lowercased in app |
| Tenant isolation | `tenant_id` + RLS (where applied) |
| App IAM isolation | dedicated schema + `search_path` |

See [Data Model](data-model.md).

## Migrations — Flyway

Under `apps/auth-server/src/main/resources/db/migration/`:

| Folder or file | What it is |
|---|---|
| `postgresql/` | Platform migrations: `V1__baseline.sql`, then `V2__…`; `R__z_repair_catalog.sql` (repeatable) |
| `application-schema/V1__app_core.sql` … | The per-application schema, applied when an application is registered |

- **Platform:** `V1__baseline.sql` creates the schema; later versioned scripts run before auth-server starts; history in **`platform.flyway_schema_history`**.
- **Per-app:** `ApplicationSchemaProvisioner` runs `CREATE SCHEMA` + `V1__app_core.sql` inside the new schema; recorded in `platform.application_schema`.

```bash
cd apps/auth-server && ./gradlew flywayMigrate   # optional; bootRun also migrates
```

Files and how to add a migration: [Flyway migration files](migrations.md). Table & schema map: [Database schemas & tables](database-schemas.md).

### `flyway_schema_history`

Flyway’s bookkeeping table. Lists which SQL scripts have run (`version`, `description`, `success`). It lives in the `platform` schema.

## Tenant isolation (defense in depth)

> Editable source: [`diagrams/tenant-isolation.drawio`](../diagrams/tenant-isolation.drawio)

1. **Mandatory `tenant_id`** on tenant-scoped tables.
2. **Hibernate `@Filter`** (where enabled) from authenticated tenant context.
3. **PostgreSQL RLS** (where policies exist) as a DB backstop.
4. **CI cross-tenant isolation tests** (target).

**Application isolation** adds schema-level separation for IAM tables (in addition to `application_id` predicates).

## Connection management & scale

- **HikariCP** in Spring; **PgBouncer** at scale.
- With RLS or `SET LOCAL search_path`, use session-level pooling or reset per transaction.
- Read replicas for admin/audit reads; Redis for sessions and cache.

## Install-time configuration

```dotenv
SECUREONE_DB_URL=jdbc:postgresql://host:5432/secureone?currentSchema=platform
SECUREONE_DB_USERNAME=secureone
SECUREONE_DB_PASSWORD=...
```

See [Installation](../install/installation.md).
