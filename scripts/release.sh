#!/usr/bin/env bash
# SecureOne release: the image (Intel/AMD and ARM) and the Helm chart, from the version in
# apps/auth-server/build.gradle.kts. Run from the repository root.
#
#   scripts/release.sh build      local image for this machine: dmtavs0108/secureone:<version>
#   scripts/release.sh chart      package the Helm chart into apps/website/public/charts (then deploy the website)
#   scripts/release.sh publish    build linux/amd64 + linux/arm64 and push <version> and latest (docker login first)
#
# IMAGE overrides the repository (default dmtavs0108/secureone); SITE_URL the website that serves the charts.
set -euo pipefail

IMAGE="${IMAGE:-dmtavs0108/secureone}"
SITE_URL="${SITE_URL:-https://secureone-website.vercel.app}"
VERSION="$(sed -n 's/^version = "\(.*\)"/\1/p' apps/auth-server/build.gradle.kts)"
CHART_VERSION="$(sed -n 's/^version: //p' deploy/helm/secureone/Chart.yaml)"
APP_VERSION="$(sed -n 's/^appVersion: "\(.*\)"/\1/p' deploy/helm/secureone/Chart.yaml)"
[ -n "$VERSION" ] || { echo "version not found in build.gradle.kts" >&2; exit 1; }
if [ "$APP_VERSION" != "$VERSION" ]; then
  echo "Chart appVersion ($APP_VERSION) differs from the app version ($VERSION): update deploy/helm/secureone/Chart.yaml" >&2
  exit 1
fi

# Helm from its official image, so nothing has to be installed.
ROOT="$(pwd -W 2>/dev/null || pwd)"   # a Windows path under Git Bash, for the Docker mount
helm() { MSYS_NO_PATHCONV=1 docker run --rm -v "$ROOT:/work" -w /work alpine/helm:3.16.4 "$@"; }

case "${1:-}" in
  build)
    docker build -f deploy/Dockerfile --build-arg SECUREONE_VERSION="$VERSION" \
      -t "$IMAGE:$VERSION" -t "$IMAGE:latest" .
    ;;
  chart)
    mkdir -p apps/website/public/charts
    helm lint deploy/helm/secureone --set database.password=lint
    helm package deploy/helm/secureone --destination apps/website/public/charts
    helm repo index apps/website/public/charts --url "$SITE_URL/charts"
    echo "Chart secureone-$CHART_VERSION.tgz is in apps/website/public/charts: deploy the website to publish it."
    ;;
  publish)
    # One builder that can build for both architectures (ARM is emulated on Intel machines: slower).
    docker buildx inspect secureone-multiarch >/dev/null 2>&1 || docker buildx create --name secureone-multiarch --driver docker-container
    docker buildx build --builder secureone-multiarch --platform linux/amd64,linux/arm64 \
      -f deploy/Dockerfile --build-arg SECUREONE_VERSION="$VERSION" \
      -t "$IMAGE:$VERSION" -t "$IMAGE:latest" --push .
    echo "Pushed $IMAGE:$VERSION and $IMAGE:latest (linux/amd64, linux/arm64)."
    ;;
  *)
    sed -n '2,10p' "$0"
    exit 2
    ;;
esac
