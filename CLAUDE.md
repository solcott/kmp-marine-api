# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Fork of ktuukkan/marine-api ("Java Marine API") — a parser library for NMEA 0183, AIS, u-blox and SeaTalk marine data. LGPL v3.

Gradle Kotlin Multiplatform build, published as `io.github.solcott:kmp-marine-api`.

**The Kotlin port is in progress.** Two trees coexist:

- `marine-api/src/commonMain/kotlin` under `io.github.solcott.marineapi` — the new multiplatform API. New work goes here.
- `marine-api/src/jvmMain/java` under `net.sf.marineapi` — the original Java library, still the complete implementation. It is deleted at the end of the port; don't extend it.

The port is a redesign, not a transliteration: values are immutable, optional NMEA fields are nullable instead of throwing, and line-level failures are returned as `ParseResult` rather than thrown. No reflection — it does not work on Native or JS. See `.claude/plans/` for the phase plan.

Two modules:

- `:marine-api` — the library. The only published module.
- `:examples` — the demo applications in `net.sf.marineapi.example`, each with a `main`. JVM-only (`jvm()` and nothing else), never published, and depends on `:marine-api`.

This fork is **not** intended to send PRs upstream — divergence is fine.

## Build & test

```
./gradlew build                              # all targets
./gradlew :marine-api:jvmTest                # JVM tests only
./gradlew :marine-api:jvmTest --tests '*GGATest'          # single test class
./gradlew :marine-api:jvmTest --tests '*GGATest.testGetAltitude'   # single method
./gradlew :marine-api:javadocJvm             # Javadoc for the Java sources
./gradlew :examples:jvmJar                   # compile the examples
./gradlew :examples:runFileExample --args="nmea.log"      # run one example (see `tasks --group examples`)
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
- **`jvmTest/resources/data/gpsd/` is a vendored conformance corpus** — 103 NMEA logs from ~90 receivers, taken from gpsd and **BSD-2-Clause, not LGPL**. The notice in that directory's `README.md` must stay with the files. `GpsdCorpusTest` parses and re-encodes every line, and holds a per-file count of the lines that legitimately fail; a count changing in either direction fails the build. This is the corpus that answers "does this cope with real hardware" — it found an `IllegalArgumentException` escaping `parse`, the load-bearing/advisory field split, VTG's format detection and the 8-decimal coordinate cap. It is test-only and appears in no published jar.
- `jvmTest` runs with `maxParallelForks = 1`: `SentenceReaderTest.testSetDatagramSocket` binds a fixed UDP port 3810 that `UDPServerMock` also uses, and several tests assert on `Thread.sleep` timing.
- **`commonMain` must never go back to being empty.** With no common Kotlin source the Kotlin/Native compilations are `NO-SOURCE`, produce no `.klib`, and the three Apple publications fail. The `MarineApi.kt` placeholder that used to guarantee this was deleted once the port began. `:examples` needs no such placeholder — it has zero Kotlin sources anywhere, and `compileJvmMainJava` still runs with `compileKotlinJvm` at `NO-SOURCE`.
- `explicitApi()` is on for `:marine-api`: every public declaration needs an explicit visibility and return type.
- **The IO layer must not choose a dispatcher.** `Source.nmeaResults()`/`nmeaSentences()` read blocking sources on the collecting coroutine. `Dispatchers.IO` exists on JVM and Native but not JS or Wasm, so callers add `.flowOn(...)` themselves. Do not import it into `commonMain`.
- **Do not replace `NmeaLineReader` with `kotlinx.io.readLine`.** That splits on `LF` alone; NMEA uses `CRLF`, and captures in the corpus use a lone `CR` throughout or mix both in one file. An `LF`-only split turns a `CR`-terminated feed into one unbounded line.

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
- `nrjavaserial` is an `implementation` dependency of `:examples` only (it was `compileOnly` on the library, and Maven `provided` before that). Used by `SerialPortExample` alone, and `:examples` is not published, so it appears in no POM.
- `Export-Package` no longer lists `net.sf.marineapi.example` — the Maven/Felix bundle exported it because the examples lived in the library source tree.
- Published javadoc jars are **empty stubs** — the real Javadoc is generated by `javadocJvm` but not attached. To be replaced by Dokka when the Kotlin port starts.
- OSGi headers on `jvmJar` are hand-written. `Export-Package` is derived from the source tree; `Import-Package` is deliberately absent, which is what the old empty `<Import-Package/>` achieved.

## Repo etiquette

- Commits: short imperative sentences, often with a trailing `(#PR)`.
- Renovate (`renovate.json`) handles dependency updates; Dependabot was removed with the Maven build.
