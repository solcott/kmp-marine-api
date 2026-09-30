---
paths:
  - "gradle.properties"
  - "changelog.txt"
  - ".github/workflows/**"
---

# Versioning, releases and CI

To cut a release, use the `/release` skill. This fork has never released.

## The version

- **It lives only in `gradle.properties`.** It used to be duplicated across `pom.xml`,
  `build.properties` and `changelog.txt`; do not add it back anywhere.
- **It is `0.5.0`, and the drop from upstream's `0.12.0` is deliberate.** Upstream's numbering
  described the Java library at `net.sf.marineapi` -- different coordinates, a different API.
  Do not "fix" it.
- **It must stay OSGi-legal**: `major.minor.micro[.qualifier]`, a dot before the qualifier, no
  hyphens. `Bundle-Version` is set to it verbatim, so `1.0.0-SNAPSHOT` or `0.6.0-alpha01` produce a
  malformed header that only an OSGi resolver will notice, long after publication.

## `changelog.txt`

It keeps its own historical record: `Version X.Y.Z (YYYY-MM-DD)` followed by indented `-` bullets.
Entries at `0.12.0` and below are upstream's Java library; the fork's own record starts at `0.5.0`,
whose `(unreleased)` date is filled in when it is cut. Write it as this fork's history -- it does not
send PRs upstream.

## Publishing

- **KMP splits the coordinates.** Gradle consumers use `io.github.solcott:kmp-marine-api` via module
  metadata; plain Maven consumers must depend on `kmp-marine-api-jvm`.
- Publishing is CI-only, from `release.yml` on `macos-latest` (the Apple targets only link on
  macOS; publishing from Linux would ship an incomplete set). Credentials are repository secrets.
  **Never run an authenticated publish locally or write credentials to a file**;
  `publishToMavenLocal` is the only local publish.

## CI (`build.yml`)

- An ubuntu `jvm` job (format, dependency sort, detekt, `jvmTest`, `linuxX64Test` -- the one native
  suite a Linux host can execute, and it runs nowhere else) and a macOS `all-targets` job.
- **The Android SDK must be installed even for JVM-only tasks**: the
  `com.android.kotlin.multiplatform.library` plugin fails configuration without it.
- `mingwX64Test` runs nowhere; there is no Windows runner.
