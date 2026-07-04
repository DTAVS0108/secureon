-- Identity federation (Phase 3 foundation): external IdPs + linked user identities.

CREATE TABLE identity_provider (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    application_id  UUID REFERENCES application(id) ON DELETE CASCADE,
    alias           VARCHAR(64) NOT NULL,
    type            VARCHAR(32) NOT NULL,
    display_name    VARCHAR(255),
    enabled         BOOLEAN NOT NULL DEFAULT false,
    config          JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_identity_provider_tenant_alias_platform
    ON identity_provider (tenant_id, alias)
    WHERE application_id IS NULL;

CREATE UNIQUE INDEX uq_identity_provider_tenant_app_alias
    ON identity_provider (tenant_id, application_id, alias)
    WHERE application_id IS NOT NULL;

CREATE INDEX idx_identity_provider_tenant ON identity_provider (tenant_id);
CREATE INDEX idx_identity_provider_application ON identity_provider (application_id);

CREATE TABLE user_identity (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    provider_id       UUID NOT NULL REFERENCES identity_provider(id) ON DELETE CASCADE,
    provider_subject  VARCHAR(512) NOT NULL,
    email             VARCHAR(320),
    metadata          JSONB NOT NULL DEFAULT '{}'::jsonb,
    linked_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider_id, provider_subject)
);

CREATE INDEX idx_user_identity_user ON user_identity (user_id);
CREATE INDEX idx_user_identity_provider ON user_identity (provider_id);

-- SCIM bearer token placeholder (enabled via platform settings bootstrap / admin).
INSERT INTO platform_setting (key, value)
VALUES (
    'scim',
    '{
      "enabled": false,
      "bearerTokenEncrypted": "",
      "userNameDomain": "secureone.local"
    }'::jsonb
)
ON CONFLICT (key) DO NOTHING;

-- Tenant-scoped federation tables
ALTER TABLE identity_provider ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON identity_provider;
CREATE POLICY tenant_isolation ON identity_provider
    USING (secureone_tenant_matches(tenant_id));

ALTER TABLE user_identity ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON user_identity;
CREATE POLICY tenant_isolation ON user_identity
    USING (
        secureone_rls_bypass()
        OR EXISTS (
            SELECT 1
            FROM user_account u
            WHERE u.id = user_identity.user_id
              AND u.tenant_id::text = current_setting('app.tenant_id', true)
        )
    );

ALTER TABLE identity_provider FORCE ROW LEVEL SECURITY;
ALTER TABLE user_identity FORCE ROW LEVEL SECURITY;
