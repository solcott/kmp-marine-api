package io.github.solcott.marineapi.example

import io.github.solcott.marineapi.nmea.ParseResult
import io.github.solcott.marineapi.nmea.io.nmeaResults
import io.github.solcott.marineapi.nmea.io.sentences
import io.github.solcott.marineapi.nmea.sentence.Gga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * Reads a log file and prints the position from every GGA sentence in it.
 *
 * ```
 * ./gradlew :examples:runFileExample --args="marine-api/src/jvmTest/resources/data/Navibe-GM720.txt"
 * ```
 *
 * The three ideas here are the whole of the reading API:
 * - a `Source` from anywhere becomes a flow of sentences with [nmeaResults];
 * - `filterIsInstance` picks the type you want, with no listener interface and no reflection;
 * - lines that failed to parse are values you can act on, not exceptions to catch.
 *
 * A feed from real hardware always carries some corruption, so this counts the failures rather than
 * dropping them silently. Use `nmeaSentences()` instead of `nmeaResults().sentences()` when you do
 * not care.
 */
fun main(args: Array<String>) {
  if (args.size != 1) {
    println("Usage: FileExample <nmea.log>")
    return
  }

  var failures = 0

  runBlocking {
    SystemFileSystem.source(Path(args[0])).buffered().use { source ->
      source
        .nmeaResults()
        .onEach { if (it !is ParseResult.Ok) failures++ }
        .sentences()
        .filterIsInstance<Gga>()
        // Reading a file blocks, and this library will not pick a dispatcher for you --
        // Dispatchers.IO does not exist on JS or Wasm. On a platform that has it, say so here.
        .flowOn(Dispatchers.IO)
        .collect { gga ->
          println("${gga.time}  ${gga.position}  ${gga.satelliteCount} satellites")
        }
    }
  }

  if (failures > 0) println("($failures lines did not parse)")
}
