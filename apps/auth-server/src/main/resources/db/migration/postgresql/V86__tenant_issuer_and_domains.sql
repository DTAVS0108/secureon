-- Issuer per tenant and custom domains.
-- Every tenant has its own issuer, <issuer>/t/<slug>, with its own discovery document; tokens issued there carry
-- it in "iss" and only the tenant's own clients are served there. issuer_mode 'tenant' also refuses the
-- tenant's clients on the shared issuer, so its tokens never carry the shared one.
ALTER TABLE platform.tenant
    ADD COLUMN IF NOT EXISTS issuer_mode VARCHAR(16) NOT NULL DEFAULT 'shared';

-- A host name of the tenant's own (login.example.com). Once verified (DNS TXT record, or the domain already
-- pointing at SecureOne), requests to it are served as the tenant's: issuer https://<domain>, hosted pages there.
CREATE TABLE IF NOT EXISTS platform.tenant_domain (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL REFERENCES platform.tenant(id) ON DELETE CASCADE,
    domain             VARCHAR(253) NOT NULL,
    status             VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    verification_token VARCHAR(64) NOT NULL,
    verified_at        TIMESTAMPTZ,
    last_checked_at    TIMESTAMPTZ,
    last_error         VARCHAR(500),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_tenant_domain_domain ON platform.tenant_domain (lower(domain));
CREATE INDEX IF NOT EXISTS ix_tenant_domain_tenant ON platform.tenant_domain (tenant_id);
