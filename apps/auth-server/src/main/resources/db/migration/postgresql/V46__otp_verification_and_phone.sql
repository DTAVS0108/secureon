-- OTP challenges (audit trail) + verification policy + SMS settings + phone uniqueness

CREATE TABLE IF NOT EXISTS otp_challenge (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID REFERENCES user_account(id) ON DELETE CASCADE,
    tenant_id       UUID NOT NULL,
    application_id  UUID,
    purpose         VARCHAR(32) NOT NULL,
    channel         VARCHAR(16) NOT NULL,
    destination     VARCHAR(255) NOT NULL,
    code_hash       VARCHAR(64) NOT NULL,
    attempts        INT NOT NULL DEFAULT 0,
    max_attempts    INT NOT NULL DEFAULT 5,
    expires_at      TIMESTAMPTZ NOT NULL,
    consumed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip_address      VARCHAR(45),
    user_agent      TEXT
);

CREATE INDEX IF NOT EXISTS idx_otp_challenge_active
    ON otp_challenge (user_id, purpose)
    WHERE consumed_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_otp_challenge_expires
    ON otp_challenge (expires_at)
    WHERE consumed_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_account_tenant_phone
    ON user_account (tenant_id, phone)
    WHERE phone IS NOT NULL AND phone <> '';

INSERT INTO platform_setting (key, value)
VALUES (
    'verification_policy',
    '{
      "signup": {
        "requiredChannels": ["email"],
        "optionalChannels": ["phone"],
        "defaultMode": "link",
        "allowedModes": ["link", "otp"],
        "allowUserChannelChoice": false,
        "otpLength": 6,
        "otpTtlSeconds": 600,
        "maxAttempts": 5,
        "resendCooldownSeconds": 60
      },
      "login": {
        "identifierTypes": ["email", "phone"],
        "passwordlessModes": ["email_otp", "phone_otp", "magic_link"],
        "mfaRequired": "optional"
      },
      "recovery": {
        "passwordResetMode": "link",
        "allowedChannels": ["email"],
        "fallbackToLink": true
      }
    }'::jsonb
)
ON CONFLICT (key) DO NOTHING;

INSERT INTO platform_setting (key, value)
VALUES (
    'sms',
    '{
      "provider": "log",
      "twilioAccountSid": "",
      "twilioAuthToken": "",
      "twilioFromNumber": "",
      "enabled": false
    }'::jsonb
)
ON CONFLICT (key) DO NOTHING;

-- Expose verification-policy to applications (merge into app_settings_exposure if present)
UPDATE platform_setting
SET value = value || '{"verification-policy": true}'::jsonb
WHERE key = 'app_settings_exposure'
  AND NOT (value ? 'verification-policy');
