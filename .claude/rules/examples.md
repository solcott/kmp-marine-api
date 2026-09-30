---
paths:
  - "examples/**"
---

# `:examples`

The demos, in `io.github.solcott.marineapi.example`. Never published. A demo is written **once**,
in `commonMain`, as a suspend function taking `open: () -> Source`, and runs on four platforms:
`jvmMain`, `jsMain` (Node **and** browser, one compilation) and `macosArm64Main` each supply an
entry point, all dispatching through `runDemo` in `Cli.kt`. To add one, use the `/add-demo` skill.

## In demo code

- **Take the `Source` from the caller.** If a change appears to need editing an entry point, the
  demo is reaching for something platform-specific and should take it as a parameter instead.
- **Never touch `SystemFileSystem` in `commonMain`.** The JS compilation serves Node and the browser
  from one `main`, which is only safe because `kotlinx-io` binds `node:fs` lazily -- a browser
  bundle that never touches `SystemFileSystem` never calls `require`.
- **Do not call `flowOn` in a demo.** The library deliberately does not choose a dispatcher and the
  *entry points* are where that decision lands: the JVM uses `runBlocking(Dispatchers.IO)`, macOS
  `Dispatchers.Default` (`Dispatchers.IO` is `internal` on Kotlin/Native), and JS has no threads.
- `println` is the whole output layer. Do not add a logging dependency.
- `nrjavaserial` is an `implementation` dependency here only, used by `SerialPortExample` alone.

## The run tasks: each one differs, and none of them by accident

Do not try to unify these.

| Platform | How the demo name arrives | Why |
| --- | --- | --- |
| JVM | `systemProperty("marineapi.demo", demo)` | Gradle's `--args` calls `setArgsString()`, which **replaces** the argument list. The system property leaves `--args` free for the file path. |
| macOS native | `-PdemoArgs="xyz nmea.log"` | The run task is a plain `Exec`; it does not take `--args`. |
| Node | `-PdemoArgs="xyz nmea.log"` | `jsNodeRun` is a `NodeJsExec`; it does not take `--args` either. Defaults to `ublox`, the one demo carrying its own feed. |
| Browser | the URL fragment, e.g. `#xyz` | No argv. Defaults to `positions`, and fetches `sample.log` over the network. |

- **`-PdemoArgs` is read with `providers.gradleProperty(...)`, not a `CommandLineArgumentProvider`.**
  A SAM-converted lambda in a `.gradle.kts` captures the script object, which the configuration
  cache refuses to serialize.
- **The run tasks set `workingDir = repositoryRoot`**, so file paths are relative to the repository
  root, not to `examples/`.
- **A name in `val demos` with no branch in `runDemo` prints the usage text rather than failing** --
  so a missing `Cli.kt` branch is silent. Edit both together.
- **Kotlin/Native needs an explicit `entryPoint`.** `binaries.executable()` looks for `main` in the
  **root** package and fails at the LINK step, not the compile, with "Could not find '/main'
  function".
