# Key rotation

## OAuth signing keys (JWT)

Persistent keys live in `platform_setting` (managed by `OAuth2SigningKeyService`).

1. Generate new key via admin procedure or redeploy bootstrap on empty store (dev only).
2. JWKS endpoint serves active + secondary keys during rotation window.
3. After all issued access tokens expire (max TTL from token policy, default 15m prod), retire old key.
4. Restart all auth-server replicas to pick up in-memory JWKS cache if applicable.

## OTP pepper (`SECUREONE_OTP_PEPPER`)

Rotating invalidates unconsumed OTP hashes.

1. Schedule maintenance window.
2. Update secret in vault/Kubernetes secret.
3. Rolling restart auth-server pods.
4. Users mid-OTP must request a new code.

## Database / Redis credentials

1. Rotate in PostgreSQL/Redis.
2. Update Kubernetes secret or env.
3. Rolling restart auth-server (sessions in Redis survive if URL unchanged).

## Session invalidation after compromise

```http
DELETE /api/v1/account/sessions
```

Platform operators can review `login_history` and revoke suspicious sessions per user via the sessions API when authenticated as that user, or flush Redis session namespace in emergencies (`secureone:session:*` — forces all users to re-login).
