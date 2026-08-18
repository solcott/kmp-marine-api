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

## The three edits

All in `examples/`.

1. **The demo itself** — `src/commonMain/kotlin/io/github/solcott/marineapi/example/Demos.kt`,
   alongside `demoFile`, `demoPositions`, `demoAis`, `demoUblox` and `demoOutput`:

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

3. **`build.gradle.kts`** — add the name to `val demos = listOf(...)` (around line 73). That
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
| Browser | the URL fragment, e.g. `#xyz` | No argv. Defaults to `positions`, and fetches `sample.log` over the network. |

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

## Verify on all four

There is a sample log at `marine-api/src/jvmTest/resources/data/Navibe-GM720.txt`. Run every platform
— a demo that works on the JVM and nowhere else is the normal failure:

```
./gradlew :examples:runXyzExample --args="marine-api/src/jvmTest/resources/data/Navibe-GM720.txt"
./gradlew :examples:jsNodeDevelopmentRun -PdemoArgs="xyz marine-api/src/jvmTest/resources/data/Navibe-GM720.txt"
./gradlew :examples:jsBrowserRun          # then open the page with #xyz
./gradlew :examples:runDebugExecutableMacosArm64 -PdemoArgs="xyz marine-api/src/jvmTest/resources/data/Navibe-GM720.txt"
./gradlew ktfmtFormat
```

`./gradlew :examples:jvmJar` compiles without running. The macOS target cannot be linked on Linux, so
the native run is macOS-only.
