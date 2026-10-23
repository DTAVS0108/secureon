-- Application settings brought in line with the standards (0.3.29). The code already applies these rules when it
-- reads settings; this tidies what is stored so the console shows what applies.
--
--  * Email templates that carry a reset or sign-in link or a code never have cc/bcc, and the security notices
--    (password changed, sign-in changes, account locked) cannot be switched off.
--  * Verification codes: 6 to 8 digits, valid at most 10 minutes (NIST SP 800-63B).
--  * OAuth tokens: access, ID and client-credentials tokens at most 1 hour, authorization codes at most 10 minutes
--    (OAuth 2.0 Security BCP, RFC 6749).
--  * Appearance: "logoUrl" that is not https is cleared.

CREATE OR REPLACE FUNCTION pg_temp.clean_templates(t jsonb) RETURNS jsonb
    LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
    k text;
    out jsonb := t;
BEGIN
    IF jsonb_typeof(t) <> 'object' THEN
        RETURN t;
    END IF;
    FOREACH k IN ARRAY ARRAY['password_reset', 'password_reset_otp', 'magic_link', 'login_otp_email', 'verify_email',
                             'verify_email_otp', 'set_password_invite', 'signup_account_exists',
                             'signup_phone_in_use', 'account_locked'] LOOP
        IF jsonb_typeof(out -> k) = 'object' THEN
            out := jsonb_set(out, ARRAY[k], (out -> k) - 'cc' - 'bcc');
        END IF;
    END LOOP;
    FOREACH k IN ARRAY ARRAY['password_changed', 'account_security_notice', 'account_locked'] LOOP
        IF jsonb_typeof(out -> k) = 'object' THEN
            out := jsonb_set(out, ARRAY[k], (out -> k) - 'enabled');
        END IF;
    END LOOP;
    RETURN out;
END $$;

CREATE OR REPLACE FUNCTION pg_temp.clamp_int(v jsonb, lo int, hi int) RETURNS jsonb
    LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE WHEN jsonb_typeof(v) = 'number' THEN to_jsonb(greatest(lo, least(hi, v::numeric::int))) ELSE v END
$$;

CREATE OR REPLACE FUNCTION pg_temp.clean_verification(p jsonb) RETURNS jsonb
    LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
    s text;
    out jsonb := p;
BEGIN
    IF jsonb_typeof(p) <> 'object' THEN
        RETURN p;
    END IF;
    FOREACH s IN ARRAY ARRAY['signup', 'login', 'recovery'] LOOP
        IF jsonb_typeof(out -> s) = 'object' THEN
            IF out -> s ? 'otpLength' THEN
                out := jsonb_set(out, ARRAY[s, 'otpLength'], pg_temp.clamp_int(out -> s -> 'otpLength', 6, 8));
            END IF;
            IF out -> s ? 'otpTtlSeconds' THEN
                out := jsonb_set(out, ARRAY[s, 'otpTtlSeconds'], pg_temp.clamp_int(out -> s -> 'otpTtlSeconds', 60, 600));
            END IF;
        END IF;
    END LOOP;
    RETURN out;
END $$;

CREATE OR REPLACE FUNCTION pg_temp.clean_tokens(p jsonb) RETURNS jsonb
    LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
    k text;
    out jsonb := p;
BEGIN
    IF jsonb_typeof(p) <> 'object' THEN
        RETURN p;
    END IF;
    FOREACH k IN ARRAY ARRAY['accessTokenTtlSeconds', 'idTokenTtlSeconds', 'clientCredentialsTtlSeconds'] LOOP
        IF out ? k THEN
            out := jsonb_set(out, ARRAY[k], pg_temp.clamp_int(out -> k, 60, 3600));
        END IF;
    END LOOP;
    IF out ? 'authorizationCodeTtlSeconds' THEN
        out := jsonb_set(out, '{authorizationCodeTtlSeconds}', pg_temp.clamp_int(out -> 'authorizationCodeTtlSeconds', 60, 600));
    END IF;
    RETURN out;
END $$;

-- ---- Platform ----------------------------------------------------------------------------------------------
UPDATE platform.platform_setting SET value = pg_temp.clean_verification(value) WHERE key = 'verification_policy';
UPDATE platform.platform_setting SET value = pg_temp.clean_tokens(value) WHERE key = 'token_policy';

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
        EXECUTE format('UPDATE %I.application_setting SET value = pg_temp.clean_templates(value)
                        WHERE application_id = $1 AND setting_key = ''email_templates''', sch) USING r.id;
        EXECUTE format('UPDATE %I.application_setting SET value = pg_temp.clean_verification(value)
                        WHERE application_id = $1 AND setting_key = ''verification_policy''', sch) USING r.id;
        EXECUTE format('UPDATE %I.application_setting SET value = pg_temp.clean_tokens(value)
                        WHERE application_id = $1 AND setting_key = ''token_policy''', sch) USING r.id;
        EXECUTE format('UPDATE %I.application_setting SET value = value - ''logoUrl''
                        WHERE application_id = $1 AND setting_key = ''appearance''
                          AND coalesce(value ->> ''logoUrl'', '''') <> ''''
                          AND value ->> ''logoUrl'' NOT ILIKE ''https://%%''', sch) USING r.id;
    END LOOP;
END $$;
