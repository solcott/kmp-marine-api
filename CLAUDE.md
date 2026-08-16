# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Fork of ktuukkan/marine-api ("Java Marine API") — a parser library for NMEA 0183, AIS, u-blox and SeaTalk marine data. LGPL v3.

Gradle Kotlin Multiplatform build, published as `io.github.solcott:kmp-marine-api`.

**The Kotlin port is complete.** All source lives in `marine-api/src/commonMain/kotlin` under `io.github.solcott.marineapi`. The original Java tree under `net.sf.marineapi` was deleted in the phase 6 cutover; `src/jvmMain` now holds only the `doc/` resources that ship in the jvm jar.

The port was a redesign, not a transliteration: values are immutable, optional NMEA fields are nullable instead of throwing, and line-level failures are returned as `ParseResult` rather than thrown. No reflection — it does not work on Native or JS. See `.claude/plans/` for the phase plan that produced it.

Two modules:

- `:marine-api` — the library. The only published module.
- `:examples` — six demo applications in `io.github.solcott.marineapi.example`, each a file with a top-level `main`. JVM-only (`jvm()` and nothing else), never published, and depends on `:marine-api`.

This fork is **not** intended to send PRs upstream — divergence is fine.

## Build & test

```
./gradlew build                              # all targets
./gradlew :marine-api:jvmTest                # JVM tests only
./gradlew :marine-api:allTests               # every target
./gradlew :marine-api:jvmTest --tests '*GgaTest'          # single test class
./gradlew :marine-api:jvmTest --tests '*GgaTest.readsEveryField'   # single method
./gradlew :marine-api:dokkaGeneratePublicationHtml        # API docs
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

- **`jvmTest` must use `useJUnit()`.** `kotlin-test` resolves to `kotlin-test-junit` under it, and that is what runs the common tests on the jvm target. Switching to `useJUnitPlatform()` means switching `kotlin-test` to its JUnit 5 variant too; get only half of that right and **zero tests run while the build reports success**. Check the count, not the exit code.
- **Never apply the `java`, `java-library`, or `jvm-toolchains` plugins.** KGP rejects them as incompatible with KMP. This is also why the OSGi metadata is written by hand instead of using `biz.aQute.bnd.builder` (which applies `java`), and why `:examples` resolves `JavaToolchainService` via `serviceOf` to give its `JavaExec` tasks a launcher.
- **Do not call `withJava()` on the `jvm` target**, and do not add Java sources back. There are none left in either module.
- **The OSGi `Export-Package` header is derived by walking `src/commonMain/kotlin`.** Pointed at a directory that does not exist, a `fileTree` is simply empty and the bundle ships an **empty** `Export-Package` without failing anything. If that header ever comes out blank, the derivation is looking in the wrong place. `Bundle-SymbolicName` and `Automatic-Module-Name` are `io.github.solcott.marineapi` and track the package root.
- **Test resources are loaded from the classpath**, e.g. `GpsdCorpusTest::class.java.getResource("/data/gpsd")`. Do not reintroduce filesystem-relative paths — Gradle splits classes from processed resources, so there is no directory containing both. This is also why the corpus tests are in `jvmTest` rather than `commonTest`.
- **`jvmTest/resources/data/gpsd/` is a vendored conformance corpus** — 103 NMEA logs from ~90 receivers, taken from gpsd and **BSD-2-Clause, not LGPL**. The notice in that directory's `README.md` must stay with the files. `GpsdCorpusTest` parses and re-encodes every line, and holds a per-file count of the lines that legitimately fail; a count changing in either direction fails the build. This is the corpus that answers "does this cope with real hardware" — it found an `IllegalArgumentException` escaping `parse`, the load-bearing/advisory field split, VTG's format detection and the 8-decimal coordinate cap. `CorpusFixTest` runs the `nmea/io` correlating operators over the same logs and pins their totals (1854 fixes, 2201 satellite views, 36 headings) — those numbers move with any change to what delimits an update cycle, so a change to `positions()` or `satellites()` must update them deliberately. It is test-only and appears in no published jar.
- **This library deliberately disagrees with the Java original in places.** Where behaviour differs from the pre-cutover implementation the reason is on the declaration, usually because the old one was wrong: AIS types 4, 18 and 27 read the position-accuracy bit one place early, type 27's navigational status range was reversed, type 9's flags were off by one and its speed scaled by ten, `isDteReady` returned the bit uninverted, `NavStatus` read `V` as valid, and `PositionProvider` required a GGA or GLL in every cycle. Do not "restore" any of these to match an old release.
- **`commonMain` must never go back to being empty.** With no common Kotlin source the Kotlin/Native compilations are `NO-SOURCE`, produce no `.klib`, and the three Apple publications fail. It now holds the whole library, so this is only a hazard if something drastic happens.
- `explicitApi()` is on for `:marine-api`: every public declaration needs an explicit visibility and return type.
- **The IO layer must not choose a dispatcher.** `Source.nmeaResults()`/`nmeaSentences()` read blocking sources on the collecting coroutine. `Dispatchers.IO` exists on JVM and Native but not JS or Wasm, so callers add `.flowOn(...)` themselves. Do not import it into `commonMain`.
- **Do not replace `NmeaLineReader` with `kotlinx.io.readLine`.** That splits on `LF` alone; NMEA uses `CRLF`, and captures in the corpus use a lone `CR` throughout or mix both in one file. An `LF`-only split turns a `CR`-terminated feed into one unbounded line.

## Code style

Everything is Kotlin, formatted by ktfmt (Google style) — run `./gradlew ktfmtFormat` before committing or CI fails.

- One `data class` per sentence type, in `nmea/sentence/`, grouped several to a file by theme. Optional fields are nullable with a `null` default.
- Each carries `const val ID` and a `from(fields: SentenceFields)` in its companion, plus private zero-based field-index constants.
- KDoc on every public declaration, with an `Example:` NMEA string on the type. **Say why, not what** — the field layout is visible in the code; what is not visible is which reading the standard supports, what real receivers actually send, and where this disagrees with the Java original.
- Tests are `kotlin.test` in `commonTest`, named after the sentence (`GgaTest`). Test *names are sentences*: `readsEveryField`, `rejectsATimestampItCannotRead`.
- Prefer asserting a value round-trips over asserting an exact re-encoded string: `Double?.field()` trims trailing zeros, so `29.9870` comes back as `29.987`.

## Adding a new NMEA sentence

A new sentence type is not usable until it is registered in `SentenceRegistry.Default`, and `FieldExposureTest.theExamplesCoverEveryRegisteredType` fails until a fully populated example is added there too. Those two steps are the ones that get missed. See the `/add-sentence` skill.

## Publishing

`./gradlew :marine-api:publishAllPublicationsToMavenCentralRepository`, driven by the Release workflow on macOS (so the Apple publications are included). Credentials come from `ORG_GRADLE_PROJECT_mavenCentralUsername/Password` and `ORG_GRADLE_PROJECT_signingInMemoryKey/KeyId/KeyPassword`.

- **Version lives only in `gradle.properties`.** `changelog.txt` keeps its own historical record.
- **KMP splits the coordinates.** Gradle consumers resolve `io.github.solcott:kmp-marine-api` via module metadata; plain Maven consumers must depend on `kmp-marine-api-jvm`.
- `nrjavaserial` is an `implementation` dependency of `:examples` only (it was `compileOnly` on the library, and Maven `provided` before that). Used by `SerialPortExample` alone, and `:examples` is not published, so it appears in no POM.
- Published javadoc jars carry real content: the Dokka plugin is applied and the publish plugin picks it up on its own. There is no `configure(KotlinMultiplatform(...))` call, so nothing here drifts out of step with Dokka's task names.
- OSGi headers on `jvmJar` are hand-written. `Export-Package` is derived from the source tree; `Import-Package` is deliberately absent, which is what the old empty `<Import-Package/>` achieved.

## The regression signal

The old signal was the test count, which spanned two suites and is meaningless now that one is gone. What is load-bearing instead, all of it already failing the build when it moves:

- `FieldExposureTest.theExamplesCoverEveryRegisteredType` — every registered type has a fully populated example, round-tripped and checked for silently dropped fields.
- `GpsdCorpusTest` — per-file counts of legitimately failing lines across 103 device logs; a count moving in **either** direction fails.
- `CorpusFixTest` — 1854 fixes, 2201 satellite views, 36 headings across the corpus.
- `SampleDataTest.everyPortedTypeWithCorpusDataIsExercised` — names the types resting only on reference tables rather than real device data.
- Common tests per target: **383**. One suite, so this number is comparable over time. JVM runs 405 (383 common + the corpus tests, which need classpath resources).

## Repo etiquette

- Commits: short imperative sentences, often with a trailing `(#PR)`.
- Renovate (`renovate.json`) handles dependency updates; Dependabot was removed with the Maven build.
