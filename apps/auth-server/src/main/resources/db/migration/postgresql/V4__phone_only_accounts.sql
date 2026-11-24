-- Accounts created with a phone number only (phone sign-up): email becomes optional, but every
-- account keeps at least one of email or phone to sign in with.

ALTER TABLE platform.user_account ALTER COLUMN email DROP NOT NULL;

ALTER TABLE platform.user_account DROP CONSTRAINT IF EXISTS user_account_email_or_phone;
ALTER TABLE platform.user_account ADD CONSTRAINT user_account_email_or_phone
    CHECK (email IS NOT NULL OR (phone IS NOT NULL AND phone <> ''));
