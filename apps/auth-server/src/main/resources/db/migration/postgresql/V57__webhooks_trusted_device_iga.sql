-- Phase 2-4 foundation: webhooks, trusted devices, IGA, organizations, tenant quotas.

CREATE TABLE webhook_endpoint (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    application_id  UUID REFERENCES application(id) ON DELETE CASCADE,
    url             VARCHAR(2048) NOT NULL,
    secret_encrypted TEXT NOT NULL,
    events          JSONB NOT NULL DEFAULT '[]'::jsonb,
    enabled         BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_webhook_endpoint_tenant ON webhook_endpoint(tenant_id);
CREATE INDEX idx_webhook_endpoint_application ON webhook_endpoint(application_id)
    WHERE application_id IS NOT NULL;

CREATE TABLE webhook_delivery (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id     UUID NOT NULL REFERENCES webhook_endpoint(id) ON DELETE CASCADE,
    event_type      VARCHAR(128) NOT NULL,
    payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    response_code   INTEGER,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_webhook_delivery_endpoint ON webhook_delivery(endpoint_id);
CREATE INDEX idx_webhook_delivery_created ON webhook_delivery(created_at DESC);

CREATE TABLE trusted_device (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    device_token_hash   VARCHAR(128) NOT NULL,
    label               VARCHAR(150),
    expires_at          TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at        TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_trusted_device_user_token ON trusted_device(user_id, device_token_hash);
CREATE INDEX idx_trusted_device_user ON trusted_device(user_id);

CREATE TABLE access_request (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    requester_user_id   UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    target_user_id      UUID REFERENCES user_account(id) ON DELETE SET NULL,
    resource_type       VARCHAR(64) NOT NULL,
    resource_id         VARCHAR(255) NOT NULL,
    reason              TEXT,
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    reviewer_id         UUID REFERENCES user_account(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at         TIMESTAMPTZ
);

CREATE INDEX idx_access_request_tenant ON access_request(tenant_id);
CREATE INDEX idx_access_request_status ON access_request(tenant_id, status);

CREATE TABLE organization (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    parent_id       UUID REFERENCES organization(id) ON DELETE SET NULL,
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(128) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_organization_tenant_slug ON organization(tenant_id, slug);
CREATE INDEX idx_organization_tenant ON organization(tenant_id);
CREATE INDEX idx_organization_parent ON organization(parent_id) WHERE parent_id IS NOT NULL;

CREATE TABLE tenant_quota (
    tenant_id           UUID PRIMARY KEY REFERENCES tenant(id) ON DELETE CASCADE,
    max_users           INTEGER NOT NULL DEFAULT 1000,
    max_applications    INTEGER NOT NULL DEFAULT 50,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE webhook_endpoint ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON webhook_endpoint;
CREATE POLICY tenant_isolation ON webhook_endpoint
    FOR ALL USING (secureone_tenant_matches(tenant_id));
ALTER TABLE webhook_endpoint FORCE ROW LEVEL SECURITY;

ALTER TABLE access_request ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON access_request;
CREATE POLICY tenant_isolation ON access_request
    FOR ALL USING (secureone_tenant_matches(tenant_id));
ALTER TABLE access_request FORCE ROW LEVEL SECURITY;

ALTER TABLE organization ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON organization;
CREATE POLICY tenant_isolation ON organization
    FOR ALL USING (secureone_tenant_matches(tenant_id));
ALTER TABLE organization FORCE ROW LEVEL SECURITY;

ALTER TABLE tenant_quota ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON tenant_quota;
CREATE POLICY tenant_isolation ON tenant_quota
    FOR ALL USING (secureone_tenant_matches(tenant_id));
ALTER TABLE tenant_quota FORCE ROW LEVEL SECURITY;
