package io.github.solcott.marineapi.example

import io.github.solcott.marineapi.nmea.AngleReference
import io.github.solcott.marineapi.nmea.DataStatus
import io.github.solcott.marineapi.nmea.Position
import io.github.solcott.marineapi.nmea.SentenceRegistry
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.Units
import io.github.solcott.marineapi.nmea.sentence.Gga
import io.github.solcott.marineapi.nmea.sentence.Mwv
import io.github.solcott.marineapi.nmea.sentenceOrNull
import kotlinx.datetime.LocalTime

/**
 * Builds sentences and encodes them.
 *
 * ```
 * ./gradlew :examples:runOutputExample
 * ```
 *
 * Sentences are immutable values, so writing one is construction rather than a sequence of setters.
 * That closes off the failure the setter API invited: a half-configured sentence that encodes
 * without complaint and means something other than you intended. Here the compiler will not let you
 * give a wind angle without saying what it is relative to.
 *
 * `toNmeaString()` computes the checksum, so it always matches the body.
 */
fun main() {
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

  // `copy` is how you change one: the original is untouched, so a value handed to a listener
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
  val reparsed = SentenceRegistry.Default.parse(fix.toNmeaString()).sentenceOrNull()
  println("round trips: ${reparsed == fix}")
}
