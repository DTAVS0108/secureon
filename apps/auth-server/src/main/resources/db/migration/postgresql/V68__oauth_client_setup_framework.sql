-- The framework chosen for a client in "Connect an app" (for example "electron" or "python-bff"), so the
-- client page shows the same setup code later. Optional: clients registered with the plain forms have none
-- and the console guesses from the redirect URL. Console display only; never used for authorization.
ALTER TABLE platform.oauth_client
    ADD COLUMN IF NOT EXISTS setup_framework VARCHAR(64);
