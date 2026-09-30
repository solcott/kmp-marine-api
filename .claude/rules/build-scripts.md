---
paths:
  - "**/*.gradle.kts"
  - "build-logic/**"
  - "gradle/**"
  - "gradle.properties"
  - "config/detekt/**"
  - "marine-api/api/**"
---

# Build scripts

Most of these are also commented at the line they concern. They are collected here because each one
breaks silently or far from the edit.

## Test wiring: the zero-tests-and-green trap

- **`jvmTest` must use `useJUnit()`.** `kotlin-test` resolves to `kotlin-test-junit` under it, and
  that is what runs the common tests on the jvm target. Switching to `useJUnitPlatform()` means
  switching `kotlin-test` to its JUnit 5 variant too; get only half of that right and **zero tests
  run while the build reports success**. Check the count, not the exit code.
- **`withHostTest {}` inside `marine-api`'s `android { }` block is what registers
  `testAndroidHostTest`** (the common suite on android, wired into `allTests`). Without it the
  android compilation is built and published but never executed. It needs no `useJUnit()` hook:
  it is a KGP compilation, so KGP's variant-aware resolution turns plain `kotlin-test` into
  `kotlin-test-junit` by itself. The zero-tests trap still applies, so check the count.
- **Five of the seven non-Apple native targets have no test task, and that is KGP's doing.**
  `linuxX64()` and `mingwX64()` return `KotlinNativeTargetWithHostTests`, so they get a test run --
  only on their own OS. `linuxArm64()` and the four `androidNative*()` return a plain
  `KotlinNativeTarget`, which has no test run on any host. Do not go looking for a missing
  `linuxArm64Test`.

## Plugins KMP will not accept

- **Never apply the `java`, `java-library`, or `jvm-toolchains` plugins**, and **never call
  `withJava()`** on the `jvm` target. KGP rejects them as incompatible with KMP. This is also why
  the OSGi metadata is hand-written instead of using `biz.aQute.bnd.builder` (which applies
  `java`), and why `:examples` resolves `JavaToolchainService` via `serviceOf` to give its
  `JavaExec` tasks a launcher.
- Do not add Java sources back. There are none left in any module.

## Targets

- Apple targets are **arm64 only** (`iosArm64`, `iosSimulatorArm64`, `macosArm64`); the x64 Apple
  variants are deprecated. Do not add `iosX64` or `macosX64`.
- **Everything except the Apple targets cross-compiles from any host.** That is what lets the
  release workflow publish all sixteen publications from one macOS runner. Only Apple is
  host-locked.
- **`commonMain` must never go back to being empty.** With no common Kotlin source the Kotlin/Native
  compilations are `NO-SOURCE`, produce no `.klib`, and the Apple publications fail.
- `explicitApi()` is on for `:marine-api`.

## OSGi manifest (`marine-api/build.gradle.kts`, `jvmJar`)

- **`Export-Package` is derived by walking `src/commonMain/kotlin`.** Pointed at a directory that
  does not exist, a `fileTree` is simply empty and the bundle ships an **empty** `Export-Package`
  without failing anything. If that header ever comes out blank, the derivation is looking in the
  wrong place.
- `Bundle-SymbolicName` and `Automatic-Module-Name` are `io.github.solcott.marineapi` and track
  the package root. `Import-Package` is deliberately absent.
- `Bundle-Version` is `project.version` verbatim, so the version must stay OSGi-legal (see
  `release-and-versioning.md`).

## The pinned ABI (`marine-api/api/`)

- `apiCheck` runs under `check`. Changing the public API fails the build until
  `./gradlew :marine-api:apiDump` is run and the diff committed; **that diff is the review
  artifact**, so read it rather than regenerating past it. The dump files are never hand-edited.
- `api/jvm/marine-api.api` is the JVM bytecode ABI; `api/marine-api.klib.api` is one merged dump
  across all 13 klib targets.
- **The `android` target is not dumped** -- binary-compatibility-validator does not recognise AGP's
  `com.android.kotlin.multiplatform.library` target. Nothing is lost: there is no `androidMain`, so
  android compiles exactly the `commonMain` the jvm dump already covers.
- `klib.strictValidation` is off so the ubuntu job, where the Apple targets are disabled, infers
  them instead of failing.

## detekt

- **detekt analyses `src/` once per project, not once per compilation.** The extension sets
  `source` to the whole tree, because the per-compilation tasks the plugin also registers
  (`detektJvmMain`, `detektMetadataCommonMain`, ...) would report every `commonMain` finding once
  per target. Only the plain `detekt` task is wired into `check`.
- `config/detekt/detekt.yml` holds **only the deviations** from detekt's defaults
  (`buildUponDefaultConfig = true`), each with the reason it deviates. `MagicNumber` is off because
  the numbers here are AIS bit ranges and NMEA field indices.

## Dependencies

- `./gradlew sortDependencies` after touching a `dependencies {}` block -- CI runs
  `checkSortDependencies`.
- Renovate owns version bumps.
