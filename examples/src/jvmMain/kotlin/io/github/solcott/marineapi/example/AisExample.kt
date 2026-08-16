package io.github.solcott.marineapi.example

import io.github.solcott.marineapi.ais.AisPositionReport
import io.github.solcott.marineapi.ais.AisResult
import io.github.solcott.marineapi.ais.AisStaticAndVoyageData
import io.github.solcott.marineapi.ais.aisMessages
import io.github.solcott.marineapi.ais.describeShipType
import io.github.solcott.marineapi.nmea.ParseResult
import io.github.solcott.marineapi.nmea.io.nmeaResults
import io.github.solcott.marineapi.nmea.io.sentences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * Decodes the AIS traffic in a log: who is out there, and where.
 *
 * ```
 * ./gradlew :examples:runAisExample --args="marine-api/src/jvmTest/resources/data/AIS-VDM-VDO.txt"
 * ```
 *
 * AIS borrows NMEA's sentence format to carry its own bit-packed messages, and a message longer
 * than one sentence is split across several. [aisMessages] reassembles those before decoding, which
 * is why it is a flow operator rather than a function on a sentence: a type 5 static report is 424
 * bits and always arrives in two parts.
 *
 * The vessel name and the vessel's position come in different message types, so tracking a target
 * properly means keeping both against its MMSI, as this does.
 */
fun main(args: Array<String>) {
  if (args.size != 1) {
    println("Usage: AisExample <ais.log>")
    return
  }

  val names = mutableMapOf<Int, String>()
  var undecoded = 0
  var unreadable = 0

  runBlocking {
    SystemFileSystem.source(Path(args[0])).buffered().use { source ->
      source
        .nmeaResults()
        // Two layers, two kinds of failure. A sentence whose checksum is wrong never reaches the
        // AIS decoder at all -- and the first line of this project's own AIS fixture is one, so
        // reading with nmeaSentences() alone would drop a message here without saying so.
        .onEach { if (it !is ParseResult.Ok) unreadable++ }
        .sentences()
        .aisMessages()
        .flowOn(Dispatchers.IO)
        .collect { result ->
          when (result) {
            is AisResult.Ok ->
              when (val message = result.message) {
                is AisStaticAndVoyageData -> {
                  names[message.mmsi] = message.name
                  println(
                    "${message.mmsi}  ${message.name} (${describeShipType(message.shipType)})" +
                      " -> ${message.destination}"
                  )
                }
                is AisPositionReport ->
                  println(
                    "${message.mmsi}  ${names[message.mmsi] ?: "unknown"}" +
                      "  ${message.position}  ${message.speedOverGround ?: 0.0} kn"
                  )
                else -> println("${message.mmsi}  type ${message.messageType}")
              }
            // Not every AIS message type is decoded, and a type this library skips is still
            // reported rather than dropped -- the payload is there if you want to decode it.
            is AisResult.Unsupported -> undecoded++
            is AisResult.Malformed -> println("malformed: ${result.reason}")
          }
        }
    }
  }

  if (undecoded > 0) println("($undecoded messages of types this library does not decode)")
  if (unreadable > 0) println("($unreadable lines never parsed as sentences)")
}
