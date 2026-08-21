package io.github.solcott.marineapi.nav.fix

import io.github.solcott.marineapi.nmea.GpsFixQuality
import io.github.solcott.marineapi.nmea.io.PositionFix
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter

/**
 * Keeps only the fixes the receiver believes are good to within [horizontalErrorAtMost] metres.
 *
 * ```
 * source.nmeaSentences()
 *   .positions()
 *   .filterAccurate(horizontalErrorAtMost = 10.0)
 *   .collect { fix -> chart.plot(fix.position) }
 * ```
 *
 * The threshold is compared against `FixAccuracy.horizontalError`, which is DRMS -- roughly a 63%
 * confidence radius. A datasheet figure is usually 2DRMS, twice that, so a receiver sold as "2.5 m"
 * will not pass `filterAccurate(2.5)` most of the time. Halve the number you were given, or think
 * in DRMS from the start.
 *
 * **A fix whose accuracy the receiver did not report is dropped**, because [requireReported]
 * defaults to true and an unmeasured error is not a small one. Most consumer receivers send no
 * `GST` at all, so on those this filter yields nothing -- which is the honest answer to "give me
 * only fixes known to be accurate", and is why the parameter exists to say otherwise. Pass
 * `requireReported = false` where a plot with gaps is worse than a plot with some uncertain points
 * on it.
 *
 * @param horizontalErrorAtMost metres, compared against the DRMS horizontal error
 * @param requireReported whether to drop a fix carrying no error estimate at all
 */
public fun Flow<PositionFix>.filterAccurate(
  horizontalErrorAtMost: Double,
  requireReported: Boolean = true,
): Flow<PositionFix> = filter { fix ->
  val error = fix.accuracy.horizontalError ?: return@filter !requireReported
  error <= horizontalErrorAtMost
}

/**
 * Keeps only the fixes obtained at least as well as [quality].
 *
 * ```
 * source.nmeaSentences().positions().minFixQuality(GpsFixQuality.DGPS)
 * ```
 *
 * **This is not [GpsFixQuality]'s declaration order**, and using that order would be a bug worth
 * spelling out: the enum runs `INVALID, NORMAL, DGPS, PPS, RTK, FRTK, ESTIMATED, MANUAL, SIMULATED`
 * because those are the numbers `GGA` puts on the wire, and the last three are *not* better than
 * the ones before them. `ESTIMATED` is dead reckoning -- the receiver has no fix and is
 * extrapolating -- while `MANUAL` and `SIMULATED` are not measurements at all. Comparing ordinals
 * would let a simulator satisfy a demand for a differential fix.
 *
 * The ranking used instead, worst to best, is: dead reckoning, autonomous, differential, precise
 * positioning service, RTK float, RTK fixed. `INVALID`, `MANUAL` and `SIMULATED` are outside it and
 * never satisfy any threshold, nor is a cycle without a `GGA`, which is the only sentence reporting
 * this at all.
 *
 * Quality is a different question from accuracy: it says how the fix was *obtained*, where
 * [filterAccurate] says how far out it is likely to be. A receiver can report either without the
 * other.
 */
public fun Flow<PositionFix>.minFixQuality(quality: GpsFixQuality): Flow<PositionFix> = filter {
  val reported = it.fixQuality?.accuracyRank ?: return@filter false
  val threshold = quality.accuracyRank ?: return@filter false
  reported >= threshold
}

/**
 * Where this quality sits on a scale of how well the position was actually measured, or `null` for
 * the values that are not a measurement of anything.
 *
 * Deliberately not the enum's ordinal; see [minFixQuality] for why.
 */
internal val GpsFixQuality.accuracyRank: Int?
  get() =
    when (this) {
      // Not a fix, or not a measured one.
      GpsFixQuality.INVALID,
      GpsFixQuality.MANUAL,
      GpsFixQuality.SIMULATED -> null
      // A fix, but extrapolated rather than observed: strictly worse than an autonomous one.
      GpsFixQuality.ESTIMATED -> 0
      GpsFixQuality.NORMAL -> 1
      GpsFixQuality.DGPS -> 2
      GpsFixQuality.PPS -> 3
      // Float before fixed: resolving the carrier ambiguities is what takes RTK from decimetres
      // to centimetres, so FRTK is the weaker of the pair despite the higher wire code.
      GpsFixQuality.FRTK -> 4
      GpsFixQuality.RTK -> 5
    }
