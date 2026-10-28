# syntax=docker/dockerfile:1.7
# SecureOne: one image with the auth-server and the admin console.
#
#   docker build -f deploy/Dockerfile -t dmtavs0108/secureone:0.1.0 .        (from the repo root)
#
# One process to run and one port (9000): the auth-server serves OAuth/OIDC, SAML, SCIM, the hosted
# sign-in pages and the APIs, and the admin console at /admin (it starts the console itself on
# 127.0.0.1 and forwards to it). All settings are environment variables read at start-up, so the
# same image serves every installation; see docs/24-deployment-image-env-and-enterprise.md.
#
# Build network: the auth-server build downloads OpenSAML from the Shibboleth Maven repository
# (https://build.shibboleth.net/maven/releases/) as well as Maven Central.

# ---- Admin console (Next.js standalone server) ------------------------------------------------
FROM node:22-alpine AS console
# The JS packages are a pnpm workspace: install from the root lockfile (pnpm version from package.json).
WORKDIR /src
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/admin-web/package.json apps/admin-web/
COPY packages/secureone-client/package.json packages/secureone-client/
RUN pnpm install --frozen-lockfile --filter admin-web
COPY apps/admin-web apps/admin-web
WORKDIR /src/apps/admin-web
ENV NEXT_TELEMETRY_DISABLED=1
# Standalone output mirrors the workspace: shared packages in node_modules/ at its root, the app (server.js)
# under apps/admin-web/. Keep the whole tree; static files go next to server.js.
RUN pnpm run build \
    && test -f .next/standalone/apps/admin-web/server.js \
    && cp -r .next/static .next/standalone/apps/admin-web/.next/static \
    && mv .next/standalone /console

# ---- Auth-server (Spring Boot jar) ------------------------------------------------------------
FROM eclipse-temurin:25-jdk-alpine AS auth
WORKDIR /src
COPY apps/auth-server/gradlew apps/auth-server/build.gradle.kts apps/auth-server/settings.gradle.kts ./
COPY apps/auth-server/gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon -q dependencies > /dev/null
COPY apps/auth-server/src src
# Unpacked layout (app jar + lib/) is required for the JVM's ahead-of-time cache. The extracted jar keeps the
# versioned build name; give it a fixed one for the entry point (lib/ is on its manifest Class-Path).
RUN ./gradlew --no-daemon bootJar -x test \
    && java -Djarmode=tools -jar build/libs/*.jar extract --destination /app \
    && mv /app/*.jar /app/auth-server.jar

# ---- Runtime -----------------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine
# Node.js runs the console (same musl build as the console stage); tini forwards signals and reaps.
RUN apk add --no-cache libstdc++ libgcc tini \
    # Numeric UID so Kubernetes runAsNonRoot can verify it.
    && adduser -D -H -u 10001 -h /opt/secureone secureone
COPY --from=console /usr/local/bin/node /usr/local/bin/node
COPY --from=console /console /opt/secureone/console
COPY --from=auth /app /opt/secureone/app

# G1 pinned: the start-up cache is only used when the runtime GC matches the training run, and the JVM
# would otherwise pick a different collector on small containers.
ENV JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxRAMPercentage=75 -Djdk.httpclient.allowRestrictedHeaders=host"

# Start-up cache (JDK 25 AOT cache: classes loaded and linked, method profiles). Recorded by starting the
# application once against a throw-away PostgreSQL + Redis that exist only inside this build step and stops
# when the Spring context is ready (spring.context.exit=onRefresh: no web traffic, no schedulers, no console).
# At runtime the JVM uses the cache when it matches and starts without it otherwise (logging an error then;
# -Xlog:aot*=error hides the harmless warnings about individual stubs it regenerates).
RUN <<'TRAIN'
set -eu
apk add --no-cache --virtual .training postgresql16 redis su-exec > /dev/null
mkdir -p /run/postgresql /tmp/training-db
chown postgres:postgres /run/postgresql /tmp/training-db
cleanup() {
  redis-cli shutdown nosave > /dev/null 2>&1 || true
  su-exec postgres pg_ctl -D /tmp/training-db -m fast -w stop > /dev/null 2>&1 || true
  rm -rf /tmp/training-db /run/postgresql
  apk del --no-network .training > /dev/null
}
trap cleanup EXIT
su-exec postgres initdb -D /tmp/training-db -U secureone --auth=trust > /dev/null
su-exec postgres pg_ctl -D /tmp/training-db -o "-k /run/postgresql -c listen_addresses=127.0.0.1" -w start > /dev/null
su-exec postgres createdb -h 127.0.0.1 -U secureone secureone
redis-server --daemonize yes --save "" --appendonly no > /dev/null
cd /opt/secureone
if ! SPRING_PROFILES_ACTIVE=prod SECUREONE_ENV=development SECUREONE_CONSOLE_DIR= \
     SECUREONE_DB_URL="jdbc:postgresql://127.0.0.1:5432/secureone?currentSchema=platform" \
     SECUREONE_DB_USERNAME=secureone SECUREONE_DB_PASSWORD=training SECUREONE_REDIS_URL=redis://127.0.0.1:6379 \
     JDK_AOT_VM_OPTIONS="-Xmx384m" \
     java -Xmx384m -XX:AOTCacheOutput=/opt/secureone/app.aot -Dspring.context.exit=onRefresh -jar app/auth-server.jar \
     > /tmp/training.log 2>&1; then
  # Without the AOT linker's per-class notes, which would fill the last 80 lines and hide the error.
  grep -v -E 'AOTClassLinking|Unsupported location|\[aot\]' /tmp/training.log | tail -80
  exit 1
fi
test -s /opt/secureone/app.aot
rm -f /tmp/training.log
chmod 444 /opt/secureone/app.aot
TRAIN

# After the training run, which does not need them: a docs-only change rebuilds in seconds.
COPY docs /opt/secureone/docs

# SECUREONE_MANAGEMENT_PORT: health, info and Prometheus only on 9001 (probes, metrics scraper); never publish it.
ENV SPRING_PROFILES_ACTIVE=prod \
    SECUREONE_ENV=production \
    SECUREONE_CONSOLE_DIR=/opt/secureone/console/apps/admin-web \
    SECUREONE_DOCS_DIR=/opt/secureone/docs \
    SECUREONE_MANAGEMENT_PORT=9001

USER 10001
WORKDIR /opt/secureone
EXPOSE 9000 9001
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
    CMD wget -qO- http://127.0.0.1:9001/actuator/health/readiness > /dev/null || exit 1
ENTRYPOINT ["/sbin/tini", "--", "java", "-XX:AOTCache=/opt/secureone/app.aot", "-Xlog:aot*=error", "-jar", "/opt/secureone/app/auth-server.jar"]
