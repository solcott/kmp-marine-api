package io.github.solcott.marineapi.nav.instrument

import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.sentence.Dbk
import io.github.solcott.marineapi.nmea.sentence.Dbs
import io.github.solcott.marineapi.nmea.sentence.Dbt
import io.github.solcott.marineapi.nmea.sentence.Dpt
import io.github.solcott.marineapi.nmea.sentence.Mtw
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

private val SOUNDER = TalkerId.SD

private fun assertClose(expected: Double, actual: Double, tolerance: Double = 0.0001) {
  assertTrue(abs(expected - actual) <= tolerance, "expected $expected within $tolerance of $actual")
}

private fun depthAt(metres: Double) = Depth(metres, DepthReference.KEEL)

class DepthReadingTest {

  @Test
  fun readsEachSentenceAgainstItsOwnDatum() {
    assertEquals(
      Depth(2.4, DepthReference.TRANSDUCER),
      Dbt(SOUNDER, depthMeters = 2.4).depthOrNull(DepthReference.TRANSDUCER),
    )
    assertEquals(
      Depth(2.4, DepthReference.SURFACE),
      Dbs(SOUNDER, depthMeters = 2.4).depthOrNull(DepthReference.SURFACE),
    )
    assertEquals(
      Depth(2.4, DepthReference.KEEL),
      Dbk(SOUNDER, depthMeters = 2.4).depthOrNull(DepthReference.KEEL),
    )
  }

  @Test
  fun refusesToTurnATransducerDepthIntoAKeelClearance() {
    // How far the transducer sits above the keel is a property of the boat, not of the sentence.
    // A plausible number derived from an assumption is worse here than no number at all.
    val dbt = Dbt(SOUNDER, depthMeters = 2.4)
    assertNull(dbt.depthOrNull(DepthReference.KEEL))
    assertNull(dbt.depthOrNull(DepthReference.SURFACE))
  }

  @Test
  fun convertsUnitsWhenTheMetresFieldIsEmpty() {
    // `$SDDBT,7.8,f,,M,,F` is a sentence real sounders send. Changing the unit is safe; changing
    // the datum is not.
    val feet = Dbt(SOUNDER, depthFeet = 7.8).depthOrNull(DepthReference.TRANSDUCER)!!
    assertClose(2.37744, feet.metres)

    val fathoms = Dbt(SOUNDER, depthFathoms = 1.3).depthOrNull(DepthReference.TRANSDUCER)!!
    assertClose(2.37744, fathoms.metres)
  }

  @Test
  fun prefersTheMetresFieldWhereASounderFillsInAllThree() {
    val depth = Dbt(SOUNDER, depthFeet = 99.0, depthMeters = 2.4, depthFathoms = 99.0)
    assertEquals(2.4, depth.depthOrNull(DepthReference.TRANSDUCER)?.metres)
  }

  @Test
  fun readsBackTheOtherUnits() {
    val depth = Depth(1.8288, DepthReference.SURFACE)
    assertClose(6.0, depth.feet)
    assertClose(1.0, depth.fathoms)
  }

  @Test
  fun addsAPositiveDptOffsetToReachTheSurface() {
    // A positive offset measures up from the transducer to the water line.
    val dpt = Dpt(TalkerId.IN, depth = 2.3, offset = 0.5)
    assertEquals(Depth(2.8, DepthReference.SURFACE), dpt.depthOrNull(DepthReference.SURFACE))
    assertEquals(Depth(2.3, DepthReference.TRANSDUCER), dpt.depthOrNull(DepthReference.TRANSDUCER))
  }

  @Test
  fun addsANegativeDptOffsetToReachTheKeel() {
    // A negative offset measures down from the transducer to the keel, so it takes depth away.
    val dpt = Dpt(TalkerId.IN, depth = 2.3, offset = -0.5)
    assertClose(1.8, dpt.depthOrNull(DepthReference.KEEL)!!.metres)
  }

  @Test
  fun cannotReachBothDatumsFromOneOffset() {
    // The sign of the offset says which end it measures to, so a DPT answers for the transducer
    // and for exactly one of the other two.
    val toSurface = Dpt(TalkerId.IN, depth = 2.3, offset = 0.5)
    assertNull(toSurface.depthOrNull(DepthReference.KEEL))

    val toKeel = Dpt(TalkerId.IN, depth = 2.3, offset = -0.5)
    assertNull(toKeel.depthOrNull(DepthReference.SURFACE))
  }

  @Test
  fun readsAZeroOffsetAsUnconfiguredRatherThanAsATransducerAtTheWaterline() {
    // `$INDPT,2.3,0.0` is a common sentence and no boat has its transducer at the water line.
    val dpt = Dpt(TalkerId.IN, depth = 2.3, offset = 0.0)
    assertNull(dpt.depthOrNull(DepthReference.SURFACE))
    assertNull(dpt.depthOrNull(DepthReference.KEEL))
    assertEquals(2.3, dpt.depthOrNull(DepthReference.TRANSDUCER)?.metres)
  }

  @Test
  fun ignoresASentenceThatIsNotADepthAtAll() {
    assertNull(Mtw(TalkerId.IN, temperature = 17.9).depthOrNull(DepthReference.TRANSDUCER))
  }
}

class DepthsTest {

  @Test
  fun keepsOnlyTheReadingsThatAnswerTheQuestionAsked() = runTest {
    val readings =
      listOf<Sentence>(
          Dbt(SOUNDER, depthMeters = 2.4),
          Dbk(SOUNDER, depthMeters = 1.1),
          Dpt(TalkerId.IN, depth = 2.3, offset = -0.5),
        )
        .asFlow()
        .depths(DepthReference.KEEL)
        .toList()

    assertEquals(2, readings.size)
    assertEquals(1.1, readings[0].metres)
    assertClose(1.8, readings[1].metres)
  }

  @Test
  fun yieldsNothingWhenTheSounderCannotAnswer() = runTest {
    // Worth checking for rather than waiting on: a feed of DBT asked for a keel clearance is
    // silent, and silence from a depth sounder should not look like deep water.
    val readings =
      listOf<Sentence>(Dbt(SOUNDER, depthMeters = 2.4))
        .asFlow()
        .depths(DepthReference.KEEL)
        .toList()
    assertTrue(readings.isEmpty())
  }
}

class ShallowerThanTest {

  @Test
  fun soundsWhenTheWaterShoalsAndClearsWhenItDeepens() = runTest {
    val alarms =
      listOf(depthAt(5.0), depthAt(1.5), depthAt(5.0))
        .asFlow()
        .shallowerThan(metres = 2.0, clearAt = 2.5)
        .toList()

    assertEquals(listOf(false, true, false), alarms.map { it.isShallow })
    assertEquals(listOf(5.0, 1.5, 5.0), alarms.map { it.depth.metres })
  }

  @Test
  fun doesNotChatterAcrossTheThreshold() = runTest {
    // An echo sounder over an uneven bottom reads either side of the truth from ping to ping, and
    // an alarm that follows every one of those gets switched off by the crew.
    val alarms =
      listOf(depthAt(1.5), depthAt(2.1), depthAt(1.9), depthAt(2.2), depthAt(1.8))
        .asFlow()
        .shallowerThan(metres = 2.0, clearAt = 2.5)
        .toList()

    assertEquals(listOf(true), alarms.map { it.isShallow })
  }

  @Test
  fun reportsTheFirstReadingWhicheverStateItIsIn() = runTest {
    // Going from knowing nothing to either state is a change.
    val deep = listOf(depthAt(9.0)).asFlow().shallowerThan(metres = 2.0).toList()
    assertEquals(listOf(false), deep.map { it.isShallow })

    val shallow = listOf(depthAt(1.0)).asFlow().shallowerThan(metres = 2.0).toList()
    assertEquals(listOf(true), shallow.map { it.isShallow })
  }

  @Test
  fun clearsTwentyPercentDeeperByDefault() = runTest {
    val alarms =
      listOf(depthAt(1.0), depthAt(2.1), depthAt(2.5)).asFlow().shallowerThan(metres = 2.0).toList()

    // 2.1 is past the alarm depth but not past 2.4, so it does not clear it; 2.5 does.
    assertEquals(listOf(true, false), alarms.map { it.isShallow })
    assertEquals(listOf(1.0, 2.5), alarms.map { it.depth.metres })
  }

  @Test
  fun rejectsAnAlarmThatCouldNeverClear() {
    assertFailsWith<IllegalArgumentException> {
      listOf<Depth>().asFlow().shallowerThan(metres = 3.0, clearAt = 2.0)
    }
  }

  @Test
  fun rejectsANonsensicalAlarmDepth() {
    assertFailsWith<IllegalArgumentException> { listOf<Depth>().asFlow().shallowerThan(0.0) }
  }
}
