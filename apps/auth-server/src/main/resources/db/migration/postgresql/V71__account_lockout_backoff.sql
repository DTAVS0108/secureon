-- Lockout back-off: how many lockouts in a row since the last successful sign-in. Each one doubles the lock
-- time (up to 24 hours); a successful sign-in, a password reset or an admin unlock sets it back to 0.
ALTER TABLE platform.user_account
    ADD COLUMN IF NOT EXISTS lockout_level INT NOT NULL DEFAULT 0;
