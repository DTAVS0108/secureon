# Deploy SecureOne

> **Full guide (two paths):** [24 — Deployment: image + env & enterprise](../24-deployment-image-env-and-enterprise.md)
>
> - **Path A** — Docker images + env only (no source on server), then admin UI for clients  
> - **Path B** — Own the monorepo, CI build, Helm/K8s for enterprise  

## Prerequisites

- PostgreSQL 16+ (the database is created and upgraded at start-up, or by the Helm migration job; see [26](../26-configuration-as-code-and-upgrades.md#part-2--upgrades))
- Redis 7+ for sessions, OTP, rate limits, MFA challenges
- SMTP or provider configured in Platform → Notifications

## Local (development)

```powershell
docker compose -f deploy/docker-compose.yml up -d
cd apps/auth-server; .\gradlew.bat bootRun
cd apps/admin-web; pnpm dev   # console on :3001; open http://localhost:9000/admin
```

## Docker images

```powershell
docker build -f deploy/Dockerfile -t secureone/secureone:<version> .   # repo root; needs Maven Central + build.shibboleth.net (OpenSAML)
# One image: auth-server on :9000 with the admin console at /admin (and docs/ for Confluence).
```

## Kubernetes (Helm)

1. Create secret `secureone` (`existingSecret` in the values) with at least:
   - `SECUREONE_DB_URL`, `SECUREONE_DB_USERNAME`, `SECUREONE_DB_PASSWORD`
   - `SECUREONE_REDIS_URL`
   - `SECUREONE_OTP_PEPPER` (strong random value)
   - `SECUREONE_ENCRYPTION_KEY` (`base64:` + 32 random bytes — encrypts stored secrets and generated keys; back it up separately)
   - `SECUREONE_JWK_PRIVATE_KEY_PEM` (token signing key; required with `SECUREONE_ENV=production`)
   - optional `SECUREONE_SAML_SP_KEY_PEM` + `SECUREONE_SAML_SP_CERT_PEM` (SAML SSO)
   - `SECUREONE_ISSUER_URL`, `SECUREONE_PUBLIC_BASE_URL` (or set them under `env` in the values)

   Generation commands: [24 § A2](../24-deployment-image-env-and-enterprise.md#a2--generate-secrets).

2. Install chart (a pre-install / pre-upgrade job migrates the database first; `migrationJob.enabled`):

```bash
helm upgrade --install secureone deploy/helm/secureone \
  -f my-values.yaml \
  --set ingress.enabled=true \
  --set ingress.host=auth.example.com
```

3. Verify:

```bash
kubectl rollout status deployment/secureone
curl -s https://auth.example.com/.well-known/openid-configuration
kubectl -n secureone exec deploy/secureone -- wget -qO- http://127.0.0.1:9001/actuator/health
```

## Upgrade

1. Back up the database.
2. `helm upgrade secureone deploy/helm/secureone --reuse-values --set image.tag=<new>`; the migration job runs first, and a failure stops the upgrade with the old pods still running. (Docker: start the new image, or run it once with `SECUREONE_DB_MIGRATE=only` first.)
3. Check `:9001/actuator/info` shows the new `build.version` and `database` versions. Rollback: [rollback.md](rollback.md).

## Post-deploy checks

- `:9001/actuator/health` → UP (management port, pod-internal; `/actuator` is not served on the public port)
- `:9001/actuator/prometheus` → metrics (scraped through the `prometheus.io/port` annotation)
- Admin login + audit log shows real client IP (not `127.0.0.1`)
- No `SECUREONE_ENCRYPTION_KEY is not set` warning in the auth-server log
- `/.well-known/openid-configuration` and `/.well-known/oauth-protected-resource` show `https://` URLs (forwarded headers work)
- If SAML is used: `/api/v1/auth/federation/saml/{alias}/metadata?applicationId=…` shows `https://` ACS/SLO URLs, and an SSO sign-in plus sign-out at the IdP both work through the proxy (no CORS rules on `/saml/acs/*`, `/saml/slo/*`)
