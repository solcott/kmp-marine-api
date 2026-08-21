package io.github.solcott.marineapi.nav.fix

import io.github.solcott.marineapi.nmea.GpsFixQuality
import io.github.solcott.marineapi.nmea.io.PositionFix
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * What the receiver is managing, as a state rather than as a stream of readings.
 *
 * A UI wants to know that the fix went from differential to autonomous, once, not to be told the
 * quality of every cycle forever. See [fixStates].
 */
public enum class FixState {
  /** No usable fix. Either nothing is being reported, or what is reported is not a measurement. */
  ACQUIRING,
  /** A fix, but extrapolated rather than observed -- `GGA` reporting dead reckoning. */
  DEAD_RECKONING,
  /** An ordinary autonomous fix. */
  AUTONOMOUS,
  /** Differentially corrected, including PPS. */
  DIFFERENTIAL,
  /** Real-time kinematic, float or fixed. */
  RTK,
}

/**
 * The state changes of a fix stream, one emission per change rather than one per cycle.
 *
 * ```
 * source.nmeaSentences()
 *   .positions()
 *   .fixStates()
 *   .collect { state -> statusBar.show(state) }
 * ```
 *
 * The first cycle always produces an emission, because going from "nothing known" to any state is
 * itself a change. After that only transitions are reported.
 *
 * **[confirmations] exists because receivers flicker.** A single cycle reporting a worse quality is
 * routine -- a satellite drops below the mask angle, a correction arrives late -- and a status
 * display that follows every one of those is noise. A new state must be seen this many cycles in a
 * row before it is reported, so the default of 2 ignores one-cycle blips while still reacting
 * inside a couple of seconds on a 1 Hz receiver. Set it to 1 to report every change as it happens.
 *
 * A cycle carrying no `GGA` has no quality to read and counts toward [FixState.ACQUIRING], since a
 * receiver that has stopped saying how it is fixing has stopped telling you it is fixed. This
 * operator therefore says nothing about whether a *position* is present -- filter for that upstream
 * if it matters.
 *
 * @param confirmations consecutive cycles a new state must persist for before it is reported
 */
public fun Flow<PositionFix>.fixStates(confirmations: Int = 2): Flow<FixState> {
  require(confirmations >= 1) { "confirmations must be at least 1, got $confirmations" }
  return flow {
    var reported: FixState? = null
    var candidate: FixState? = null
    var seen = 0

    collect { fix ->
      val state = fix.fixQuality.toFixState()
      if (state == candidate) {
        seen++
      } else {
        candidate = state
        seen = 1
      }

      if (state != reported && seen >= confirmations) {
        reported = state
        emit(state)
      }
    }
  }
}

private fun GpsFixQuality?.toFixState(): FixState =
  when (this) {
    null,
    GpsFixQuality.INVALID,
    // Neither is a measurement of where the receiver is, so neither counts as being fixed.
    GpsFixQuality.MANUAL,
    GpsFixQuality.SIMULATED -> FixState.ACQUIRING
    GpsFixQuality.ESTIMATED -> FixState.DEAD_RECKONING
    GpsFixQuality.NORMAL -> FixState.AUTONOMOUS
    GpsFixQuality.DGPS,
    GpsFixQuality.PPS -> FixState.DIFFERENTIAL
    GpsFixQuality.RTK,
    GpsFixQuality.FRTK -> FixState.RTK
  }
