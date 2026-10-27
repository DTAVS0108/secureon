-- private_key_jwt client authentication (RFC 7523, OIDC Core 9): the client signs a short-lived JWT with its own
-- private key instead of sending a secret. SecureOne keeps only the public keys: a JWKS URL the client serves
-- (keys rotate there) or the JWKS itself.
ALTER TABLE platform.oauth_client
    ADD COLUMN IF NOT EXISTS jwks_uri VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS jwks JSONB;
