package io.github.solcott.marineapi.nav.ais

import io.github.solcott.marineapi.nmea.Position
import io.github.solcott.marineapi.nmea.io.PositionFix
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** One degree of arc, on the convention that it is exactly 60 nautical miles. */
private const val METRES_PER_DEGREE: Double = Position.EARTH_RADIUS_METRES * PI / 180.0

/** A nautical mile, which is what every distance in this domain is actually read in. */
private const val METRES_PER_NAUTICAL_MILE: Double = 1852.0

private const val SECONDS_PER_HOUR: Double = 3600.0

private const val FULL_CIRCLE: Double = 360.0

/**
 * Where the vessel doing the calculating is and how she is moving.
 *
 * Own ship's own position comes from her GNSS receiver rather than from AIS, which is why this is a
 * separate type from [Vessel] rather than the same one: the fields are the same three, and the
 * source, the update rate and the trustworthiness are not.
 *
 * @property position where own ship is
 * @property courseOverGround degrees true. Course, not heading -- the target's motion is over the
 *   ground and own ship's must be too, or the relative velocity between them is wrong by however
 *   much the tide is setting her.
 * @property speedOverGround knots
 */
public data class OwnShip(
  val position: Position,
  val courseOverGround: Double,
  val speedOverGround: Double,
) {
  public companion object {
    /**
     * Own ship from a GNSS fix, or `null` if the fix does not say how she is moving.
     *
     * A course of zero is substituted when the receiver reports a speed of exactly zero and no
     * course, which is what a stationary receiver looks like -- course is undefined at rest, and a
     * vessel at anchor is precisely the case where the traffic around her matters most. Any other
     * missing field gives `null`, because guessing a velocity would produce a closest approach that
     * looked like a measurement.
     */
    public fun from(fix: PositionFix): OwnShip? {
      val speed = fix.speedKnots ?: return null
      val course = fix.courseTrue ?: if (speed == 0.0) 0.0 else return null
      return OwnShip(fix.position, course, speed)
    }
  }
}

/**
 * How close a target will come, and when.
 *
 * The two numbers a collision-avoidance decision is made on. A large [distance] means the target
 * will pass clear whatever else she does; a small one with a long [timeToClosestApproach] is a
 * situation to keep watching rather than to act on.
 *
 * @property distance metres between the two vessels at their closest, if both hold their present
 *   course and speed
 * @property timeToClosestApproach how long until that moment. **Negative when it has already
 *   passed** and the vessels are opening, which is a normal and safe reading; clamping it to zero
 *   would make an opening target indistinguishable from one that is closest right now. [isOpening]
 *   is the readable form of the test.
 * @property range metres between them at this moment
 * @property bearing degrees true from own ship to the target at this moment
 */
public data class ClosestApproach(
  val distance: Double,
  val timeToClosestApproach: Duration,
  val range: Double,
  val bearing: Double,
) {

  /** True when the closest approach is in the past and the two are drawing apart. */
  public val isOpening: Boolean
    get() = timeToClosestApproach < Duration.ZERO

  /** [distance] in nautical miles, which is the unit a CPA is read in. */
  public val distanceNauticalMiles: Double
    get() = distance / METRES_PER_NAUTICAL_MILE

  /** [range] in nautical miles. */
  public val rangeNauticalMiles: Double
    get() = range / METRES_PER_NAUTICAL_MILE
}

/**
 * How close this target will come to [ownShip], or `null` if she has not said enough to tell.
 *
 * ```
 * val approach = vessel.closestApproachTo(ownShip) ?: return
 * if (!approach.isOpening && approach.distanceNauticalMiles < 0.5) alarm(vessel)
 * ```
 *
 * `null` unless the target has reported a position, a speed and a course -- [Vessel.isUnderWay] is
 * the same test. A target that has sent only her name cannot be projected forward at all, and a
 * closest approach invented for her would be worse than none.
 *
 * **Both vessels are assumed to hold their present course and speed**, which is what a CPA means
 * and is exactly as true as that assumption. A target under helm invalidates it the moment she
 * begins to turn, and [Vessel.rateOfTurn] is the warning that she has.
 *
 * The arithmetic is done on a plane tangent to the earth at the midpoint of the two, rather than on
 * the sphere. Over the few miles a closest approach is decided at, the difference is far smaller
 * than the position error the transponders themselves carry; over hundreds of miles it is not, and
 * neither is a straight-line course. `Position.distanceTo` is the great-circle distance and is the
 * right tool for a passage plan -- it is the wrong one here, because subtracting two great-circle
 * distances does not give a relative velocity.
 */
public fun Vessel.closestApproachTo(ownShip: OwnShip): ClosestApproach? {
  val targetPosition = position ?: return null
  val targetSpeed = speedOverGround ?: return null
  val targetCourse = courseOverGround ?: if (targetSpeed == 0.0) 0.0 else return null

  // Target's position relative to own ship, in metres east and north.
  val meanLatitude = ((ownShip.position.latitude + targetPosition.latitude) / 2.0).toRadians()
  val longitudeDelta = (targetPosition.longitude - ownShip.position.longitude).wrapDegrees()
  val east = longitudeDelta * cos(meanLatitude) * METRES_PER_DEGREE
  val north = (targetPosition.latitude - ownShip.position.latitude) * METRES_PER_DEGREE

  val range = hypot(east, north)
  // atan2(east, north), not the usual (north, east): a compass bearing is measured clockwise from
  // north, where the mathematical convention is counter-clockwise from east.
  val bearing = (atan2(east, north).toDegrees() + FULL_CIRCLE) % FULL_CIRCLE

  // Relative velocity, in metres per second east and north.
  val relativeEast =
    targetSpeed.eastComponent(targetCourse) -
      ownShip.speedOverGround.eastComponent(ownShip.courseOverGround)
  val relativeNorth =
    targetSpeed.northComponent(targetCourse) -
      ownShip.speedOverGround.northComponent(ownShip.courseOverGround)
  val closingSpeedSquared = relativeEast * relativeEast + relativeNorth * relativeNorth

  // Two vessels on the same course at the same speed never close and never open: the range they
  // have now is the range they keep, so it is the closest approach and it is happening already.
  if (closingSpeedSquared == 0.0) {
    return ClosestApproach(range, Duration.ZERO, range, bearing)
  }

  val secondsToCpa = -(east * relativeEast + north * relativeNorth) / closingSpeedSquared
  val cpaEast = east + relativeEast * secondsToCpa
  val cpaNorth = north + relativeNorth * secondsToCpa
  return ClosestApproach(
    distance = hypot(cpaEast, cpaNorth),
    timeToClosestApproach = secondsToCpa.seconds,
    range = range,
    bearing = bearing,
  )
}

/** This speed in knots as its eastward component in metres per second, on a course in degrees. */
private fun Double.eastComponent(course: Double): Double =
  this * METRES_PER_NAUTICAL_MILE / SECONDS_PER_HOUR * sin(course.toRadians())

/** This speed in knots as its northward component in metres per second, on a course in degrees. */
private fun Double.northComponent(course: Double): Double =
  this * METRES_PER_NAUTICAL_MILE / SECONDS_PER_HOUR * cos(course.toRadians())

/**
 * This difference in degrees of longitude, brought back into -180 to 180.
 *
 * Two vessels either side of the antimeridian are a degree apart and their longitudes differ by
 * 359, and a target a mile away would otherwise be projected halfway round the world.
 */
private fun Double.wrapDegrees(): Double = ((this + 540.0) % FULL_CIRCLE) - 180.0

private fun Double.toRadians(): Double = this * PI / 180.0

private fun Double.toDegrees(): Double = this * 180.0 / PI
