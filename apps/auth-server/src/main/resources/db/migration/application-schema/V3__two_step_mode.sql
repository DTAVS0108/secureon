-- Two-step verification: the application's "MFA required" (verification policy, login) becomes the mode of
-- its two-step policy, so behaviour does not change on upgrade. Kept on the verification policy as well.
INSERT INTO application_setting (application_id, setting_key, value, updated_at)
SELECT v.application_id, 'mfa_policy', jsonb_build_object('mode', v.value -> 'login' ->> 'mfaRequired'), now()
FROM application_setting v
WHERE v.setting_key = 'verification_policy'
  AND v.value -> 'login' ->> 'mfaRequired' IN ('off', 'optional', 'required')
ON CONFLICT (application_id, setting_key) DO UPDATE
    SET value = application_setting.value || jsonb_build_object('mode', EXCLUDED.value ->> 'mode')
