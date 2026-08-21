package io.github.solcott.marineapi.nav.route

import io.github.solcott.marineapi.nmea.Direction
import io.github.solcott.marineapi.nmea.Position
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.sentence.Aam
import io.github.solcott.marineapi.nmea.sentence.Apb
import io.github.solcott.marineapi.nmea.sentence.Bwc
import io.github.solcott.marineapi.nmea.sentence.Bwr
import io.github.solcott.marineapi.nmea.sentence.Rmb
import io.github.solcott.marineapi.nmea.sentence.Wcv
import io.github.solcott.marineapi.nmea.sentence.Xte
import io.github.solcott.marineapi.nmea.sentence.Ztg
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * How far off the intended track the vessel is, and which way to steer to get back on it.
 *
 * @property nauticalMiles distance off track, always positive -- the side is [steerTo]
 * @property steerTo the direction to alter course towards, which is the *opposite* of the side the
 *   vessel has strayed to. A vessel that has drifted to port is told to steer right.
 */
public data class CrossTrack(val nauticalMiles: Double, val steerTo: Direction)

/**
 * Whether the vessel has arrived at the active waypoint, by each of the two separate tests.
 *
 * @property circleEntered the vessel came within the waypoint's arrival circle
 * @property perpendicularPassed the vessel crossed the line drawn through the waypoint at right
 *   angles to the leg
 */
public data class Arrival(val circleEntered: Boolean, val perpendicularPassed: Boolean) {

  /**
   * True once either test has fired, which is what advances a route to its next leg.
   *
   * Both are needed, and either alone is a route that stalls. A vessel set down-tide of a mark can
   * pass it by a cable and never enter the circle; one that motors straight at a mark and stops
   * short of it enters the circle without ever crossing the perpendicular.
   */
  public val hasArrived: Boolean
    get() = circleEntered || perpendicularPassed
}

/**
 * Where the vessel is in relation to the waypoint it is steering to.
 *
 * Every property is nullable because every one of them comes from a sentence a given device may
 * simply not send. A plotter emitting nothing but `RMB` fills in most of this; an autopilot
 * emitting nothing but `XTE` fills in [crossTrack] alone and does not even name the destination.
 *
 * The great-circle and rhumb-line figures are kept apart on purpose. `BWC` measures the shortest
 * path over a sphere, whose bearing changes continuously as it is flown; `BWR` measures the rhumb
 * line, which is longer but holds one compass course. Over a short leg the two agree and over an
 * ocean passage they do not, so merging them would produce a number belonging to neither.
 *
 * @property destinationWaypointId the waypoint being steered to
 * @property destinationPosition where that waypoint is
 * @property originWaypointId the waypoint the leg started from; absent when the destination was
 *   selected as a direct goto rather than as part of a route
 * @property crossTrack distance off the intended track, and the way back to it
 * @property bearingTrue great-circle bearing to the destination, degrees true
 * @property bearingMagnetic the same bearing, degrees magnetic
 * @property distanceNauticalMiles great-circle distance to the destination
 * @property rhumbBearingTrue rhumb-line bearing to the destination, degrees true
 * @property rhumbBearingMagnetic the same bearing, degrees magnetic
 * @property rhumbDistanceNauticalMiles rhumb-line distance, never shorter than
 *   [distanceNauticalMiles]
 * @property closingVelocityKnots how fast the range is shutting, negative when the waypoint is
 *   receding. Not the vessel's speed: one making six knots across the leg rather than along it
 *   closes at nearly nothing, and this is the number an ETA has to be computed from.
 * @property timeToGo how long the leg still has to run, as the device worked it out
 * @property arrival which of the two arrival tests have fired
 */
public data class NavigationState(
  val destinationWaypointId: String? = null,
  val destinationPosition: Position? = null,
  val originWaypointId: String? = null,
  val crossTrack: CrossTrack? = null,
  val bearingTrue: Double? = null,
  val bearingMagnetic: Double? = null,
  val distanceNauticalMiles: Double? = null,
  val rhumbBearingTrue: Double? = null,
  val rhumbBearingMagnetic: Double? = null,
  val rhumbDistanceNauticalMiles: Double? = null,
  val closingVelocityKnots: Double? = null,
  val timeToGo: Duration? = null,
  val arrival: Arrival? = null,
) {

  /** True once the vessel has arrived at [destinationWaypointId] by either test. */
  public val hasArrived: Boolean
    get() = arrival?.hasArrived == true
}

/**
 * The vessel's navigation state, gathered from every sentence that describes the active leg.
 *
 * ```
 * source.nmeaSentences().navigation().collect { display.show(it.crossTrack, it.timeToGo) }
 * ```
 *
 * Eight sentence types answer overlapping parts of one question -- `XTE`, `APB` and `RMB` all carry
 * cross-track error, `BWC`, `BWR` and `RMB` all carry a bearing and range -- and no device sends
 * all of them. This fuses whichever arrive into one value, so a consumer reads the same properties
 * whether the feed came from a plotter, an autopilot or a Loran set.
 *
 * **State persists between update cycles, and is discarded the moment the destination changes.**
 * The persistence is because these sentences run on different schedules: a plotter that sends `RMB`
 * every second and `ZTG` every tenth would otherwise report no time-to-go nine times out of ten.
 * The discarding is the safety property, and it is the whole reason this is a stateful operator
 * rather than a `map`. When the helm selects a new waypoint, every figure held from the old leg --
 * its range, its bearing, its time-to-go, its arrival flags -- is wrong, and wrong in the direction
 * that puts a vessel confidently on a course to somewhere it is not going. So the accumulator is
 * cleared and refilled from the new leg's own sentences.
 *
 * One value is emitted per update cycle, delimited as it is in `positions()`: a sentence type the
 * cycle already holds means the device has come round again. A cycle carrying nothing that says
 * anything about a leg emits nothing, so a feed with no active waypoint is silent rather than
 * emitting an empty state on every turn.
 *
 * Sentences that flag their own data as unreliable are ignored -- a void status on `XTE`, `APB` or
 * `RMB` -- rather than being read and passed on with the flag attached. The status field is what
 * the device says about its own numbers, and a cross-track error it has disowned is not a
 * measurement.
 *
 * Distances are normalised to nautical miles. `XTE` and `APB` may report kilometres, and a
 * cross-track error read in the wrong unit is out by a factor of two in the dangerous direction.
 */
public fun Flow<Sentence>.navigation(): Flow<NavigationState> = flow {
  val leg = NavigationCycle()
  collect { sentence -> leg.add(sentence)?.let { emit(it) } }
  // The last cycle of a finite feed has no successor to close it, so the end of the feed does.
  leg.close()?.let { emit(it) }
}

/**
 * The state of the active leg, and which sentence types this update cycle has already seen.
 *
 * The two are tracked separately and cleared at different times: `state` survives a cycle boundary
 * and dies with the leg, while `seen` dies at every boundary. That split is the whole design.
 */
private class NavigationCycle {

  private var state = NavigationState()
  private val seen = mutableSetOf<String>()

  fun add(sentence: Sentence): NavigationState? {
    if (!sentence.describesALeg()) return null
    // Recorded after the close, not before: closing clears the set, so a sentence that ended the
    // cycle has to open the next one or the boundary after it goes unnoticed.
    val ended = if (sentence.id in seen) close() else null
    seen += sentence.id
    apply(sentence)
    return ended
  }

  /** Ends the current cycle, returning the leg state if anything is known about a leg at all. */
  fun close(): NavigationState? {
    seen.clear()
    // The empty state is the "no active waypoint" case, and a receiver with none still sends XTE
    // and BWC with nothing but their markers in. Emitting that on every turn would be noise.
    return state.takeIf { it != NavigationState() }
  }

  private fun apply(sentence: Sentence) {
    retarget(sentence)
    state = state.updatedBy(sentence)
  }

  /**
   * Points the accumulator at whichever waypoint this sentence names, emptying it if that is a
   * different waypoint from the one it was holding.
   *
   * A new destination invalidates every figure held from the old one, so nothing is carried across
   * -- not even the fields the new leg has yet to fill in.
   */
  private fun retarget(sentence: Sentence) {
    val destination = sentence.destinationOrNull() ?: return
    if (state.destinationWaypointId != null && state.destinationWaypointId != destination) {
      state = NavigationState()
    }
    state = state.copy(destinationWaypointId = destination)
  }
}

/**
 * Whether this sentence has anything to say about the active leg.
 *
 * Only these eight delimit a cycle here. Using every sentence would make a `GGA` end the cycle a
 * plotter had only half finished describing.
 */
private fun Sentence.describesALeg(): Boolean =
  when (this) {
    is Xte,
    is Apb,
    is Rmb,
    is Bwc,
    is Bwr,
    is Ztg,
    is Wcv,
    is Aam -> true
    else -> false
  }

/** The waypoint this sentence names as the destination, if it names one. */
private fun Sentence.destinationOrNull(): String? =
  when (this) {
    is Rmb -> destinationWaypointId
    is Apb -> destinationWaypointId
    is Bwc -> waypointId
    is Bwr -> waypointId
    is Ztg -> destinationWaypointId
    is Wcv -> waypointId
    is Aam -> waypointId
    // XTE carries no waypoint id at all. It describes whatever leg is current and cannot start a
    // new one, which is why it can never clear the accumulator.
    else -> null
  }
