# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Fork of ktuukkan/marine-api ("Java Marine API") — a parser library for NMEA 0183, AIS, u-blox and SeaTalk marine data. LGPL v3.

Gradle Kotlin Multiplatform build, published as `io.github.solcott:kmp-marine-api`. **The Kotlin port has not started** — all real code is still Java in `marine-api/src/jvmMain/java` under the `net.sf.marineapi` package. Write Java there; ask before adding Kotlin outside the placeholder described below.

This fork is **not** intended to send PRs upstream — divergence is fine.

## Build & test

```
./gradlew build                              # all targets
./gradlew :marine-api:jvmTest                # JVM tests only
./gradlew :marine-api:jvmTest --tests '*GGATest'          # single test class
./gradlew :marine-api:jvmTest --tests '*GGATest.testGetAltitude'   # single method
./gradlew :marine-api:javadocJvm             # Javadoc for the Java sources
./gradlew ktfmtFormat                        # format Kotlin/build scripts (ktfmtCheck in CI)
./gradlew sortDependencies                   # checkSortDependencies in CI
./gradlew :marine-api:publishToMavenLocal
```

There is **no `test` task** — the JVM test task is `jvmTest`.

- Toolchain JDK 25 (pinned in `gradle/gradle-daemon-jvm.properties`), bytecode target 17 via `options.release`.
- **The Android SDK must be present even for JVM-only tasks.** The `com.android.kotlin.multiplatform.library` plugin fails the entire build configuration without it, so `ANDROID_HOME` is required.
- The full build needs macOS — the Apple targets cannot be linked on Linux. CI splits this into an ubuntu `jvm` job and a macOS `all-targets` job.

## Build constraints

These are non-obvious and easy to break:

- **`jvmTest` must use `useJUnit()`.** The suite is JUnit 4 and `BODTest` still extends `junit.framework.TestCase` (JUnit 3). Under Gradle's default JUnit Platform, zero tests run *and the build still passes*. If the test count drops from 81 classes / 1041 tests, this is why.
- **Never apply the `java`, `java-library`, or `jvm-toolchains` plugins.** KGP rejects them as incompatible with KMP. This is also why the OSGi metadata is written by hand instead of using `biz.aQute.bnd.builder` (which applies `java`), and why `javadocJvm` resolves `JavaToolchainService` via `serviceOf` rather than the `javaToolchains` extension.
- **Do not call `withJava()` on the `jvm` target.** Since Kotlin 2.1.20 `src/jvmMain/java` and `src/jvmTest/java` compile automatically.
- **Test resources are loaded from the classpath**, e.g. `getClass().getResourceAsStream("/data/sample1.txt")`. Do not reintroduce filesystem-relative paths — Gradle splits classes from processed resources, so there is no directory containing both.
- `jvmTest` runs with `maxParallelForks = 1`: `SentenceReaderTest.testSetDatagramSocket` binds a fixed UDP port 3810 that `UDPServerMock` also uses, and several tests assert on `Thread.sleep` timing.
- `marine-api/src/commonMain/kotlin/.../MarineApi.kt` is a **placeholder**. Without at least one common Kotlin source the Kotlin/Native compilations are `NO-SOURCE`, produce no `.klib`, and the three Apple publications fail. Delete it once real common code exists.

## Code style

Java sources have no formatter — match the surrounding files:

- **Tabs for indentation.** UTF-8.
- **Every `.java` file starts with the LGPL v3 header**: filename line, `Copyright (C) <year> <author>`, then the standard "This file is part of Java Marine API" blurb. Copy from a neighbouring file.
- Javadoc on all public types and members, with `@author`. Sentence interfaces document an `Example:` NMEA string.
- Naming triad: interface `XXXSentence` (`nmea/sentence`) ⇄ implementation `XXXParser` (`nmea/parser`) ⇄ test `XXXTest`. **The test is named after the sentence, not the parser** — `GGATest` tests `GGAParser`.
- Tests are **JUnit 4**. Do not introduce JUnit 5. Each test class declares `public static final String EXAMPLE = "$GP...";`.

Kotlin and `.gradle.kts` files are formatted by ktfmt (Google style) — run `./gradlew ktfmtFormat` before committing or CI fails.

## Adding a new NMEA sentence

A new sentence type is not usable until its parser is registered in `SentenceFactory.reset()` and its id added to `SentenceId`. Missing the registration is the common failure. See the `/add-sentence` skill and the class-level Javadoc on `SentenceFactory`.

## Publishing

`./gradlew :marine-api:publishAllPublicationsToMavenCentralRepository`, driven by the Release workflow on macOS (so the Apple publications are included). Credentials come from `ORG_GRADLE_PROJECT_mavenCentralUsername/Password` and `ORG_GRADLE_PROJECT_signingInMemoryKey/KeyId/KeyPassword`.

- **Version lives only in `gradle.properties`.** `changelog.txt` keeps its own historical record.
- **KMP splits the coordinates.** Gradle consumers resolve `io.github.solcott:kmp-marine-api` via module metadata; plain Maven consumers must depend on `kmp-marine-api-jvm`.
- `nrjavaserial` is `compileOnly` (was Maven `provided`), used only by `example/SerialPortExample.java`. It does not appear in any published POM.
- Published javadoc jars are **empty stubs** — the real Javadoc is generated by `javadocJvm` but not attached. To be replaced by Dokka when the Kotlin port starts.
- OSGi headers on `jvmJar` are hand-written. `Export-Package` is derived from the source tree; `Import-Package` is deliberately absent, which is what the old empty `<Import-Package/>` achieved.

## Repo etiquette

- Commits: short imperative sentences, often with a trailing `(#PR)`.
- Renovate (`renovate.json`) handles dependency updates; Dependabot was removed with the Maven build.
