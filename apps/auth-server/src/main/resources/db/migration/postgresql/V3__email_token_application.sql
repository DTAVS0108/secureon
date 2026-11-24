-- The application an emailed link was sent for (magic links): verifying the link signs in to that
-- application's rules (its two-step setting) even when the app sends only the token.

ALTER TABLE platform.email_token ADD COLUMN IF NOT EXISTS application_id uuid;
