# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Fork of ktuukkan/marine-api ("Java Marine API") — a parser library for NMEA 0183, AIS, u-blox and SeaTalk marine data. LGPL v3.

Gradle Kotlin Multiplatform build, published as `io.github.solcott:kmp-marine-api`.

**The Kotlin port is complete.** All source lives in `marine-api/src/commonMain/kotlin` under `io.github.solcott.marineapi` — there is no `src/jvmMain` at all, so every published artifact is built from the same code. The original Java tree under `net.sf.marineapi` was deleted in the phase 6 cutover.

The port was a redesign, not a transliteration: values are immutable, optional NMEA fields are nullable instead of throwing, and line-level failures are returned as `ParseResult` rather than thrown. No reflection — it does not work on Native or JS.

Three modules:

- `:marine-api` — the library. The only published module.
- `:examples` — the demos. Multiplatform: one `commonMain` implementation per demo, run on the JVM, Node, the browser and macOS native. Never published.
- `:examples-android` — an Android app (Compose, Hilt, Navigation 3), the only non-KMP module in the build. Consumes `:marine-api`'s `android` target as an external consumer would. Never published.

This fork is **not** intended to send PRs upstream — divergence is fine.

## Where the rest of the guidance lives

- **`.claude/rules/`** — path-scoped rules, loaded when working on matching files: build scripts, `:examples`, `:examples-android`, library source, NMEA sentences, AIS, the `nmea/io` layer, the corpus tests, and versioning/CI. Constraints that only matter in one part of the tree belong there, not here.
- **`.claude/skills/`** — procedures: `/add-sentence`, `/add-ais-message`, `/add-demo`, `/gpsd-crosscheck`, `/repin-regressions`, `/release`.
- **`.claude/hooks/`** — enforcement, wired in `.claude/settings.json`. They refuse a bare `./gradlew test`, refuse hand edits to `marine-api/api/`, ask before touching the vendored gpsd corpus, block a `git commit` that would fail `ktfmtCheck`/`checkSortDependencies`, report the test count after a Gradle test run, and warn on write about missed registration steps and forbidden constructs (java plugins, `withJava()`, kapt, `Dispatchers.IO`/`flowOn` in `commonMain`, ...).

## Build & test

```
./gradlew build                              # all targets
./gradlew :marine-api:jvmTest                # JVM tests only
./gradlew :marine-api:allTests               # every target, android included
./gradlew :marine-api:testAndroidHostTest    # the common suite on the android target
./gradlew :marine-api:jvmTest --tests '*GgaTest'          # single test class
./gradlew :marine-api:jvmTest --tests '*GgaTest.readsEveryField'   # single method
./gradlew :marine-api:dokkaGeneratePublicationHtml        # API docs
./gradlew :examples:jvmJar                   # compile the examples
./gradlew :examples:runFileExample --args="nmea.log"      # run one (see `tasks --group examples`)
./gradlew :examples:jsNodeDevelopmentRun -PdemoArgs="file nmea.log"   # same demo, on Node
./gradlew :examples:jsBrowserRun             # same demo, in a browser
./gradlew :examples:runDebugExecutableMacosArm64 -PdemoArgs="file nmea.log"
./gradlew :examples-android:assembleDebug
./gradlew :examples-android:testDebugUnitTest    # the app's ViewModel tests
./gradlew ktfmtFormat                        # format Kotlin/build scripts (ktfmtCheck in CI)
./gradlew detekt                             # static analysis; also runs under `check`
./gradlew :marine-api:apiDump                # re-pin the public ABI after an intended API change
./gradlew sortDependencies                   # checkSortDependencies in CI
./gradlew :marine-api:publishToMavenLocal
```

- There is **no library `test` task** — the JVM test task is `jvmTest`. A bare `./gradlew test` resolves to `:examples-android:test` and runs none of the library's tests while reporting success.
- **Check the test count, not the exit code.** A kotlin-test/JUnit runner mismatch makes zero tests run while the build stays green.
- Toolchain JDK 25 (pinned in `gradle/gradle-daemon-jvm.properties`), bytecode target 17 via `options.release`.
- **The Android SDK must be present even for JVM-only tasks.** The `com.android.kotlin.multiplatform.library` plugin fails the entire build configuration without it, so `ANDROID_HOME` is required.
- The full build needs macOS — the Apple targets cannot be linked on Linux, though every other target cross-compiles anywhere.

## Working rules that apply everywhere

- **Do not trust a summarised web fetch for bit-level data.** `WebFetch` runs a small model over the page, and its rendering of gpsd's AIS bit tables for types 21, 24 and 27 was internally inconsistent. Bit ranges and field layouts come from the corpus `.log.chk` files or from raw source.
- **gpsd is the reference implementation.** Each corpus log in `marine-api/src/jvmTest/resources/data/gpsd/` has gpsd's own decode beside it; a disagreement is a bug here until shown otherwise (`/gpsd-crosscheck`).
- **This library deliberately disagrees with the Java original in places**, with the reason on each declaration. Do not "restore" old behaviour to match an old release.
- **A pinned number is never nudged to make the build green.** See the regression signal below.

## Code style

Everything is Kotlin, formatted by ktfmt (Google style) — run `./gradlew ktfmtFormat` before committing or CI fails.

- `explicitApi()` is on for `:marine-api`. KDoc on every public declaration; **say why, not what**.
- Tests are `kotlin.test`. Test *names are sentences*: `readsEveryField`, `rejectsATimestampItCannotRead`.

## The regression signal

What is load-bearing, all of it already failing the build when it moves:

- `FieldExposureTest.theExamplesCoverEveryRegisteredType` — every registered type has a fully populated example, round-tripped and checked for silently dropped fields.
- `GpsdCorpusTest` — per-file counts of legitimately failing lines across 103 device logs; a count moving in **either** direction fails.
- `CorpusFixTest` — 2089 fixes, 2201 satellite views, 36 headings across the corpus, and the 288 fixes (from 15 of the 103 receivers) that carry a measured accuracy. It pins more than those; the assertions themselves are the record, so read them rather than this line.
- `SampleDataTest.everyPortedTypeWithCorpusDataIsExercised` — names the types resting only on reference tables rather than real device data.
- Common tests per target: **421**. One suite, so this number is comparable over time. JVM runs 448 — the same 421 plus the 27 corpus tests, which need classpath resources and so live in `jvmTest`. Android runs the plain 421 through `testAndroidHostTest`. Adding a target does not move this number; it only changes how many targets execute it, and most of the native ones execute it nowhere.

This section is the **only** place the per-target test counts are written down; skills, agents and hooks point here rather than repeating them. When one of these numbers moves, `/repin-regressions` covers deciding whether to re-pin it and where each pin lives, and this section is updated in the same commit.

## Repo etiquette

- Commits: short imperative sentences, often with a trailing `(#PR)`.
- Renovate (`renovate.json`) handles dependency updates; Dependabot was removed with the Maven build.
