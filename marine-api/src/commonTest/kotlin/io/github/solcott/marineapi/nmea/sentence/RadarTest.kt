package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.AcquisitionType
import io.github.solcott.marineapi.nmea.AngleReference
import io.github.solcott.marineapi.nmea.Checksum
import io.github.solcott.marineapi.nmea.DataStatus
import io.github.solcott.marineapi.nmea.DisplayRotation
import io.github.solcott.marineapi.nmea.ParseResult
import io.github.solcott.marineapi.nmea.ReferenceSystem
import io.github.solcott.marineapi.nmea.SentenceRegistry
import io.github.solcott.marineapi.nmea.Side
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.TargetStatus
import io.github.solcott.marineapi.nmea.Units
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RotTest {

  @Test
  fun readsRateAndStatus() {
    // gpsd's worked example.
    val rot = parse<Rot>("\$HEROT,0.0,A*2B")
    assertEquals(0.0, rot.rateOfTurn)
    assertEquals(DataStatus.ACTIVE, rot.status)
  }

  @Test
  fun keepsTheSignThatMeansTurningToPort() {
    // The Java suite's EXAMPLE constant.
    val toPort = parse<Rot>(Checksum.append("\$HCROT,-0.3,A"))
    assertEquals(-0.3, toPort.rateOfTurn)
  }

  @Test
  fun reportsAnInvalidReadingRatherThanHidingIt() {
    // The Java suite's INVALID_EXAMPLE. The rate is still readable; the status says not to use it.
    val invalid = parse<Rot>(Checksum.append("\$HCROT,-0.3,V"))
    assertEquals(-0.3, invalid.rateOfTurn)
    assertEquals(DataStatus.VOID, invalid.status)
  }
}

class RsaTest {

  // The Java suite's EXAMPLE constant.
  private val rsa = parse<Rsa>(Checksum.append("\$IIRSA,1.2,A,2.3,V"))

  @Test
  fun readsBothSensors() {
    assertEquals(1.2, rsa.starboardAngle)
    assertEquals(DataStatus.ACTIVE, rsa.starboardStatus)
    assertEquals(2.3, rsa.portAngle)
    assertEquals(DataStatus.VOID, rsa.portStatus)
  }

  @Test
  fun looksUpASensorBySide() {
    assertEquals(1.2, rsa.angleOf(Side.STARBOARD))
    assertEquals(2.3, rsa.angleOf(Side.PORT))
    assertEquals(DataStatus.VOID, rsa.statusOf(Side.PORT))
  }

  @Test
  fun readsASingleRudderVessel() {
    // One rudder means the port pair is empty, not zero.
    val single = parse<Rsa>(Checksum.append("\$IIRSA,-4.5,A,,"))
    assertEquals(-4.5, single.starboardAngle, "negative turns the vessel to port")
    assertNull(single.portAngle)
    assertNull(single.portStatus)
  }
}

class OsdTest {

  // The Java suite's EXAMPLE constant.
  private val osd = parse<Osd>("\$RAOSD,35.1,A,36.0,P,10.2,P,15.3,0.1,N*41")

  @Test
  fun readsAllNineFields() {
    assertEquals(35.1, osd.heading)
    assertEquals(DataStatus.ACTIVE, osd.headingStatus)
    assertEquals(36.0, osd.course)
    assertEquals(ReferenceSystem.POSITIONING_SYSTEM_GROUND_REFERENCE, osd.courseReference)
    assertEquals(10.2, osd.speed)
    assertEquals(ReferenceSystem.POSITIONING_SYSTEM_GROUND_REFERENCE, osd.speedReference)
    assertEquals(15.3, osd.vesselSet)
    assertEquals(0.1, osd.vesselDrift)
    assertEquals(Units.NAUTICAL_MILES, osd.speedUnits)
  }

  @Test
  fun readsCourseAndSpeedFromDifferentSources() {
    // A radar can take course from one system and speed from another, so the two flags are read
    // separately rather than one standing in for both.
    val mixed = parse<Osd>(Checksum.append("\$RAOSD,35.1,A,36.0,B,10.2,W,15.3,0.1,K"))
    assertEquals(ReferenceSystem.BOTTOM_TRACKING_LOG, mixed.courseReference)
    assertEquals(ReferenceSystem.WATER_REFERENCED, mixed.speedReference)
    assertEquals(Units.KILOMETERS, mixed.speedUnits)
  }

  @Test
  fun rejectsAUnitThatIsNotASpeed() {
    val result =
      SentenceRegistry.Default.parse(Checksum.append("\$RAOSD,35.1,A,36.0,P,10.2,P,15.3,0.1,C"))
    val malformed = assertIs<ParseResult.Malformed>(result)
    assertTrue("one of [K, N, S]" in malformed.reason, malformed.reason)
  }
}

class RsdTest {

  // The Java suite's EXAMPLE constant.
  private val rsd = parse<Rsd>("\$RARSD,12,90,24,45,6,270,12,315,6.5,118,96,N,N*5A")

  @Test
  fun readsAllThirteenFields() {
    assertEquals(12.0, rsd.originOneRange)
    assertEquals(90.0, rsd.originOneBearing)
    assertEquals(24.0, rsd.variableRangeMarkerOne)
    assertEquals(45.0, rsd.bearingLineOne)
    assertEquals(6.0, rsd.originTwoRange)
    assertEquals(270.0, rsd.originTwoBearing)
    assertEquals(12.0, rsd.variableRangeMarkerTwo)
    assertEquals(315.0, rsd.bearingLineTwo)
    assertEquals(6.5, rsd.cursorRange)
    assertEquals(118.0, rsd.cursorBearing)
    assertEquals(96.0, rsd.rangeScale)
    assertEquals(Units.NAUTICAL_MILES, rsd.rangeUnits)
    assertEquals(DisplayRotation.NORTH_UP, rsd.displayRotation)
  }

  @Test
  fun readsTheOtherDisplayRotations() {
    val courseUp = parse<Rsd>(Checksum.append("\$RARSD,12,90,24,45,6,270,12,315,6.5,118,96,N,C"))
    assertEquals(DisplayRotation.COURSE_UP, courseUp.displayRotation)
    val headUp = parse<Rsd>(Checksum.append("\$RARSD,12,90,24,45,6,270,12,315,6.5,118,96,N,H"))
    assertEquals(DisplayRotation.HEAD_UP, headUp.displayRotation)
  }
}

class TtmTest {

  // The Java suite's EXAMPLE constant.
  private val ttm =
    parse<Ttm>("\$RATTM,11,25.3,13.7,T,7.0,20.0,T,10.1,20.2,N,NAME,Q,,175550.24,A*34")

  @Test
  fun readsAllFifteenFields() {
    assertEquals(11, ttm.number)
    assertEquals(25.3, ttm.distance)
    assertEquals(13.7, ttm.bearing)
    assertEquals(AngleReference.TRUE, ttm.bearingReference)
    assertEquals(7.0, ttm.speed)
    assertEquals(20.0, ttm.course)
    assertEquals(AngleReference.TRUE, ttm.courseReference)
    assertEquals(10.1, ttm.closestPointOfApproachDistance)
    assertEquals(20.2, ttm.timeToClosestPointOfApproach)
    assertEquals(Units.NAUTICAL_MILES, ttm.units)
    assertEquals("NAME", ttm.name)
    assertEquals(TargetStatus.QUERY, ttm.status)
    assertFalse(ttm.isReferenceTarget, "the reference field is empty")
    assertEquals(17, ttm.time?.hour)
    assertEquals(55, ttm.time?.minute)
    assertEquals(AcquisitionType.AUTO, ttm.acquisitionType)
  }

  @Test
  fun readsBearingAndCourseReferencesIndependently() {
    // A radar may report bearing relative to own heading while giving course as true.
    val mixed =
      parse<Ttm>(
        Checksum.append("\$RATTM,11,25.3,13.7,R,7.0,20.0,T,10.1,20.2,N,NAME,T,,175550.24,M")
      )
    assertEquals(AngleReference.RELATIVE, mixed.bearingReference)
    assertEquals(AngleReference.TRUE, mixed.courseReference)
    assertEquals(TargetStatus.TRACKING, mixed.status)
    assertEquals(AcquisitionType.MANUAL, mixed.acquisitionType)
  }

  @Test
  fun readsAnOpeningTarget() {
    // A negative time to closest approach means the target is already drawing away.
    val opening =
      parse<Ttm>(
        Checksum.append("\$RATTM,11,25.3,13.7,T,7.0,20.0,T,10.1,-4.5,N,NAME,T,,175550.24,A")
      )
    assertEquals(-4.5, opening.timeToClosestPointOfApproach)
  }

  @Test
  fun readsTheReferenceTargetFlag() {
    val reference =
      parse<Ttm>(
        Checksum.append("\$RATTM,11,25.3,13.7,T,7.0,20.0,T,10.1,20.2,N,NAME,T,R,175550.24,A")
      )
    assertTrue(reference.isReferenceTarget)
  }

  @Test
  fun readsALostTarget() {
    val lost =
      parse<Ttm>(
        Checksum.append("\$RATTM,11,25.3,13.7,T,7.0,20.0,T,10.1,20.2,N,NAME,L,,175550.24,A")
      )
    assertEquals(TargetStatus.LOST, lost.status)
  }
}

class TllTest {

  // The Java suite's EXAMPLE constant.
  private val tll = parse<Tll>("\$RATLL,01,3731.51205,N,02436.00000,E,ANDROS,163700.86,T,*25")

  @Test
  fun readsTheTargetAndItsPosition() {
    assertEquals(1, tll.number)
    assertEquals("ANDROS", tll.name)
    assertEquals(TargetStatus.TRACKING, tll.status)
    assertFalse(tll.isReferenceTarget)
    assertEquals(16, tll.time?.hour)
    assertEquals(37, tll.time?.minute)

    val position = tll.position
    assertTrue(position != null && abs(position.latitude - 37.52520083) < 1e-6, "$position")
    assertTrue(position != null && abs(position.longitude - 24.6) < 1e-6, "$position")
  }

  @Test
  fun readsTheReferenceTargetFlag() {
    val reference =
      parse<Tll>(Checksum.append("\$RATLL,01,3731.51205,N,02436.00000,E,ANDROS,163700.86,T,R"))
    assertTrue(reference.isReferenceTarget)
  }

  @Test
  fun rejectsAReferenceFieldThatIsNeitherRNorEmpty() {
    // The field has exactly one meaningful value, so anything else is a sentence to look at
    // rather than quietly treat as "not the reference".
    val result =
      SentenceRegistry.Default.parse(
        Checksum.append("\$RATLL,01,3731.51205,N,02436.00000,E,ANDROS,163700.86,T,X")
      )
    val malformed = assertIs<ParseResult.Malformed>(result)
    assertTrue("is not 'R' or empty" in malformed.reason, malformed.reason)
  }
}

class TlbTest {

  // The Java suite's EXAMPLE constant.
  private val tlb = parse<Tlb>("\$RATLB,1,SHIPONE,2,SHIPTWO,3,SHIPTHREE*3D")

  @Test
  fun readsEveryPair() {
    assertEquals(
      listOf(
        TargetLabel(1, "SHIPONE"),
        TargetLabel(2, "SHIPTWO"),
        TargetLabel(3, "SHIPTHREE"),
      ),
      tlb.labels,
    )
  }

  @Test
  fun readsANumberedTargetWithNoLabel() {
    val unlabelled = parse<Tlb>(Checksum.append("\$RATLB,1,SHIPONE,2,"))
    assertEquals(listOf(TargetLabel(1, "SHIPONE"), TargetLabel(2, null)), unlabelled.labels)
  }

  @Test
  fun rejectsAnOddFieldCount() {
    // Numbers and labels come in pairs; an odd count means one was lost in transmission.
    val result = SentenceRegistry.Default.parse(Checksum.append("\$RATLB,1,SHIPONE,2"))
    val malformed = assertIs<ParseResult.Malformed>(result)
    assertTrue("carries 3 fields" in malformed.reason, malformed.reason)
    assertTrue("come in pairs" in malformed.reason, malformed.reason)
  }

  @Test
  fun roundTripsExactly() {
    assertEquals("\$RATLB,1,SHIPONE,2,SHIPTWO,3,SHIPTHREE*3D", tlb.toNmeaString())
  }
}

class TargetSentenceCrossReferenceTest {

  @Test
  fun theThreeTargetSentencesShareATargetNumber() {
    // TTM gives a target's motion, TLL its position and TLB its label; the number is what ties
    // the three together, so it is read the same way in each.
    val ttm =
      parse<Ttm>(
        Checksum.append("\$RATTM,07,25.3,13.7,T,7.0,20.0,T,10.1,20.2,N,NAME,T,,175550.24,A")
      )
    val tll = parse<Tll>(Checksum.append("\$RATLL,07,3731.51205,N,02436.00000,E,NAME,163700.86,T,"))
    val tlb = parse<Tlb>(Checksum.append("\$RATLB,7,NAME"))

    assertEquals(7, ttm.number)
    assertEquals(7, tll.number)
    assertEquals(7, tlb.labels.single().number)
  }

  @Test
  fun theTargetNumberIsWrittenBackWithItsPadding() {
    // Target numbers are two digits in TTM and TLL, so 7 goes back out as 07.
    assertTrue("\$RATTM,07," in Ttm(TalkerId.RA, number = 7).toNmeaString())
    assertTrue("\$RATLL,07," in Tll(TalkerId.RA, number = 7).toNmeaString())
  }
}
