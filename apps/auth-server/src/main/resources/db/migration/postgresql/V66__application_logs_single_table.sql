-- Application logs are written by a background appender into platform.application_log, but isolating
-- an application moved its existing rows into <app schema>.application_log, and app-scoped reads
-- (search_path set to the app schema) read that copy. New events never appeared in an application's
-- log view. Logs now live only in the platform table, filtered by application_id like audit_log and
-- login_history. Fold the moved rows back; the per-app tables are left empty.

DO $$
DECLARE
    s TEXT;
BEGIN
    FOR s IN
        SELECT DISTINCT schema_name FROM platform.application
        WHERE schema_name IS NOT NULL AND schema_name <> 'platform'
    LOOP
        IF to_regclass(format('%I.application_log', s)) IS NOT NULL THEN
            EXECUTE format(
                'INSERT INTO platform.application_log (id, created_at, level, logger, message, session_id, request_id,'
                    || ' principal, tenant_id, application_id, ip, user_agent, metadata)'
                    || ' SELECT id, created_at, level, logger, message, session_id, request_id,'
                    || ' principal, tenant_id, application_id, ip, user_agent, metadata FROM %I.application_log'
                    || ' ON CONFLICT (id) DO NOTHING',
                s);
            EXECUTE format('DELETE FROM %I.application_log', s);
        END IF;
    END LOOP;
END $$;
