package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.Checksum
import io.github.solcott.marineapi.nmea.DataStatus
import io.github.solcott.marineapi.nmea.Direction
import io.github.solcott.marineapi.nmea.ParseResult
import io.github.solcott.marineapi.nmea.SentenceRegistry
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.Units
import io.github.solcott.marineapi.nmea.sentenceOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VhwTest {

  @Test
  fun readsHeadingsAndSpeeds() {
    val vhw = parse<Vhw>(Checksum.append("\$IIVHW,240.5,T,234.7,M,4.9,N,9.1,K"))
    assertEquals(TalkerId.II, vhw.talker)
    assertEquals(240.5, vhw.headingTrue)
    assertEquals(234.7, vhw.headingMagnetic)
    assertEquals(4.9, vhw.speedKnots)
    assertEquals(9.1, vhw.speedKmh)
  }

  @Test
  fun readsARealLineWithTheMarkersOmitted() {
    // Straight out of sample1.txt. This device drops the T and K markers when their values are
    // empty -- while keeping T in an MWD with an empty value in the same capture. The markers
    // carry no data, so they are not read.
    val vhw = parse<Vhw>("\$IIVHW,,,347,M,0.00,N,,*64")
    assertNull(vhw.headingTrue)
    assertEquals(347.0, vhw.headingMagnetic)
    assertEquals(0.0, vhw.speedKnots)
    assertNull(vhw.speedKmh)
  }
}

class VlwTest {

  @Test
  fun readsTheWaterDistancesOfAPreNmea3Sentence() {
    // Straight out of sample1.txt: four fields, no ground pair.
    val vlw = parse<Vlw>("\$IIVLW,1958.64,N,1958.64,N*4D")
    assertEquals(1958.64, vlw.totalWaterDistance)
    assertEquals(Units.NAUTICAL_MILES, vlw.totalWaterUnits)
    assertEquals(1958.64, vlw.tripWaterDistance)
    assertEquals(Units.NAUTICAL_MILES, vlw.tripWaterUnits)
    assertNull(vlw.totalGroundDistance, "the ground pair arrived in NMEA 3")
    assertNull(vlw.tripGroundDistance)
  }

  @Test
  fun readsTheNmea3GroundDistancesTheJavaImplementationCouldNotSee() {
    // The parser this replaces declared four fields, so these two values were unreachable.
    val vlw = parse<Vlw>(Checksum.append("\$IIVLW,1958.64,N,365.2,N,2011.3,N,401.7,N"))
    assertEquals(2011.3, vlw.totalGroundDistance)
    assertEquals(Units.NAUTICAL_MILES, vlw.totalGroundUnits)
    assertEquals(401.7, vlw.tripGroundDistance)
    assertEquals(Units.NAUTICAL_MILES, vlw.tripGroundUnits)
  }

  @Test
  fun doesNotInventAGroundPairThatWasNotThere() {
    // Writing four empty fields onto a pre-NMEA 3 sentence would claim the device reports a
    // ground log it does not have.
    assertEquals(
      "\$IIVLW,1958.64,N,1958.64,N*4D",
      parse<Vlw>("\$IIVLW,1958.64,N,1958.64,N*4D").toNmeaString(),
    )
  }

  @Test
  fun acceptsKilometres() {
    // Documented as N throughout, but the implementation this replaces accepted K.
    assertEquals(
      Units.KILOMETERS,
      parse<Vlw>(Checksum.append("\$IIVLW,3627.0,K,676.4,K")).totalWaterUnits,
    )
  }

  @Test
  fun rejectsAUnitThatIsNotADistance() {
    val result = SentenceRegistry.Default.parse(Checksum.append("\$IIVLW,1958.64,C,1958.64,N"))
    val malformed = assertIs<ParseResult.Malformed>(result)
    assertTrue("one of [N, K]" in malformed.reason, malformed.reason)
  }
}

class VbwTest {

  private val vbw = parse<Vbw>(Checksum.append("\$IIVBW,11.0,02.0,A,10.0,03.0,A,05.3,A,01.0,A"))

  @Test
  fun readsAllTenFields() {
    assertEquals(11.0, vbw.longitudinalWaterSpeed)
    assertEquals(2.0, vbw.transverseWaterSpeed)
    assertEquals(DataStatus.ACTIVE, vbw.waterSpeedStatus)
    assertEquals(10.0, vbw.longitudinalGroundSpeed)
    assertEquals(3.0, vbw.transverseGroundSpeed)
    assertEquals(DataStatus.ACTIVE, vbw.groundSpeedStatus)
    assertEquals(5.3, vbw.sternTransverseWaterSpeed)
    assertEquals(DataStatus.ACTIVE, vbw.sternWaterSpeedStatus)
    assertEquals(1.0, vbw.sternTransverseGroundSpeed)
    assertEquals(DataStatus.ACTIVE, vbw.sternGroundSpeedStatus)
  }

  @Test
  fun keepsTheSignThatMeansAsternOrToPort() {
    val backing = parse<Vbw>(Checksum.append("\$IIVBW,-1.5,-0.4,A,-1.2,-0.3,A,,V,,V"))
    assertEquals(-1.5, backing.longitudinalWaterSpeed, "negative longitudinal is astern")
    assertEquals(-0.4, backing.transverseWaterSpeed, "negative transverse is to port")
    assertEquals(DataStatus.VOID, backing.sternWaterSpeedStatus)
  }

  @Test
  fun reportsEachSensorSeparately() {
    // A working water log with a failed ground sensor is a real state, not a malformed sentence.
    val partial = parse<Vbw>(Checksum.append("\$IIVBW,11.0,02.0,A,,,V,,V,,V"))
    assertEquals(DataStatus.ACTIVE, partial.waterSpeedStatus)
    assertEquals(DataStatus.VOID, partial.groundSpeedStatus)
    assertNull(partial.longitudinalGroundSpeed)
  }
}

class VdrTest {

  @Test
  fun readsSetAndDrift() {
    val vdr = parse<Vdr>(Checksum.append("\$IIVDR,10.0,T,12.0,M,1.5,N"))
    assertEquals(10.0, vdr.directionTrue)
    assertEquals(12.0, vdr.directionMagnetic)
    assertEquals(1.5, vdr.speedKnots)
  }

  @Test
  fun writesTheFixedMarkers() {
    assertEquals(
      Checksum.append("\$IIVDR,10.0,T,12.0,M,1.5,N"),
      Vdr(TalkerId.II, 10.0, 12.0, 1.5).toNmeaString(),
    )
  }
}

class VpwTest {

  @Test
  fun readsARealLineTheJavaImplementationHadNoParserFor() {
    // Straight out of sample1.txt. There is no VPWParser in the tree this replaces, so these
    // three lines were read as an unrecognised sentence.
    val vpw = parse<Vpw>("\$IIVPW,00.00,N,,*31")
    assertEquals(0.0, vpw.speedKnots)
    assertNull(vpw.speedMetersPerSecond)
  }

  @Test
  fun keepsTheSignThatMeansDownwind() {
    val downwind = parse<Vpw>(Checksum.append("\$IIVPW,-4.5,N,-2.3,M"))
    assertEquals(-4.5, downwind.speedKnots)
    assertEquals(-2.3, downwind.speedMetersPerSecond)
  }
}

class VwrVwtTest {

  @Test
  fun readsApparentWindFromARealLine() {
    val vwr = parse<Vwr>("\$IIVWR,088,L,24.5,N,12.6,M,,*2A")
    assertEquals(88.0, vwr.windAngle)
    assertEquals(Direction.LEFT, vwr.side, "L is off the port bow")
    assertEquals(24.5, vwr.speedKnots)
    assertEquals(12.6, vwr.speedMetersPerSecond)
    assertNull(vwr.speedKmh)
  }

  @Test
  fun readsTrueWindFromARealLine() {
    val vwt = parse<Vwt>("\$IIVWT,088,L,24.7,N,12.6,M,,*2E")
    assertEquals(88.0, vwt.windAngle)
    assertEquals(Direction.LEFT, vwt.side)
    assertEquals(24.7, vwt.speedKnots)
  }

  @Test
  fun theTwoAreDistinctTypes() {
    // Same eight fields, but apparent and true wind are not interchangeable.
    assertIs<Vwr>(
      SentenceRegistry.Default.parse("\$IIVWR,088,L,24.5,N,12.6,M,,*2A").sentenceOrNull()
    )
    assertIs<Vwt>(
      SentenceRegistry.Default.parse("\$IIVWT,088,L,24.7,N,12.6,M,,*2E").sentenceOrNull()
    )
  }

  @Test
  fun readsStarboardWind() {
    assertEquals(
      Direction.RIGHT,
      parse<Vwr>(Checksum.append("\$IIVWR,032,R,12.0,N,6.2,M,22.2,K")).side,
    )
  }

  @Test
  fun theMisnamedJavaGetterReturnedTheMetresPerSecondSpeed() {
    // VWRParser.getTrueCourse() and VWTParser.getTrueCourse() both returned field 4, which is the
    // m/s wind speed and has nothing to do with course. Here it is named for what it holds.
    val vwr = parse<Vwr>(Checksum.append("\$IIVWR,032,R,12.0,N,6.2,M,22.2,K"))
    assertEquals(6.2, vwr.speedMetersPerSecond)
  }
}
