package io.github.solcott.marineapi.example

import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.io.headings
import io.github.solcott.marineapi.nmea.io.nmeaSentences
import io.github.solcott.marineapi.nmea.io.positions
import io.github.solcott.marineapi.nmea.io.satellites
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * Correlates a log into fixes, headings and satellite views.
 *
 * ```
 * ./gradlew :examples:runPositionsExample --args="marine-api/src/jvmTest/resources/data/Navibe-GM720.txt"
 * ```
 *
 * No single NMEA sentence carries a whole fix: GGA has the position and altitude but no date or
 * speed, RMC has the date and speed but no altitude, VTG has only the velocity. A receiver sends
 * them as a burst, and [positions] gathers one burst into one
 * [io.github.solcott.marineapi.nmea.io.PositionFix].
 *
 * The three operators are independent, so a flow can be split as many ways as you like -- but each
 * one reads the source when collected, so a file has to be opened once per operator. A live feed
 * would be shared with `shareIn` instead.
 */
fun main(args: Array<String>) {
  if (args.size != 1) {
    println("Usage: PositionsExample <nmea.log>")
    return
  }
  val path = Path(args[0])

  runBlocking {
    println("-- fixes --")
    read(path).positions().flowOn(Dispatchers.IO).collect { fix ->
      println("${fix.dateTime ?: fix.time}  ${fix.position}  ${fix.speedKnots ?: 0.0} kn")
    }

    println("-- headings --")
    read(path).headings().flowOn(Dispatchers.IO).collect { println("$it") }

    println("-- satellites --")
    read(path).satellites().flowOn(Dispatchers.IO).collect { view ->
      println(
        "${view.talker}: ${view.satellites.size} of ${view.satellitesInView} in view, " +
          "${view.satellitesUsed.size} used, HDOP ${view.horizontalDop}"
      )
    }
  }
}

/**
 * A fresh flow over the file.
 *
 * The flow is cold: collecting it reads the source. Handing the same one to three operators would
 * give the first everything and the other two an exhausted file.
 */
private fun read(path: Path): Flow<Sentence> =
  SystemFileSystem.source(path).buffered().nmeaSentences()
