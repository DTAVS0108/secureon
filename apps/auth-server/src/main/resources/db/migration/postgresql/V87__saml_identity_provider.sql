-- SecureOne as a SAML 2.0 identity provider: applications that only speak SAML (service providers) sign users in
-- through SecureOne. Each tenant has its IdP (entity ID <base>/saml2/idp/<slug>) with its own signing key; each
-- service provider is registered under one of the tenant's applications, whose access rules apply.
CREATE TABLE IF NOT EXISTS platform.saml_service_provider (
    id                         UUID PRIMARY KEY,
    tenant_id                  UUID NOT NULL REFERENCES platform.tenant(id) ON DELETE CASCADE,
    application_id             UUID NOT NULL REFERENCES platform.application(id) ON DELETE CASCADE,
    name                       VARCHAR(200) NOT NULL,
    entity_id                  VARCHAR(1024) NOT NULL,
    acs_url                    VARCHAR(1024) NOT NULL,
    slo_url                    VARCHAR(1024),
    name_id_format             VARCHAR(16) NOT NULL DEFAULT 'email',
    sign_response              BOOLEAN NOT NULL DEFAULT TRUE,
    sign_assertion             BOOLEAN NOT NULL DEFAULT TRUE,
    sp_certificate             TEXT,
    require_signed_requests    BOOLEAN NOT NULL DEFAULT FALSE,
    attributes                 JSONB NOT NULL DEFAULT '[]'::jsonb,
    assertion_lifetime_seconds INT NOT NULL DEFAULT 300,
    status                     VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A service provider is known by its entity ID within the tenant's IdP.
CREATE UNIQUE INDEX IF NOT EXISTS ux_saml_sp_tenant_entity ON platform.saml_service_provider (tenant_id, lower(entity_id));
CREATE INDEX IF NOT EXISTS ix_saml_sp_application ON platform.saml_service_provider (application_id);

-- The tenant IdP's signing key (private key encrypted like other stored secrets) and its certificate.
CREATE TABLE IF NOT EXISTS platform.saml_idp_credential (
    tenant_id              UUID PRIMARY KEY REFERENCES platform.tenant(id) ON DELETE CASCADE,
    private_key_encrypted  TEXT NOT NULL,
    certificate_pem        TEXT NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
