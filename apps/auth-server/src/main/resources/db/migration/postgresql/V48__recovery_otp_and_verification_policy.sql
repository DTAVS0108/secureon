-- Expand verification policy (recovery OTP modes, login OTP params) and add recovery_otp feature flag

UPDATE platform_setting
SET value = jsonb_set(
    value,
    '{recovery}',
    COALESCE(value->'recovery', '{}'::jsonb)
        || '{
          "allowedModes": ["link", "otp"],
          "allowedChannels": ["email", "phone"],
          "allowUserChannelChoice": true,
          "requireVerifiedChannel": true,
          "otpLength": 6,
          "otpTtlSeconds": 600,
          "maxAttempts": 5,
          "resendCooldownSeconds": 60
        }'::jsonb,
    true)
WHERE key = 'verification_policy';

UPDATE platform_setting
SET value = jsonb_set(
    value,
    '{login}',
    COALESCE(value->'login', '{}'::jsonb)
        || '{
          "otpLength": 6,
          "otpTtlSeconds": 600,
          "maxAttempts": 5,
          "resendCooldownSeconds": 60
        }'::jsonb,
    true)
WHERE key = 'verification_policy';

-- Merge recovery_otp into feature_flags catalog if missing
UPDATE platform_setting
SET value = (
    SELECT jsonb_agg(flag)
    FROM (
        SELECT flag
        FROM jsonb_array_elements(value) AS flag
        UNION ALL
        SELECT '{"key":"recovery_otp","name":"Recovery OTP","description":"Password reset via email/SMS OTP","enabled":true,"rollout":100,"category":"identity"}'::jsonb
        WHERE NOT EXISTS (
            SELECT 1 FROM jsonb_array_elements(value) f WHERE f->>'key' = 'recovery_otp'
        )
    ) merged
)
WHERE key = 'feature_flags'
  AND NOT EXISTS (
    SELECT 1 FROM jsonb_array_elements(value) f WHERE f->>'key' = 'recovery_otp'
  );
