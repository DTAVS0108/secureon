-- Opt-in fail-closed row-level security.
--
-- app.rls_enforce = 'on'  (connection default, from SECUREONE_RLS_ENFORCE)
--     Rows are visible only for the tenant in app.tenant_id, or all rows when the transaction
--     explicitly sets app.rls_bypass = 'on' (platform operators and system jobs).
--     A transaction with neither sees no tenant-scoped rows.
-- app.rls_enforce unset / 'off'
--     Legacy behaviour from V54: an empty app.tenant_id sees every row.
--
-- secureone_tenant_matches() and every policy call this function, so no policy changes are needed.
CREATE OR REPLACE FUNCTION secureone_rls_bypass() RETURNS boolean
    LANGUAGE sql
    STABLE
AS $$
    SELECT COALESCE(current_setting('app.rls_bypass', true), '') = 'on'
        OR (
            COALESCE(current_setting('app.rls_enforce', true), '') <> 'on'
            AND COALESCE(current_setting('app.tenant_id', true), '') = ''
        );
$$;
