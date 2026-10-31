# Build the image and run or update the container

How to build the SecureOne image from this repository, publish it, update a running container to the new
version, roll back, and start a brand-new installation. Commands are for **PowerShell on Windows**; on Linux or
macOS replace the trailing `` ` `` with `\`.

SecureOne is **one image** (auth-server + admin console on port 9000). It needs **PostgreSQL 16** and **Redis 7**,
which run as their own containers. All three share a Docker network:

| Container | Image | Purpose |
|-----------|-------|---------|
| `secureone` | `dmtavs0108/secureone:<version>` | SecureOne (console at `/admin`) |
| `secureone-db` | `postgres:16-alpine` | Database (data in the `secureone-pgdata` volume) |
| `secureone-cache` | `redis:7-alpine` | Sessions, rate limits (data in `secureone-redisdata`) |

Network: `secureone-net`. Every setting is an environment variable; see
[Deployment](../docs/install/deployment.md) for the full list.

---

## 1. Build the image

**Prerequisites:** Docker Desktop with at least **4 GB** of memory (6 GB is comfortable; *Settings → Resources*),
and this repository checked out.

From the **repository root**:

```powershell
cd "C:\path\to\secureone"
docker build -f deploy/Dockerfile -t dmtavs0108/secureone:1.0.0 .
```

- Pick a new version number each time (`1.0.0`, `1.0.1`, …) so you can always roll back.
- A full build takes **8–10 minutes**: the console build, the Java build, then a start-up training step that
  launches the server once to record a start-up cache (this is why the container starts in about 10 seconds).
- The build ends with `naming to docker.io/dmtavs0108/secureone:1.0.0 done`. Check with
  `docker images dmtavs0108/secureone`.

## 2. Publish it (optional)

Only needed if other machines pull the image from Docker Hub (`docker login` first):

```powershell
docker push dmtavs0108/secureone:1.0.0
docker tag  dmtavs0108/secureone:1.0.0 dmtavs0108/secureone:latest
docker push dmtavs0108/secureone:latest
```

---

## 3. Update a running container to the new image

> **From 0.3.x or earlier to 0.1.x:** not possible in place. 0.1.0 started a new schema baseline and 0.1.x refuses a
> database created by an earlier release. Back up (3.2), then start 0.1.x on an empty database (section 4) and
> set it up again.

Your data lives in the database container, so replacing the `secureone` container keeps every tenant, user and
setting. The steps below keep the old container until the new one is healthy.

### 3.1 Save the container's settings (once)

Keep the container's environment variables in a file **outside the repository** (it holds passwords). If you
created the container with `-e` options, extract them once:

```powershell
New-Item -ItemType Directory -Force C:\secureone | Out-Null
$image    = docker inspect secureone --format '{{.Config.Image}}'
$imageEnv = docker inspect $image --format '{{range .Config.Env}}{{println .}}{{end}}'
docker inspect secureone --format '{{range .Config.Env}}{{println .}}{{end}}' |
  Where-Object { $_ -and ($imageEnv -notcontains $_) } |
  Set-Content -Encoding ascii C:\secureone\secureone.env
Get-Content C:\secureone\secureone.env | ForEach-Object { ($_ -split '=')[0] }   # names only
```

The file keeps only what you set (`SECUREONE_DB_URL`, `SECUREONE_DB_PASSWORD`, …), not the image's own
defaults. From now on, edit this file to change settings and pass it with `--env-file`.

### 3.2 Back up the database

A new version may upgrade the database schema, and an older version refuses a newer database. Take a backup
before every update:

```powershell
$stamp = Get-Date -Format yyyyMMdd-HHmm
docker exec secureone-db pg_dump -U secureone -d secureone -Fc -f /tmp/secureone-$stamp.dump
docker cp secureone-db:/tmp/secureone-$stamp.dump C:\secureone\secureone-$stamp.dump
```

### 3.3 Replace the container

```powershell
docker stop secureone
docker rename secureone secureone-old
docker run -d --name secureone --network secureone-net -p 9000:9000 --restart unless-stopped `
  --env-file C:\secureone\secureone.env `
  dmtavs0108/secureone:1.0.1
```

### 3.4 Wait until it is healthy

```powershell
do { Start-Sleep 5; $s = docker inspect secureone --format '{{.State.Health.Status}}'; $s } until ($s -in 'healthy','unhealthy')
docker logs secureone 2>&1 | Select-String '"level":"ERROR"'     # should print nothing
```

`healthy` usually appears within 30 seconds (longer when the database is upgraded). Then open
**http://localhost:9000/admin** and sign in again: a restart ends console sessions.

### 3.5 Clean up

When the new version works:

```powershell
docker rm secureone-old
docker rmi dmtavs0108/secureone:1.0.0         # the previous version, if you no longer need it
docker builder prune -f                       # build cache (can be several GB)
```

### Roll back

If the new version misbehaves and `secureone-old` still exists:

```powershell
docker stop secureone
docker rm secureone
docker rename secureone-old secureone
docker start secureone
```

If the new version upgraded the database schema, the old version will refuse to start
(*database is newer than this release*). Restore the backup from 3.2 first:

```powershell
docker cp C:\secureone\secureone-20261017-1200.dump secureone-db:/tmp/restore.dump
docker exec secureone-db pg_restore -U secureone -d secureone --clean --if-exists /tmp/restore.dump
```

---

## 4. Start a new installation

### 4.1 Create the settings file

`C:\secureone\secureone.env` (choose your own passwords; the admin password needs 12+ characters with upper
case, a number and a symbol):

```dotenv
SECUREONE_ENV=development
SECUREONE_ISSUER_URL=http://localhost:9000
SECUREONE_PUBLIC_BASE_URL=http://localhost:9000
SECUREONE_DB_URL=jdbc:postgresql://secureone-db:5432/secureone?currentSchema=platform
SECUREONE_DB_USERNAME=secureone
SECUREONE_DB_PASSWORD=change-me-db-password
SECUREONE_REDIS_URL=redis://secureone-cache:6379
SECUREONE_BOOTSTRAP_ADMIN_EMAIL=owner@example.com
SECUREONE_BOOTSTRAP_ADMIN_PASSWORD=Change-Me-2026!x
```

`SECUREONE_ENV=development` allows plain `http://` and local URLs, and turns on the API docs (Swagger UI at
`/docs`, OpenAPI at `/v3/api-docs/all`). For a real server use `production` with
`https://` URLs and the extra settings in [Deployment](../docs/install/deployment.md)
(encryption key, secrets as files, mail).

### 4.2 Start the containers

```powershell
docker network create secureone-net

docker run -d --name secureone-db --network secureone-net --restart unless-stopped `
  -e POSTGRES_DB=secureone -e POSTGRES_USER=secureone -e POSTGRES_PASSWORD=change-me-db-password `
  -v secureone-pgdata:/var/lib/postgresql/data postgres:16-alpine

docker run -d --name secureone-cache --network secureone-net --restart unless-stopped `
  -v secureone-redisdata:/data redis:7-alpine redis-server --appendonly yes

# Wait for PostgreSQL, then start SecureOne
do { Start-Sleep 2 } until ((docker exec secureone-db pg_isready -U secureone -d secureone) -match 'accepting')

docker run -d --name secureone --network secureone-net -p 9000:9000 --restart unless-stopped `
  --env-file C:\secureone\secureone.env `
  dmtavs0108/secureone:1.0.0
```

The database password must be the same in `POSTGRES_PASSWORD` and `SECUREONE_DB_PASSWORD`.

### 4.3 Sign in

When `docker inspect secureone --format '{{.State.Health.Status}}'` says `healthy` (and `docker logs secureone`
shows *First platform operator created*), open **http://localhost:9000/admin**, choose **Platform operator**,
and sign in with the bootstrap email and password. The first sign-in asks for a new password and, in production, sets up an
authenticator app; keep the recovery codes it shows. The bootstrap admin is created once; later starts ignore those two variables.

---

## Troubleshooting

| Symptom | Cause and fix |
|---------|---------------|
| `The container name "/secureone" is already in use` | A container with that name exists. Update it (section 3) or remove it: `docker rm -f secureone`. |
| `port is already allocated` | Something else uses 9000. Stop it, or map another port: `-p 9100:9000` and set the URLs in the env file to `http://localhost:9100`. |
| Status `unhealthy` right after start, log says *Connection to secureone-db refused* | SecureOne started before PostgreSQL was ready. `docker restart secureone`. |
| Status `unhealthy`, *database is newer than this release* | You started an older image on a database a newer version upgraded. Use the newer image, or restore a backup (Roll back). |
| Build stops at `gradlew … dependencies` with *Connect timed out* | The Gradle download (redirected to GitHub) cannot be reached from Docker. Check the network or proxy for Docker Desktop and run the build again. |
| Build fails or is killed (exit 137) | Docker ran out of memory. Give Docker Desktop 6 GB and close other heavy apps. |
| Console shows *Session ended* after an update | Expected: sign in again. |
| Page shows old screens after an update | Hard-reload the browser (Ctrl+F5). |
| Need to see what happened | `docker logs secureone --since 10m` |
