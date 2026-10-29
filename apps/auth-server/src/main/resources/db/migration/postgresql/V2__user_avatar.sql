-- Profile pictures: at most one small image per user account, kept apart from user_account rows.
-- The server accepts only PNG, JPEG or WebP (checked by content, not by name); the console scales
-- uploads to 256 px first.

CREATE TABLE IF NOT EXISTS platform.user_avatar (
    user_id      uuid PRIMARY KEY REFERENCES platform.user_account(id) ON DELETE CASCADE,
    tenant_id    uuid NOT NULL REFERENCES platform.tenant(id) ON DELETE CASCADE,
    content_type varchar(32) NOT NULL,
    image        bytea NOT NULL,
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT user_avatar_type_chk CHECK (content_type IN ('image/png', 'image/jpeg', 'image/webp')),
    CONSTRAINT user_avatar_size_chk CHECK (octet_length(image) BETWEEN 1 AND 524288)
);

CREATE INDEX IF NOT EXISTS idx_user_avatar_tenant ON platform.user_avatar (tenant_id);

ALTER TABLE platform.user_avatar ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform.user_avatar FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON platform.user_avatar;
CREATE POLICY tenant_isolation ON platform.user_avatar USING (platform.secureone_tenant_matches(tenant_id));
