package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.Checksum
import io.github.solcott.marineapi.nmea.Measurement
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

class MtaTest {

  @Test
  fun readsARealAirTemperature() {
    // Straight out of sample1.txt.
    assertEquals(17.9, parse<Mta>("\$IIMTA,17.9,C*0A").temperature)
  }

  @Test
  fun isNotTheWaterTemperatureSentence() {
    // MTA is air, MTW is water. Both appear in the same capture, one letter apart, and reading
    // one as the other would put the air temperature into a sea-surface reading.
    assertIs<Mta>(SentenceRegistry.Default.parse("\$IIMTA,39.4,C*0B").sentenceOrNull())
    assertIs<Mtw>(SentenceRegistry.Default.parse("\$IIMTW,016,C*3A").sentenceOrNull())
  }

  @Test
  fun readsSubZeroAir() {
    assertEquals(-8.5, parse<Mta>(Checksum.append("\$IIMTA,-8.5,C")).temperature)
  }
}

class MmbTest {

  // The Java suite's EXAMPLE constant.
  private val mmb = parse<Mmb>("\$IIMMB,29.9870,I,1.0154,B*75")

  @Test
  fun readsBothPressures() {
    assertEquals(29.9870, mmb.inchesOfMercury)
    assertEquals(1.0154, mmb.bars)
  }

  @Test
  fun roundTripsItsValue() {
    // Not the exact text: the sentence carries 29.9870 and this writes 29.987, because a numeric
    // field is rendered with its trailing zeros dropped. The pressure is unchanged.
    assertEquals("\$IIMMB,29.987,I,1.0154,B*45", mmb.toNmeaString())
    assertEquals(mmb, SentenceRegistry.Default.parse(mmb.toNmeaString()).sentenceOrNull())
  }
}

class MhuTest {

  // The Java suite's EXAMPLE constant.
  private val mhu = parse<Mhu>(Checksum.append("\$IIMHU,66.0,5.0,3.0,C"))

  @Test
  fun readsHumidityAndDewPoint() {
    assertEquals(66.0, mhu.relativeHumidity)
    assertEquals(5.0, mhu.absoluteHumidity)
    assertEquals(3.0, mhu.dewPoint)
  }

  @Test
  fun readsASentenceWithOnlyRelativeHumidity() {
    val partial = parse<Mhu>(Checksum.append("\$IIMHU,66.0,,,C"))
    assertEquals(66.0, partial.relativeHumidity)
    assertNull(partial.absoluteHumidity)
    assertNull(partial.dewPoint)
  }
}

class MdaTest {

  // The Java suite's EXAMPLE constant, and the reason the pressure unit is read rather than
  // assumed: it says P for pascals where the format's table says inches of mercury.
  private val mda =
    parse<Mda>("\$IIMDA,99700.0,P,1.00,B,3.2,C,,C,,,,C,295.19,T,,M,5.70,N,2.93,M*08")

  @Test
  fun readsThePressureAndItsUnit() {
    assertEquals(99700.0, mda.primaryPressure)
    assertEquals(Units.PASCAL, mda.primaryPressureUnits)
    assertEquals(1.00, mda.secondaryPressureBars)
  }

  @Test
  fun readsTheRestOfTheValuesItCarries() {
    assertEquals(3.2, mda.airTemperature)
    assertNull(mda.waterTemperature, "this station leaves the water temperature blank")
    assertNull(mda.relativeHumidity)
    assertNull(mda.absoluteHumidity)
    assertNull(mda.dewPoint)
    assertEquals(295.19, mda.windDirectionTrue)
    assertNull(mda.windDirectionMagnetic)
    assertEquals(5.70, mda.windSpeedKnots)
    assertEquals(2.93, mda.windSpeedMetersPerSecond)
  }

  @Test
  fun readsInchesOfMercuryToo() {
    val inches =
      parse<Mda>(
        Checksum.append(
          "\$IIMDA,29.9,I,1.01,B,3.2,C,4.1,C,66.0,5.0,3.0,C,295.19,T,301.2,M,5.70,N,2.93,M"
        )
      )
    assertEquals(Units.INCHES, inches.primaryPressureUnits)
    assertEquals(4.1, inches.waterTemperature)
    assertEquals(66.0, inches.relativeHumidity)
    assertEquals(301.2, inches.windDirectionMagnetic)
  }

  @Test
  fun writesKnotsNotKilometresForTheWindSpeedUnit() {
    // The implementation this replaces wrote K in field 17, where the format says N. A sentence
    // it built therefore labelled a knots value as kilometres.
    val rendered =
      Mda(TalkerId.II, windSpeedKnots = 5.7, windSpeedMetersPerSecond = 2.93).toNmeaString()
    assertTrue(rendered.contains("5.7,N,2.93,M"), rendered)
  }

  @Test
  fun rejectsAPressureUnitThatIsNeitherInchesNorPascals() {
    val result =
      SentenceRegistry.Default.parse(
        Checksum.append("\$IIMDA,29.9,C,1.01,B,3.2,C,,C,,,,C,295.19,T,,M,5.70,N,2.93,M")
      )
    val malformed = assertIs<ParseResult.Malformed>(result)
    assertTrue("one of [I, P]" in malformed.reason, malformed.reason)
  }
}

class XdrTest {

  @Test
  fun readsASingleTransducer() {
    // The Java suite's EXAMPLE constant.
    val xdr = parse<Xdr>(Checksum.append("\$IIXDR,P,1.02481,B,Barometer"))
    assertEquals(
      listOf(Measurement(type = "P", value = 1.02481, units = "B", name = "Barometer")),
      xdr.measurements,
    )
  }

  @Test
  fun readsEveryQuadrupleOfGpsdsWorkedExample() {
    val xdr =
      parse<Xdr>("\$HCXDR,A,171,D,PITCH,A,-37,D,ROLL,G,367,,MAGX,G,2420,,MAGY,G,-8984,,MAGZ*41")
    assertEquals(5, xdr.measurements.size)
    assertEquals(Measurement("A", 171.0, "D", "PITCH"), xdr.measurements[0])
    assertEquals(Measurement("A", -37.0, "D", "ROLL"), xdr.measurements[1])
    // The magnetometer readings carry no unit at all, which is legal and must not be invented.
    assertEquals(Measurement("G", 367.0, null, "MAGX"), xdr.measurements[2])
    assertEquals(Measurement("G", -8984.0, null, "MAGZ"), xdr.measurements[4])
  }

  @Test
  fun keepsUnitsAsRawTextBecauseTheLettersAreAmbiguous() {
    // B is bars here and binary elsewhere; P is pascals here and percent of full range
    // elsewhere. Only the transducer type says which, so no enum is imposed.
    val xdr = parse<Xdr>(Checksum.append("\$IIXDR,P,1.02481,B,Barometer,C,21.5,C,AirTemp"))
    assertEquals("B", xdr.measurements[0].units)
    assertEquals("C", xdr.measurements[1].units)
  }

  @Test
  fun roundTripsItsValues() {
    // Transducer values are decimals in the format, and a numeric field always keeps one, so the
    // whole numbers a magnetometer reports come back as 171.0 rather than 171. Every value, the
    // empty units and the names survive, which is what the sentence actually carries.
    val line = "\$HCXDR,A,171,D,PITCH,A,-37,D,ROLL,G,367,,MAGX,G,2420,,MAGY,G,-8984,,MAGZ*41"
    val xdr = parse<Xdr>(line)
    val rendered = xdr.toNmeaString()
    assertTrue(rendered.startsWith("\$HCXDR,A,171.0,D,PITCH,"), rendered)
    assertEquals(xdr, SentenceRegistry.Default.parse(rendered).sentenceOrNull())
  }

  @Test
  fun rejectsATruncatedQuadruple() {
    val result = SentenceRegistry.Default.parse(Checksum.append("\$IIXDR,P,1.02481,B"))
    val malformed = assertIs<ParseResult.Malformed>(result)
    assertTrue("carries 3 fields" in malformed.reason, malformed.reason)
    assertTrue("truncated" in malformed.reason, malformed.reason)
  }

  @Test
  fun reportsAValueThatIsNotANumberAgainstItsOwnField() {
    // Indices rather than chunking, so the message names the field the bad value came from.
    val result = SentenceRegistry.Default.parse(Checksum.append("\$IIXDR,P,abc,B,Barometer"))
    val malformed = assertIs<ParseResult.Malformed>(result)
    assertTrue("field 1" in malformed.reason, malformed.reason)
  }
}
