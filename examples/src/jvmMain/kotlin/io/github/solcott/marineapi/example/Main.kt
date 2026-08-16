package io.github.solcott.marineapi.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * The JVM entry point.
 *
 * ```
 * ./gradlew :examples:runFileExample --args="marine-api/src/jvmTest/resources/data/Navibe-GM720.txt"
 * ./gradlew :examples:runOutputExample
 * ```
 *
 * All it does is supply the two platform-specific things [runDemo] needs: a way to open a file, and
 * a coroutine to run in. The demos themselves are in `commonMain` and are the same code the
 * browser, Node and macOS entry points run.
 *
 * `runBlocking(Dispatchers.IO)` is where the dispatcher decision lands. The library refuses to make
 * it because there is no one right answer: `Dispatchers.IO` is public API here and on Android, is
 * `internal` on Kotlin/Native, and does not exist on JS or Wasm. The macOS entry point beside this
 * one uses `Dispatchers.Default` for exactly that reason.
 */
fun main(args: Array<String>) {
  // The Gradle run tasks pass the demo name as a system property rather than an argument, because
  // `--args` REPLACES the argument list: a demo name set at configuration time would be wiped the
  // moment a user passed a file path. That leaves argv holding just the path.
  val fromTask = System.getProperty(DEMO_PROPERTY)
  val demo = fromTask ?: args.firstOrNull()
  val path = if (fromTask != null) args.firstOrNull() else args.getOrNull(1)

  runBlocking(Dispatchers.IO) { runDemo(demo, path?.let { { openFile(it) } }) }
}

/** Set by the `run<Name>Example` Gradle tasks. */
private const val DEMO_PROPERTY = "marineapi.demo"

private fun openFile(path: String): Source = SystemFileSystem.source(Path(path)).buffered()
