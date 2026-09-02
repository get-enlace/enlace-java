# Contributing to enlace-java

## Layout

- `src/main/java/io/github/getenlace/spring/` — the adapter, packaged as the
  `enlace-spring-boot-starter` Maven artifact
- `src/test/java/io/github/getenlace/spring/` — unit tests
- `scripts/dev-sync-ui.sh` — local dev: builds `@get-enlace/ui` from a checkout and copies its
  `dist/` into `src/main/resources/enlace-ui-embedded/`
- `scripts/ci-fetch-ui.sh` — CI: fetches `@get-enlace/ui`'s published tarball, from GitHub
  Packages (dev) or npmjs.org (prod), and does the same (curl + tar, no Node)
- `ui-version.txt` — the pinned `@get-enlace/ui` version `deploy-prod` embeds; updated by
  `handle-ui-release` (see CI/CD below) whenever `enlace-ui` publishes a real prod release

## Build & test

```bash
mvn test
mvn package
```

## Local development against `@get-enlace/ui`

`src/main/resources/enlace-ui-embedded/` (the adapter's embedded static assets) is never
committed — it's a build artifact, populated one of two ways:

- **`scripts/dev-sync-ui.sh [path-to-enlace-ui-checkout]`** — builds `@get-enlace/ui` from a
  local checkout and copies its `dist/` output in, so you can sanity-check the real
  embedded-resource path before a release without touching the registry.
- **CI** (`scripts/ci-fetch-ui.sh`) — fetches the published tarball instead; see CI/CD below.

## CI/CD

Mirrors [`enlace-dotnet`](https://github.com/get-enlace/enlace-dotnet)'s per-package workflow
pattern, Maven-ized.

- `.github/workflows/build.yml` — build + test on every PR into `main`.
- `.github/workflows/enlace-spring-boot-starter.yml` — two triggers: push to `main`
  (path-filtered to `src/main/**`, `pom.xml`, `ui-version.txt`, etc.), and `repository_dispatch:
  enlace-ui-release`, fired by `enlace-ui`'s own release workflow whenever it publishes (see
  [`release-strategy.md`](https://github.com/get-enlace/enlace-ui)) — this repo is an **embedding-based**
  adapter, so it must actively rebuild and republish on every `enlace-ui` change, or consumers
  stay frozen on an old bundle. No manual `workflow_dispatch` escape hatch — a forced rebuild is
  just a commit (even a trivial one) pushed to `main`.

  `handle-ui-release` only runs for a *production* dispatch (a dev dispatch is a no-op here —
  see `deploy-dev` below) and does exactly one thing: pins the incoming version into
  `ui-version.txt` and commits it — a `GITHUB_TOKEN`-authored push can't retrigger the
  workflow, so it doesn't try to.

  `deploy-dev` runs **unconditionally** on every trigger — push, or a `repository_dispatch` for
  either environment — always fetching whatever's currently under `@get-enlace/ui`'s floating
  `dev` dist-tag. Its "anything to publish?" check diffs `src/main`/`pom.xml` against the last
  published tag **only for a plain push**; a `repository_dispatch` always republishes
  regardless, since the embedded UI bundle changed even when this repo's own source didn't. It
  packs a `<version>-dev.<run id>` build (via `versions:set`, never committed) and publishes it
  to GitHub Packages, then tags it (`enlace-spring-boot-starter-v*`).

  `deploy-prod` (gated behind the `production` environment's required-reviewer approval) packs
  whatever version is currently committed in `pom.xml`, fetches the UI bundle pinned in
  `ui-version.txt` from npmjs.org (not GitHub Packages — this is the prod path), signs and
  publishes to Maven Central via the Central Portal, tags the release, and commits the next
  patch version bump.

  Unlike NuGet's Trusted Publishing (OIDC) or PyPI's equivalent, **Maven Central has no
  GitHub Actions trusted-publishing mechanism** as of this writing — `deploy-prod` instead
  imports a GPG key from a secret and authenticates to the Central Portal with a stored token
  pair. See the `release` Maven profile in `pom.xml` for exactly what that activates
  (`maven-gpg-plugin`, sources/javadoc jars, `central-publishing-maven-plugin`) — it's authored
  and buildable locally, but **not yet verified against a real Central Portal publish**, since
  none of the credentials below exist yet.

### One-time setup this needs

Written and buildable, but three things outside this repo still need to happen before
`deploy-dev`/`deploy-prod` can actually run end to end:

- **On GitHub** (repo → **Settings → Environments**): a `development` environment and a
  `production` environment, the latter with a required reviewer. `GITHUB_TOKEN` (used for the
  GitHub Packages dev channel) is automatic — no secret to add there.
- **On GitHub** (`production` environment secrets):
  - `GPG_PRIVATE_KEY` — an ASCII-armored GPG private key (`gpg --export-secret-keys --armor
    <key-id>`) generated specifically for signing releases; its public half needs publishing to
    a keyserver (e.g. `keys.openpgp.org`) since Central rejects unsigned or unverifiable
    artifacts.
  - `GPG_PASSPHRASE` — that key's passphrase.
  - `CENTRAL_TOKEN_USERNAME` / `CENTRAL_TOKEN_PASSWORD` — a user token pair generated from a
    [Central Portal](https://central.sonatype.com/) account, once the `io.github.get-enlace`
    namespace is verified against the `get-enlace` GitHub org (Central Portal supports verifying
    a `io.github.<owner>` namespace by proving control of that GitHub account/org — no separate
    domain needed, unlike a custom groupId would require).
- **On `enlace-ui`**: add this repo as a `repository_dispatch: enlace-ui-release` target
  (fires for `enlace-js`, `enlace-dotnet`, and now `enlace-java`; `enlace-python` isn't listed
  yet — see its own CONTRIBUTING.md) — a change in that repo, pending review before it's
  committed and pushed there.

`deploy-dev` only needs the `development` environment to exist — `GITHUB_TOKEN` is automatic,
no secret to add. `deploy-prod` needs all of the above; until then it fails at either the GPG
import or the Central Portal `mvn deploy` step — expected, not a bug in the workflow itself.
