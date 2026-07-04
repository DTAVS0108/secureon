-- Extend MFA factors for TOTP secrets, recovery codes, and WebAuthn (phase B).

ALTER TABLE mfa_factor
    ADD COLUMN IF NOT EXISTS last_used_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN mfa_factor.secret_encrypted IS 'AES-GCM encrypted TOTP seed or recovery payload';
COMMENT ON COLUMN mfa_factor.metadata IS 'Recovery code hashes, WebAuthn credential fields (future)';
