-- Named platform operators (replaces the single shared operator account for day-to-day use).
--
-- Platform operators are ordinary user accounts in the reserved "platform" tenant, so they get
-- passwords, MFA, lockout and audit like everyone else. platform_operator records who holds the
-- platform-admin role. The configured break-glass account (SECUREONE_DEV_USER) remains for
-- emergencies only.

INSERT INTO platform.tenant (slug, name, status, settings)
VALUES ('platform', 'Platform operators', 'ACTIVE', '{"system": true, "plan": "enterprise"}'::jsonb)
ON CONFLICT (slug) DO NOTHING;

CREATE TABLE IF NOT EXISTS platform.platform_operator (
    user_id    UUID PRIMARY KEY REFERENCES platform.user_account (id) ON DELETE CASCADE,
    granted_by VARCHAR(320),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
