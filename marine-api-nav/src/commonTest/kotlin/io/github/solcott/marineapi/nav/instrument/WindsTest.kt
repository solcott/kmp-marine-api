package io.github.solcott.marineapi.nav.instrument

import io.github.solcott.marineapi.nmea.AngleReference
import io.github.solcott.marineapi.nmea.DataStatus
import io.github.solcott.marineapi.nmea.Direction
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.Units
import io.github.solcott.marineapi.nmea.sentence.Hdt
import io.github.solcott.marineapi.nmea.sentence.Mwd
import io.github.solcott.marineapi.nmea.sentence.Mwv
import io.github.solcott.marineapi.nmea.sentence.Rmc
import io.github.solcott.marineapi.nmea.sentence.Vhw
import io.github.solcott.marineapi.nmea.sentence.Vwr
import io.github.solcott.marineapi.nmea.sentence.Vwt
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

private val INSTRUMENT = TalkerId.II

private fun assertClose(expected: Double, actual: Double, tolerance: Double = 0.01) {
  assertTrue(abs(expected - actual) <= tolerance, "expected $expected within $tolerance of $actual")
}

/** Apparent wind as a masthead unit sends it: an angle round from the bow and a speed in knots. */
private fun apparent(angle: Double, knots: Double) =
  Mwv(INSTRUMENT, angle, AngleReference.RELATIVE, knots, Units.NAUTICAL_MILES, DataStatus.ACTIVE)

private fun log(knots: Double, headingTrue: Double? = null) =
  Vhw(INSTRUMENT, headingTrue = headingTrue, speedKnots = knots)

class WindReadingTest {

  @Test
  fun readsAnApparentWindFromAMastheadUnit() {
    val wind = apparent(angle = 125.1, knots = 5.5).windOrNull()!!
    assertEquals(WindReference.APPARENT, wind.reference)
    assertEquals(125.1, wind.angleFromBow)
    assertEquals(5.5, wind.speedKnots)
    assertNull(wind.directionTrue)
  }

  @Test
  fun readsAnMwvMarkedTrueAsTrueWindRatherThanApparent() {
    // The T in an MWV means the angle is the true wind angle, not that the angle is a bearing.
    val sentence =
      Mwv(INSTRUMENT, 125.1, AngleReference.TRUE, 5.5, Units.NAUTICAL_MILES, DataStatus.ACTIVE)
    assertEquals(WindReference.TRUE_OVER_WATER, sentence.windOrNull()?.reference)
  }

  @Test
  fun normalisesEveryUnitTheSentenceMayUse() {
    val metresPerSecond =
      Mwv(INSTRUMENT, 90.0, AngleReference.RELATIVE, 10.0, Units.METER, DataStatus.ACTIVE)
    assertClose(19.44, metresPerSecond.windOrNull()!!.speedKnots)

    val kmh =
      Mwv(INSTRUMENT, 90.0, AngleReference.RELATIVE, 18.52, Units.KILOMETERS, DataStatus.ACTIVE)
    assertClose(10.0, kmh.windOrNull()!!.speedKnots)
  }

  @Test
  fun refusesASpeedWithNoUnit() {
    // The three units the field allows differ by a factor of two, so an unlabelled number is not
    // a wind speed.
    val sentence = Mwv(INSTRUMENT, 90.0, AngleReference.RELATIVE, 10.0, null, DataStatus.ACTIVE)
    assertNull(sentence.windOrNull())
  }

  @Test
  fun dropsAReadingTheInstrumentMarksVoid() {
    val sentence =
      Mwv(INSTRUMENT, 90.0, AngleReference.RELATIVE, 10.0, Units.NAUTICAL_MILES, DataStatus.VOID)
    assertNull(sentence.windOrNull())
  }

  @Test
  fun keepsAReadingThatOmitsTheStatusField() {
    // The field is advisory, and plenty of instruments leave it out.
    val sentence = Mwv(INSTRUMENT, 90.0, AngleReference.RELATIVE, 10.0, Units.NAUTICAL_MILES, null)
    assertEquals(90.0, sentence.windOrNull()?.angleFromBow)
  }

  @Test
  fun turnsAPortSideAngleIntoTheSameSweepAsMwvUses() {
    // VWR reports an unsigned magnitude with an L or an R beside it; MWV sweeps 0 to 359. A wind
    // 88 degrees off the port bow is 272 in that sweep, not 88.
    val port = Vwr(INSTRUMENT, windAngle = 88.0, side = Direction.LEFT, speedKnots = 24.5)
    assertEquals(272.0, port.windOrNull()?.angleFromBow)

    val starboard = Vwr(INSTRUMENT, windAngle = 88.0, side = Direction.RIGHT, speedKnots = 24.5)
    assertEquals(88.0, starboard.windOrNull()?.angleFromBow)
  }

  @Test
  fun refusesAnAngleWithNoSide() {
    // Without the L or the R, 30 degrees could be either side of the vessel, and the two are 60
    // degrees apart.
    val sentence = Vwr(INSTRUMENT, windAngle = 30.0, side = null, speedKnots = 10.0)
    assertNull(sentence.windOrNull())
  }

  @Test
  fun readsVwtAsTrueWindOverWater() {
    val sentence = Vwt(INSTRUMENT, windAngle = 88.0, side = Direction.LEFT, speedKnots = 24.7)
    val wind = sentence.windOrNull()!!
    assertEquals(WindReference.TRUE_OVER_WATER, wind.reference)
    assertEquals(272.0, wind.angleFromBow)
  }

  @Test
  fun readsMwdAsACompassDirectionWithNoBowAngle() {
    val sentence = Mwd(TalkerId.WI, directionTrue = 302.4, speedKnots = 10.5)
    val wind = sentence.windOrNull()!!
    assertEquals(302.4, wind.directionTrue)
    assertNull(wind.angleFromBow)
  }
}

class TrueWindTest {

  @Test
  fun takesTheVesselsOwnMotionOutOfAHeadWind() = runTest {
    // Motoring at 5 knots into 20 knots of apparent wind on the nose: the true wind is 15.
    val winds = listOf<Sentence>(log(5.0), apparent(0.0, 20.0)).asFlow().trueWind().toList()

    assertEquals(1, winds.size)
    assertClose(15.0, winds.single().speedKnots)
    assertClose(0.0, winds.single().angleFromBow!!)
    assertEquals(WindReference.TRUE_OVER_WATER, winds.single().reference)
  }

  @Test
  fun putsTheTrueWindFurtherAftThanTheApparentOne() = runTest {
    // 10 knots of apparent wind on the beam at 10 knots of boat speed is 14.1 knots of true wind
    // from 135 degrees -- abaft the beam. Apparent wind is always drawn forward of true, which is
    // the whole reason a sailor needs the correction.
    val winds = listOf<Sentence>(log(10.0), apparent(90.0, 10.0)).asFlow().trueWind().toList()

    assertClose(14.142, winds.single().speedKnots)
    assertClose(135.0, winds.single().angleFromBow!!)
  }

  @Test
  fun leavesTheWindAloneWhenTheVesselIsNotMoving() = runTest {
    val winds = listOf<Sentence>(log(0.0), apparent(45.0, 12.0)).asFlow().trueWind().toList()

    assertClose(12.0, winds.single().speedKnots)
    assertClose(45.0, winds.single().angleFromBow!!)
  }

  @Test
  fun passesThroughAReadingTheInstrumentAlreadyCorrected() = runTest {
    // Correcting an already-corrected wind would take the vessel's motion out twice.
    val reported =
      Mwv(INSTRUMENT, 135.0, AngleReference.TRUE, 14.14, Units.NAUTICAL_MILES, DataStatus.ACTIVE)
    val winds = listOf<Sentence>(log(10.0), reported).asFlow().trueWind().toList()

    assertClose(14.14, winds.single().speedKnots)
    assertClose(135.0, winds.single().angleFromBow!!)
  }

  @Test
  fun saysNothingUntilItKnowsHowFastTheVesselIsGoing() = runTest {
    // Without a boat speed there is no correction to make, and reporting the apparent wind as
    // though it were true would be a fabrication.
    val winds = listOf<Sentence>(apparent(0.0, 20.0)).asFlow().trueWind().toList()
    assertTrue(winds.isEmpty())
  }

  @Test
  fun fillsInACompassDirectionOnceTheHeadingIsKnown() = runTest {
    val winds =
      listOf<Sentence>(Hdt(INSTRUMENT, heading = 300.0), log(10.0), apparent(90.0, 10.0))
        .asFlow()
        .trueWind()
        .toList()

    // 135 degrees off a bow pointing 300 comes round past north to 075.
    assertClose(75.0, winds.single().directionTrue!!)
  }

  @Test
  fun takesTheHeadingFromTheLogWhenThereIsNoGyro() = runTest {
    val winds =
      listOf<Sentence>(log(10.0, headingTrue = 300.0), apparent(90.0, 10.0))
        .asFlow()
        .trueWind()
        .toList()

    assertClose(75.0, winds.single().directionTrue!!)
  }

  @Test
  fun leavesTheDirectionUnknownOnAVesselWithNoTrueHeading() = runTest {
    // A magnetic compass alone is not a true heading, and turning one into the other needs the
    // local variation. The angle off the bow is still right.
    val winds = listOf<Sentence>(log(10.0), apparent(90.0, 10.0)).asFlow().trueWind().toList()

    assertNull(winds.single().directionTrue)
    assertClose(135.0, winds.single().angleFromBow!!)
  }

  @Test
  fun fillsInABowAngleForAnMwdOnceTheHeadingIsKnown() = runTest {
    val winds =
      listOf<Sentence>(
          Hdt(INSTRUMENT, heading = 300.0),
          log(10.0),
          Mwd(TalkerId.WI, directionTrue = 75.0, speedKnots = 14.14),
        )
        .asFlow()
        .trueWind()
        .toList()

    assertClose(135.0, winds.single().angleFromBow!!)
  }
}

class GroundWindTest {

  @Test
  fun differsFromTheTrueWindByExactlyTheCurrent() = runTest {
    // Two knots of fair tide: 10 knots through the water and 12 over the ground, same direction.
    // The sailor's true wind and the meteorologist's ground wind are two knots apart, and in
    // light airs that is most of the wind.
    val feed =
      listOf<Sentence>(
        Hdt(INSTRUMENT, heading = 0.0),
        log(10.0),
        Rmc(TalkerId.GP, speedKnots = 12.0, courseTrue = 0.0),
        apparent(0.0, 20.0),
      )

    assertClose(10.0, feed.asFlow().trueWind().toList().single().speedKnots)
    assertClose(8.0, feed.asFlow().groundWind().toList().single().speedKnots)
  }

  @Test
  fun accountsForTheAngleBetweenTheBowAndTheCourse() = runTest {
    // A vessel crabbing 90 degrees across her own heading -- an extreme, but it is the term that
    // is dropped when heading and course are assumed equal. Head-on 10 knots of apparent wind
    // with 10 knots of ground track to starboard gives 14.1 knots from 45 degrees off the bow.
    val winds =
      listOf<Sentence>(
          Hdt(INSTRUMENT, heading = 0.0),
          Rmc(TalkerId.GP, speedKnots = 10.0, courseTrue = 90.0),
          apparent(0.0, 10.0),
        )
        .asFlow()
        .groundWind()
        .toList()

    assertClose(14.142, winds.single().speedKnots)
    assertClose(315.0, winds.single().angleFromBow!!)
    assertEquals(WindReference.GROUND, winds.single().reference)
  }

  @Test
  fun refusesToAssumeTheBowPointsWhereTheVesselIsGoing() = runTest {
    // Course over ground without a heading is the shortcut that makes a ground wind wrong.
    val winds =
      listOf<Sentence>(Rmc(TalkerId.GP, speedKnots = 10.0, courseTrue = 90.0), apparent(0.0, 10.0))
        .asFlow()
        .groundWind()
        .toList()

    assertTrue(winds.isEmpty())
  }

  @Test
  fun needsNoCourseFromAVesselStoppedOverTheGround() = runTest {
    // A stopped vessel reports no course and does not need one: her motion is zero whichever way
    // it would have pointed.
    val winds =
      listOf<Sentence>(
          Hdt(INSTRUMENT, heading = 0.0),
          Rmc(TalkerId.GP, speedKnots = 0.0, courseTrue = null),
          apparent(45.0, 12.0),
        )
        .asFlow()
        .groundWind()
        .toList()

    assertClose(12.0, winds.single().speedKnots)
    assertClose(45.0, winds.single().angleFromBow!!)
  }

  @Test
  fun ignoresAReadingThatHasAlreadyHadTheWaterTakenOutOfIt() = runTest {
    // Converting a true wind over water into a ground wind needs the current, which no sentence
    // carries.
    val winds =
      listOf<Sentence>(
          Hdt(INSTRUMENT, heading = 0.0),
          Rmc(TalkerId.GP, speedKnots = 10.0, courseTrue = 0.0),
          Vwt(INSTRUMENT, windAngle = 45.0, side = Direction.RIGHT, speedKnots = 12.0),
        )
        .asFlow()
        .groundWind()
        .toList()

    assertTrue(winds.isEmpty())
  }
}

class WindsTest {

  @Test
  fun reportsEveryReadingAsTheInstrumentGaveIt() = runTest {
    val winds =
      listOf<Sentence>(
          apparent(90.0, 10.0),
          Mwv(
            INSTRUMENT,
            135.0,
            AngleReference.TRUE,
            14.1,
            Units.NAUTICAL_MILES,
            DataStatus.ACTIVE,
          ),
          log(10.0),
        )
        .asFlow()
        .winds()
        .toList()

    assertEquals(
      listOf(WindReference.APPARENT, WindReference.TRUE_OVER_WATER),
      winds.map { it.reference },
    )
  }
}
