-- Row-level security only applies to roles that are neither superuser nor BYPASSRLS. The schema
-- owner used by Flyway is often both, so request-time queries switch (SET ROLE, see the datasource
-- connection-init-sql) to this role: data privileges only, policies always enforced.
--
-- Production may instead give the application its own LOGIN role that is a member of
-- secureone_app; the startup check refuses to enforce RLS on a connection that bypasses it.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'secureone_app') THEN
        CREATE ROLE secureone_app NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
    END IF;
END
$$;

-- Lets a non-superuser schema owner SET ROLE to it (superusers can anyway).
GRANT secureone_app TO CURRENT_USER;

-- Existing schemas: platform, public and every application schema.
DO $$
DECLARE
    s text;
BEGIN
    FOR s IN
        SELECT nspname FROM pg_namespace
        WHERE nspname NOT LIKE 'pg\_%' AND nspname <> 'information_schema'
    LOOP
        EXECUTE format('GRANT USAGE ON SCHEMA %I TO secureone_app', s);
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA %I TO secureone_app', s);
        EXECUTE format('GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA %I TO secureone_app', s);
        EXECUTE format('GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA %I TO secureone_app', s);
    END LOOP;
END
$$;

-- Objects the owner creates later (new migrations, newly provisioned application schemas).
ALTER DEFAULT PRIVILEGES GRANT USAGE ON SCHEMAS TO secureone_app;
ALTER DEFAULT PRIVILEGES GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO secureone_app;
ALTER DEFAULT PRIVILEGES GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO secureone_app;
ALTER DEFAULT PRIVILEGES GRANT EXECUTE ON FUNCTIONS TO secureone_app;
