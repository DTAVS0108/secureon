-- Align app-schema user_application with JPA entity (platform uses created_at).
ALTER TABLE user_application
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();

UPDATE user_application
SET created_at = joined_at
WHERE created_at IS NULL OR created_at = '1970-01-01 00:00:00+00'::timestamptz;
