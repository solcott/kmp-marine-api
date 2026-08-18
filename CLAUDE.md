# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Fork of ktuukkan/marine-api ("Java Marine API") — a parser library for NMEA 0183, AIS, u-blox and SeaTalk marine data. LGPL v3.

Gradle Kotlin Multiplatform build, published as `io.github.solcott:kmp-marine-api`.

**The Kotlin port is complete.** All source lives in `marine-api/src/commonMain/kotlin` under `io.github.solcott.marineapi` — there is no `src/jvmMain` at all, so every published artifact is built from the same code. The original Java tree under `net.sf.marineapi` was deleted in the phase 6 cutover.

The port was a redesign, not a transliteration: values are immutable, optional NMEA fields are nullable instead of throwing, and line-level failures are returned as `ParseResult` rather than thrown. No reflection — it does not work on Native or JS. See `.claude/plans/` for the phase plan that produced it.

Three modules:

- `:marine-api` — the library. The only published module.
- `:examples` — the demos, in `io.github.solcott.marineapi.example`. Multiplatform: the demo bodies are `commonMain` suspend functions taking a `() -> Source`, and `jvmMain`, `jsMain` (Node **and** browser, one compilation) and `macosArm64Main` each supply an entry point. Never published.
- `:examples-android` — an Android app, the only non-KMP module in the build. `com.android.application` with **no** `kotlin-android` plugin: AGP 9 has built-in Kotlin support and rejects it. The UI is Compose, and `org.jetbrains.kotlin.plugin.compose` attaches to AGP's built-in Kotlin perfectly well without `kotlin-android` — the Compose compiler is versioned with Kotlin, not with the Compose BOM, so the two move independently. Consumes `:marine-api`'s `android` target through module metadata, as an external consumer would. Never published.

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
./gradlew :examples:runFileExample --args="nmea.log"      # run one (see `tasks --group examples`)
./gradlew :examples:jsNodeDevelopmentRun -PdemoArgs="file nmea.log"   # same demo, on Node
./gradlew :examples:jsBrowserRun             # same demo, in a browser
./gradlew :examples:runDebugExecutableMacosArm64 -PdemoArgs="file nmea.log"
./gradlew :examples-android:assembleDebug
./gradlew ktfmtFormat                        # format Kotlin/build scripts (ktfmtCheck in CI)
./gradlew detekt                             # static analysis; also runs under `check`
./gradlew :marine-api:apiDump                # re-pin the public ABI after an intended API change
./gradlew sortDependencies                   # checkSortDependencies in CI
./gradlew :marine-api:publishToMavenLocal
```

There is **no `test` task** — the JVM test task is `jvmTest`.

- Toolchain JDK 25 (pinned in `gradle/gradle-daemon-jvm.properties`), bytecode target 17 via `options.release`.
- **The Android SDK must be present even for JVM-only tasks.** The `com.android.kotlin.multiplatform.library` plugin fails the entire build configuration without it, so `ANDROID_HOME` is required.
- The full build needs macOS — the Apple targets cannot be linked on Linux, though every other target cross-compiles anywhere. CI splits this into an ubuntu `jvm` job (which also runs `linuxX64Test`, the one native suite a Linux host can execute) and a macOS `all-targets` job.

## Build constraints

These are non-obvious and easy to break:

- **`jvmTest` must use `useJUnit()`.** `kotlin-test` resolves to `kotlin-test-junit` under it, and that is what runs the common tests on the jvm target. Switching to `useJUnitPlatform()` means switching `kotlin-test` to its JUnit 5 variant too; get only half of that right and **zero tests run while the build reports success**. Check the count, not the exit code.
- **Never apply the `java`, `java-library`, or `jvm-toolchains` plugins.** KGP rejects them as incompatible with KMP. This is also why the OSGi metadata is written by hand instead of using `biz.aQute.bnd.builder` (which applies `java`), and why `:examples` resolves `JavaToolchainService` via `serviceOf` to give its `JavaExec` tasks a launcher.
- **Do not call `withJava()` on the `jvm` target**, and do not add Java sources back. There are none left in either module.
- **The OSGi `Export-Package` header is derived by walking `src/commonMain/kotlin`.** Pointed at a directory that does not exist, a `fileTree` is simply empty and the bundle ships an **empty** `Export-Package` without failing anything. If that header ever comes out blank, the derivation is looking in the wrong place. `Bundle-SymbolicName` and `Automatic-Module-Name` are `io.github.solcott.marineapi` and track the package root.
- **Test resources are loaded from the classpath**, e.g. `GpsdCorpusTest::class.java.getResource("/data/gpsd")`. Do not reintroduce filesystem-relative paths — Gradle splits classes from processed resources, so there is no directory containing both. This is also why the corpus tests are in `jvmTest` rather than `commonTest`.
- **`jvmTest/resources/data/gpsd/` is a vendored conformance corpus** — 103 NMEA logs from ~90 receivers, taken from gpsd and **BSD-2-Clause, not LGPL**. The notice in that directory's `README.md` must stay with the files. `GpsdCorpusTest` parses and re-encodes every line, and holds a per-file count of the lines that legitimately fail; a count changing in either direction fails the build. This is the corpus that answers "does this cope with real hardware" — it found an `IllegalArgumentException` escaping `parse`, the load-bearing/advisory field split, VTG's format detection and the 8-decimal coordinate cap. `CorpusFixTest` runs the `nmea/io` correlating operators over the same logs and pins their totals (2089 fixes, 2201 satellite views, 36 headings) — those numbers move with any change to what delimits an update cycle, so a change to `positions()` or `satellites()` must update them deliberately. It is test-only and appears in no published jar.
- **Each log has a `.log.chk` beside it: gpsd's OWN decoder output for that input.** With `"scaled":false` the numbers are raw bit fields, so they compare directly with no unit conversion in between. This makes **gpsd the reference implementation** for anything they cover, and a disagreement with them is a bug here until shown otherwise. `GpsdAisCheckTest` compares 1,308 AIS messages field by field; `GpsdFixCheckTest` asserts that every capture gpsd can get a fix out of, `positions()` can too. Between them they found four defects the port had carried: the type 21 name extension appended when the name field was not full, AIS text losing its leading whitespace, `positions()` requiring a velocity, and RMC's date being load-bearing when it is not. See the `/gpsd-crosscheck` skill for adjudicating a disagreement, and `/add-ais-message` for the six types gpsd decodes and this library does not.
- **Do not trust a summarised web fetch for bit-level data.** `WebFetch` runs a small model over the page, and its rendering of gpsd's AIS bit tables for types 21, 24 and 27 was internally inconsistent and contradicted payloads that decode correctly. Bit ranges come from the `.chk` files or from raw source, never from a summary.
- **This library deliberately disagrees with the Java original in places.** Where behaviour differs from the pre-cutover implementation the reason is on the declaration, usually because the old one was wrong: AIS types 4, 18 and 27 read the position-accuracy bit one place early, type 27's navigational status range was reversed, type 9's flags were off by one and its speed scaled by ten, `isDteReady` returned the bit uninverted, `NavStatus` read `V` as valid, and `PositionProvider` required a GGA or GLL in every cycle. Do not "restore" any of these to match an old release.
- **Five of the seven non-Apple native targets have no test task, and that is KGP's doing.** `linuxX64()` and `mingwX64()` return `KotlinNativeTargetWithHostTests`, so they get a test run — but only on their own OS, so `linuxX64Test` runs on the ubuntu CI job and `mingwX64Test` runs nowhere (no Windows runner). `linuxArm64()` and all four `androidNative*()` return a plain `KotlinNativeTarget`, which has no test run on any host. Do not go looking for a missing `linuxArm64Test`. All seven are still compiled and linked everywhere, so a break is a build failure rather than a silent gap.
- **Everything except the Apple targets cross-compiles from any host.** That is what lets the release workflow publish all sixteen publications from one macOS runner, and it is why the macOS `all-targets` job is the slow one. Only Apple is host-locked.
- **`commonMain` must never go back to being empty.** With no common Kotlin source the Kotlin/Native compilations are `NO-SOURCE`, produce no `.klib`, and the three Apple publications fail. It now holds the whole library, so this is only a hazard if something drastic happens.
- `explicitApi()` is on for `:marine-api`: every public declaration needs an explicit visibility and return type.
- **The public ABI is pinned in `marine-api/api/` and `apiCheck` runs under `check`.** Changing the public API fails the build until `./gradlew :marine-api:apiDump` is run and the diff committed; that diff is the review artifact, so read it rather than regenerating past it. Two files: `api/jvm/marine-api.api` is the JVM bytecode ABI, `api/marine-api.klib.api` is one merged dump across all 13 klib targets. **The `android` target is not dumped** — binary-compatibility-validator does not recognise AGP's `com.android.kotlin.multiplatform.library` target, so there is no `androidApiBuild` task. Nothing is lost in practice: there is no `androidMain` source set, so android compiles exactly the `commonMain` the jvm dump already covers. `klib.strictValidation` is off so the ubuntu job, where the Apple targets are disabled, infers them instead of failing.
- **detekt analyses `src/` once per project, not once per compilation.** The extension sets `source` to the whole tree, because the per-compilation tasks the plugin also registers (`detektJvmMain`, `detektMetadataCommonMain`, ...) would report every finding in `commonMain` once per target. Only the plain `detekt` task is wired into `check`. `config/detekt/detekt.yml` holds **only the deviations** from detekt's defaults (`buildUponDefaultConfig = true`), each with the reason it deviates; `MagicNumber` is off because the numbers here are AIS bit ranges and NMEA field indices, which is the whole of what the code is.
- **The IO layer must not choose a dispatcher.** `Source.nmeaResults()`/`nmeaSentences()` read blocking sources on the collecting coroutine. There is no one dispatcher to pick: `Dispatchers.IO` is public API on **JVM and Android only** — on Native it exists but is `internal` (`nativeMain/Dispatchers.kt` in coroutines), and JS/Wasm have no threads at all. Callers add `.flowOn(...)` with whatever their platform has. Do not import it into `commonMain`.
- **The examples' run tasks each work differently, and none of them by accident.** Gradle's `--args` calls `setArgsString()` and **replaces** the argument list, so the JVM tasks pass the demo name as a system property to leave `--args` free for the file path. The Kotlin/Native run task is a plain `Exec` and `jsNodeRun` is a `NodeJsExec`; neither takes `--args`, so both read `-PdemoArgs`. A `CommandLineArgumentProvider` lambda cannot be used to supply them — a SAM conversion in a `.gradle.kts` captures the script object, which the configuration cache refuses to serialize.
- **Kotlin/Native needs an explicit `entryPoint`.** `binaries.executable()` looks for `main` in the **root** package and fails at the LINK step, not the compile, with "Could not find '/main' function".
- **Do not replace `NmeaLineReader` with `kotlinx.io.readLine`.** That splits on `LF` alone; NMEA uses `CRLF`, and captures in the corpus use a lone `CR` throughout or mix both in one file. An `LF`-only split turns a `CR`-terminated feed into one unbounded line.

## Code style

Everything is Kotlin, formatted by ktfmt (Google style) — run `./gradlew ktfmtFormat` before committing or CI fails.

- One `data class` per sentence type, in `nmea/sentence/`, grouped several to a file by theme. Optional fields are nullable with a `null` default.
- Each carries `const val ID` and a `from(fields: SentenceFields)` in its companion, plus private zero-based field-index constants.
- KDoc on every public declaration, with an `Example:` NMEA string on the type. **Say why, not what** — the field layout is visible in the code; what is not visible is which reading the standard supports, what real receivers actually send, and where this disagrees with the Java original.
- Tests are `kotlin.test` in `commonTest`, named after the sentence (`GgaTest`). Test *names are sentences*: `readsEveryField`, `rejectsATimestampItCannotRead`.
- Prefer asserting a value round-trips over asserting an exact re-encoded string: `Double?.field()` trims trailing zeros, so `29.9870` comes back as `29.987`.

## Adding a new NMEA sentence

A new sentence type is not usable until it is registered in `SentenceRegistry.Default`, and `FieldExposureTest.theExamplesCoverEveryRegisteredType` fails until a fully populated example is added there too. Those two steps are the ones that get missed. See the `/add-sentence` skill; `.claude/hooks/check-registration.sh` also warns on a write when either is still outstanding.

## Publishing

`./gradlew :marine-api:publishAllPublicationsToMavenCentralRepository`, driven by the Release workflow on macOS (so the Apple publications are included). Credentials come from `ORG_GRADLE_PROJECT_mavenCentralUsername/Password` and `ORG_GRADLE_PROJECT_signingInMemoryKey/KeyId/KeyPassword`. See the `/release` skill — note that this fork has never released, and the inherited `0.12.0` describes a different library.

- **Version lives only in `gradle.properties`.** `changelog.txt` keeps its own historical record.
- **KMP splits the coordinates.** Gradle consumers resolve `io.github.solcott:kmp-marine-api` via module metadata; plain Maven consumers must depend on `kmp-marine-api-jvm`.
- `nrjavaserial` is an `implementation` dependency of `:examples` only (it was `compileOnly` on the library, and Maven `provided` before that). Used by `SerialPortExample` alone, and `:examples` is not published, so it appears in no POM.
- Published javadoc jars carry real content: the Dokka plugin is applied and the publish plugin picks it up on its own. There is no `configure(KotlinMultiplatform(...))` call, so nothing here drifts out of step with Dokka's task names.
- OSGi headers on `jvmJar` are hand-written. `Export-Package` is derived from the source tree; `Import-Package` is deliberately absent, which is what the old empty `<Import-Package/>` achieved.

## The regression signal

The old signal was the test count, which spanned two suites and is meaningless now that one is gone. What is load-bearing instead, all of it already failing the build when it moves:

- `FieldExposureTest.theExamplesCoverEveryRegisteredType` — every registered type has a fully populated example, round-tripped and checked for silently dropped fields.
- `GpsdCorpusTest` — per-file counts of legitimately failing lines across 103 device logs; a count moving in **either** direction fails.
- `CorpusFixTest` — 2089 fixes, 2201 satellite views, 36 headings across the corpus. It pins more than those three (fixes with altitude, fixes with a date, satellites counted, silent logs); the assertions themselves are the record, so read them rather than this line.
- `SampleDataTest.everyPortedTypeWithCorpusDataIsExercised` — names the types resting only on reference tables rather than real device data.
- Common tests per target: **408**. One suite, so this number is comparable over time. JVM runs 433 — the same 408 plus the 25 corpus tests, which need classpath resources and so live in `jvmTest`. Adding a target does not move this number; it only changes how many targets execute it, and most of the native ones execute it nowhere (see the build constraints).

When one of these numbers moves, `/repin-regressions` covers deciding whether to re-pin it and where each pin lives. A number is never nudged to make the build green.

## Repo etiquette

- Commits: short imperative sentences, often with a trailing `(#PR)`.
- Renovate (`renovate.json`) handles dependency updates; Dependabot was removed with the Maven build.
