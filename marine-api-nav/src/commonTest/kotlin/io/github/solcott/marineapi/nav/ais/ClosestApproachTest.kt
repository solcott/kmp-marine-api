package io.github.solcott.marineapi.nav.ais

import io.github.solcott.marineapi.nmea.Position
import io.github.solcott.marineapi.nmea.io.PositionFix
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.DurationUnit

/** A nautical mile, so the fixtures can be written in the unit the domain is read in. */
private const val NAUTICAL_MILE = 1852.0

/** A tenth of a degree of latitude is exactly six nautical miles on this library's earth radius. */
private const val SIX_MILES = 0.1

private fun target(
  latitude: Double,
  longitude: Double,
  course: Double,
  speed: Double,
): Vessel =
  Vessel(
    mmsi = Mmsi(244200000),
    position = Position(latitude, longitude),
    courseOverGround = course,
    speedOverGround = speed,
  )

private fun assertClose(expected: Double, actual: Double, tolerance: Double = 1.0) {
  assertTrue(abs(expected - actual) <= tolerance, "expected $expected within $tolerance of $actual")
}

/**
 * Compares a time to closest approach in seconds rather than exactly.
 *
 * The arithmetic runs through a latitude in degrees and a speed in knots, so an answer that is 2160
 * seconds on paper comes back 172 nanoseconds out. Asserting on [Duration] equality pins that
 * rounding rather than the navigation.
 */
private fun assertSeconds(expected: Double, actual: Duration) {
  assertClose(expected, actual.toDouble(DurationUnit.SECONDS), tolerance = 0.001)
}

class ClosestApproachTest {

  @Test
  fun readsAHeadOnApproachAsZeroDistance() {
    // Six miles apart, closing at twenty knots: no distance at CPA and eighteen minutes to it.
    val ownShip = OwnShip(Position(0.0, 0.0), courseOverGround = 0.0, speedOverGround = 10.0)
    val approach = target(SIX_MILES, 0.0, course = 180.0, speed = 10.0).closestApproachTo(ownShip)!!

    assertClose(0.0, approach.distance)
    assertSeconds(1080.0, approach.timeToClosestApproach)
    assertClose(6.0, approach.rangeNauticalMiles, tolerance = 0.001)
    assertClose(0.0, approach.bearing, tolerance = 0.001)
    assertFalse(approach.isOpening)
  }

  @Test
  fun readsACrossingSituation() {
    // Own ship north at 10 knots, a target six miles east running west at 10. They pass at the
    // hypotenuse of the two three-mile legs each covers: six over root two.
    val ownShip = OwnShip(Position(0.0, 0.0), courseOverGround = 0.0, speedOverGround = 10.0)
    val approach = target(0.0, SIX_MILES, course = 270.0, speed = 10.0).closestApproachTo(ownShip)!!

    assertClose(4.2426, approach.distanceNauticalMiles, tolerance = 0.001)
    assertSeconds(1080.0, approach.timeToClosestApproach)
    assertClose(90.0, approach.bearing, tolerance = 0.001)
  }

  @Test
  fun reportsAnApproachThatHasAlreadyPassedAsNegativeRatherThanZero() {
    // A target drawing ahead: they were three miles abeam thirty-six minutes ago. Clamping the
    // time to zero would make an opening target read exactly like one that is closest right now,
    // which is the difference between a safe pass and an alarm.
    val ownShip = OwnShip(Position(0.0, 0.0), courseOverGround = 0.0, speedOverGround = 10.0)
    val approach =
      target(SIX_MILES, SIX_MILES / 2, course = 0.0, speed = 20.0).closestApproachTo(ownShip)!!

    assertTrue(approach.isOpening)
    assertSeconds(-2160.0, approach.timeToClosestApproach)
    assertClose(3.0, approach.distanceNauticalMiles, tolerance = 0.001)
  }

  @Test
  fun holdsTheRangeOfTwoVesselsOnTheSameCourseAndSpeed() {
    // The range never changes, so it is the closest approach and it is happening already. Dividing
    // by a relative speed of zero would otherwise give a time of NaN.
    val ownShip = OwnShip(Position(0.0, 0.0), courseOverGround = 45.0, speedOverGround = 10.0)
    val approach = target(0.0, SIX_MILES, course = 45.0, speed = 10.0).closestApproachTo(ownShip)!!

    assertClose(6.0, approach.distanceNauticalMiles, tolerance = 0.001)
    assertEquals(Duration.ZERO, approach.timeToClosestApproach)
    assertFalse(approach.isOpening)
  }

  @Test
  fun measuresAcrossTheAntimeridianRatherThanRoundTheWorld() {
    // Longitudes of 179.95 and -179.95 are a tenth of a degree apart. Subtracting them gives
    // -359.9, which unwrapped puts a target six miles away eleven thousand miles away.
    val ownShip = OwnShip(Position(0.0, 179.95), courseOverGround = 90.0, speedOverGround = 10.0)
    val approach = target(0.0, -179.95, course = 90.0, speed = 10.0).closestApproachTo(ownShip)!!

    assertClose(6.0, approach.rangeNauticalMiles, tolerance = 0.001)
    assertClose(90.0, approach.bearing, tolerance = 0.001)
  }

  @Test
  fun reportsBearingAsACompassBearing() {
    // Clockwise from north, not counter-clockwise from east.
    val ownShip = OwnShip(Position(0.0, 0.0), courseOverGround = 0.0, speedOverGround = 0.0)
    assertClose(
      180.0,
      target(-SIX_MILES, 0.0, course = 0.0, speed = 1.0).closestApproachTo(ownShip)!!.bearing,
      tolerance = 0.001,
    )
    assertClose(
      270.0,
      target(0.0, -SIX_MILES, course = 0.0, speed = 1.0).closestApproachTo(ownShip)!!.bearing,
      tolerance = 0.001,
    )
  }

  @Test
  fun worksForAVesselAtAnchorWatchingTrafficGoPast() {
    val ownShip = OwnShip(Position(0.0, 0.0), courseOverGround = 0.0, speedOverGround = 0.0)
    val approach = target(SIX_MILES, 0.0, course = 180.0, speed = 10.0).closestApproachTo(ownShip)!!

    assertClose(0.0, approach.distance)
    assertSeconds(2160.0, approach.timeToClosestApproach)
  }

  @Test
  fun declinesToProjectATargetThatHasSentOnlyHerName() {
    // A closest approach invented for a target with no velocity would look like a measurement.
    val ownShip = OwnShip(Position(0.0, 0.0), courseOverGround = 0.0, speedOverGround = 10.0)
    val nameOnly = Vessel(mmsi = Mmsi(244200000), name = "MAERSK EDINBURGH")

    assertFalse(nameOnly.isUnderWay)
    assertNull(nameOnly.closestApproachTo(ownShip))
  }

  @Test
  fun projectsAStationaryTargetThatReportsNoCourse() {
    // Course is undefined at rest, so a stopped target sends none. She still has a position, and
    // she is still something to be run into.
    val ownShip = OwnShip(Position(0.0, 0.0), courseOverGround = 0.0, speedOverGround = 10.0)
    val stopped =
      Vessel(
        mmsi = Mmsi(244200000),
        position = Position(SIX_MILES, 0.0),
        speedOverGround = 0.0,
        courseOverGround = null,
      )

    val approach = stopped.closestApproachTo(ownShip)!!
    assertClose(0.0, approach.distance)
    assertSeconds(2160.0, approach.timeToClosestApproach)
  }

  @Test
  fun givesTheDistanceInMetresAndInMiles() {
    val ownShip = OwnShip(Position(0.0, 0.0), courseOverGround = 0.0, speedOverGround = 10.0)
    val approach = target(0.0, SIX_MILES, course = 0.0, speed = 10.0).closestApproachTo(ownShip)!!

    assertClose(6.0 * NAUTICAL_MILE, approach.distance, tolerance = 1.0)
    assertClose(6.0, approach.distanceNauticalMiles, tolerance = 0.001)
  }
}

class OwnShipTest {

  @Test
  fun takesOwnShipFromAGnssFix() {
    val fix = PositionFix(position = Position(51.95, 4.05), speedKnots = 12.0, courseTrue = 90.0)
    val ownShip = OwnShip.from(fix)!!

    assertEquals(Position(51.95, 4.05), ownShip.position)
    assertEquals(12.0, ownShip.speedOverGround)
    assertEquals(90.0, ownShip.courseOverGround)
  }

  @Test
  fun acceptsAStationaryReceiverThatReportsNoCourse() {
    // Course is undefined at rest, and a vessel at anchor is the case where the traffic around her
    // matters most.
    val fix = PositionFix(position = Position(51.95, 4.05), speedKnots = 0.0, courseTrue = null)
    assertEquals(0.0, OwnShip.from(fix)!!.courseOverGround)
  }

  @Test
  fun refusesToGuessAVelocityItWasNotGiven() {
    val noSpeed = PositionFix(position = Position(51.95, 4.05), courseTrue = 90.0)
    val movingWithNoCourse =
      PositionFix(position = Position(51.95, 4.05), speedKnots = 12.0, courseTrue = null)

    assertNull(OwnShip.from(noSpeed))
    assertNull(OwnShip.from(movingWithNoCourse))
  }
}
