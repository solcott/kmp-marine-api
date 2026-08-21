package io.github.solcott.marineapi.example

import io.github.solcott.marineapi.nav.fix.filterAccurate
import io.github.solcott.marineapi.nav.fix.fixStates
import io.github.solcott.marineapi.nav.fix.minFixQuality
import io.github.solcott.marineapi.nav.fix.rejectTeleports
import io.github.solcott.marineapi.nmea.GpsFixQuality
import io.github.solcott.marineapi.nmea.io.PositionFix
import io.github.solcott.marineapi.nmea.io.nmeaSentences
import io.github.solcott.marineapi.nmea.io.positions
import kotlinx.coroutines.flow.count
import kotlinx.io.Source

/**
 * How well the receiver thinks it knows where it is, and what to do with that.
 *
 * `positions()` fuses an update cycle into a `PositionFix`; everything that cycle said about its
 * own error ends up on `fix.accuracy`, gathered from four sentences no receiver sends all of --
 * `GST` for the error estimate in metres, `GSA` for the dilutions of precision, `GGA` for the
 * satellite count and the age of the differential corrections, `GBS` for the satellite RAIM
 * suspects. The parser reads all four and used to throw the lot away.
 *
 * Worth running against `marine-api/src/jvmTest/resources/data/gpsd/skytraq-fix.log`, a receiver
 * that flips between an autonomous and a differential fix almost every second and fills in its
 * `GST` in four cycles out of eleven. Reading it twice with different `confirmations` is the
 * clearest demonstration of what that parameter buys -- seven reported state changes become three,
 * off the same feed.
 *
 * The gap between the two `filterAccurate` lines is the point of `requireReported`. Most consumer
 * receivers send no `GST` at all, so a filter that quietly read "no estimate" as "small estimate"
 * would be lying on almost every device in existence, and one that dropped every unmeasured fix
 * would empty the flow on them. Neither is right for everyone, which is why it is a parameter
 * rather than a decision taken here.
 */
suspend fun demoAccuracy(open: () -> Source) {
  fun fixes() = open().nmeaSentences().positions()

  println("-- what each cycle knew about its own error --")
  fixes().collect { println("  ${describeAccuracy(it)}") }

  // The same feed read twice, because `confirmations` is the parameter nobody believes they need
  // until they see the difference. A receiver dropping to an autonomous fix for one second has not
  // changed state, it has flickered, and a status display that follows every flicker is unusable.
  println()
  println("-- fix state, reporting every change --")
  fixes().fixStates(confirmations = 1).collect { println("  $it") }

  println("-- fix state, ignoring anything that lasts one cycle --")
  fixes().fixStates().collect { println("  $it") }

  println()
  println("-- what the filters keep --")
  val total = fixes().count()
  report(total, total, "fixes in the feed")
  report(fixes().filterAccurate(1.0).count(), total, "accurate to 1 m, where the receiver said so")
  report(
    fixes().filterAccurate(1.0, requireReported = false).count(),
    total,
    "... plus the ones that never said",
  )
  report(
    fixes().minFixQuality(GpsFixQuality.DGPS).count(),
    total,
    "differentially corrected or better",
  )
  report(
    fixes().rejectTeleports(maxSpeedKnots = 60.0).count(),
    total,
    "reachable from the last one at under 60 kn",
  )
}

/** One cycle's worth of self-reported error, with the fields no device fills in marked as such. */
private fun describeAccuracy(fix: PositionFix): String {
  val accuracy = fix.accuracy
  // Pulled into locals so they smart-cast: a property of a class from another module cannot be,
  // because nothing stops that module from making it a `get()` that answers differently each time.
  val major = accuracy.semiMajorError
  val minor = accuracy.semiMinorError
  val ellipse =
    if (major == null || minor == null) ""
    else
      "  ellipse ${major.toHundredths()} x ${minor.toHundredths()} m" +
        (accuracy.errorEllipseOrientation?.let { " at ${it.toWholeDegrees()}" } ?: "")

  return listOf(
      "${fix.time}",
      (fix.fixQuality?.name ?: "no GGA").fit(QUALITY_WIDTH),
      "${accuracy.satelliteCount ?: "?"} sats",
      // DOP is a geometry factor, not a distance: it says how much a ranging error is multiplied
      // by the satellites' arrangement, so it never has a unit and cannot be compared with metres.
      (accuracy.horizontalDop?.let { "HDOP $it" } ?: "no DOP").fit(DOP_WIDTH),
      // DRMS, so roughly a 63% confidence radius -- half of a datasheet's 2DRMS figure.
      accuracy.horizontalError?.let { "${it.toHundredths()} m DRMS" } ?: "error not reported",
    )
    .joinToString("  ") + ellipse
}

/** One line of the filter tally, counts right-aligned so the column reads at a glance. */
private fun report(kept: Int, total: Int, what: String) =
  println("  ${kept.toString().padStart(COUNT_WIDTH)} of $total  $what")

private const val QUALITY_WIDTH = 9

private const val DOP_WIDTH = 10

private const val COUNT_WIDTH = 3
