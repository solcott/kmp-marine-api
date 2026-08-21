package io.github.solcott.marineapi.nav.route

import io.github.solcott.marineapi.nmea.DataStatus
import io.github.solcott.marineapi.nmea.Direction
import io.github.solcott.marineapi.nmea.Position
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.Units
import io.github.solcott.marineapi.nmea.sentence.Aam
import io.github.solcott.marineapi.nmea.sentence.Apb
import io.github.solcott.marineapi.nmea.sentence.Bwc
import io.github.solcott.marineapi.nmea.sentence.Bwr
import io.github.solcott.marineapi.nmea.sentence.Rmb
import io.github.solcott.marineapi.nmea.sentence.Wcv
import io.github.solcott.marineapi.nmea.sentence.Xte
import io.github.solcott.marineapi.nmea.sentence.Ztg
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

private fun rmb(
  destination: String? = "RUSKI",
  crossTrackError: Double? = 0.5,
  steerTo: Direction? = Direction.RIGHT,
  range: Double? = 432.3,
  bearing: Double? = 234.9,
  velocity: Double? = 6.0,
  arrived: Boolean = false,
  status: DataStatus? = DataStatus.ACTIVE,
): Rmb =
  Rmb(
    talker = TalkerId.GP,
    status = status,
    crossTrackError = crossTrackError,
    steerTo = steerTo,
    originWaypointId = "MELIN",
    destinationWaypointId = destination,
    destinationPosition = Position(55.603333, 14.608333),
    range = range,
    bearing = bearing,
    velocity = velocity,
    arrivalStatus = if (arrived) DataStatus.ACTIVE else DataStatus.VOID,
  )

private fun xte(
  magnitude: Double? = 5.36,
  steerTo: Direction? = Direction.RIGHT,
  units: Units? = Units.NAUTICAL_MILES,
  status: DataStatus? = DataStatus.ACTIVE,
): Xte =
  Xte(
    talker = TalkerId.II,
    status = status,
    cycleLockStatus = DataStatus.ACTIVE,
    magnitude = magnitude,
    steerTo = steerTo,
    units = units,
  )

private suspend fun navigationOf(vararg sentences: Sentence): List<NavigationState> =
  sentences.toList().asFlow().navigation().toList()

class NavigationStateTest {

  @Test
  fun readsAWholeLegFromASingleRmb() = runTest {
    val state = navigationOf(rmb()).single()
    assertEquals("RUSKI", state.destinationWaypointId)
    assertEquals("MELIN", state.originWaypointId)
    assertEquals(Position(55.603333, 14.608333), state.destinationPosition)
    assertEquals(CrossTrack(0.5, Direction.RIGHT), state.crossTrack)
    assertEquals(234.9, state.bearingTrue)
    assertEquals(432.3, state.distanceNauticalMiles)
    assertEquals(6.0, state.closingVelocityKnots)
    assertFalse(state.hasArrived)
  }

  @Test
  fun fusesFiguresFromSentencesNoOneDeviceSendsTogether() = runTest {
    val state =
      navigationOf(
          xte(),
          Ztg(TalkerId.GP, timeRemaining = 90.minutes, destinationWaypointId = "RUSKI"),
          Wcv(TalkerId.GP, velocityKnots = 4.5, waypointId = "RUSKI"),
        )
        .single()
    assertEquals(CrossTrack(5.36, Direction.RIGHT), state.crossTrack)
    assertEquals(90.minutes, state.timeToGo)
    assertEquals(4.5, state.closingVelocityKnots)
  }

  @Test
  fun keepsTheGreatCircleAndTheRhumbLineApart() = runTest {
    // Over an ocean passage the two disagree, and merging them gives a number belonging to
    // neither. BWC is the shorter path; BWR is the one steerable on a single compass course.
    val state =
      navigationOf(
          Bwc(
            talker = TalkerId.GP,
            waypointPosition = Position(51.5003, -0.7723),
            bearingTrue = 213.8,
            bearingMagnetic = 218.0,
            distanceNauticalMiles = 4.6,
            waypointId = "EGLM",
          ),
          Bwr(
            talker = TalkerId.GP,
            bearingTrue = 215.1,
            bearingMagnetic = 219.3,
            distanceNauticalMiles = 4.9,
            waypointId = "EGLM",
          ),
        )
        .single()
    assertEquals(213.8, state.bearingTrue)
    assertEquals(4.6, state.distanceNauticalMiles)
    assertEquals(215.1, state.rhumbBearingTrue)
    assertEquals(219.3, state.rhumbBearingMagnetic)
    assertEquals(4.9, state.rhumbDistanceNauticalMiles)
  }

  @Test
  fun carriesAFigureAcrossCyclesUntilSomethingUpdatesIt() = runTest {
    // A plotter sending RMB every second and ZTG every tenth must not report no time-to-go for
    // the nine cycles in between.
    val states =
      navigationOf(
        Ztg(TalkerId.GP, timeRemaining = 90.minutes, destinationWaypointId = "RUSKI"),
        rmb(),
        rmb(),
        rmb(),
      )
    assertEquals(listOf(90.minutes, 90.minutes, 90.minutes), states.map { it.timeToGo })
  }

  @Test
  fun emitsOneValuePerUpdateCycle() = runTest {
    // A repeated sentence type is the boundary, and the end of the feed closes the last cycle.
    assertEquals(3, navigationOf(rmb(), xte(), rmb(), rmb()).size)
  }

  @Test
  fun staysSilentWhileNothingIsBeingNavigatedTo() = runTest {
    // A receiver with no active waypoint still sends these, empty apart from their markers.
    val empty = Xte(TalkerId.II, status = DataStatus.ACTIVE)
    assertTrue(navigationOf(empty, empty, empty).isEmpty())
  }
}

class LegChangeTest {

  @Test
  fun forgetsEveryFigureFromTheOldLegWhenTheDestinationChanges() = runTest {
    // The safety property: the old leg's range and bearing are not merely stale on the new leg,
    // they are wrong in the direction that puts a vessel confidently on the wrong course.
    val states =
      navigationOf(
        rmb(destination = "RUSKI", range = 432.3),
        Ztg(TalkerId.GP, timeRemaining = 90.minutes, destinationWaypointId = "RUSKI"),
        rmb(destination = "KNUDAN", range = null, bearing = null, velocity = null),
      )
    val newLeg = states.last()
    assertEquals("KNUDAN", newLeg.destinationWaypointId)
    assertNull(newLeg.distanceNauticalMiles)
    assertNull(newLeg.bearingTrue)
    assertNull(newLeg.timeToGo)
  }

  @Test
  fun keepsTheLegWhenTheDestinationIsRepeated() = runTest {
    val states =
      navigationOf(
        Ztg(TalkerId.GP, timeRemaining = 90.minutes, destinationWaypointId = "RUSKI"),
        rmb(destination = "RUSKI"),
        rmb(destination = "RUSKI"),
      )
    assertEquals(90.minutes, states.last().timeToGo)
  }

  @Test
  fun letsACrossTrackErrorApplyToWhicheverLegIsCurrent() = runTest {
    // XTE names no waypoint, so it can describe the active leg but can never start a new one.
    val states = navigationOf(rmb(destination = "RUSKI"), xte(), xte())
    assertEquals("RUSKI", states.last().destinationWaypointId)
    assertEquals(CrossTrack(5.36, Direction.RIGHT), states.last().crossTrack)
  }
}

class CrossTrackTest {

  @Test
  fun convertsAKilometreReadingToNauticalMiles() = runTest {
    // Reading kilometres as miles is out by nearly a factor of two, in the dangerous direction.
    val state = navigationOf(xte(magnitude = 1.852, units = Units.KILOMETERS)).single()
    assertEquals(1.0, state.crossTrack?.nauticalMiles)
  }

  @Test
  fun rejectsAReadingWithNoSideToIt() = runTest {
    // A mile off track with no L or R could be either way, and the two are two miles apart.
    assertTrue(navigationOf(xte(steerTo = null)).isEmpty())
  }

  @Test
  fun rejectsAReadingWithNoUnit() = runTest {
    assertTrue(navigationOf(xte(units = null)).isEmpty())
  }

  @Test
  fun ignoresASentenceThatFlagsItsOwnDataUnreliable() = runTest {
    // A cross-track error the device has disowned is not a measurement.
    assertTrue(navigationOf(xte(status = DataStatus.VOID)).isEmpty())
  }

  @Test
  fun readsAnAutopilotSentenceThatReportsInKilometres() = runTest {
    val apb =
      Apb(
        talker = TalkerId.GP,
        status = DataStatus.ACTIVE,
        cycleLockStatus = DataStatus.ACTIVE,
        crossTrackError = 1.852,
        steerTo = Direction.LEFT,
        crossTrackUnits = Units.KILOMETERS,
        destinationWaypointId = "DEST",
      )
    val state = navigationOf(apb).single()
    assertEquals(CrossTrack(1.0, Direction.LEFT), state.crossTrack)
    assertEquals("DEST", state.destinationWaypointId)
  }
}

class ArrivalTest {

  @Test
  fun reportsArrivalWhenTheVesselPassesWideOfTheMark() = runTest {
    // The perpendicular test alone. A vessel set down-tide can pass a mark by a cable and never
    // enter its arrival circle, and a route that waited for the circle would stall there.
    val state =
      navigationOf(
          Aam(
            talker = TalkerId.GP,
            circleEntered = DataStatus.VOID,
            perpendicularPassed = DataStatus.ACTIVE,
            arrivalCircleRadius = 0.1,
            waypointId = "DEST",
          )
        )
        .single()
    assertEquals(Arrival(circleEntered = false, perpendicularPassed = true), state.arrival)
    assertTrue(state.hasArrived)
  }

  @Test
  fun readsArrivalFromRmbWithoutClaimingToKnowWhichTestFired() = runTest {
    // RMB collapses both into one flag.
    val state = navigationOf(rmb(arrived = true)).single()
    assertTrue(state.hasArrived)
  }

  @Test
  fun doesNotReadAnUnreportedArrivalAsANegativeOne() = runTest {
    // "There is nothing to arrive at" is not "you have not arrived yet" -- and the second, on its
    // own, would make an otherwise empty state look like a leg in progress.
    assertTrue(navigationOf(Aam(TalkerId.GP)).isEmpty())
  }
}
