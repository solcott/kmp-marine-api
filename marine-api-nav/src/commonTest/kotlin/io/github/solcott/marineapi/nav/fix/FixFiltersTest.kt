package io.github.solcott.marineapi.nav.fix

import io.github.solcott.marineapi.nmea.GpsFixQuality
import io.github.solcott.marineapi.nmea.Position
import io.github.solcott.marineapi.nmea.io.FixAccuracy
import io.github.solcott.marineapi.nmea.io.PositionFix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime

/**
 * Fixes are built directly rather than parsed from sentences: these operators take a
 * `Flow<PositionFix>`, so going through the parser would only test the layer underneath again.
 */
private fun fixAt(
  latitude: Double = 60.0,
  longitude: Double = 25.0,
  seconds: Int? = null,
  quality: GpsFixQuality? = null,
  accuracy: FixAccuracy = FixAccuracy(),
): PositionFix =
  PositionFix(
    position = Position(latitude, longitude),
    time = seconds?.let { LocalTime(0, 0, 0).plusSeconds(it) },
    fixQuality = quality,
    accuracy = accuracy,
  )

private fun LocalTime.plusSeconds(seconds: Int): LocalTime =
  LocalTime.fromSecondOfDay((toSecondOfDay() + seconds) % (24 * 60 * 60))

class FilterAccurateTest {

  @Test
  fun keepsOnlyTheFixesInsideTheThreshold() = runTest {
    val fixes =
      listOf(
        fixAt(accuracy = FixAccuracy(latitudeError = 3.0, longitudeError = 4.0)), // DRMS 5.0
        fixAt(accuracy = FixAccuracy(latitudeError = 30.0, longitudeError = 40.0)), // DRMS 50.0
      )
    val kept = fixes.asFlow().filterAccurate(horizontalErrorAtMost = 10.0).toList()
    assertEquals(1, kept.size)
    assertEquals(5.0, kept.single().accuracy.horizontalError)
  }

  @Test
  fun dropsAFixWhoseAccuracyWasNeverReported() = runTest {
    // An unmeasured error is not a small one, and most consumer receivers send no GST at all.
    val kept = listOf(fixAt()).asFlow().filterAccurate(horizontalErrorAtMost = 10.0).toList()
    assertTrue(kept.isEmpty())
  }

  @Test
  fun keepsAnUnreportedFixWhenAskedTo() = runTest {
    val kept =
      listOf(fixAt())
        .asFlow()
        .filterAccurate(horizontalErrorAtMost = 10.0, requireReported = false)
        .toList()
    assertEquals(1, kept.size)
  }
}

class MinFixQualityTest {

  @Test
  fun admitsTheThresholdItselfAndEverythingBetter() = runTest {
    val kept =
      listOf(
          fixAt(quality = GpsFixQuality.NORMAL),
          fixAt(quality = GpsFixQuality.DGPS),
          fixAt(quality = GpsFixQuality.RTK),
        )
        .asFlow()
        .minFixQuality(GpsFixQuality.DGPS)
        .toList()
    assertEquals(listOf(GpsFixQuality.DGPS, GpsFixQuality.RTK), kept.map { it.fixQuality })
  }

  @Test
  fun rejectsSimulatedAndManualPositionsDespiteTheirHigherWireCodes() = runTest {
    // The bug this operator exists to avoid: MANUAL is 7 and SIMULATED is 8, both above DGPS's 2,
    // so comparing the enum's own ordinals would let a simulator satisfy a demand for a
    // differential fix. Neither is a measurement of where the receiver is.
    val kept =
      listOf(
          fixAt(quality = GpsFixQuality.MANUAL),
          fixAt(quality = GpsFixQuality.SIMULATED),
          fixAt(quality = GpsFixQuality.INVALID),
        )
        .asFlow()
        .minFixQuality(GpsFixQuality.DGPS)
        .toList()
    assertTrue(kept.isEmpty())
  }

  @Test
  fun ranksDeadReckoningBelowAnAutonomousFix() = runTest {
    // ESTIMATED is wire code 6, above NORMAL's 1, but the receiver is extrapolating rather than
    // observing, so it must not satisfy a demand for an autonomous fix.
    val kept =
      listOf(fixAt(quality = GpsFixQuality.ESTIMATED))
        .asFlow()
        .minFixQuality(GpsFixQuality.NORMAL)
        .toList()
    assertTrue(kept.isEmpty())
  }

  @Test
  fun ranksRtkFixedAboveRtkFloat() = runTest {
    // FRTK is wire code 5 and RTK is 4, but resolving the carrier ambiguities is what takes RTK
    // from decimetres to centimetres, so the float solution is the weaker of the pair.
    val kept =
      listOf(fixAt(quality = GpsFixQuality.FRTK)).asFlow().minFixQuality(GpsFixQuality.RTK).toList()
    assertTrue(kept.isEmpty())
  }

  @Test
  fun dropsACycleThatReportedNoQualityAtAll() = runTest {
    val kept = listOf(fixAt()).asFlow().minFixQuality(GpsFixQuality.NORMAL).toList()
    assertTrue(kept.isEmpty())
  }
}

class FixStatesTest {

  @Test
  fun reportsTheFirstStateAndThenOnlyChanges() = runTest {
    val states =
      listOf(
          fixAt(quality = GpsFixQuality.NORMAL),
          fixAt(quality = GpsFixQuality.NORMAL),
          fixAt(quality = GpsFixQuality.NORMAL),
          fixAt(quality = GpsFixQuality.DGPS),
          fixAt(quality = GpsFixQuality.DGPS),
        )
        .asFlow()
        .fixStates(confirmations = 1)
        .toList()
    assertEquals(listOf(FixState.AUTONOMOUS, FixState.DIFFERENTIAL), states)
  }

  @Test
  fun ignoresASingleCycleBlip() = runTest {
    // A satellite dropping below the mask angle for one cycle is routine, and a status display
    // that follows every one of those is noise.
    val states =
      listOf(
          fixAt(quality = GpsFixQuality.DGPS),
          fixAt(quality = GpsFixQuality.DGPS),
          fixAt(quality = GpsFixQuality.NORMAL), // the blip
          fixAt(quality = GpsFixQuality.DGPS),
          fixAt(quality = GpsFixQuality.DGPS),
        )
        .asFlow()
        .fixStates()
        .toList()
    assertEquals(listOf(FixState.DIFFERENTIAL), states)
  }

  @Test
  fun reportsAChangeThatPersists() = runTest {
    val states =
      listOf(
          fixAt(quality = GpsFixQuality.DGPS),
          fixAt(quality = GpsFixQuality.DGPS),
          fixAt(quality = GpsFixQuality.NORMAL),
          fixAt(quality = GpsFixQuality.NORMAL),
        )
        .asFlow()
        .fixStates()
        .toList()
    assertEquals(listOf(FixState.DIFFERENTIAL, FixState.AUTONOMOUS), states)
  }

  @Test
  fun treatsACycleWithNoQualityAsAcquiring() = runTest {
    val states = listOf(fixAt(), fixAt()).asFlow().fixStates().toList()
    assertEquals(listOf(FixState.ACQUIRING), states)
  }

  @Test
  fun rejectsANonsensicalConfirmationCount() {
    assertFailsWith<IllegalArgumentException> { listOf<PositionFix>().asFlow().fixStates(0) }
  }
}

class RejectTeleportsTest {

  @Test
  fun dropsAFixThatCouldOnlyBeReachedImplausiblyFast() = runTest {
    val kept =
      listOf(
          fixAt(latitude = 60.0, seconds = 0),
          // A degree of latitude is 60 nautical miles; one second later is 216,000 knots.
          fixAt(latitude = 61.0, seconds = 1),
        )
        .asFlow()
        .rejectTeleports(maxSpeedKnots = 60.0)
        .toList()
    assertEquals(1, kept.size)
    assertEquals(60.0, kept.single().position.latitude)
  }

  @Test
  fun keepsAJumpTheVesselCouldActuallyHaveMade() = runTest {
    val kept =
      listOf(
          fixAt(latitude = 60.0, seconds = 0),
          // 0.001 degrees is 0.06 nautical miles; in 10 seconds that is about 21.6 knots.
          fixAt(latitude = 60.001, seconds = 10),
        )
        .asFlow()
        .rejectTeleports(maxSpeedKnots = 60.0)
        .toList()
    assertEquals(2, kept.size)
  }

  @Test
  fun comparesAgainstTheLastFixKeptRatherThanTheLastSeen() = runTest {
    // The failure mode that makes naive versions of this worse than none: one bad fix must not
    // drag the reference point with it and take the good fixes after it down too.
    val kept =
      listOf(
          fixAt(latitude = 60.0000, seconds = 0),
          fixAt(latitude = 70.0000, seconds = 1), // rejected
          // 0.0002 degrees is about 22 m, and at 60 knots two seconds allows about 62 m -- so
          // this is plausible from the FIRST fix, and wildly implausible from the rejected one.
          fixAt(latitude = 60.0002, seconds = 2),
        )
        .asFlow()
        .rejectTeleports(maxSpeedKnots = 60.0)
        .toList()
    assertEquals(listOf(60.0000, 60.0002), kept.map { it.position.latitude })
  }

  @Test
  fun keepsAFixItCannotJudge() = runTest {
    // No timestamp means no interval to divide by. Rejecting on an unanswerable question would
    // silently empty the flow on any feed without times.
    val kept =
      listOf(fixAt(latitude = 60.0), fixAt(latitude = 70.0))
        .asFlow()
        .rejectTeleports(maxSpeedKnots = 60.0)
        .toList()
    assertEquals(2, kept.size)
  }

  @Test
  fun keepsBothFixesCarryingTheSameTimestamp() = runTest {
    // A receiver reporting whole seconds legitimately emits several fixes within one, and no
    // distance is implausible in zero time.
    val kept =
      listOf(fixAt(latitude = 60.0, seconds = 5), fixAt(latitude = 60.001, seconds = 5))
        .asFlow()
        .rejectTeleports(maxSpeedKnots = 60.0)
        .toList()
    assertEquals(2, kept.size)
  }

  @Test
  fun abandonsTheTestAfterALongDropout() = runTest {
    // After an hour off the air the vessel really could be anywhere, so the implied speed says
    // nothing and the fix is kept regardless.
    val kept =
      listOf(fixAt(latitude = 60.0, seconds = 0), fixAt(latitude = 70.0, seconds = 3600))
        .asFlow()
        .rejectTeleports(maxSpeedKnots = 60.0, maxGap = 30.minutes)
        .toList()
    assertEquals(2, kept.size)
  }

  @Test
  fun readsATimeGoingBackwardsAsMidnight() = runTest {
    val justBeforeMidnight = 23 * 3600 + 59 * 60 + 58
    val kept =
      listOf(
          fixAt(latitude = 60.0, seconds = justBeforeMidnight),
          // Four seconds later, not very nearly minus a day. At 60 knots the vessel can cover
          // about 123 m in that time, and 0.001 degrees is about 111 m.
          fixAt(latitude = 60.001, seconds = 2),
        )
        .asFlow()
        .rejectTeleports(maxSpeedKnots = 60.0)
        .toList()
    assertEquals(2, kept.size)
  }

  @Test
  fun rejectsANonsensicalSpeedLimit() {
    assertFailsWith<IllegalArgumentException> {
      listOf<PositionFix>().asFlow().rejectTeleports(0.0)
    }
  }
}
