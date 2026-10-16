-- Which value an OAuth client receives as the token subject ("sub").
--   legacy  : tenant-slug:email (changes when the user changes email)
--   user_id : the account UUID (stable; what new integrations should key users on)
-- Existing clients keep legacy so integrations that stored "sub" keep working; new clients default to
-- user_id in the application. Switch a client explicitly once its app keys users on user_id.
ALTER TABLE platform.oauth_client
    ADD COLUMN IF NOT EXISTS subject_type VARCHAR(16) NOT NULL DEFAULT 'legacy';

ALTER TABLE platform.oauth_client
    ADD CONSTRAINT oauth_client_subject_type_check CHECK (subject_type IN ('legacy', 'user_id'));
