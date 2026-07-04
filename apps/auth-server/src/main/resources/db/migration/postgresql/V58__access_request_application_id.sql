ALTER TABLE access_request
    ADD COLUMN application_id UUID REFERENCES application(id) ON DELETE SET NULL;

ALTER TABLE access_request
    ADD COLUMN grant_type VARCHAR(64),
    ADD COLUMN granted_role_id UUID,
    ADD COLUMN granted_user_id UUID REFERENCES user_account(id) ON DELETE SET NULL;

CREATE INDEX idx_access_request_application ON access_request(application_id)
    WHERE application_id IS NOT NULL;
