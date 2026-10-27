-- Platform audit (0.3.31): webhook retries, append-only audit log, account deletion requests, read-only platform
-- operators, temporary passwords, and an overlap when rotating OAuth client and webhook secrets.

-- Webhooks: retried with backoff until delivered (or 8 tries); a rotated secret keeps signing for a while.
ALTER TABLE webhook_delivery
    ADD COLUMN IF NOT EXISTS attempts        INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_error      VARCHAR(500),
    ADD COLUMN IF NOT EXISTS delivered_at    TIMESTAMPTZ;
-- Deliveries left waiting by the old one-shot sender: recent ones are sent now, older ones are marked failed
-- (they can be sent again from the console).
UPDATE webhook_delivery SET next_attempt_at = now()
    WHERE status = 'PENDING' AND created_at > now() - interval '1 day';
UPDATE webhook_delivery SET status = 'FAILED', last_error = 'Not sent before retries were introduced.'
    WHERE status = 'PENDING' AND next_attempt_at IS NULL;
UPDATE webhook_delivery SET attempts = 1 WHERE status IN ('DELIVERED', 'FAILED');
CREATE INDEX IF NOT EXISTS idx_webhook_delivery_due ON webhook_delivery(next_attempt_at)
    WHERE status IN ('PENDING', 'RETRYING');

ALTER TABLE webhook_endpoint
    ADD COLUMN IF NOT EXISTS previous_secret_encrypted  TEXT,
    ADD COLUMN IF NOT EXISTS previous_secret_expires_at TIMESTAMPTZ;

-- OAuth clients: after a rotation with an overlap, the old secret's hash works until it expires.
ALTER TABLE oauth_client
    ADD COLUMN IF NOT EXISTS previous_client_secret     TEXT,
    ADD COLUMN IF NOT EXISTS previous_secret_expires_at TIMESTAMPTZ;

-- Temporary passwords set by an administrator: the user chooses their own at the next sign-in.
ALTER TABLE user_credential ADD COLUMN IF NOT EXISTS must_change BOOLEAN NOT NULL DEFAULT false;

-- Platform operators: full access, or read-only (auditors, support).
ALTER TABLE platform_operator ADD COLUMN IF NOT EXISTS role VARCHAR(32) NOT NULL DEFAULT 'SUPER_ADMIN';
ALTER TABLE platform_operator DROP CONSTRAINT IF EXISTS platform_operator_role_chk;
ALTER TABLE platform_operator ADD CONSTRAINT platform_operator_role_chk CHECK (role IN ('SUPER_ADMIN', 'READ_ONLY'));

-- Account deletion requests (right to erasure): listed for the tenant's admins, completed after 30 days.
CREATE TABLE IF NOT EXISTS account_deletion_request (
    user_id      UUID PRIMARY KEY REFERENCES user_account(id) ON DELETE CASCADE,
    tenant_id    UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_account_deletion_request_tenant ON account_deletion_request(tenant_id, requested_at);
-- Requests made before this list existed: still-disabled accounts whose owner asked for deletion.
INSERT INTO account_deletion_request (user_id, tenant_id, requested_at)
SELECT DISTINCT ON (u.id) u.id, u.tenant_id, a.created_at
FROM audit_log a JOIN user_account u ON u.id = a.target_id
WHERE a.action = 'account.deletion_requested' AND u.status = 'DISABLED'
ORDER BY u.id, a.created_at DESC
ON CONFLICT (user_id) DO NOTHING;

-- Audit log: append-only. Entries cannot be changed, and are removed only by retention (30 days at least).
-- The application reference stays when an application is deleted (it was set to null, which changed the entry).
ALTER TABLE audit_log DROP CONSTRAINT IF EXISTS audit_log_application_id_fkey;

CREATE OR REPLACE FUNCTION audit_log_append_only() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    -- Deliberate maintenance only: SET LOCAL secureone.audit_maintenance = 'on' in that transaction.
    IF coalesce(current_setting('secureone.audit_maintenance', true), '') = 'on' THEN
        RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
    END IF;
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'The audit log cannot be changed' USING ERRCODE = 'insufficient_privilege';
    END IF;
    IF OLD.created_at > now() - interval '30 days' THEN
        RAISE EXCEPTION 'Audit entries are kept for at least 30 days' USING ERRCODE = 'insufficient_privilege';
    END IF;
    RETURN OLD;
END;
$$;

DROP TRIGGER IF EXISTS audit_log_append_only ON audit_log;
CREATE TRIGGER audit_log_append_only BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_append_only();

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'secureone_app') THEN
        REVOKE UPDATE, TRUNCATE ON audit_log FROM secureone_app;
    END IF;
END;
$$;
