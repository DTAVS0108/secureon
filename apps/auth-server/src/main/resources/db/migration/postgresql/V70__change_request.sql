-- High-impact changes that wait for a second admin's approval (docs/plans/change-approvals.md).
-- First action: oauth_client.delete. Read and written only through the admin API, which checks the
-- requester's and approver's rights (no RLS policy, like platform_operator).
CREATE TABLE IF NOT EXISTS platform.change_request (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action          VARCHAR(64)  NOT NULL,
    tenant_id       UUID REFERENCES platform.tenant (id) ON DELETE CASCADE,
    application_id  UUID,
    target_id       UUID         NOT NULL,
    target_label    VARCHAR(255) NOT NULL,
    reason          VARCHAR(500),
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    requested_by    VARCHAR(320) NOT NULL,
    requested_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    decided_by      VARCHAR(320),
    decided_at      TIMESTAMPTZ,
    decision_note   VARCHAR(500),
    CONSTRAINT change_request_status_chk
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS change_request_tenant_status_idx
    ON platform.change_request (tenant_id, status, requested_at DESC);

-- At most one pending request per action and target.
CREATE UNIQUE INDEX IF NOT EXISTS change_request_one_pending_idx
    ON platform.change_request (action, target_id) WHERE status = 'PENDING';
