package io.github.solcott.marineapi.nav.route

import io.github.solcott.marineapi.nmea.DataStatus
import io.github.solcott.marineapi.nmea.Direction
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.Units
import io.github.solcott.marineapi.nmea.sentence.Aam
import io.github.solcott.marineapi.nmea.sentence.Apb
import io.github.solcott.marineapi.nmea.sentence.Bwc
import io.github.solcott.marineapi.nmea.sentence.Bwr
import io.github.solcott.marineapi.nmea.sentence.Rmb
import io.github.solcott.marineapi.nmea.sentence.Wcv
import io.github.solcott.marineapi.nmea.sentence.Xte
import io.github.solcott.marineapi.nmea.sentence.Ztg

/** A nautical mile is 1852 metres exactly, so a kilometre is this many of them. */
private const val NAUTICAL_MILES_PER_KILOMETRE = 1000.0 / METRES_PER_NAUTICAL_MILE

/** This state with whatever the sentence says about the current leg folded into it. */
internal fun NavigationState.updatedBy(sentence: Sentence): NavigationState =
  when (sentence) {
    is Xte -> withXte(sentence)
    is Apb -> withApb(sentence)
    is Rmb -> withRmb(sentence)
    is Bwc -> withBwc(sentence)
    is Bwr -> withBwr(sentence)
    is Ztg -> copy(timeToGo = sentence.timeRemaining ?: timeToGo)
    is Wcv -> copy(closingVelocityKnots = sentence.velocityKnots ?: closingVelocityKnots)
    is Aam -> withAam(sentence)
    else -> this
  }

private fun NavigationState.withXte(xte: Xte): NavigationState {
  if (xte.status == DataStatus.VOID) return this
  return copy(crossTrack = crossTrackOf(xte.magnitude, xte.steerTo, xte.units) ?: crossTrack)
}

private fun NavigationState.withApb(apb: Apb): NavigationState {
  if (apb.status == DataStatus.VOID) return this
  return copy(
    crossTrack = crossTrackOf(apb.crossTrackError, apb.steerTo, apb.crossTrackUnits) ?: crossTrack,
    arrival = arrivalOf(apb.arrivalCircleEntered, apb.perpendicularPassed) ?: arrival,
  )
}

private fun NavigationState.withRmb(rmb: Rmb): NavigationState {
  if (rmb.status == DataStatus.VOID) return this
  return copy(
    destinationPosition = rmb.destinationPosition ?: destinationPosition,
    originWaypointId = rmb.originWaypointId ?: originWaypointId,
    // RMB reports its cross-track error in nautical miles and has no unit field to say otherwise.
    crossTrack = crossTrackOf(rmb.crossTrackError, rmb.steerTo, Units.NAUTICAL_MILES) ?: crossTrack,
    bearingTrue = rmb.bearing ?: bearingTrue,
    distanceNauticalMiles = rmb.range ?: distanceNauticalMiles,
    closingVelocityKnots = rmb.velocity ?: closingVelocityKnots,
    // RMB collapses both arrival tests into one flag, so it cannot say which of them fired.
    arrival =
      if (rmb.hasArrived) Arrival(circleEntered = true, perpendicularPassed = true) else arrival,
  )
}

private fun NavigationState.withBwc(bwc: Bwc): NavigationState =
  copy(
    destinationPosition = bwc.waypointPosition ?: destinationPosition,
    bearingTrue = bwc.bearingTrue ?: bearingTrue,
    bearingMagnetic = bwc.bearingMagnetic ?: bearingMagnetic,
    distanceNauticalMiles = bwc.distanceNauticalMiles ?: distanceNauticalMiles,
  )

private fun NavigationState.withBwr(bwr: Bwr): NavigationState =
  copy(
    destinationPosition = bwr.waypointPosition ?: destinationPosition,
    rhumbBearingTrue = bwr.bearingTrue ?: rhumbBearingTrue,
    rhumbBearingMagnetic = bwr.bearingMagnetic ?: rhumbBearingMagnetic,
    rhumbDistanceNauticalMiles = bwr.distanceNauticalMiles ?: rhumbDistanceNauticalMiles,
  )

private fun NavigationState.withAam(aam: Aam): NavigationState =
  copy(arrival = arrivalOf(aam.circleEntered, aam.perpendicularPassed) ?: arrival)

/**
 * The two arrival flags, or `null` if the sentence reported neither.
 *
 * A receiver with no active waypoint sends the arrival fields empty, and reading those as two
 * negatives would turn "there is nothing to arrive at" into "you have not arrived yet" -- which is
 * a leg in progress, and enough on its own to make an otherwise empty state look like one.
 */
private fun arrivalOf(circleEntered: DataStatus?, perpendicularPassed: DataStatus?): Arrival? {
  if (circleEntered == null && perpendicularPassed == null) return null
  return Arrival(
    circleEntered = circleEntered == DataStatus.ACTIVE,
    perpendicularPassed = perpendicularPassed == DataStatus.ACTIVE,
  )
}

/**
 * A cross-track error normalised to nautical miles, or `null` if the sentence did not give one.
 *
 * The side is required. The magnitude is unsigned in every sentence that carries it, so without `L`
 * or `R` a mile off track could be a mile either way, and the two are two miles apart.
 */
private fun crossTrackOf(magnitude: Double?, steerTo: Direction?, units: Units?): CrossTrack? {
  if (magnitude == null || steerTo == null) return null
  val nauticalMiles =
    when (units) {
      Units.NAUTICAL_MILES -> magnitude
      Units.KILOMETERS -> magnitude * NAUTICAL_MILES_PER_KILOMETRE
      // Without a unit the number could be either, and they differ by nearly a factor of two.
      else -> return null
    }
  return CrossTrack(nauticalMiles, steerTo)
}
