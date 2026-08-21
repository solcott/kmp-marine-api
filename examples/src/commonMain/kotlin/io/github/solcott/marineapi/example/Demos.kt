package io.github.solcott.marineapi.example

import io.github.solcott.marineapi.ais.AisPositionReport
import io.github.solcott.marineapi.ais.AisResult
import io.github.solcott.marineapi.ais.AisStaticAndVoyageData
import io.github.solcott.marineapi.ais.aisMessages
import io.github.solcott.marineapi.ais.describeShipType
import io.github.solcott.marineapi.nmea.AngleReference
import io.github.solcott.marineapi.nmea.DataStatus
import io.github.solcott.marineapi.nmea.Position
import io.github.solcott.marineapi.nmea.SentenceRegistry
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.Units
import io.github.solcott.marineapi.nmea.io.headings
import io.github.solcott.marineapi.nmea.io.nmeaResults
import io.github.solcott.marineapi.nmea.io.nmeaSentences
import io.github.solcott.marineapi.nmea.io.positions
import io.github.solcott.marineapi.nmea.io.satellites
import io.github.solcott.marineapi.nmea.io.sentences
import io.github.solcott.marineapi.nmea.sentence.Gga
import io.github.solcott.marineapi.nmea.sentence.Mwv
import io.github.solcott.marineapi.nmea.sentenceOrNull
import io.github.solcott.marineapi.ublox.UbloxPositionVelocityTime
import io.github.solcott.marineapi.ublox.UbloxSatelliteStatus
import io.github.solcott.marineapi.ublox.UbloxSatelliteStatusReport
import io.github.solcott.marineapi.ublox.ubloxMessages
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.LocalTime
import kotlinx.io.Source

/*
 * The example bodies, written once for every platform.
 *
 * None of this is platform-specific, because none of it does any IO: each demo takes a factory that
 * hands it a Source and does not care where the source came from. That is the whole multiplatform
 * story of this library -- opening a file, fetching bytes over HTTP and reading a serial port
 * differ completely, and everything after them is identical.
 *
 * A FACTORY rather than a Source, because these flows are cold: collecting one reads its source, so
 * a demo that collects twice has to open twice. demoPositions is the one that does.
 *
 * These are the demos of :marine-api, the parser. The demos of :marine-api-nav -- the operators
 * built on top of it -- are one to a file alongside this one, because detekt caps a file at
 * eleven top-level functions and this one was already at it.
 *
 * Nothing here chooses a dispatcher either. The entry point does, for the same reason the library
 * itself declines to: Dispatchers.IO exists on JVM and Native and not on JS or Wasm.
 */

/** Prints the position from every GGA in the feed, and says what else was on the wire. */
suspend fun demoFile(open: () -> Source) {
  val noise = FeedNoise()
  open().nmeaResults().onEach(noise::record).sentences().filterIsInstance<Gga>().collect { gga ->
    println("${gga.time}  ${gga.position}  ${gga.satelliteCount} satellites")
  }

  // A feed from real hardware always carries some corruption, and usually a few lines that were
  // never sentences. `nmeaSentences()` drops both silently; `nmeaResults()` reports them, which is
  // why this can tell them apart -- see FeedNoise for why conflating the two is a bug.
  noise.report()
}

/**
 * Correlates the feed into fixes, headings and satellite views.
 *
 * No single NMEA sentence carries a whole fix: GGA has the position and altitude but no date or
 * speed, RMC has the date and speed but no altitude, VTG has only the velocity. A receiver sends
 * them as a burst, and `positions()` gathers one burst into one value.
 *
 * The three operators are independent, so the feed can be split as many ways as you like -- but
 * each one reads its source when collected, which is why [open] is called three times. A live feed
 * would be shared with `shareIn` instead of reopened.
 */
suspend fun demoPositions(open: () -> Source) {
  println("-- fixes --")
  open().nmeaSentences().positions().collect { fix ->
    println("${fix.dateTime ?: fix.time}  ${fix.position}  ${fix.speedKnots ?: 0.0} kn")
  }

  println("-- headings --")
  open().nmeaSentences().headings().collect { println("$it") }

  println("-- satellites --")
  open().nmeaSentences().satellites().collect { view ->
    println(
      "${view.talker}: ${view.satellites.size} of ${view.satellitesInView} in view, " +
        "${view.satellitesUsed.size} used, HDOP ${view.horizontalDop}"
    )
  }
}

/**
 * Decodes the AIS traffic in the feed: who is out there, and where.
 *
 * AIS borrows NMEA's sentence format to carry its own bit-packed messages, and a message longer
 * than one sentence is split across several. `aisMessages()` reassembles those before decoding,
 * which is why it is a flow operator rather than a function on a sentence: a type 5 static report
 * is 424 bits and always arrives in two parts.
 *
 * The vessel name and the vessel's position come in different message types, so tracking a target
 * properly means keeping both against its MMSI, as this does.
 */
suspend fun demoAis(open: () -> Source) {
  val names = mutableMapOf<Int, String>()
  val noise = FeedNoise()
  var undecoded = 0

  open()
    .nmeaResults()
    // Two layers, and three kinds of thing that is not a decoded message. A sentence whose checksum
    // is wrong never reaches the AIS decoder at all, so reading with nmeaSentences() alone can drop
    // a message without saying so.
    .onEach(noise::record)
    .sentences()
    .aisMessages()
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
        // Not every AIS message type is decoded, and a type this library skips is still reported
        // rather than dropped -- the payload is there if you want to decode it yourself.
        is AisResult.Unsupported -> undecoded++
        is AisResult.Malformed -> println("malformed: ${result.reason}")
      }
    }

  if (undecoded > 0) println("($undecoded messages of types this library does not decode)")
  noise.report()
}

/**
 * Decodes the `$PUBX` sentences a u-blox receiver adds to its NMEA output.
 *
 * Worth reading when standard NMEA is already on the wire because it carries what the standard
 * sentences have no field for: an accuracy estimate in **metres** rather than a
 * dilution-of-precision factor, and per-satellite carrier lock times.
 */
suspend fun demoUblox(open: () -> Source) {
  open().nmeaSentences().ubloxMessages().collect { message ->
    when (message) {
      is UbloxPositionVelocityTime ->
        println(
          "${message.utcTime}  ${message.position}  ${message.navigationStatus}\n" +
            "  accurate to ${message.horizontalAccuracy} m horizontally," +
            " ${message.verticalAccuracy} m vertically, from ${message.satellitesUsed} satellites"
        )
      is UbloxSatelliteStatusReport -> {
        val used = message.satellites.count { it.status == UbloxSatelliteStatus.USED_IN_SOLUTION }
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

/**
 * Builds sentences and encodes them. The only demo that needs no source at all.
 *
 * Sentences are immutable values, so writing one is construction rather than a sequence of setters.
 * That closes off the failure the setter API invited: a half-configured sentence that encodes
 * without complaint and means something other than you intended. Here the compiler will not let you
 * give a wind angle without saying what it is relative to.
 *
 * `toNmeaString()` computes the checksum, so it always matches the body.
 */
fun demoOutput() {
  // An empty sentence still encodes. Every field is optional, so this is what "no data" looks like.
  println(Mwv(talker = TalkerId.II).toNmeaString())

  val wind =
    Mwv(
      talker = TalkerId.II,
      windAngle = 43.7,
      reference = AngleReference.TRUE,
      windSpeed = 4.5,
      speedUnits = Units.METER,
      status = DataStatus.ACTIVE,
    )
  println(wind.toNmeaString())

  // `copy` is how you change one: the original is untouched, so a value handed to a collector
  // cannot be mutated behind its back.
  println(wind.copy(windAngle = 128.9, windSpeed = 12.1).toNmeaString())

  val fix =
    Gga(
      talker = TalkerId.GP,
      time = LocalTime(12, 0, 44),
      position = Position(latitude = 60.192533, longitude = 25.032350),
      satelliteCount = 8,
      altitude = 28.0,
      altitudeUnits = Units.METER,
    )
  println(fix.toNmeaString())

  // Round trip: parsing what was just written gives back an equal value.
  println(
    "round trips: ${SentenceRegistry.Default.parse(fix.toNmeaString()).sentenceOrNull() == fix}"
  )
}
