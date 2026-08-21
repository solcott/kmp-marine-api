---
name: add-demo
description: Add a demo to the :examples module so it runs on the JVM, Node, the browser and macOS native from one implementation. Use when asked to add, write or wire up an example or demo of the library, or to make an existing demo runnable from Gradle.
---

# Adding a demo to :examples

A demo is written **once**, in `commonMain`, and runs on four platforms. `:examples` is never
published.

The four entry points (`jvmMain/Main.kt`, `jsMain/Main.kt` — Node *and* browser in one compilation —
and `macosArm64Main/Main.kt`) all dispatch through `runDemo` in `Cli.kt`. **None of them needs
editing to add a demo.** If a change appears to require touching an entry point, the demo is reaching
for something platform-specific and should take it as a parameter instead — the way every demo takes
`open: () -> Source` rather than opening a file itself.

`:examples-android` is **not** part of this. It consumes the published `:marine-api` android target
directly, as an external consumer would, and has its own Compose UI that calls
`nmeaSentences().positions()` itself. It shares no code with `:examples` and adding a demo does not
touch it.

## The edits

All in `examples/` — three, or four for a demo you want working in a browser.

1. **The demo itself** — in `src/commonMain/kotlin/io/github/solcott/marineapi/example/`. `Demos.kt`
   holds the demos of `:marine-api`, the parser (`demoFile`, `demoPositions`, `demoAis`,
   `demoUblox`, `demoOutput`); each demo of `:marine-api-nav` gets its own file named after it
   (`AccuracyDemo.kt`, `TrafficDemo.kt`, `DepthDemo.kt`, `WindDemo.kt`, `RouteDemo.kt`). That split
   is not taste: **detekt caps a file at eleven top-level functions**, private helpers included, and
   `Demos.kt` reached the cap. A new demo with two or three helpers goes in a new file rather than
   into an existing one. Formatting shared between demos lives in `Format.kt` and is `internal`,
   because Kotlin's `private` is file-scoped.

   A demo that reports what a feed carried besides sentences uses `FeedNoise`, not a counter of its
   own. **Counting every non-`Ok` `ParseResult` as a failure is wrong**: `#` headers are the
   ordinary convention in a capture and some streams interleave non-NMEA records, so a bare count
   turns a log's provenance header into phantom transmission errors. `FeedNoise` splits them the way
   the corpus tests in `:marine-api` already do.

   ```kotlin
   /** KDoc saying what the demo shows, and why that is worth showing. */
   suspend fun demoXyz(open: () -> Source) {
     open().nmeaSentences().collect { ... }
   }
   ```

   - Take `open: () -> Source`, not a path, not a file. A demo that needs no feed takes no parameter
     (`demoOutput`); a demo that can supply its own falls back to a built-in sample (`demoUblox` uses
     `UBLOX_SAMPLE` via `sourceOf`).
   - `println` is the whole output layer. Do not add a logging dependency.
   - **Do not call `flowOn` in the demo.** The IO layer deliberately does not choose a dispatcher, and
     the *entry points* are where that decision lands: the JVM uses `runBlocking(Dispatchers.IO)`,
     macOS uses `Dispatchers.Default` because `Dispatchers.IO` is `internal` on Kotlin/Native, and JS
     has no threads at all. A `flowOn` in `commonMain` would not compile everywhere and would take
     the decision away from the caller, which is the thing this library is careful not to do.

2. **`Cli.kt`** — three places in one small file:
   - a branch in the `when (demo)` inside `runDemo`, using `withFeed(demo, open, ::demoXyz)` if the
     demo needs a feed, or calling it directly if it does not;
   - the name in `DEMOS`;
   - a line in `USAGE`, aligned with the others, in the same order as `DEMOS`.

3. **`build.gradle.kts`** — add the name to `val demos = listOf(...)`. That
   generates the `run<Name>Example` JavaExec task. The comment there notes that a name in this list
   with no branch in `runDemo` prints the usage text rather than failing, which is the right way round
   for a demo — but it also means **a missing `Cli.kt` branch is silent**. Do both edits together.

## The argument conventions, all three different on purpose

Do not try to unify these. Each is the way it is for a reason recorded in the source:

| Platform | How the demo name arrives | Why |
| --- | --- | --- |
| JVM | `systemProperty("marineapi.demo", demo)` | Gradle's `--args` calls `setArgsString()`, which **replaces** the argument list. A demo name passed as an argument would vanish the moment a user passed a file path. The system property leaves `--args` free for the path. |
| macOS native | `-PdemoArgs="xyz nmea.log"` | The run task is a plain `Exec` and does not accept `--args`. |
| Node | `-PdemoArgs="xyz nmea.log"` | `jsNodeRun` is a `NodeJsExec` and does not accept `--args` either. Defaults to `ublox`, the one demo that carries its own feed. |
| Browser | the URL fragment, e.g. `#xyz` | No argv. Defaults to `positions`, and fetches a log over the network. |

A browser demo needs a **fourth** edit the others do not: `sampleFor` in `jsMain/Main.kt` picks
which of the bundled logs to fetch, and `jsMain/resources/index.html` lists the demos as links. No
NMEA feed carries everything — a masthead unit and an echo sounder share a bus with each other and
not with a GPS, and only a plotter sends a route — so a demo left on the default `sample.log` prints
nothing and reads as broken rather than as empty. Add a log to `jsMain/resources/` if none of
`sample.log`, `instruments.log`, `route.log` or `ais.log` carries what the demo reads.

Two further traps in `build.gradle.kts`, both already commented there:

- **`-PdemoArgs` is read with `providers.gradleProperty(...)` at configuration time, not through a
  `CommandLineArgumentProvider`.** A SAM-converted lambda in a `.gradle.kts` captures the enclosing
  script object, which the configuration cache refuses to serialize. A `gradleProperty` read is
  tracked as a configuration input, so it stays correct.
- **The run tasks set `workingDir = repositoryRoot`**, so a file path is written relative to the
  repository root rather than to `examples/`. A demo with a built-in sample path must respect that.

## If the demo reads a file

The JS compilation serves Node **and** the browser from one `main`. That is only safe because
`kotlinx-io` binds `node:fs` lazily — a browser bundle that never touches `SystemFileSystem` never
calls `require`. A demo that reaches for `SystemFileSystem` in `commonMain` breaks the browser build.
Take the `Source` from the caller; that is what the parameter is for.

**The factory may be called more than once**, and `demoTraffic` does: it reads the feed once for own
ship's position and once for the AIS targets, because a flow has one ending and that demo needs two.
Every entry point hands back a fresh `Source` each call -- the JVM and Node reopen the file, the
browser wraps a new `Buffer` over bytes it already holds -- so this is safe from a log. It is *not*
safe from a live device: reopening a serial port mid-stream loses whatever arrived in between. A
program reading a device holds the last fix as it goes instead, which is what a demo cannot do
because the log has already ended by the time it prints.

## Verify on all four

Pick a log that actually carries what the demo reads, or the run proves nothing. In
`marine-api/src/jvmTest/resources/data/`: `Navibe-GM720.txt` and `gpsd/skytraq-fix.log` for GPS
(the latter flips fix quality and sends a `GST`), `sample1.txt` for wind, depth and log
instruments, `Garmin-GPS76_route.txt` for routes and waypoints, `AIS-VDM-VDO.txt` for AIS.

Run every platform — a demo that works on the JVM and nowhere else is the normal failure:

```
./gradlew :examples:runXyzExample --args="marine-api/src/jvmTest/resources/data/Navibe-GM720.txt"
./gradlew :examples:jsNodeDevelopmentRun -PdemoArgs="xyz marine-api/src/jvmTest/resources/data/Navibe-GM720.txt"
./gradlew :examples:jsBrowserRun          # then open the page with #xyz
./gradlew :examples:runDebugExecutableMacosArm64 -PdemoArgs="xyz marine-api/src/jvmTest/resources/data/Navibe-GM720.txt"
./gradlew ktfmtFormat
```

`./gradlew :examples:jvmJar` compiles without running. The macOS target cannot be linked on Linux, so
the native run is macOS-only. To check the browser without a live server run, build
`:examples:jsBrowserDevelopmentExecutableDistribution` and serve `examples/build/dist/js/
developmentExecutable` — the logs are copied in beside `examples.js`.

**Compare the output across platforms rather than just checking each one ran.** The demos share
every line of their code, so a difference is always in something under them, and it is usually
number formatting: Kotlin/JS renders the `Double` `4.0` as `4` where the JVM and Native render
`4.0`, so a bare `$someDouble` in a `println` makes one platform's output differ from the others'.
Send it through `toHundredths()` in `Format.kt`.
