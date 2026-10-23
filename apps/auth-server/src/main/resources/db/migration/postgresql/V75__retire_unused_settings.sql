-- Platform settings clean-up: options that did nothing, or were a second switch for something decided elsewhere.
--
--  * Sign-in method catalog: "TOTP Authenticator" (m_totp) and "Push Notification" (m_push). Two-step options come
--    from the Two Factor Authentication policy only; push is not built.
--  * Feature flags: "Recovery OTP" (recovery_otp; Verification & OTP › Password recovery allows codes or not),
--    "LDAP / AD" (ldap; not built yet), "Audit log" (audit_log; the audit log is always available), and the
--    "rollout" percentage of every flag (flags are on or off).
--  * Verification & OTP: login.mfaRequired (whether a second step is asked is the Two Factor Authentication mode).
--  * Two Factor Authentication: "Trust the identity provider's own MFA" (trustIdpMfa; never applied at sign-in) and
--    "Require passkey when enrolled" (requirePasskeyWhenEnrolled; the default method does this).
--
-- Where "Recovery OTP" was off, "otp" is removed from that level's allowed recovery modes first, so nobody gets
-- password reset by code who could not before. Application settings live in each application's schema.

CREATE OR REPLACE FUNCTION pg_temp.drop_ids(arr jsonb, field text, ids text[]) RETURNS jsonb
    LANGUAGE sql IMMUTABLE AS $$
    SELECT COALESCE(jsonb_agg(e), '[]'::jsonb)
    FROM jsonb_array_elements(COALESCE(arr, '[]'::jsonb)) e
    WHERE NOT (e ->> field = ANY (ids))
$$;

-- Feature flag list without the retired flags and without "rollout".
CREATE OR REPLACE FUNCTION pg_temp.clean_flags(arr jsonb) RETURNS jsonb
    LANGUAGE sql IMMUTABLE AS $$
    SELECT COALESCE(jsonb_agg(e - 'rollout'), '[]'::jsonb)
    FROM jsonb_array_elements(COALESCE(arr, '[]'::jsonb)) e
    WHERE NOT (e ->> 'key' = ANY (ARRAY['recovery_otp', 'ldap', 'audit_log']))
$$;

-- True when this flag list turns Recovery OTP off.
CREATE OR REPLACE FUNCTION pg_temp.recovery_otp_off(arr jsonb) RETURNS boolean
    LANGUAGE sql IMMUTABLE AS $$
    SELECT EXISTS (SELECT 1 FROM jsonb_array_elements(COALESCE(arr, '[]'::jsonb)) e
                   WHERE e ->> 'key' = 'recovery_otp' AND (e ->> 'enabled') = 'false')
$$;

-- A verification policy that no longer allows recovery by code (link stays; a default of "otp" becomes "link").
CREATE OR REPLACE FUNCTION pg_temp.recovery_without_codes(policy jsonb) RETURNS jsonb
    LANGUAGE sql IMMUTABLE AS $$
    SELECT jsonb_set(
               jsonb_set(COALESCE(policy, '{}'::jsonb), '{recovery,allowedModes}', '["link"]'::jsonb, true),
               '{recovery,passwordResetMode}', '"link"'::jsonb, true)
$$;

-- Two-factor policy without the two retired settings (values and locks).
CREATE OR REPLACE FUNCTION pg_temp.clean_mfa(policy jsonb) RETURNS jsonb
    LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE
               WHEN policy ? 'locks' AND jsonb_typeof(policy -> 'locks') = 'object'
                   THEN jsonb_set(policy - 'trustIdpMfa' - 'requirePasskeyWhenEnrolled', '{locks}',
                                  (policy -> 'locks') - 'trustIdpMfa' - 'requirePasskeyWhenEnrolled')
               ELSE policy - 'trustIdpMfa' - 'requirePasskeyWhenEnrolled'
           END
$$;

-- ---- Platform ----------------------------------------------------------------------------------------------
UPDATE platform.platform_setting
SET value = pg_temp.drop_ids(value, 'id', ARRAY['m_totp', 'm_push'])
WHERE key = 'auth_methods' AND jsonb_typeof(value) = 'array';

UPDATE platform.platform_setting v
SET value = pg_temp.recovery_without_codes(v.value)
WHERE v.key = 'verification_policy'
  AND EXISTS (SELECT 1 FROM platform.platform_setting f
              WHERE f.key = 'feature_flags' AND pg_temp.recovery_otp_off(f.value));

UPDATE platform.platform_setting
SET value = pg_temp.clean_flags(value)
WHERE key = 'feature_flags' AND jsonb_typeof(value) = 'array';

UPDATE platform.platform_setting
SET value = jsonb_set(value, '{login}', (value -> 'login') - 'mfaRequired')
WHERE key = 'verification_policy' AND jsonb_typeof(value -> 'login') = 'object';

UPDATE platform.platform_setting
SET value = pg_temp.clean_mfa(value)
WHERE key = 'mfa_policy';

-- ---- Tenants -----------------------------------------------------------------------------------------------
UPDATE platform.tenant_setting
SET value = pg_temp.clean_mfa(value)
WHERE setting_key = 'mfa_policy';

-- ---- Applications (each in its own schema) -----------------------------------------------------------------
DO $$
DECLARE
    r record;
    sch text;
BEGIN
    FOR r IN SELECT id, coalesce(nullif(schema_name, ''), 'platform') AS sch FROM platform.application LOOP
        sch := r.sch;
        IF to_regclass(format('%I.application_setting', sch)) IS NULL THEN
            CONTINUE;
        END IF;
        EXECUTE format(
            'UPDATE %I.application_setting SET value = pg_temp.drop_ids(value, ''id'', ARRAY[''m_totp'', ''m_push''])
             WHERE application_id = $1 AND setting_key = ''auth_methods'' AND jsonb_typeof(value) = ''array''', sch)
            USING r.id;
        -- Recovery OTP turned off for this application: its verification override stops allowing codes.
        EXECUTE format(
            'UPDATE %I.application_setting v SET value = pg_temp.recovery_without_codes(v.value)
             WHERE v.application_id = $1 AND v.setting_key = ''verification_policy''
               AND EXISTS (SELECT 1 FROM %I.application_setting f WHERE f.application_id = $1
                           AND f.setting_key = ''feature_flags'' AND pg_temp.recovery_otp_off(f.value))', sch, sch)
            USING r.id;
        -- No override yet: start from the platform policy (an override is a complete policy, not a patch).
        EXECUTE format(
            'INSERT INTO %I.application_setting (application_id, setting_key, value)
             SELECT $1, ''verification_policy'', pg_temp.recovery_without_codes(
                 COALESCE((SELECT p.value FROM platform.platform_setting p WHERE p.key = ''verification_policy''), ''{}''::jsonb))
             WHERE EXISTS (SELECT 1 FROM %I.application_setting f WHERE f.application_id = $1
                           AND f.setting_key = ''feature_flags'' AND pg_temp.recovery_otp_off(f.value))
               AND NOT EXISTS (SELECT 1 FROM %I.application_setting v WHERE v.application_id = $1
                               AND v.setting_key = ''verification_policy'')', sch, sch, sch)
            USING r.id;
        EXECUTE format(
            'UPDATE %I.application_setting SET value = pg_temp.clean_flags(value)
             WHERE application_id = $1 AND setting_key = ''feature_flags'' AND jsonb_typeof(value) = ''array''', sch)
            USING r.id;
        EXECUTE format(
            'UPDATE %I.application_setting SET value = jsonb_set(value, ''{login}'', (value -> ''login'') - ''mfaRequired'')
             WHERE application_id = $1 AND setting_key = ''verification_policy''
               AND jsonb_typeof(value -> ''login'') = ''object''', sch)
            USING r.id;
        EXECUTE format(
            'UPDATE %I.application_setting SET value = pg_temp.clean_mfa(value)
             WHERE application_id = $1 AND setting_key = ''mfa_policy''', sch)
            USING r.id;
    END LOOP;
END $$;
