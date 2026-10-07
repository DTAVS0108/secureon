# Deploy SecureOne

> **Full guide (two paths):** [24 — Deployment: image + env & enterprise](../24-deployment-image-env-and-enterprise.md)
>
> - **Path A** — Docker images + env only (no source on server), then admin UI for clients  
> - **Path B** — Own the monorepo, CI build, Helm/K8s for enterprise  

## Prerequisites

- PostgreSQL 16+ with `platform` schema (Flyway runs on auth-server startup)
- Redis 7+ for sessions, OTP, rate limits, MFA challenges
- SMTP or provider configured in Platform → Notifications

## Local (development)

```powershell
docker compose -f deploy/docker-compose.yml up -d
cd apps/auth-server; .\gradlew.bat bootRun
cd apps/admin-web; npm run dev
```

## Docker images

```powershell
docker build -f deploy/Dockerfile.auth-server -t secureone/auth-server apps/auth-server
docker build -f deploy/Dockerfile.admin-web -t secureone/admin-web apps/admin-web
```

## Kubernetes (Helm)

1. Create secret `secureone-auth-server` with at least:
   - `SECUREONE_DB_URL`, `SECUREONE_DB_USERNAME`, `SECUREONE_DB_PASSWORD`
   - `SECUREONE_REDIS_URL`
   - `SECUREONE_OTP_PEPPER` (strong random value)
   - `SECUREONE_ISSUER_URL`, `SECUREONE_PUBLIC_BASE_URL`, `SECUREONE_ADMIN_WEB_URL`

2. Install chart:

```bash
helm upgrade --install secureone deploy/helm/secureone \
  -f my-values.yaml \
  --set ingress.enabled=true \
  --set ingress.host=auth.example.com
```

3. Verify:

```bash
kubectl rollout status deployment/secureone-auth-server
curl -s https://auth.example.com/actuator/health
```

## Post-deploy checks

- `/actuator/health` → UP
- `/actuator/prometheus` → metrics (restrict network access in prod)
- Admin login + audit log shows real client IP (not `127.0.0.1`)
