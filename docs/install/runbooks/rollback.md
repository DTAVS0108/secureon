---
title: Rollback
summary: Return to the previous release safely
order: 2
audience: operator
---

# Rollback

Migrations are forward-only, and a SecureOne image **refuses to start** against a database that a newer release has upgraded (it names both versions in the log). So:

- **The upgrade failed before the database changed** (the migration job failed, or the new pods never started): nothing to restore. With Helm the old pods keep running; `helm rollback` if needed.
- **The database was upgraded and the new release misbehaves:** restore the backup taken before the upgrade, then start the previous image. Or fix forward with a patch release.

See [Configuration as code and upgrades](../configuration-and-upgrades.md#rolling-back).

## Kubernetes / Helm

```bash
helm history secureone
# If the database was upgraded: restore the pre-upgrade backup first (incident runbook).
helm rollback secureone <revision>
kubectl rollout status deployment/secureone
```

## Docker / VM

1. Stop the current container.
2. If the database was upgraded: restore the pre-upgrade backup.
3. Start the previous image tag with the same environment and secrets (including `SECUREONE_ENCRYPTION_KEY`).

## Verification after rollback

- `:9001/actuator/health` UP; `:9001/actuator/info` shows the expected `build.version` and `database` versions
- Console sign-in and one OAuth token request work
- No elevated error rate on `/oauth2/token` or `/login`
