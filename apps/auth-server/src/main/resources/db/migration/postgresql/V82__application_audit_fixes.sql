-- Application audit round (0.3.31).

-- Removing a user from an application sticks: with self-registration on, signing in again does not make them a
-- member again. Adding them back (an administrator) clears it.
CREATE TABLE IF NOT EXISTS application_access_removal (
    application_id UUID NOT NULL REFERENCES application(id) ON DELETE CASCADE,
    user_id        UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    tenant_id      UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    removed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (application_id, user_id)
);

-- Self-registrations from before sign-up created the membership: made members once, here, instead of by the
-- users list on every read (which also re-added users an administrator had removed).
DO $$
DECLARE
    app RECORD;
BEGIN
    FOR app IN SELECT id, schema_name FROM application WHERE schema_name ~ '^[a-z][a-z0-9_]{0,62}$' LOOP
        IF to_regclass(format('%I.user_application', app.schema_name)) IS NULL THEN
            CONTINUE;
        END IF;
        EXECUTE format(
            'INSERT INTO %I.user_application (user_id, application_id)
             SELECT DISTINCT a.target_id, a.application_id FROM audit_log a
             JOIN user_account u ON u.id = a.target_id
             WHERE a.action = ''user.self_registered'' AND a.application_id = $1
             ON CONFLICT DO NOTHING', app.schema_name)
        USING app.id;
    END LOOP;
END $$;
