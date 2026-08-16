package io.github.solcott.marineapi.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * The Kotlin/Native entry point.
 *
 * ```
 * ./gradlew :examples:runDebugExecutableMacosArm64 --args="file marine-api/src/jvmTest/resources/data/Navibe-GM720.txt"
 * ```
 *
 * Nearly the JVM entry point, with one difference worth the whole file: **`Dispatchers.IO` is not
 * public API on Kotlin/Native.** It exists -- `internal val IO: CoroutineDispatcher =
 * DefaultIoScheduler` in coroutines' `nativeMain/Dispatchers.kt` -- but you cannot reach it, so a
 * blocking read goes on [Dispatchers.Default] or a pool of your own. Native has `runBlocking` and
 * `SystemFileSystem` just as the JVM does; the dispatcher is the only thing that moves.
 */
fun main(args: Array<String>) =
  runBlocking(Dispatchers.Default) {
    val demo = args.firstOrNull()
    val path = args.getOrNull(1)
    runDemo(demo, path?.let { { openFile(it) } })
  }

private fun openFile(path: String): Source = SystemFileSystem.source(Path(path)).buffered()
