-- OAuth2 JWT signing keys: persisted in platform_setting (key = oauth2_signing_jwk) at runtime.
-- Production: set SECUREONE_JWK_PRIVATE_KEY_PEM or pre-seed that row before first deploy.

SELECT 1;
