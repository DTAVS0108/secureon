-- SCIM groups + service accounts / API keys (Phase 2 M2M foundation).

CREATE TABLE scim_group (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    display_name    VARCHAR(255) NOT NULL,
    external_id     VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_scim_group_tenant_external
    ON scim_group(tenant_id, external_id)
    WHERE external_id IS NOT NULL;

CREATE INDEX idx_scim_group_tenant ON scim_group(tenant_id);

CREATE TABLE scim_group_member (
    group_id    UUID NOT NULL REFERENCES scim_group(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, user_id)
);

CREATE INDEX idx_scim_group_member_user ON scim_group_member(user_id);

CREATE TABLE service_account (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    name            VARCHAR(150) NOT NULL,
    description     TEXT,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_service_account_tenant_name ON service_account(tenant_id, name);
CREATE INDEX idx_service_account_tenant ON service_account(tenant_id);

CREATE TABLE api_key (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_account_id  UUID NOT NULL REFERENCES service_account(id) ON DELETE CASCADE,
    key_prefix          VARCHAR(16) NOT NULL,
    key_hash            VARCHAR(128) NOT NULL,
    label               VARCHAR(150),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ,
    last_used_at        TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_api_key_hash ON api_key(key_hash);
CREATE INDEX idx_api_key_service_account ON api_key(service_account_id);

ALTER TABLE scim_group ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON scim_group;
CREATE POLICY tenant_isolation ON scim_group
    FOR ALL USING (secureone_tenant_matches(tenant_id));
ALTER TABLE scim_group FORCE ROW LEVEL SECURITY;

ALTER TABLE service_account ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON service_account;
CREATE POLICY tenant_isolation ON service_account
    FOR ALL USING (secureone_tenant_matches(tenant_id));
ALTER TABLE service_account FORCE ROW LEVEL SECURITY;
