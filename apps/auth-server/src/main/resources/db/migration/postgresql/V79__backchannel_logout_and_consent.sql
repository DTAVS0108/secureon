-- OpenID Connect Back-Channel Logout: an OAuth client may register the URL SecureOne POSTs a signed logout_token to
-- when the user signs out of SecureOne, so the application ends its own session too.
ALTER TABLE platform.oauth_client ADD COLUMN IF NOT EXISTS backchannel_logout_uri VARCHAR(1024);

-- Third-party clients: the user is asked which access to give the app (OAuth consent screen) before it gets tokens.
ALTER TABLE platform.oauth_client ADD COLUMN IF NOT EXISTS consent_required BOOLEAN NOT NULL DEFAULT FALSE;
