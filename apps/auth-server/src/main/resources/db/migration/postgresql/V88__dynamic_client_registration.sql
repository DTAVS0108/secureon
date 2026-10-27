-- Dynamic client registration (RFC 7591 / OIDC Dynamic Client Registration 1.0) and its management (RFC 7592).
-- Per application: off (the default), with an initial access token handed out by an administrator, or open.
-- Policies limit what a registered client may ask for (scopes, grant types, redirect hosts, how many clients).
CREATE TABLE IF NOT EXISTS platform.application_client_registration (
    application_id       UUID PRIMARY KEY REFERENCES platform.application(id) ON DELETE CASCADE,
    tenant_id            UUID NOT NULL REFERENCES platform.tenant(id) ON DELETE CASCADE,
    mode                 VARCHAR(16) NOT NULL DEFAULT 'off',
    allowed_scopes       JSONB NOT NULL DEFAULT '["openid","profile","email"]'::jsonb,
    allowed_grant_types  JSONB NOT NULL DEFAULT '["authorization_code","refresh_token"]'::jsonb,
    trusted_hosts        JSONB NOT NULL DEFAULT '[]'::jsonb,
    max_clients          INT NOT NULL DEFAULT 100,
    unused_days          INT NOT NULL DEFAULT 30,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- At most one application per tenant takes open registrations (the tenant issuer's registration endpoint).
CREATE UNIQUE INDEX IF NOT EXISTS ux_client_registration_open_per_tenant
    ON platform.application_client_registration (tenant_id) WHERE mode = 'open';

-- Initial access tokens: shown once, stored as SHA-256; each allows a number of registrations until it expires.
CREATE TABLE IF NOT EXISTS platform.client_registration_token (
    id              UUID PRIMARY KEY,
    application_id  UUID NOT NULL REFERENCES platform.application(id) ON DELETE CASCADE,
    token_hash      VARCHAR(64) NOT NULL UNIQUE,
    label           VARCHAR(200),
    max_uses        INT NOT NULL,
    uses            INT NOT NULL DEFAULT 0,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_client_registration_token_application ON platform.client_registration_token (application_id);

-- A client registered through the API: its registration access token (SHA-256, replaced on each use) for RFC 7592,
-- and the metadata it registered with (returned on read).
ALTER TABLE platform.oauth_client
    ADD COLUMN IF NOT EXISTS dynamically_registered BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS registration_token_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS registration_metadata JSONB;
