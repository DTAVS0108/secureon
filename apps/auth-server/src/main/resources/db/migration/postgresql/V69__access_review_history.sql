-- Completed access reviews (evidence for periodic privileged-access reviews). tenant_id NULL is the
-- platform-wide review by platform operators; otherwise the review of one tenant by its super admins.
-- Read and written only through the admin API, which checks the reviewer's rights (no RLS policy, like
-- platform_operator).
CREATE TABLE IF NOT EXISTS platform.access_review (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID REFERENCES platform.tenant (id) ON DELETE CASCADE,
    completed_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_by      VARCHAR(320) NOT NULL,
    entries_reviewed  INTEGER NOT NULL DEFAULT 0,
    flagged_count     INTEGER NOT NULL DEFAULT 0,
    revoked_count     INTEGER NOT NULL DEFAULT 0,
    note              VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS access_review_tenant_completed_idx
    ON platform.access_review (tenant_id, completed_at DESC);
