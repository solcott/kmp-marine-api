package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.AngleReference
import io.github.solcott.marineapi.nmea.Checksum
import io.github.solcott.marineapi.nmea.CompassPoint
import io.github.solcott.marineapi.nmea.DataStatus
import io.github.solcott.marineapi.nmea.ParseResult
import io.github.solcott.marineapi.nmea.SentenceRegistry
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.Units
import io.github.solcott.marineapi.nmea.sentenceOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MwvTest {

  // The EXAMPLE constant of the Java suite's MWVTest.
  private val mwv = parse<Mwv>("\$IIMWV,125.1,T,5.5,M,A")

  @Test
  fun readsEveryField() {
    assertEquals(TalkerId.II, mwv.talker)
    assertEquals(125.1, mwv.windAngle)
    assertEquals(AngleReference.TRUE, mwv.reference)
    assertEquals(5.5, mwv.windSpeed)
    assertEquals(Units.METER, mwv.speedUnits)
    assertEquals(DataStatus.ACTIVE, mwv.status)
  }

  @Test
  fun readsRelativeWind() {
    // R is what a masthead vane reports before correcting for the vessel's own motion.
    assertEquals(AngleReference.RELATIVE, parse<Mwv>("\$IIMWV,125.1,R,5.5,N,A").reference)
  }

  @Test
  fun rejectsAnUnknownReference() {
    val result = SentenceRegistry.Default.parse(Checksum.append("\$IIMWV,125.1,X,5.5,M,A"))
    val malformed = assertIs<ParseResult.Malformed>(result)
    assertTrue("one of [R, T]" in malformed.reason, malformed.reason)
  }
}

class MwdTest {

  // gpsd's worked example.
  private val mwd = parse<Mwd>("\$WIMWD,302.4,T,289.6,M,10.5,N,5.4,M*6F")

  @Test
  fun readsBothReferencesAndBothUnits() {
    assertEquals(302.4, mwd.directionTrue)
    assertEquals(289.6, mwd.directionMagnetic)
    assertEquals(10.5, mwd.speedKnots)
    assertEquals(5.4, mwd.speedMetersPerSecond)
  }
}

class DbtTest {

  // gpsd's worked example.
  private val dbt = parse<Dbt>("\$SDDBT,7.8,f,2.4,M,1.3,F*0D")

  @Test
  fun readsAllThreeConversions() {
    assertEquals(7.8, dbt.depthFeet)
    assertEquals(2.4, dbt.depthMeters)
    assertEquals(1.3, dbt.depthFathoms)
  }

  @Test
  fun readsSentenceWithOnlyOneConversion() {
    // gpsd: "sometimes not all three conversions are reported".
    val metersOnly = parse<Dbt>(Checksum.append("\$SDDBT,,f,22.5,M,,F"))
    assertNull(metersOnly.depthFeet)
    assertEquals(22.5, metersOnly.depthMeters)
    assertNull(metersOnly.depthFathoms)
  }
}

class DptTest {

  // gpsd's worked example.
  private val dpt = parse<Dpt>("\$INDPT,2.3,0.0*46")

  @Test
  fun readsDepthAndOffset() {
    assertEquals(2.3, dpt.depth)
    assertEquals(0.0, dpt.offset)
    assertNull(dpt.maximumRange, "the maximum range field arrived in NMEA 3.0")
  }

  @Test
  fun readsTheNmea30MaximumRange() {
    assertEquals(200.0, parse<Dpt>(Checksum.append("\$INDPT,2.3,0.5,200.0")).maximumRange)
  }
}

class MtwTest {

  @Test
  fun readsTemperature() {
    // gpsd's worked example.
    assertEquals(17.9, parse<Mtw>("\$INMTW,17.9,C*1B").temperature)
  }

  @Test
  fun readsSubZeroTemperature() {
    assertEquals(-1.5, parse<Mtw>(Checksum.append("\$INMTW,-1.5,C")).temperature)
  }
}

class HdgTest {

  private val hdg = parse<Hdg>(Checksum.append("\$HCHDG,123.4,1.2,E,4.8,W"))

  @Test
  fun readsHeadingDeviationAndVariation() {
    assertEquals(123.4, hdg.heading)
    assertEquals(1.2, hdg.deviation)
    assertEquals(CompassPoint.EAST, hdg.deviationDirection)
    assertEquals(4.8, hdg.variation)
    assertEquals(CompassPoint.WEST, hdg.variationDirection)
  }

  @Test
  fun signsBothEastPositive() {
    assertEquals(1.2, hdg.deviationEastPositive)
    assertEquals(-4.8, hdg.variationEastPositive)
  }

  @Test
  fun aWesterlyZeroStaysWesterly() {
    // The Java implementation returned zero unsigned, losing the direction entirely, and used the
    // opposite sign convention here from the one it used in RMC.
    val zero = parse<Hdg>(Checksum.append("\$HCHDG,123.4,0.0,W,0.0,W"))
    assertEquals(CompassPoint.WEST, zero.deviationDirection)
    assertEquals(CompassPoint.WEST, zero.variationDirection)
    assertEquals(zero, SentenceRegistry.Default.parse(zero.toNmeaString()).sentenceOrNull())
  }

  @Test
  fun magnitudeWithoutDirectionIsMalformed() {
    val result = SentenceRegistry.Default.parse(Checksum.append("\$HCHDG,123.4,1.2,,4.8,W"))
    val malformed = assertIs<ParseResult.Malformed>(result)
    assertTrue("no E/W direction" in malformed.reason, malformed.reason)
  }

  @Test
  fun rejectsInconsistentConstruction() {
    assertFailsWith<IllegalArgumentException> {
      Hdg(TalkerId.HC, deviation = -1.0, deviationDirection = CompassPoint.EAST)
    }
    assertFailsWith<IllegalArgumentException> { Hdg(TalkerId.HC, deviation = 1.0) }
    assertFailsWith<IllegalArgumentException> {
      Hdg(TalkerId.HC, variation = 1.0, variationDirection = CompassPoint.NORTH)
    }
  }

  @Test
  fun readsSentenceWithNoCorrections() {
    val bare = parse<Hdg>(Checksum.append("\$HCHDG,123.4,,,,"))
    assertEquals(123.4, bare.heading)
    assertNull(bare.deviation)
    assertNull(bare.variation)
  }
}

class HdmHdtTest {

  @Test
  fun readsMagneticHeading() {
    assertEquals(123.4, parse<Hdm>(Checksum.append("\$HCHDM,123.4,M")).heading)
  }

  @Test
  fun readsTrueHeading() {
    // gpsd's worked example.
    assertEquals(274.07, parse<Hdt>("\$GPHDT,274.07,T*03").heading)
  }

  @Test
  fun theTwoAreDistinctTypes() {
    val magnetic =
      SentenceRegistry.Default.parse(Checksum.append("\$HCHDM,123.4,M")).sentenceOrNull()
    val trueHeading = SentenceRegistry.Default.parse("\$GPHDT,274.07,T*03").sentenceOrNull()
    assertIs<Hdm>(magnetic)
    assertIs<Hdt>(trueHeading)
  }
}
