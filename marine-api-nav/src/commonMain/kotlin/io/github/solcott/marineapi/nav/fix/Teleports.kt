package io.github.solcott.marineapi.nav.fix

import io.github.solcott.marineapi.nmea.io.PositionFix
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalTime

/** Metres per nautical mile, so a speed in knots can be compared with a distance in metres. */
private const val METRES_PER_NAUTICAL_MILE = 1852.0

/**
 * Drops a fix that could only be reached from the one before it by travelling implausibly fast.
 *
 * ```
 * source.nmeaSentences()
 *   .positions()
 *   .rejectTeleports(maxSpeedKnots = 60.0)
 *   .collect { fix -> track.append(fix.position) }
 * ```
 *
 * A jump like that is a bad fix rather than a fast vessel: a multipath reflection in a harbour, a
 * receiver briefly solving with a satellite it should have rejected, or two receivers' sentences
 * interleaved on one bus. Screening on implied speed catches all three without needing to know
 * which happened.
 *
 * **The comparison is against the last fix that was kept, not the last one seen.** Otherwise a
 * single bad fix would drag the reference point with it and reject the good fixes that follow,
 * which is the failure mode that makes naive versions of this filter worse than none.
 *
 * A fix is kept, not rejected, whenever the test cannot be applied: the first fix of a stream has
 * nothing to compare against, and a receiver that reports no time gives no interval to divide by.
 * Rejecting on an unanswerable question would silently empty the flow on any feed without
 * timestamps.
 *
 * The interval comes from [PositionFix.time], and a decrease across it is read as midnight rather
 * than as time running backwards. Two fixes carrying the same timestamp are kept: a receiver
 * reporting whole seconds legitimately emits several fixes within one, and no distance is
 * implausible in zero time.
 *
 * @param maxSpeedKnots the fastest the vessel could plausibly be going. Choose it for the vessel,
 *   with headroom -- a value near the real top speed will reject good fixes whenever the receiver
 *   is a little noisy.
 * @param maxGap how long a break may be before the test is abandoned and the fix kept regardless.
 *   After a long dropout the vessel really could be anywhere, so the implied speed says nothing.
 */
public fun Flow<PositionFix>.rejectTeleports(
  maxSpeedKnots: Double,
  maxGap: Duration = 1.hours,
): Flow<PositionFix> {
  require(maxSpeedKnots > 0.0) { "maxSpeedKnots must be positive, got $maxSpeedKnots" }
  return flow {
    var kept: PositionFix? = null

    collect { fix ->
      if (isPlausible(kept, fix, maxSpeedKnots, maxGap)) {
        kept = fix
        emit(fix)
      }
    }
  }
}

private fun isPlausible(
  previous: PositionFix?,
  fix: PositionFix,
  maxSpeedKnots: Double,
  maxGap: Duration,
): Boolean {
  val last = previous ?: return true
  val elapsed = last.time?.let { from -> fix.time?.let { to -> from.elapsedTo(to) } } ?: return true
  if (elapsed > maxGap) return true
  if (elapsed == Duration.ZERO) return true

  val metres = last.position.distanceTo(fix.position)
  val limit = maxSpeedKnots * METRES_PER_NAUTICAL_MILE * elapsed.inWholeMilliseconds / 3_600_000.0
  return metres <= limit
}

/**
 * Time from this instant to [other], treating a decrease as having crossed midnight.
 *
 * A `LocalTime` has no date attached, so 23:59:58 followed by 00:00:02 is either four seconds or
 * very nearly minus a day. On a stream of fixes in arrival order the first reading is the only one
 * that makes sense.
 */
private fun LocalTime.elapsedTo(other: LocalTime): Duration {
  val delta = (other.toNanosecondOfDay() - toNanosecondOfDay()).nanoseconds
  return if (delta < Duration.ZERO) delta + 24.hours else delta
}
