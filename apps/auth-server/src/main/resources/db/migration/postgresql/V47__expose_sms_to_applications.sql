-- Expose per-application SMS provider settings (merge into app_settings_exposure if present)
UPDATE platform_setting
SET value = value || '{"sms": true}'::jsonb
WHERE key = 'app_settings_exposure'
  AND NOT (value ? 'sms');
