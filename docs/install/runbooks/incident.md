---
title: Incident response
summary: What to do when something goes wrong
order: 3
audience: operator
---

# Incident response

## Severity guide

| Level | Examples |
|-------|----------|
| S1 | Auth down, token endpoint 5xx, DB unavailable |
| S2 | Elevated login failures, rate-limit storms, single-tenant data concern |
| S3 | Degraded mail/SMS, non-critical admin UI errors |

## First steps (all levels)

1. Check `/actuator/health` and `/actuator/prometheus` (error rates, JVM, DB pool).
2. Inspect auth-server logs — prod uses ECS JSON; filter on `requestId`, `principal`, `clientIp`.
3. Confirm Postgres and Redis connectivity from a pod: env vars, network policies.

## Common scenarios

### Auth-server not starting (prod)

- Startup validator rejects default OTP pepper or dev credentials — fix secrets.
- Flyway migration failure — check logs, restore DB if needed, deploy fix-forward migration.

### OAuth / login failures spike

- Check Redis (sessions, rate limits, MFA challenges).
- Check `RateLimitFilter` 429 volume — adjust limits temporarily if legitimate traffic.
- Review recent deploy — consider [rollback](rollback.md).

### Suspected credential leak

1. Rotate affected user passwords / revoke sessions (`DELETE /api/v1/account/sessions`).
2. Enable stricter lockout temporarily via platform password policy.
3. Review `audit_log` and `login_history` for actor IP and timestamps.

### Suspected cross-tenant data access

- RLS uses `app.tenant_id` per request — verify `TenantContextFilter` resolves tenant on app-scoped routes.
- Audit queries should include tenant filter; platform bypass only when tenant context is empty.

## Communication

- S1: page on-call, status update every 30 minutes until stable.
- Document timeline, root cause, and follow-up migrations/tests in post-incident notes.
