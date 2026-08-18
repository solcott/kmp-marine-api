---
name: release
description: Cut a release of :marine-api to Maven Central — the version decision, changelog entry, pre-flight checks and the macOS-only publish workflow. Use when asked to release, publish, cut a version, tag a release or push the library to Maven Central.
---

# Releasing kmp-marine-api

Published as `io.github.solcott:kmp-marine-api` from the `:marine-api` module. `:examples` and
`:examples-android` are never published.

## Step 0 — the version decision, which is not yours to make

**This fork has never released.** `gradle.properties` carries `version=0.12.0` and the newest
`changelog.txt` entry is `0.12.0 (2023-02-26)` — both inherited from upstream `ktuukkan/marine-api`,
for a library that has since had its entire source tree replaced, its group id changed, its artifact
id changed and its API redesigned (immutable values, nullable optional fields, `ParseResult` instead
of thrown exceptions).

So `0.12.0` describes a different library under different coordinates. **Ask for the version number
explicitly and do not pick one.** The things worth putting in front of whoever decides:

- Continuing upstream's numbering (`0.13.0`) implies continuity with an API that no longer exists.
- Starting over (`0.1.0`, `1.0.0`) is honest about the redesign but abandons the shared history that
  `changelog.txt` records.
- The coordinates already differ, so nothing technically forces either choice.

**OSGi constrains the format.** `Bundle-Version` is set to `project.version` verbatim
(`marine-api/build.gradle.kts:126`), and OSGi versions are `major.minor.micro[.qualifier]` with a
**dot** before the qualifier and no hyphens. `0.13.0-alpha01` or `1.0.0-SNAPSHOT` produce a malformed
`Bundle-Version` that only an OSGi resolver will complain about — long after publication. If a
pre-release qualifier is wanted, that header needs converting rather than passing through.

## Step 1 — the version and the changelog

- **The version lives only in `gradle.properties`.** It used to be duplicated across `pom.xml`,
  `build.properties` and `changelog.txt`; the comment above the property says so. Do not add it back
  anywhere.
- **`changelog.txt` gets a new entry, not a version bump.** It keeps its own historical record, in
  the existing format: `Version X.Y.Z (YYYY-MM-DD)` followed by indented `-` bullets. Note that every
  entry currently in it describes the Java library; the first fork entry should say plainly that the
  implementation was replaced, and what that means for anyone upgrading — the deliberate behavioural
  differences (AIS types 4/18/27 accuracy bit, type 27 status range, type 9 flags and speed,
  `isDteReady`, `NavStatus` reading `V`, `PositionProvider` no longer requiring GGA/GLL) are exactly
  what a changelog is for. Run
  `grep -rn "implementation this replaces\|Java implementation" marine-api/src/commonMain/kotlin/`
  for the live list.

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

Check the test **count**, not the exit code — see the `useJUnit()` hazard: expect 428 on `jvmTest`
and 403 common tests per target. `regression-checker` will confirm no pinned number moved.

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
- **The publication set is complete**: `kmp-marine-api` (root, module metadata),
  `-jvm`, `-android`, `-js`, `-wasm-js`, `-wasm-wasi`, `-iosarm64`, `-iossimulatorarm64`,
  `-macosarm64`. A missing Apple artifact means the build did not run on macOS.
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
