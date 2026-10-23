-- Change approvals for Two Factor Authentication (docs/plans/change-approvals.md): a request now carries what it
-- changes. payload: the values to apply and the "before" picture used to refuse a stale approval. details: the
-- change in words for the approver (e.g. "Mode: required → off"). Platform-wide requests use the zero UUID as
-- target_id.
ALTER TABLE platform.change_request ADD COLUMN IF NOT EXISTS payload JSONB;
ALTER TABLE platform.change_request ADD COLUMN IF NOT EXISTS details VARCHAR(1000);
