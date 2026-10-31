# syntax=docker/dockerfile:1.7
# SecureOne public website: product pages and the public documentation (apps/website, built from docs/).
#
#   docker build -f deploy/website.Dockerfile -t secureone-website:<version> .      (from the repository root)
#   docker run -d --name secureone-website -p 8080:8080 secureone-website:<version>
#
# SITE_URL sets the public address used in canonical links and the sitemap (default http://localhost:8080);
# SUPPORT_EMAIL the contact shown for help and security reports.

FROM node:22-alpine AS build
ARG SITE_URL=http://localhost:8080
# Where users ask for help and report security problems (optional; shown on Support and Security).
ARG SUPPORT_EMAIL=
ENV SITE_URL=${SITE_URL} SUPPORT_EMAIL=${SUPPORT_EMAIL} ASTRO_TELEMETRY_DISABLED=1
WORKDIR /src
RUN corepack enable
# Every workspace manifest, so the frozen lockfile matches; only the website's dependencies are installed.
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/admin-web/package.json apps/admin-web/
COPY apps/website/package.json apps/website/
COPY packages/secureone-client/package.json packages/secureone-client/
RUN pnpm install --frozen-lockfile --filter website
COPY apps/website apps/website
COPY docs docs
RUN pnpm --filter website build

FROM nginx:1.27-alpine
COPY deploy/website-nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/apps/website/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://127.0.0.1:8080/ > /dev/null || exit 1
