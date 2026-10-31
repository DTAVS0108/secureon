---
title: Installation
summary: Install SecureOne from its image with a few variables, then finish setup in the browser
order: 1
audience: public
---

# Installation

SecureOne is installed from one container image, `dmtavs0108/secureone:1.1.0`. There is nothing to build and no
file to edit: you start it with a few variables, and do the rest in your browser.

| Before it starts | After it starts, in the browser |
|---|---|
| A PostgreSQL database and Redis it can reach | Check the installation, set up email |
| Your public address (`https://id.example.com`) | Back up the encryption key |
| A volume for its encryption key | Create the first operator, then connect applications |

To try SecureOne on your laptop first, use the [Quickstart](../get-started/quickstart.md). For Kubernetes, every
setting and the details behind each step, see [Deployment](deployment.md).

## What you need

- A server with **Docker** (with Compose for option A), or Docker Desktop
- A **domain name** for SecureOne, for example `id.example.com`, and **HTTPS** in front of it (a reverse proxy or
  load balancer)
- **PostgreSQL 16+** and **Redis 7+**: option A starts both for you; option B uses ones you already have

## Option A: Docker Compose (database and cache included)

Save this as `docker-compose.yml` in an empty folder, and change the two values marked `# change`:

```yaml
name: secureone

services:
  secureone:
    image: dmtavs0108/secureone:1.1.0
    ports:
      - "127.0.0.1:9000:9000"        # your HTTPS proxy forwards to this port
    environment:
      SECUREONE_PUBLIC_BASE_URL: https://id.example.com          # change: your address
      SECUREONE_DB_URL: jdbc:postgresql://postgres:5432/secureone?currentSchema=platform
      SECUREONE_DB_USERNAME: secureone
      SECUREONE_DB_PASSWORD: &db-password choose-a-database-password   # change
      SECUREONE_REDIS_URL: redis://redis:6379
    volumes:
      - secureone-data:/var/lib/secureone   # the encryption key lives here
    restart: unless-stopped

  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: secureone
      POSTGRES_USER: secureone
      POSTGRES_PASSWORD: *db-password
    volumes:
      - pgdata:/var/lib/postgresql/data
    restart: unless-stopped

  redis:
    image: redis:7-alpine
    restart: unless-stopped

volumes:
  secureone-data:
  pgdata:
```

Start it:

```bash
docker compose up -d
docker compose logs -f secureone
```

SecureOne waits until the database and Redis are ready, creates its tables, and prints a **setup code** in the log:

```
 SecureOne is not set up yet: no platform operator exists.
 Open https://id.example.com/admin/setup and enter this setup code:  9NNC-DZ3H-WF6S
```

## Option B: `docker run` with your own database and Redis

Create an empty database and a user for SecureOne in your PostgreSQL, then:

```bash
docker run -d --name secureone --restart unless-stopped \
  -p 127.0.0.1:9000:9000 \
  -v secureone-data:/var/lib/secureone \
  -e SECUREONE_PUBLIC_BASE_URL=https://id.example.com \
  -e SECUREONE_DB_URL="jdbc:postgresql://db.example.internal:5432/secureone?currentSchema=platform" \
  -e SECUREONE_DB_USERNAME=secureone \
  -e SECUREONE_DB_PASSWORD=your-database-password \
  -e SECUREONE_REDIS_URL=redis://redis.example.internal:6379 \
  dmtavs0108/secureone:1.1.0

docker logs -f secureone        # wait for the setup code
```

**Docker Desktop:** in **Images**, choose **Run** on `dmtavs0108/secureone:1.1.0`, open **Optional settings**, add
the same variables under **Environment variables**, map port `9000`, and add a volume with container path
`/var/lib/secureone`.

## The variables

| Variable | Required | What it is |
|---|---|---|
| `SECUREONE_PUBLIC_BASE_URL` | Yes | The address users open, with `https://`. It goes into tokens and sign-in links: choose it before you connect applications |
| `SECUREONE_DB_URL` | Yes | `jdbc:postgresql://<host>:<port>/<database>?currentSchema=platform` |
| `SECUREONE_DB_USERNAME`, `SECUREONE_DB_PASSWORD` | Yes | The database user SecureOne connects as |
| `SECUREONE_REDIS_URL` | Yes | `redis://<host>:6379` (`rediss://` with TLS, `redis://:<password>@<host>:6379` with a password) |
| Volume at `/var/lib/secureone` | Yes | Where SecureOne keeps the encryption key it creates. Use a named volume or a host folder, never nothing |

Everything else is optional and listed in [Deployment](deployment.md#all-settings). The secrets SecureOne needs are
created on the first start:

| Secret | Where it is kept |
|---|---|
| Encryption key (protects every stored secret) | The volume, outside the database: `/var/lib/secureone/encryption.key` |
| Token signing key | The database, encrypted with that key |
| One-time code pepper | The database, encrypted with that key |

You can supply your own instead, with `SECUREONE_ENCRYPTION_KEY`, `SECUREONE_JWK_PRIVATE_KEY_PEM` and
`SECUREONE_OTP_PEPPER` (or as files in `/run/secrets`). Once SecureOne runs, keep whichever you used: changing them
later makes stored secrets, issued tokens or recovery codes unusable.

## Put HTTPS in front

Point your reverse proxy or load balancer for `id.example.com` at `http://127.0.0.1:9000` and forward the
`X-Forwarded-Proto`, `X-Forwarded-Host` and `X-Forwarded-For` headers. Everything is on this one address: sign-in
pages, APIs and the admin console at `/admin`. See [Deployment](deployment.md#a7--tls--reverse-proxy) for details.

## Finish setup in the browser

Open `https://id.example.com/admin/setup`:

1. **Setup code**: copy it from the log.
2. **Check your installation**: the address, where the encryption key is kept, and whether email is set up.
3. **Email** (optional): your SMTP server, with a test message. You can also do this later under
   **Settings › Notifications**.
4. **Back up the encryption key**: copy or download it and store it in your password manager or secret store,
   apart from the database backups. **Without it, a database backup cannot be restored.** It is shown only here.
5. **First operator**: your name, email and password. Then sign in, add an authenticator app and save the recovery
   codes it shows.

Then connect your first application: [Integration guide](../integrate/integration-guide.md).

## Check that everything works

```bash
curl -s https://id.example.com/api/info                              # "status":"UP", "version":"1.1.0"
curl -s https://id.example.com/.well-known/openid-configuration      # sign-in discovery document
docker compose ps                                                    # secureone is "healthy"
```

## Keep it running

| Task | How |
|---|---|
| Back up | The database (`docker compose exec postgres pg_dump -U secureone -Fc secureone > secureone-$(date +%F).dump`) **and** the encryption key from setup step 4 |
| Upgrade | Change the image version, then `docker compose pull && docker compose up -d`. The database upgrades itself; back up first |
| Read the log | `docker compose logs --since 1h secureone` |
| Restart | `docker compose restart secureone` |
| Stop | `docker compose down` keeps the data. `down -v` **deletes the database and the encryption key** |

## Next steps

- [Operator getting started](../get-started/operator-getting-started.md): tenants, applications and operators
- [Integration guide](../integrate/integration-guide.md): connect your first application
- [Configuration as code and upgrades](configuration-and-upgrades.md): keep tenants in files, upgrade and roll back
- [Runbooks](runbooks/index.md): step-by-step procedures for day-to-day operations
