# 18 — Flyway Migration Files

How the database schema is created and changed. Source files live under `apps/auth-server/src/main/resources/db/migration/`.

Related: [17 — Database schemas & tables](17-database-schemas-and-tables.md) · [06 — Database strategy](06-database.md) · [16 — Environment & operations](16-environment-setup-and-operations.md)

---

## Files

```
apps/auth-server/src/main/resources/db/migration/
├── postgresql/
│   ├── V1__baseline.sql            ← complete platform schema + built-in reference rows
│   └── R__z_repair_catalog.sql     ← idempotent catalog repair (repeatable)
└── application-schema/
    ├── V1__app_core.sql            ← per-application schema
    ├── V2__user_application_created_at.sql
    └── V3__two_step_mode.sql
```

| Path | Mechanism | History |
|------|-----------|---------|
| `postgresql/V*.sql` | Flyway, before the server starts (`DatabaseUpgrade`) | `platform.flyway_schema_history` |
| `postgresql/R__*.sql` | Flyway repeatable: re-runs when its checksum changes | same |
| `application-schema/V*.sql` | `ApplicationSchemaProvisioner` when an application is registered; `ApplicationSchemaUpgrader` for existing ones | `platform.application_schema.flyway_version` |

**Flyway config** (`application.yml`): `spring.flyway.locations: classpath:db/migration/postgresql`. Flyway connects as the schema owner (`SECUREONE_DB_MIGRATION_USERNAME`, defaulting to `SECUREONE_DB_USERNAME`).

---

## `V1__baseline.sql`

One script creates the whole platform schema on an empty database:

- the `platform` schema with every table, index, constraint, function and trigger;
- row-level security policies (`secureone_tenant_matches`, `secureone_rls_bypass`);
- the append-only trigger on `audit_log`;
- the `secureone_app` role (no login, no `BYPASSRLS`) with data privileges and default privileges for objects created later;
- built-in reference rows only:

| Table | Rows |
|-------|------|
| `tenant` | The system `platform` tenant (platform operators) |
| `tenant_role` | Tenant Administrator, Application Operator, Tenant Auditor |
| `tenant_permission` / `tenant_role_permission` | The tenant console permission catalog and its role mapping |
| `platform_setting` | Defaults: password, token, verification and 2FA policy, auth methods, feature flags, notifications, SMTP, SMS, SCIM, directory |

There are no sample tenants, users or applications. On first start the log prints a setup code; the setup wizard at `/admin/setup` creates the first platform operator.

### Databases from older releases

Releases before the baseline built the same schema through a long chain of migrations. Those databases cannot be upgraded in place: the server detects them and refuses to start with *"This database was created by a release before the schema baseline"*. Start the new release on an empty database and set it up again.

---

## Adding a migration

1. Add `postgresql/V2__short_description.sql` (then `V3__…`, and so on). Never edit a migration that a release has already shipped: Flyway refuses a changed checksum.
2. Write it for a database that is in use: `IF NOT EXISTS`, backfills for existing rows, no sample data.
3. New tables get the `secureone_app` privileges through the default privileges; add a row-level security policy for tenant-scoped tables.
4. A per-application change goes in `application-schema/V<n>__….sql` and is listed in `ApplicationSchemaMigrations`.

Run the server (or `SECUREONE_DB_MIGRATE=only`) to apply it. `SECUREONE_DB_MIGRATE=validate` checks without changing anything.

---

## `R__z_repair_catalog.sql`

Idempotent inserts (`ON CONFLICT DO NOTHING`) for platform settings and the permission catalog. It restores rows that were deleted by hand and changes nothing on a healthy database. `PlatformSettingsBootstrap` and `CatalogRepairBootstrap` do the same at startup.
