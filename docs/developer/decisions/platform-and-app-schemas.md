---
title: Platform schema and per-application PostgreSQL schemas
order: 10
audience: internal
---

# Platform schema and per-application PostgreSQL schemas

## Status

Accepted (implemented)

## Context

SecureOne originally stored all tables in PostgreSQL `public`. As the platform grew, we needed:

1. A clear boundary between **shared platform data** (tenants, application registry, OAuth clients, identity) and **per-application IAM** (roles, permissions, app settings).
2. Separation of **application products** from **OAuth clients** (one product, many protocol clients).
3. A path to isolate legacy seeded applications without breaking platform/tenant features.

## Decision

1. Move shared tables from `public` → **`platform`** schema (migration `V45__platform_schema_oauth_client.sql`).
2. Keep **`public.flyway_schema_history`** only — Flyway history is not moved into `platform`.
3. Add **`platform.oauth_client`**; backfill from legacy `application.config` OAuth keys; strip those keys from config.
4. Add **`application.schema_name`** and **`platform.application_schema`** registry.
5. On new application create (or isolate), provision **`{slugified_schema}`** and run `application-schema/V1__app_core.sql` inside it.
6. Route app-scoped ORM access via **`SET LOCAL search_path`** at transaction begin (`ApplicationSchemaJpaTransactionManager`), driven by `ApplicationSchemaContext` from `ApplicationSchemaFilter`.
7. **Dual-mode:** `schema_name IS NULL` → legacy rows remain in `platform.*`; non-null → IAM in app schema.

## Consequences

### Positive

- OAuth clients are first-class, multi-client per application.
- New applications get isolated IAM without manual DDL.
- Legacy apps continue working until explicitly isolated.
- Platform settings, tenants, and OAuth registry stay in one predictable schema.

### Negative / trade-offs

- `search_path` must be set on the same connection as JPA (transaction-bound); filter-only `SET LOCAL` in a separate transaction was insufficient.
- Hibernate `default_schema` cannot be `platform` if `search_path` routing is to work — JDBC `currentSchema=platform` is used for default connection instead.
- Isolate is one-way (copy + delete); no automatic merge back.
- Application-schema DDL is a separate template file, not Flyway-versioned per app (tracked in `application_schema.flyway_version` as `V1__app_core`).

## References

- [Applications & OAuth clients](../../integrate/applications-and-oauth-clients.md)
- [Database strategy](../database.md)
- Migration: `V45__platform_schema_oauth_client.sql`
- Template: `db/migration/application-schema/V1__app_core.sql`
