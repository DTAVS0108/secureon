-- Per-application schema DDL (executed inside each app schema, e.g. demostore).
-- Cross-schema FKs reference platform.* for tenant, application, and user identity.

CREATE TABLE IF NOT EXISTS permission (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL,
    key            VARCHAR(150) NOT NULL,
    description    VARCHAR(500),
    UNIQUE (application_id, key)
);

CREATE TABLE IF NOT EXISTS role (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL,
    application_id UUID NOT NULL,
    name           VARCHAR(150) NOT NULL,
    description    VARCHAR(500),
    is_default     BOOLEAN NOT NULL DEFAULT false,
    is_system      BOOLEAN NOT NULL DEFAULT false,
    is_composite   BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (application_id, name)
);

CREATE TABLE IF NOT EXISTS role_permission (
    role_id       UUID NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permission (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS role_composite (
    parent_role_id UUID NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    child_role_id  UUID NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    PRIMARY KEY (parent_role_id, child_role_id),
    CHECK (parent_role_id <> child_role_id)
);

CREATE TABLE IF NOT EXISTS user_role (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID,
    role_id    UUID NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    granted_by UUID,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS user_application (
    user_id        UUID NOT NULL,
    application_id UUID NOT NULL,
    status         VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    joined_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, application_id)
);

CREATE TABLE IF NOT EXISTS application_setting (
    application_id UUID NOT NULL,
    setting_key    VARCHAR(150) NOT NULL,
    value          JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (application_id, setting_key)
);

CREATE TABLE IF NOT EXISTS rbac_group (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL,
    application_id UUID NOT NULL,
    name           VARCHAR(150) NOT NULL,
    description    VARCHAR(500),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (application_id, name)
);

CREATE TABLE IF NOT EXISTS rbac_group_role (
    group_id UUID NOT NULL REFERENCES rbac_group (id) ON DELETE CASCADE,
    role_id  UUID NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, role_id)
);

CREATE TABLE IF NOT EXISTS rbac_group_member (
    group_id UUID NOT NULL REFERENCES rbac_group (id) ON DELETE CASCADE,
    user_id  UUID NOT NULL,
    added_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, user_id)
);

CREATE TABLE IF NOT EXISTS application_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    level           VARCHAR(16) NOT NULL,
    logger          VARCHAR(256) NOT NULL,
    message         TEXT NOT NULL,
    session_id      VARCHAR(128),
    request_id      VARCHAR(64),
    principal       VARCHAR(320),
    tenant_id       UUID,
    application_id  UUID,
    ip              VARCHAR(64),
    user_agent      VARCHAR(512),
    metadata        JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_application_log_created_at ON application_log (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_application_log_session_id ON application_log (session_id) WHERE session_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_application_log_request_id ON application_log (request_id) WHERE request_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_application_log_level ON application_log (level);
CREATE INDEX IF NOT EXISTS idx_application_log_app_created ON application_log (application_id, created_at DESC)
    WHERE application_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_role_application ON role (application_id);
CREATE INDEX IF NOT EXISTS idx_permission_application ON permission (application_id);
CREATE INDEX IF NOT EXISTS idx_user_role_user ON user_role (user_id);
CREATE INDEX IF NOT EXISTS idx_rbac_group_application ON rbac_group (application_id);
