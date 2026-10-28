-- Token lookups (introspection, refresh, revocation, code exchange, device flow) search oauth2_authorization by
-- token value; with only the primary key every lookup read the whole table (introspection fell to ~27 a second
-- once ~20,000 tokens existed). Hash indexes: exact-match only, and no size limit for long JWT values (B-tree
-- entries must stay under ~2.7 kB). Spring's "any token type" lookup ORs these columns; PostgreSQL combines them.
CREATE INDEX IF NOT EXISTS oauth2_authorization_state_idx ON platform.oauth2_authorization USING hash (state);
CREATE INDEX IF NOT EXISTS oauth2_authorization_code_idx ON platform.oauth2_authorization USING hash (authorization_code_value);
CREATE INDEX IF NOT EXISTS oauth2_authorization_access_idx ON platform.oauth2_authorization USING hash (access_token_value);
CREATE INDEX IF NOT EXISTS oauth2_authorization_id_token_idx ON platform.oauth2_authorization USING hash (oidc_id_token_value);
CREATE INDEX IF NOT EXISTS oauth2_authorization_refresh_idx ON platform.oauth2_authorization USING hash (refresh_token_value);
CREATE INDEX IF NOT EXISTS oauth2_authorization_user_code_idx ON platform.oauth2_authorization USING hash (user_code_value);
CREATE INDEX IF NOT EXISTS oauth2_authorization_device_code_idx ON platform.oauth2_authorization USING hash (device_code_value);

-- Sign-out everywhere, consent withdrawal and application removal delete by user and by client.
CREATE INDEX IF NOT EXISTS oauth2_authorization_principal_idx ON platform.oauth2_authorization (principal_name);
CREATE INDEX IF NOT EXISTS oauth2_authorization_client_idx ON platform.oauth2_authorization (registered_client_id);

-- ExpiredAuthorizationCleanup: when the last token of an authorization expired (GREATEST skips NULLs).
CREATE INDEX IF NOT EXISTS oauth2_authorization_last_expiry_idx ON platform.oauth2_authorization (
    (GREATEST(authorization_code_expires_at, access_token_expires_at, oidc_id_token_expires_at,
              refresh_token_expires_at, user_code_expires_at, device_code_expires_at)));
