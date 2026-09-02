#!/usr/bin/env bash
# CI-only: fetches @get-enlace/ui (from GitHub Packages or public npmjs.org)
# and copies its dist/ output into src/main/resources/enlace-ui-embedded/ —
# no npm or Node needed, since a published npm package is just a gzipped
# tarball at an HTTP URL (plain curl + tar). Local development uses
# scripts/dev-sync-ui.sh instead, which builds from a checkout.
#
# Requires GITHUB_TOKEN in the environment when fetching from GitHub Packages
# (packages:read is enough — GitHub Packages requires auth even for public
# reads). Not needed, and not sent, when fetching from public npmjs.org.
#
# Usage: scripts/ci-fetch-ui.sh <dist-tag-or-version> [registry-url]
#   A bare tag like "dev" resolves to whatever version currently holds it —
#   only meaningful the first time in a release, to pick a build to ship.
#   An exact version (e.g. "0.0.1-dev.123") is pinned as-is, no resolution —
#   use this to re-fetch the identical bytes an earlier step already chose,
#   since a floating tag can move between a dev publish and its paired prod
#   promotion (which may sit behind a manual approval gate for a while).
#   registry-url defaults to GitHub Packages (the dev channel); pass
#   https://registry.npmjs.org to fetch the public prod release instead.
#
# If GITHUB_OUTPUT is set (true in a GitHub Actions step), the resolved
# version is also written there as `ui_version`, for exactly that reuse.

set -euo pipefail

REF="${1:?usage: ci-fetch-ui.sh <dist-tag-or-version> [registry-url]}"
PACKAGE="@get-enlace/ui"
REGISTRY="${2:-https://npm.pkg.github.com}"
DEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/src/main/resources/enlace-ui-embedded"

auth=()
if [[ "$REGISTRY" == *"npm.pkg.github.com"* ]]; then
  if [[ -z "${GITHUB_TOKEN:-}" ]]; then
    echo "error: GITHUB_TOKEN is required (packages:read) to fetch from GitHub Packages" >&2
    exit 1
  fi
  auth=(-H "Authorization: Bearer ${GITHUB_TOKEN}")
fi

echo "Resolving ${PACKAGE}@${REF} from ${REGISTRY}..."
packument="$(curl -sSfL "${auth[@]}" "${REGISTRY}/${PACKAGE}")"

if [[ "$REF" =~ ^[0-9] ]]; then
  # Looks like a version already (starts with a digit) — pin as-is, skip
  # dist-tag resolution entirely.
  version="$REF"
else
  version="$(echo "$packument" | jq -r --arg tag "$REF" '.["dist-tags"][$tag] // empty')"
  if [[ -z "$version" ]]; then
    echo "error: no '${REF}' dist-tag found for ${PACKAGE}" >&2
    exit 1
  fi
fi

tarball_url="$(echo "$packument" | jq -r --arg v "$version" '.versions[$v].dist.tarball // empty')"
if [[ -z "$tarball_url" ]]; then
  echo "error: ${PACKAGE}@${version} not found in the registry" >&2
  exit 1
fi
echo "Fetching ${PACKAGE}@${version} from ${tarball_url}"

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

# -L: GitHub Packages' /download/ tarball URLs redirect to backing storage
# — without following, curl saves the redirect response itself (empty or a
# tiny HTML/text body) as package.tgz, and tar fails with a confusing
# "not in gzip format" rather than a clear fetch error. -f: fail loudly on
# an HTTP error status instead of writing the error page to package.tgz
# and letting tar's failure be the only symptom.
curl -sSfL "${auth[@]}" "$tarball_url" -o "$workdir/package.tgz"
tar -xzf "$workdir/package.tgz" -C "$workdir"

if [[ ! -d "$workdir/package/dist" ]]; then
  echo "error: '$workdir/package/dist' not found in the fetched tarball" >&2
  exit 1
fi

echo "Syncing ${PACKAGE}@${version}/dist -> $DEST_DIR"
rm -rf "${DEST_DIR:?}"/*
cp -R "$workdir/package/dist/." "$DEST_DIR/"

echo "Done. Fetched ${PACKAGE}@${version} (requested: ${REF})."

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  echo "ui_version=${version}" >> "$GITHUB_OUTPUT"
fi
