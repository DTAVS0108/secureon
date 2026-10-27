-- Market-standard audit round (0.3.32): APIs (resource servers) an application defines.
-- An API has an identifier, which access tokens carry in "aud" when the client asked for one of the API's
-- scopes (or named it with the RFC 8707 "resource" parameter), so the API can check a token is meant for it.
CREATE TABLE IF NOT EXISTS application_api (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    application_id UUID NOT NULL REFERENCES application(id) ON DELETE CASCADE,
    name           VARCHAR(200) NOT NULL,
    identifier     VARCHAR(512) NOT NULL,
    scopes         JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One API per identifier across the platform: an audience must name exactly one API.
CREATE UNIQUE INDEX IF NOT EXISTS ux_application_api_identifier ON application_api (lower(identifier));
CREATE INDEX IF NOT EXISTS ix_application_api_application ON application_api (application_id);
