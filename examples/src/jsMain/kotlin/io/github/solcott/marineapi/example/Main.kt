package io.github.solcott.marineapi.example

import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.khronos.webgl.Int8Array

/**
 * The JavaScript entry point, for Node and for the browser.
 *
 * ```
 * ./gradlew :examples:jsNodeRun        # reads a file
 * ./gradlew :examples:jsBrowserRun     # fetches sample.log, then open the page
 * ```
 *
 * One `main` for both, because a Kotlin/JS target has one compilation however many environments it
 * runs in. That is only workable because kotlinx-io binds `node:fs` lazily -- see
 * `jsMain/node/nodeModulesJs.kt` in kotlinx-io, where `fs` is a `by lazy {
 * js("eval('require')('fs')") }`. A browser bundle that never touches [SystemFileSystem] never
 * forces that `lazy`, so `require` is never called and nothing breaks.
 *
 * `suspend fun main` rather than `runBlocking`: JS has no threads to block, and coroutines does not
 * ship `runBlocking` for this target at all.
 */
suspend fun main() {
  if (isNode) runOnNode() else runInBrowser()
}

/**
 * Under Node, this is the JVM demo with a different way of spelling argv.
 *
 * `process.argv` is `[node, script, ...]`, so the demo name and path start at index 2.
 */
private suspend fun runOnNode() {
  val argv = js("process.argv").unsafeCast<Array<String>>().drop(2)
  val demo = argv.firstOrNull() ?: "ublox" // the one demo that brings its own feed
  val path = argv.getOrNull(1)
  runDemo(demo, path?.let { { SystemFileSystem.source(Path(it)).buffered() } })
}

/**
 * In a browser there is no filesystem, so the bytes come over the network.
 *
 * This is the only genuinely different platform in the set, and the difference is two lines: fetch
 * the bytes, wrap them in a [Buffer]. That `Buffer` is a [Source] like any other, so the demo it is
 * handed to is the identical common code Node, the JVM, macOS and Android all run.
 *
 * A WebSocket -- which is how NMEA usually reaches a browser, from a boat's WiFi gateway -- is the
 * same shape: take the bytes of each message and write them into a `Buffer`.
 */
private suspend fun runInBrowser() {
  val demo = window.location.hash.removePrefix("#").ifEmpty { "positions" }
  println("-- $demo --")

  val response = window.fetch(SAMPLE_URL).await()
  if (!response.ok) {
    println("could not fetch $SAMPLE_URL: ${response.status}")
    return
  }
  // ByteArray is an Int8Array underneath on Kotlin/JS, so this is a view rather than a copy.
  val bytes = Int8Array(response.arrayBuffer().await()).unsafeCast<ByteArray>()

  runDemo(demo) { Buffer().also { it.write(bytes) } }
}

private const val SAMPLE_URL = "sample.log"

/** True under Node, false in a browser. */
private val isNode: Boolean
  get() = js("typeof process !== 'undefined' && process.versions != null").unsafeCast<Boolean>()
