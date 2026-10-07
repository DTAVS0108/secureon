-- The tenant policy called secureone_rls_bypass() unqualified, so it failed whenever search_path
-- did not include the platform schema (application-schema transactions switch search_path). It
-- went unnoticed because the old connection role skipped RLS (see V63). Same logic, qualified name.
CREATE OR REPLACE FUNCTION platform.secureone_tenant_matches(row_tenant_id uuid) RETURNS boolean
    LANGUAGE sql
    STABLE
AS $$
    SELECT platform.secureone_rls_bypass()
        OR row_tenant_id::text = current_setting('app.tenant_id', true);
$$;
