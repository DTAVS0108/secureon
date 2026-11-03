#!/usr/bin/env bash
# SecureOne release: the image and the Helm chart, from the version in apps/auth-server/build.gradle.kts.
# Run from the repository root.
#
#   scripts/release.sh build      local image for this machine: dmtavs0108/secureone:<version> and latest
#   scripts/release.sh chart      package the Helm chart into apps/website/public/charts (then deploy the website)
#   scripts/release.sh publish    build and push <version> and latest (docker login first; and for MIRROR, a login there)
#
# IMAGE      repository (default dmtavs0108/secureone)
# MIRROR     a second repository pushed with the same tags, e.g. ghcr.io/<owner>/secureone (default: none)
# PLATFORMS  linux/amd64 (default). Add ARM with PLATFORMS=linux/amd64,linux/arm64 on a machine that builds ARM
#            natively (an ARM runner or VM): emulated on an Intel PC, the ARM build is too slow to be practical.
# SITE_URL   the website that serves the charts (default https://secureone-website.vercel.app)
set -euo pipefail

IMAGE="${IMAGE:-dmtavs0108/secureone}"
MIRROR="${MIRROR:-}"
PLATFORMS="${PLATFORMS:-linux/amd64}"
SITE_URL="${SITE_URL:-https://secureone-website.vercel.app}"
VERSION="$(sed -n 's/^version = "\(.*\)"/\1/p' apps/auth-server/build.gradle.kts)"
CHART_VERSION="$(sed -n 's/^version: //p' deploy/helm/secureone/Chart.yaml)"
APP_VERSION="$(sed -n 's/^appVersion: "\(.*\)"/\1/p' deploy/helm/secureone/Chart.yaml)"
[ -n "$VERSION" ] || { echo "version not found in build.gradle.kts" >&2; exit 1; }
if [ "$APP_VERSION" != "$VERSION" ]; then
  echo "Chart appVersion ($APP_VERSION) differs from the app version ($VERSION): update deploy/helm/secureone/Chart.yaml" >&2
  exit 1
fi

TAGS=("$IMAGE:$VERSION" "$IMAGE:latest")
if [ -n "$MIRROR" ]; then
  TAGS+=("$MIRROR:$VERSION" "$MIRROR:latest")
fi
TAG_ARGS=()
for tag in "${TAGS[@]}"; do TAG_ARGS+=(-t "$tag"); done

# Helm from its official image, so nothing has to be installed.
ROOT="$(pwd -W 2>/dev/null || pwd)"   # a Windows path under Git Bash, for the Docker mount
helm() { MSYS_NO_PATHCONV=1 docker run --rm -v "$ROOT:/work" -w /work alpine/helm:3.16.4 "$@"; }

case "${1:-}" in
  build)
    docker build -f deploy/Dockerfile --build-arg SECUREONE_VERSION="$VERSION" "${TAG_ARGS[@]}" .
    ;;
  chart)
    mkdir -p apps/website/public/charts
    helm lint deploy/helm/secureone --set database.password=lint
    helm package deploy/helm/secureone --destination apps/website/public/charts
    helm repo index apps/website/public/charts --url "$SITE_URL/charts"
    echo "Chart secureone-$CHART_VERSION.tgz is in apps/website/public/charts: deploy the website to publish it."
    ;;
  publish)
    if [ "$PLATFORMS" = "linux/amd64" ]; then
      # One architecture: the normal builder (reuses the local build cache), then push every tag.
      docker build -f deploy/Dockerfile --build-arg SECUREONE_VERSION="$VERSION" "${TAG_ARGS[@]}" .
      for tag in "${TAGS[@]}"; do docker push "$tag"; done
    else
      docker buildx inspect secureone-multiarch >/dev/null 2>&1 \
        || docker buildx create --name secureone-multiarch --driver docker-container
      docker buildx build --builder secureone-multiarch --platform "$PLATFORMS" \
        -f deploy/Dockerfile --build-arg SECUREONE_VERSION="$VERSION" "${TAG_ARGS[@]}" --push .
    fi
    echo "Pushed ${TAGS[*]} ($PLATFORMS)."
    ;;
  *)
    sed -n '2,13p' "$0"
    exit 2
    ;;
esac
