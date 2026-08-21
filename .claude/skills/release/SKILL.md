---
name: release
description: Cut a release of :marine-api to Maven Central — the version decision, changelog entry, pre-flight checks and the macOS-only publish workflow. Use when asked to release, publish, cut a version, tag a release or push the library to Maven Central.
---

# Releasing kmp-marine-api

Published as `io.github.solcott:kmp-marine-api` from the `:marine-api` module. `:examples` and
`:examples-android` are never published.

## Step 0 — the version

**This fork has never released**, but the numbering question is settled: `gradle.properties` carries
`version=0.5.0`, and `changelog.txt` has a `Version 0.5.0 (unreleased)` entry above upstream's
`0.12.0 (2023-02-26)`.

The restart is deliberate. Upstream's `0.12.0` and everything below it describe the Java library at
`net.sf.marineapi` — a different source tree, a different group id, a different artifact id and a
redesigned API (immutable values, nullable optional fields, `ParseResult` instead of thrown
exceptions). Continuing that numbering would have implied continuity with an API that no longer
exists. **Do not "fix" the drop from 0.12.0 to 0.5.0**; the changelog header says why it is there.

From here it is ordinary pre-1.0 semver judgement — `0.5.1` for a fix, `0.6.0` for anything that
moves the pinned ABI in `marine-api/api/`, and `1.0.0` when the API is considered settled. That is a
call worth confirming rather than assuming, but it is no longer a question about which lineage to
follow.

**OSGi constrains the format.** `Bundle-Version` is set to `project.version` verbatim
(`marine-api/build.gradle.kts:116`), and OSGi versions are `major.minor.micro[.qualifier]` with a
**dot** before the qualifier and no hyphens. `0.13.0-alpha01` or `1.0.0-SNAPSHOT` produce a malformed
`Bundle-Version` that only an OSGi resolver will complain about — long after publication. If a
pre-release qualifier is wanted, that header needs converting rather than passing through.

## Step 1 — the version and the changelog

- **The version lives only in `gradle.properties`.** It used to be duplicated across `pom.xml`,
  `build.properties` and `changelog.txt`; the comment above the property says so. Do not add it back
  anywhere.
- **`changelog.txt` gets a new entry, not a version bump.** It keeps its own historical record, in
  the existing format: `Version X.Y.Z (YYYY-MM-DD)` followed by indented `-` bullets. The `0.5.0`
  entry is already written and carries the "the implementation was replaced" story, including the
  deliberate behavioural differences from the Java library; **it is dated `(unreleased)` and that
  date is what you fill in when the release is cut.** For a later release, list what changed since
  the previous entry rather than restating the rewrite. When a behavioural divergence is added, run
  `grep -rn "implementation this replaces\|Java implementation" marine-api/src/commonMain/kotlin/`
  for the live list — those KDoc notes are the source of truth, not the changelog.

## Step 2 — pre-flight, on macOS

**The full build requires macOS.** The Apple targets cannot be linked on Linux, which is why
`release.yml` runs on `macos-latest` — publishing from Linux would silently ship an incomplete set of
KMP artifacts. `ANDROID_HOME` must be set even for JVM-only tasks: the
`com.android.kotlin.multiplatform.library` plugin fails build configuration without it.

```
./gradlew build
./gradlew :marine-api:allTests
./gradlew ktfmtCheck checkSortDependencies
./gradlew :marine-api:dokkaGeneratePublicationHtml
./gradlew :marine-api:publishToMavenLocal
```

Check the test **count**, not the exit code — see the `useJUnit()` hazard: expect 433 on `jvmTest`
and 408 common tests per target. `regression-checker` will confirm no pinned number moved.

Then inspect what `publishToMavenLocal` produced in `~/.m2/repository/io/github/solcott/`:

- **The `Export-Package` header in `kmp-marine-api-jvm-<version>.jar`.** It is derived by walking
  `src/commonMain/kotlin` with a `fileTree`, and a `fileTree` over a directory that does not exist is
  simply **empty** — the bundle then ships a blank `Export-Package` without failing anything. Verify
  it is non-blank and lists the five packages:

  ```
  unzip -p ~/.m2/repository/io/github/solcott/kmp-marine-api-jvm/<version>/kmp-marine-api-jvm-<version>.jar \
      META-INF/MANIFEST.MF | tr -d '\r' | grep -A2 Export-Package
  ```

  Expect `io.github.solcott.marineapi.ais`, `.nmea`, `.nmea.io`, `.nmea.sentence`, `.ublox`.
  `Import-Package` is deliberately **absent**; that is correct, not a defect.
- **`Bundle-Version` matches the release version** and is OSGi-legal (step 0).
- **The javadoc jars carry real content.** Dokka is applied and the publish plugin picks it up on its
  own; an empty javadoc jar is a Central validation failure.
- **The publication set is complete** -- sixteen of them: `kmp-marine-api` (root, module metadata),
  `-jvm`, `-android`, `-js`, `-wasm-js`, `-wasm-wasi`, `-iosarm64`, `-iossimulatorarm64`,
  `-macosarm64`, `-linuxx64`, `-linuxarm64`, `-mingwx64`, `-androidnativearm32`,
  `-androidnativearm64`, `-androidnativex86`, `-androidnativex64`. A missing Apple artifact means
  the build did not run on macOS; everything else cross-compiles from any host, so a missing
  linux/mingw/androidNative artifact means something else went wrong. Note the suffix convention is
  not uniform -- the wasm pair is hyphenated, the rest are the target name lowercased. Check against
  `publishToMavenLocal` rather than against this list if they ever disagree.
- **No `nrjavaserial` in any POM.** It is an `implementation` dependency of `:examples` only, and
  `:examples` is not published.

## Step 3 — publish

Publishing is driven by CI, not from a laptop. Credentials are repository secrets and there is no
local copy:

- `ORG_GRADLE_PROJECT_mavenCentralUsername` / `Password`
- `ORG_GRADLE_PROJECT_signingInMemoryKey` / `KeyId` / `KeyPassword`

Publish a GitHub release for the tag, which triggers `.github/workflows/release.yml` (`on: release:
published`, plus a `workflow_dispatch` for a re-run). It runs
`./gradlew :marine-api:publishAllPublicationsToMavenCentralRepository` on `macos-latest` in the
`release` environment.

**Do not attempt an authenticated publish locally**, and do not add credentials to a file.
`publishToMavenLocal` is the only local publish.

## Step 4 — tell consumers the coordinates split

Worth stating in the release notes every time, because it is the most common integration failure:

- **Gradle** consumers depend on `io.github.solcott:kmp-marine-api:<version>` and resolve the right
  variant through module metadata.
- **Plain Maven** consumers cannot read module metadata and must depend on
  `io.github.solcott:kmp-marine-api-jvm:<version>`.

## Notes

- Version bumps to dependencies are Renovate's job (`renovate.json`), not a release step.
- This fork does not send PRs upstream; divergence is expected, and the changelog should not be
  written as though upstream will merge it.
