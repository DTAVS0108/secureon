-- Two Factor Authentication per application: each application manages its own settings, so the state those
-- settings drive is kept per application too (docs/user-guide/12-two-step-verification.md).

-- 1. Setup state per user and application. The zero UUID means "no application": sign-ins without one and,
--    for exemptions, every application of the user's tenant.
ALTER TABLE platform.user_two_step_state
    ADD COLUMN IF NOT EXISTS application_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000';
ALTER TABLE platform.user_two_step_state DROP CONSTRAINT IF EXISTS user_two_step_state_pkey;
ALTER TABLE platform.user_two_step_state ADD PRIMARY KEY (user_id, application_id);

-- Setup progress so far (deadline, skips, "don't ask again", reminders) continues in each application of the
-- user's tenant, so nobody gets a new grace period. Exemptions stay on the "every application" row.
INSERT INTO platform.user_two_step_state (user_id, application_id, first_prompted_at, next_prompt_at, skips_used,
    deadline, opted_out, extended_by, reminders_sent)
SELECT s.user_id, a.id, s.first_prompted_at, s.next_prompt_at, s.skips_used, s.deadline, s.opted_out,
       s.extended_by, s.reminders_sent
FROM platform.user_two_step_state s
JOIN platform.user_account u ON u.id = s.user_id
JOIN platform.application a ON a.tenant_id = u.tenant_id
WHERE s.application_id = '00000000-0000-0000-0000-000000000000'
  AND (s.deadline IS NOT NULL OR s.opted_out OR s.next_prompt_at IS NOT NULL OR s.skips_used > 0)
ON CONFLICT (user_id, application_id) DO NOTHING;

-- 2. A remembered device skips the second step only in the application it was remembered in. Devices
--    remembered before this release have no application: they count only for sign-ins without one, so users
--    verify once more in each application.
ALTER TABLE platform.trusted_device
    ADD COLUMN IF NOT EXISTS application_id UUID REFERENCES platform.application (id) ON DELETE CASCADE;

-- 3. "Two Factor Authentication" is its own section under Platform settings → For applications, separate from
--    sign-in methods. It starts as the Authentication section was, so nothing changes on upgrade.
UPDATE platform.platform_setting
SET value = value || jsonb_build_object('two-factor', COALESCE((value ->> 'auth-methods')::boolean, true))
WHERE key = 'app_settings_exposure'
  AND NOT (value ? 'two-factor');
