-- Platform MFA policy defaults (step-up behavior complements verification_policy.login.mfaRequired).

INSERT INTO platform_setting (key, value)
VALUES (
    'mfa_policy',
    '{
      "stepUpAfterPassword": true,
      "recoveryCodesPerEnrollment": 10,
      "challengeTtlSeconds": 300,
      "maxChallengeAttempts": 5
    }'::jsonb
)
ON CONFLICT (key) DO NOTHING;
