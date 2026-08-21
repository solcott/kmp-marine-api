package io.github.solcott.marineapi.example

import io.github.solcott.marineapi.ais.AisPositionReport
import io.github.solcott.marineapi.ais.AisResult
import io.github.solcott.marineapi.ais.AisStaticAndVoyageData
import io.github.solcott.marineapi.ais.aisMessages
import io.github.solcott.marineapi.ais.describeShipType
import io.github.solcott.marineapi.ais.messages
import io.github.solcott.marineapi.nav.ais.ClosestApproach
import io.github.solcott.marineapi.nav.ais.OwnShip
import io.github.solcott.marineapi.nav.ais.TargetRegistry
import io.github.solcott.marineapi.nav.ais.Vessel
import io.github.solcott.marineapi.nav.ais.aisTargets
import io.github.solcott.marineapi.nav.ais.closestApproachTo
import io.github.solcott.marineapi.nmea.AngleReference
import io.github.solcott.marineapi.nmea.DataStatus
import io.github.solcott.marineapi.nmea.ParseResult
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
import kotlin.time.Duration
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.mapNotNull
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
 * Nothing here chooses a dispatcher either. The entry point does, for the same reason the library
 * itself declines to: Dispatchers.IO exists on JVM and Native and not on JS or Wasm.
 */

/** Prints the position from every GGA in the feed, and counts the lines that did not parse. */
suspend fun demoFile(open: () -> Source) {
  var failures = 0
  open()
    .nmeaResults()
    .onEach { if (it !is ParseResult.Ok) failures++ }
    .sentences()
    .filterIsInstance<Gga>()
    .collect { gga -> println("${gga.time}  ${gga.position}  ${gga.satelliteCount} satellites") }

  // A feed from real hardware always carries some corruption. `nmeaSentences()` drops those lines;
  // `nmeaResults()` reports them, which is why this can say how many there were.
  if (failures > 0) println("($failures lines did not parse)")
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
  var undecoded = 0
  var unreadable = 0

  open()
    .nmeaResults()
    // Two layers, two kinds of failure. A sentence whose checksum is wrong never reaches the AIS
    // decoder at all, so reading with nmeaSentences() alone can drop a message without saying so.
    .onEach { if (it !is ParseResult.Ok) unreadable++ }
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
  if (unreadable > 0) println("($unreadable lines never parsed as sentences)")
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

/**
 * The same AIS feed as [demoAis], read as traffic rather than as messages.
 *
 * The difference is the whole point of `:marine-api-nav`. [demoAis] keeps a `mutableMapOf` of MMSI
 * to name so that a position report can be labelled with a name that arrived minutes earlier --
 * every consumer of the parser ends up writing that map, because AIS transmits where a vessel is
 * every few seconds and who she is every six minutes. [aisTargets] is that map done properly:
 * merged by MMSI, expiring, and covering all seven of the report types rather than the two the
 * hand-rolled version bothered with.
 *
 * It then does the thing the join exists for. A [ClosestApproach] is where and when a target will
 * pass own ship if both hold their course and speed, and it is what a watchkeeper actually acts on
 * -- a target three miles off closing at twenty knots matters, and one a mile off opening does not.
 *
 * **The feed is read twice**, which is why every demo takes a `() -> Source` factory rather than a
 * `Source`. Own ship's position comes from the receiver's own `GGA` and `RMC` and the targets come
 * from the `!AIVDM` sentences interleaved with them, and one pass cannot have two endings. A live
 * program never has this problem -- it holds the last fix as it goes -- but a log has to be
 * rewound. A log carrying no fix of its own has nothing to measure a closest approach against, and
 * the plot is printed without one.
 */
suspend fun demoTraffic(open: () -> Source) {
  val ownShip = open().nmeaSentences().positions().mapNotNull { OwnShip.from(it) }.lastOrNull()

  val traffic = TargetRegistry()
  open().nmeaSentences().aisMessages().messages().aisTargets(traffic).collect()

  if (ownShip == null) {
    println("no fix of our own in this feed, so no closest approach")
  } else {
    println(
      "own ship ${ownShip.position}, ${ownShip.speedOverGround} kn" +
        " on ${ownShip.courseOverGround.toInt()} true"
    )
  }
  println("${traffic.size} targets")
  println()

  // Closest first, which is the order a watchkeeper wants them in. A target that has sent only her
  // name has no approach to compute and sorts to the end.
  traffic.vessels
    .map { it to ownShip?.let { own -> it.closestApproachTo(own) } }
    .sortedBy { (_, approach) -> approach?.distance ?: Double.MAX_VALUE }
    .forEach { (vessel, approach) -> println(describeTarget(vessel, approach)) }
}

/** One line of the traffic plot. */
private fun describeTarget(vessel: Vessel, approach: ClosestApproach?): String {
  val name = (vessel.name ?: "").fit(NAME_WIDTH)
  val flag = vessel.flagOrStationClass().fit(FLAG_WIDTH)
  val speed = vessel.speedOverGround?.let { " $it kn" } ?: " speed not reported"
  val motion = vessel.position?.let { "$it$speed" } ?: "no position reported"
  val cpa =
    approach?.let {
      val timing =
        if (it.isOpening) "opening" else "in ${it.timeToClosestApproach.toHoursAndMinutes()}"
      "  CPA ${it.distanceNauticalMiles.toHundredths()} nm $timing"
    } ?: ""
  return "${vessel.mmsi}  $name  $flag  $motion$cpa"
}

/**
 * The flag state, or what kind of station this is when the identity carries no MID.
 *
 * A target whose MMSI says it is a buoy or a man-overboard beacon is worth labelling as one: it
 * sends position reports that look exactly like a small vessel moving slowly.
 */
private fun Vessel.flagOrStationClass(): String =
  mmsi.flagState ?: mmsi.stationClass.name.lowercase().replace('_', ' ')

private fun String.fit(width: Int): String = padEnd(width).take(width)

/**
 * Hours and minutes, because two vessels barely moving relative to each other produce a closest
 * approach days away and "in 2278m" is not a number anyone reads.
 */
private fun Duration.toHoursAndMinutes(): String = toComponents { hours, minutes, _, _ ->
  if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/** Two decimal places, without pulling a formatting library into a demo. */
private fun Double.toHundredths(): String {
  val hundredths = (this * 100).toLong()
  return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}

private const val NAME_WIDTH = 20

private const val FLAG_WIDTH = 16
