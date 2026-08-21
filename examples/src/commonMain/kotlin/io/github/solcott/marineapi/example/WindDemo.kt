package io.github.solcott.marineapi.example

import io.github.solcott.marineapi.nav.instrument.Wind
import io.github.solcott.marineapi.nav.instrument.groundWind
import io.github.solcott.marineapi.nav.instrument.trueWind
import io.github.solcott.marineapi.nav.instrument.winds
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.io.nmeaSentences
import io.github.solcott.marineapi.nmea.sentence.Mwd
import io.github.solcott.marineapi.nmea.sentence.Mwv
import io.github.solcott.marineapi.nmea.sentence.Vhw
import io.github.solcott.marineapi.nmea.sentence.Vwr
import io.github.solcott.marineapi.nmea.sentence.Vwt
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.io.Source

/**
 * Three winds that are not three ways of saying one wind.
 *
 * Try it on `marine-api/src/jvmTest/resources/data/sample1.txt`, a masthead unit sending `MWV`,
 * `VWR`, `VWT` and `MWD` with a `VHW` for boat speed.
 *
 * **Apparent** is what the masthead feels: the true wind plus the wind the vessel makes by moving.
 * It sets the sails and it does not tell you where the wind is. **True over water** is apparent
 * with the vessel's motion *through the water* taken out, which is the sailor's true wind and what
 * decides which tack is favoured. **Ground wind** is apparent with the motion *over the ground*
 * taken out, which is what a shore station reports and what a forecast is written in. The last two
 * differ by exactly the current -- in a tidal gate by several knots, which in light airs is most of
 * the wind -- and conflating them is the bug this pair of operators exists to prevent.
 *
 * Two of the three sections come out short on that log, both for the same reason. The vessel has no
 * gyro: her `VHW` and `MWD` carry a magnetic heading and a magnetic wind direction and leave the
 * true fields empty. Turning magnetic into true needs the local variation, which no sentence here
 * carries and which this library will not invent, so the compass directions stay absent and
 * `groundWind` -- which cannot work without a true heading -- yields nothing at all.
 */
suspend fun demoWind(open: () -> Source) {
  println("-- as the instruments report it --")
  val reported = open().nmeaSentences().winds().toList()
  show(reported, "no wind sentence in this feed carried a reading this could use")

  // The gap between these two is the readings that were sent and could not be used, which is
  // almost always a missing unit, a missing side, or a direction given only in magnetic.
  val sent = open().nmeaSentences().count { it.reportsWind() }
  println("  (${reported.size} readings from $sent wind sentences)")

  println()
  println("-- true wind over the water, worked out from the apparent wind --")
  show(
    open().nmeaSentences().trueWind().toList(),
    "nothing: subtracting the vessel's motion needs a speed through the water, from VHW",
  )
  // Printed because otherwise the section above looks like an operator that did nothing. On a
  // vessel lying still the correction really is a subtraction of zero, and the true wind really
  // does equal the apparent wind -- which is the one case where the two are the same reading.
  val throughWater =
    open().nmeaSentences().filterIsInstance<Vhw>().mapNotNull { it.speedKnots }.lastOrNull()
  if (throughWater != null) {
    println("  (corrected against ${throughWater.toHundredths()} kn through the water)")
  }

  println()
  println("-- ground wind, the one to compare a forecast against --")
  show(
    open().nmeaSentences().groundWind().toList(),
    "nothing: this needs a true heading, and this vessel carries only a magnetic compass",
  )
}

/** Prints the readings, or says why there were none -- which is a result rather than a failure. */
private fun show(readings: List<Wind>, ifEmpty: String) {
  if (readings.isEmpty()) println("  $ifEmpty")
  else readings.forEach { println("  ${describeWind(it)}") }
}

private fun describeWind(wind: Wind): String =
  listOf(
      "${wind.speedKnots.toHundredths()} kn".fit(SPEED_WIDTH),
      // Named for where the wind comes FROM, so 0 is on the nose rather than astern.
      (wind.angleFromBow?.let { "${it.toWholeDegrees()} off the bow" } ?: "no bow angle").fit(
        ANGLE_WIDTH
      ),
      (wind.directionTrue?.let { "${it.toWholeDegrees()} true" } ?: "no true direction").fit(
        DIRECTION_WIDTH
      ),
      wind.reference.name,
    )
    .joinToString("  ")

/** The four sentences [winds] reads, as a `when` rather than a chain of `||` detekt would flag. */
private fun Sentence.reportsWind(): Boolean =
  when (this) {
    is Mwd,
    is Mwv,
    is Vwr,
    is Vwt -> true
    else -> false
  }

private const val SPEED_WIDTH = 9

private const val ANGLE_WIDTH = 18

private const val DIRECTION_WIDTH = 18
