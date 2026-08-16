package io.github.solcott.marineapi.example

import io.github.solcott.marineapi.nmea.io.nmeaSentences
import io.github.solcott.marineapi.ublox.UbloxPositionVelocityTime
import io.github.solcott.marineapi.ublox.UbloxSatelliteStatus
import io.github.solcott.marineapi.ublox.UbloxSatelliteStatusReport
import io.github.solcott.marineapi.ublox.ubloxMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString

/**
 * Decodes the `$PUBX` sentences a u-blox receiver adds to its NMEA output.
 *
 * ```
 * ./gradlew :examples:runUbloxExample                       # the built-in feed below
 * ./gradlew :examples:runUbloxExample --args="ublox.log"    # your own capture
 * ```
 *
 * Worth reading when standard NMEA is already on the wire because it carries what the standard
 * sentences have no field for: an accuracy estimate in **metres** rather than a dilution-of-
 * precision factor, and per-satellite carrier lock times.
 *
 * Runs with no argument because none of the sample logs in this project contain `$PUBX` -- these
 * two sentences are the ones the library's own tests are built on.
 */
private val SAMPLE =
  listOf(
    "\$PUBX,00,202920.00,1932.33821,N,15555.72641,W,451.876,G3,3.3,4.0,0.177,0.00,-0.035,,1.11,1.39,1.15,17,0,0*62",
    "\$PUBX,03,4,5,U,063,15,18,000,12,U,100,36,40,064,14,-,257,05,,000,18,-,219,67,20,000*14",
  )

fun main(args: Array<String>) {
  val source: Source =
    if (args.isEmpty())
      Buffer().also { it.writeString(SAMPLE.joinToString("\r\n", postfix = "\r\n")) }
    else SystemFileSystem.source(Path(args[0])).buffered()

  runBlocking {
    source.use {
      it.nmeaSentences().ubloxMessages().flowOn(Dispatchers.IO).collect { message ->
        when (message) {
          is UbloxPositionVelocityTime ->
            println(
              "${message.utcTime}  ${message.position}  ${message.navigationStatus}\n" +
                "  accurate to ${message.horizontalAccuracy} m horizontally," +
                " ${message.verticalAccuracy} m vertically, from ${message.satellitesUsed} satellites"
            )
          is UbloxSatelliteStatusReport -> {
            val used =
              message.satellites.count { it.status == UbloxSatelliteStatus.USED_IN_SOLUTION }
            println("${message.satellites.size} satellites tracked, $used used in the fix")
            for (satellite in message.satellites) {
              println(
                "  ${satellite.id}\tel ${satellite.elevation}\taz ${satellite.azimuth}" +
                  "\tsignal ${satellite.signalStrength ?: "-"}\t${satellite.status}"
              )
            }
          }
          else -> println("u-blox message ${message.messageId}")
        }
      }
    }
  }
}
