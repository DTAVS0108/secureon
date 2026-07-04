# Rollback

## Kubernetes / Helm

```bash
helm history secureone
helm rollback secureone <revision>
kubectl rollout status deployment/secureone-auth-server
```

If only auth-server changed, scale admin-web independently — it is stateless.

## Docker / VM

1. Stop current auth-server container/process.
2. Start previous image tag (keep env vars and secrets unchanged).
3. Confirm Flyway: newer migrations are **not** auto-reverted — if a bad migration shipped, restore DB from backup before rollback (see incident runbook).

## Verification after rollback

- Health endpoints UP
- Login + OAuth token exchange smoke test
- No elevated error rate on `/oauth2/token` or `/login`

## Database note

Flyway migrations are forward-only. Rolling back application code to a version that expects an older schema requires a matching DB restore or a forward-fix migration.
