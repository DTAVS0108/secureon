-- Application settings that only copied the platform go back to inheriting it.
--
-- Until now, opening some application settings pages gave the application its own copy of the platform values
-- straight away, before anything was changed. From then on platform changes no longer reached that application.
-- A copy that still equals the platform value changes nothing today, so it is removed and the application follows
-- the platform again.
--
--  * Appearance: the copy came from the platform's console look, not from sign-in page settings; a copy equal to it
--    (or empty) is removed, and the application uses the built-in sign-in page defaults as before.
--  * OAuth tokens: the "OAuth tokens" switch is now simply "this application has its own token policy". A saved
--    policy with the switch off was never meant to be used, so it is removed; "tabEnabled" leaves the others.
--  * Public manifest: platform key "public_manifest_defaults".

DO $$
DECLARE
    r record;
    sch text;
    pairs text[][] := ARRAY[
        ARRAY['password_policy', 'password_policy'],
        ARRAY['verification_policy', 'verification_policy'],
        ARRAY['user_directory', 'user_directory'],
        ARRAY['public_manifest', 'public_manifest_defaults'],
        ARRAY['auth_methods', 'auth_methods'],
        ARRAY['feature_flags', 'feature_flags'],
        ARRAY['token_policy', 'token_policy'],
        ARRAY['appearance', 'appearance']];
    i int;
BEGIN
    FOR r IN SELECT id, coalesce(nullif(schema_name, ''), 'platform') AS sch FROM platform.application LOOP
        sch := r.sch;
        IF to_regclass(format('%I.application_setting', sch)) IS NULL THEN
            CONTINUE;
        END IF;

        -- Token policy saved while its switch was off: not this application's policy.
        EXECUTE format(
            'DELETE FROM %I.application_setting WHERE application_id = $1 AND setting_key = ''token_policy''
               AND (value ->> ''tabEnabled'') = ''false''', sch)
            USING r.id;
        EXECUTE format(
            'UPDATE %I.application_setting SET value = value - ''tabEnabled''
             WHERE application_id = $1 AND setting_key = ''token_policy'' AND value ? ''tabEnabled''', sch)
            USING r.id;

        -- Copies equal to the platform value.
        FOR i IN 1 .. array_length(pairs, 1) LOOP
            EXECUTE format(
                'DELETE FROM %I.application_setting a WHERE a.application_id = $1 AND a.setting_key = $2
                   AND EXISTS (SELECT 1 FROM platform.platform_setting p WHERE p.key = $3 AND p.value = a.value)', sch)
                USING r.id, pairs[i][1], pairs[i][2];
        END LOOP;

        EXECUTE format(
            'DELETE FROM %I.application_setting WHERE application_id = $1 AND setting_key = ''appearance''
               AND value = ''{}''::jsonb', sch)
            USING r.id;

        -- The copy of the token policy kept in the application record follows the setting.
        EXECUTE format(
            'UPDATE platform.application SET config = config - ''tokenPolicy''
             WHERE id = $1 AND config ? ''tokenPolicy''
               AND NOT EXISTS (SELECT 1 FROM %I.application_setting s WHERE s.application_id = $1
                               AND s.setting_key = ''token_policy'')', sch)
            USING r.id;
    END LOOP;
END $$;
