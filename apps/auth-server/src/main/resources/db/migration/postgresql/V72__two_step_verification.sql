-- Two-step verification (docs/plans/two-factor-authentication.md).

-- 1. Tenant defaults: settings a tenant super admin sets for all of the tenant's applications, between the
--    platform policy and each application's own. Read and written only through the admin API, which checks
--    the caller's rights (no RLS policy, like application_setting).
CREATE TABLE IF NOT EXISTS platform.tenant_setting (
    tenant_id   UUID         NOT NULL REFERENCES platform.tenant (id) ON DELETE CASCADE,
    setting_key VARCHAR(150) NOT NULL,
    value       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, setting_key)
);

-- 2. Per-user setup state: reminders, skips and the deadline when two-step verification is required, and
--    admin exemptions.
CREATE TABLE IF NOT EXISTS platform.user_two_step_state (
    user_id           UUID PRIMARY KEY REFERENCES platform.user_account (id) ON DELETE CASCADE,
    first_prompted_at TIMESTAMPTZ,
    next_prompt_at    TIMESTAMPTZ,
    skips_used        INT          NOT NULL DEFAULT 0,
    deadline          TIMESTAMPTZ,
    opted_out         BOOLEAN      NOT NULL DEFAULT false,
    exempt_until      TIMESTAMPTZ,
    exempt_reason     VARCHAR(500),
    exempted_by       VARCHAR(320),
    extended_by       VARCHAR(320),
    reminders_sent    VARCHAR(100) NOT NULL DEFAULT '',
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS user_two_step_state_deadline_idx
    ON platform.user_two_step_state (deadline) WHERE deadline IS NOT NULL;

-- 3. "MFA required" moves from the verification policy (login.mfaRequired) to the two-step policy (mode).
--    Copy every existing value so behaviour does not change on upgrade.
INSERT INTO platform.platform_setting (key, value, updated_at)
SELECT 'mfa_policy', jsonb_build_object('mode', v.value -> 'login' ->> 'mfaRequired'), now()
FROM platform.platform_setting v
WHERE v.key = 'verification_policy'
  AND v.value -> 'login' ->> 'mfaRequired' IN ('off', 'optional', 'required')
ON CONFLICT (key) DO UPDATE
    SET value = platform.platform_setting.value || jsonb_build_object('mode', EXCLUDED.value ->> 'mode');

INSERT INTO platform.application_setting (application_id, setting_key, value, updated_at)
SELECT v.application_id, 'mfa_policy', jsonb_build_object('mode', v.value -> 'login' ->> 'mfaRequired'), now()
FROM platform.application_setting v
WHERE v.setting_key = 'verification_policy'
  AND v.value -> 'login' ->> 'mfaRequired' IN ('off', 'optional', 'required')
ON CONFLICT (application_id, setting_key) DO UPDATE
    SET value = platform.application_setting.value || jsonb_build_object('mode', EXCLUDED.value ->> 'mode');
