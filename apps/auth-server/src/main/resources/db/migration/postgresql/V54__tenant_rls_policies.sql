-- PostgreSQL row-level security (defense in depth).
-- When app.tenant_id is empty, platform operators see all rows.
-- When set (per-request via set_config), only matching tenant rows are visible.

CREATE OR REPLACE FUNCTION secureone_rls_bypass() RETURNS boolean
    LANGUAGE sql
    STABLE
AS $$
    SELECT COALESCE(NULLIF(current_setting('app.tenant_id', true), ''), '') = '';
$$;

CREATE OR REPLACE FUNCTION secureone_tenant_matches(row_tenant_id uuid) RETURNS boolean
    LANGUAGE sql
    STABLE
AS $$
    SELECT secureone_rls_bypass()
        OR row_tenant_id::text = current_setting('app.tenant_id', true);
$$;

-- Core tenant-scoped tables
ALTER TABLE user_account ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON user_account;
CREATE POLICY tenant_isolation ON user_account
    USING (secureone_tenant_matches(tenant_id));

ALTER TABLE application ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON application;
CREATE POLICY tenant_isolation ON application
    USING (secureone_tenant_matches(tenant_id));

ALTER TABLE role ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON role;
CREATE POLICY tenant_isolation ON role
    USING (secureone_tenant_matches(tenant_id));

ALTER TABLE rbac_group ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON rbac_group;
CREATE POLICY tenant_isolation ON rbac_group
    USING (secureone_tenant_matches(tenant_id));

ALTER TABLE tenant_permission ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON tenant_permission;
CREATE POLICY tenant_isolation ON tenant_permission
    USING (secureone_tenant_matches(tenant_id));

ALTER TABLE tenant_role ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON tenant_role;
CREATE POLICY tenant_isolation ON tenant_role
    USING (secureone_tenant_matches(tenant_id));

ALTER TABLE tenant_user_roster ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON tenant_user_roster;
CREATE POLICY tenant_isolation ON tenant_user_roster
    USING (secureone_tenant_matches(tenant_id));

ALTER TABLE admin_console_access ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON admin_console_access;
CREATE POLICY tenant_isolation ON admin_console_access
    USING (secureone_tenant_matches(tenant_id));

ALTER TABLE admin_console_feature_override ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON admin_console_feature_override;
CREATE POLICY tenant_isolation ON admin_console_feature_override
    USING (secureone_tenant_matches(tenant_id));

ALTER TABLE tenant_console_role_feature ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON tenant_console_role_feature;
CREATE POLICY tenant_isolation ON tenant_console_role_feature
    USING (secureone_tenant_matches(tenant_id));

ALTER TABLE otp_challenge ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON otp_challenge;
CREATE POLICY tenant_isolation ON otp_challenge
    USING (secureone_tenant_matches(tenant_id));

-- Nullable tenant_id: visible only when bypassing or when tenant matches
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON audit_log;
CREATE POLICY tenant_isolation ON audit_log
    USING (
        secureone_rls_bypass()
        OR (tenant_id IS NOT NULL AND secureone_tenant_matches(tenant_id))
    );

ALTER TABLE login_history ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON login_history;
CREATE POLICY tenant_isolation ON login_history
    USING (
        secureone_rls_bypass()
        OR (tenant_id IS NOT NULL AND secureone_tenant_matches(tenant_id))
    );

-- MFA factors inherit tenant scope via user_account
ALTER TABLE mfa_factor ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON mfa_factor;
CREATE POLICY tenant_isolation ON mfa_factor
    USING (
        secureone_rls_bypass()
        OR EXISTS (
            SELECT 1
            FROM user_account u
            WHERE u.id = mfa_factor.user_id
              AND u.tenant_id::text = current_setting('app.tenant_id', true)
        )
    );

-- App DB role must not bypass policies (table owner otherwise skips RLS).
ALTER TABLE user_account FORCE ROW LEVEL SECURITY;
ALTER TABLE application FORCE ROW LEVEL SECURITY;
ALTER TABLE role FORCE ROW LEVEL SECURITY;
ALTER TABLE rbac_group FORCE ROW LEVEL SECURITY;
ALTER TABLE tenant_permission FORCE ROW LEVEL SECURITY;
ALTER TABLE tenant_role FORCE ROW LEVEL SECURITY;
ALTER TABLE tenant_user_roster FORCE ROW LEVEL SECURITY;
ALTER TABLE admin_console_access FORCE ROW LEVEL SECURITY;
ALTER TABLE admin_console_feature_override FORCE ROW LEVEL SECURITY;
ALTER TABLE tenant_console_role_feature FORCE ROW LEVEL SECURITY;
ALTER TABLE otp_challenge FORCE ROW LEVEL SECURITY;
ALTER TABLE audit_log FORCE ROW LEVEL SECURITY;
ALTER TABLE login_history FORCE ROW LEVEL SECURITY;
ALTER TABLE mfa_factor FORCE ROW LEVEL SECURITY;
