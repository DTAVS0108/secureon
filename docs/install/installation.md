---
title: Installation
summary: Install SecureOne on one server from its image, step by step
order: 1
audience: public
---

# Installation

SecureOne is installed from one container image, `dmtavs0108/secureone:1.0.0`. You do not build anything: you give
it a database, a cache and a few secrets, start it, and do everything else in the admin console.

This page installs SecureOne on **one server** with Docker Compose. For your own PostgreSQL and Redis, Kubernetes,
TLS and every setting, see [Deployment](deployment.md). To try SecureOne on your laptop first, use the
[Quickstart](../get-started/quickstart.md).

## What you need

| Item | Details |
|---|---|
| A Linux server | 2 CPU and 2 GB memory is enough to start; Docker with Compose installed |
| A domain name | For example `id.example.com`, pointing at the server |
| HTTPS | A reverse proxy or load balancer with a certificate in front of port 9000 |
| Email (SMTP) | For verification, password reset and invitations; you can add it later in the console |

SecureOne uses **PostgreSQL 16+** and **Redis 7+**. The Compose file below runs both next to SecureOne; you can use
managed services instead.

## 1. Create the secrets

On the server, in a new folder such as `/opt/secureone`:

```bash
mkdir -p /opt/secureone && cd /opt/secureone
openssl rand -hex 32                                                   # one-time code pepper
openssl rand -base64 32                                                # encryption key
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwk.pem   # token signing key
```

Keep these values in your password manager or secret store. **Back up the encryption key separately from the
database**: secrets stored by SecureOne cannot be read without it.

## 2. Write the settings file

Save this as `/opt/secureone/secureone.env`, with your own values:

```bash
# Your public address (https)
SECUREONE_ISSUER_URL=https://id.example.com
SECUREONE_PUBLIC_BASE_URL=https://id.example.com

# First platform operator: the first sign-in asks for a new password
SECUREONE_BOOTSTRAP_ADMIN_EMAIL=owner@example.com
SECUREONE_BOOTSTRAP_ADMIN_PASSWORD=Change-This-Strong-Password-1!

# Database and cache (the services in docker-compose.yml)
SECUREONE_DB_URL=jdbc:postgresql://postgres:5432/secureone?currentSchema=platform
SECUREONE_DB_USERNAME=secureone
SECUREONE_DB_PASSWORD=choose-a-database-password
SECUREONE_REDIS_URL=redis://redis:6379

# Secrets from step 1
SECUREONE_OTP_PEPPER=<the hex value>
SECUREONE_ENCRYPTION_KEY=base64:<the base64 value>
# The whole jwk.pem on one line, with \n for each line break
SECUREONE_JWK_PRIVATE_KEY_PEM=-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----

# Email (optional now; can also be set later under Settings › Notifications)
SECUREONE_SMTP_HOST=smtp.example.com
SECUREONE_SMTP_PORT=587
SECUREONE_SMTP_USERNAME=smtp-user
SECUREONE_SMTP_PASSWORD=smtp-password
SECUREONE_SMTP_AUTH=true
SECUREONE_SMTP_STARTTLS=true
```

```bash
chmod 600 /opt/secureone/secureone.env
```

## 3. Write the Compose file

Save this as `/opt/secureone/docker-compose.yml`. Use the same database password as in the settings file:

```yaml
name: secureone

services:
  secureone:
    image: dmtavs0108/secureone:1.0.0
    env_file: ./secureone.env
    ports:
      - "127.0.0.1:9000:9000"        # your HTTPS proxy forwards to this port
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_started

  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: secureone
      POSTGRES_USER: secureone
      POSTGRES_PASSWORD: choose-a-database-password
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U secureone -d secureone"]
      interval: 5s
      retries: 20
    restart: unless-stopped

  redis:
    image: redis:7-alpine
    restart: unless-stopped

volumes:
  pgdata:
```

## 4. Start SecureOne

```bash
cd /opt/secureone
docker compose up -d
docker compose logs -f secureone
```

SecureOne is ready when the log shows `Started AuthServerApplication`. The first start creates the database tables
by itself; you never run SQL by hand.

## 5. Put HTTPS in front

Point your reverse proxy or load balancer for `id.example.com` at `http://127.0.0.1:9000` and forward the
`X-Forwarded-Proto`, `X-Forwarded-Host` and `X-Forwarded-For` headers. Everything is on this one address: sign-in
pages, APIs and the admin console at `/admin`. See [Deployment](deployment.md#a7--tls--reverse-proxy) for details.

## 6. Sign in to the console

1. Open `https://id.example.com/admin`.
2. Choose **Platform operator** and sign in with the email and password from the settings file.
3. Choose your own password, then set up an authenticator app. Save the recovery codes it shows.
4. Remove `SECUREONE_BOOTSTRAP_ADMIN_PASSWORD` from the settings file. It is only used once.

## 7. Check that everything works

```bash
curl -s https://id.example.com/api/info                              # "status":"UP", "version":"1.0.0"
curl -s https://id.example.com/.well-known/openid-configuration      # sign-in discovery document
docker compose ps                                                    # secureone is "healthy"
```

## Keep it running

| Task | How |
|---|---|
| Back up | `docker compose exec postgres pg_dump -U secureone -Fc secureone > secureone-$(date +%F).dump`, plus the settings file and encryption key |
| Upgrade | Change the image version in `docker-compose.yml`, then `docker compose pull && docker compose up -d`. The database upgrades itself; back up first |
| Read the log | `docker compose logs --since 1h secureone` |
| Restart | `docker compose restart secureone` |
| Stop | `docker compose down` (keeps the data; `down -v` deletes it) |

## Next steps

- [Operator getting started](../get-started/operator-getting-started.md): tenants, applications and operators
- [Integration guide](../integrate/integration-guide.md): connect your first application
- [Configuration as code and upgrades](configuration-and-upgrades.md): keep tenants in files, upgrade and roll back
- [Runbooks](runbooks/index.md): step-by-step procedures for day-to-day operations
