package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.Checksum
import io.github.solcott.marineapi.nmea.CompassPoint
import io.github.solcott.marineapi.nmea.DataStatus
import io.github.solcott.marineapi.nmea.FaaMode
import io.github.solcott.marineapi.nmea.GpsFixQuality
import io.github.solcott.marineapi.nmea.GpsFixStatus
import io.github.solcott.marineapi.nmea.NavStatus
import io.github.solcott.marineapi.nmea.ParseResult
import io.github.solcott.marineapi.nmea.SatelliteInfo
import io.github.solcott.marineapi.nmea.SentenceRegistry
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.Units
import io.github.solcott.marineapi.nmea.UnknownSentence
import io.github.solcott.marineapi.nmea.sentenceOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.UtcOffset

/**
 * Fixtures are the `EXAMPLE` constants of the Java suite's own tests, so these check the port
 * against what the implementation being replaced was verified with.
 */
private object Examples {
  const val GGA = "\$GPGGA,120044.567,6011.552,N,02501.941,E,1,00,2.0,28.0,M,19.6,M,,*63"
  const val GLL = "\$GPGLL,6011.552,N,02501.941,E,120045,A*26"
  const val RMC = "\$GPRMC,120044.567,A,6011.552,N,02501.941,E,000.0,360.0,160705,006.1,E,A,S*74"
  const val VTG = "\$GPVTG,360.0,T,348.7,M,16.89,N,31.28,K,A"
  const val GSA = "\$GPGSA,A,3,02,,,07,,09,24,26,,,,,1.6,1.6,1.0*3D"
  const val GSV = "\$GPGSV,3,2,12,15,56,182,51,17,38,163,47,18,63,058,50,21,53,329,47*73"
  const val ZDA = "\$GPZDA,032915,07,08,2004,00,00*4D"
}

class GgaTest {

  private val gga = parse<Gga>(Examples.GGA)

  @Test
  fun readsEveryField() {
    assertEquals(TalkerId.GP, gga.talker)
    assertEquals(LocalTime(12, 0, 44, 567_000_000), gga.time)
    assertEquals(60.0 + 11.552 / 60.0, gga.position!!.latitude, 1e-12)
    assertEquals(25.0 + 1.941 / 60.0, gga.position!!.longitude, 1e-12)
    assertEquals(GpsFixQuality.NORMAL, gga.fixQuality)
    assertEquals(0, gga.satelliteCount)
    assertEquals(2.0, gga.horizontalDilution)
    assertEquals(28.0, gga.altitude)
    assertEquals(Units.METER, gga.altitudeUnits)
    assertEquals(19.6, gga.geoidalHeight)
    assertEquals(Units.METER, gga.geoidalHeightUnits)
  }

  @Test
  fun emptyFieldsReadAsNull() {
    assertNull(gga.dgpsAge)
    assertNull(gga.dgpsStationId)
  }

  @Test
  fun altitudeIsNotFoldedIntoPosition() {
    // GGA reports altitude with its own unit field, so it stays a separate property.
    assertNull(gga.position!!.altitude)
    assertEquals(28.0, gga.altitude)
  }
}

class GllTest {

  private val gll = parse<Gll>(Examples.GLL)

  @Test
  fun readsEveryField() {
    assertEquals(60.0 + 11.552 / 60.0, gll.position!!.latitude, 1e-12)
    assertEquals(25.0 + 1.941 / 60.0, gll.position!!.longitude, 1e-12)
    assertEquals(LocalTime(12, 0, 45), gll.time)
    assertEquals(DataStatus.ACTIVE, gll.status)
    assertNull(gll.faaMode, "the fixture predates NMEA 2.3")
  }

  @Test
  fun readsFaaModeWhenPresent() {
    val withMode = parse<Gll>("\$GNGLL,4404.14012,N,12118.85993,W,001037.00,A,A*67")
    assertEquals(FaaMode.AUTOMATIC, withMode.faaMode)
    assertEquals(CompassPoint.WEST, withMode.position!!.longitudeHemisphere)
  }
}

class RmcTest {

  private val rmc = parse<Rmc>(Examples.RMC)

  @Test
  fun readsEveryField() {
    assertEquals(LocalTime(12, 0, 44, 567_000_000), rmc.time)
    assertEquals(DataStatus.ACTIVE, rmc.status)
    assertEquals(60.0 + 11.552 / 60.0, rmc.position!!.latitude, 1e-12)
    assertEquals(0.0, rmc.speedKnots)
    assertEquals(360.0, rmc.courseTrue)
    assertEquals(LocalDate(2005, 7, 16), rmc.date)
    assertEquals(FaaMode.AUTOMATIC, rmc.faaMode)
    assertEquals(NavStatus.SAFE, rmc.navStatus, "S is Safe here, not Simulator as in FaaMode")
  }

  @Test
  fun variationKeepsItsMagnitudeAndDirectionApart() {
    // The sentence carries two fields and so does the type; the signed form is derived.
    assertEquals(6.1, rmc.magneticVariation)
    assertEquals(CompassPoint.EAST, rmc.variationDirection)
    assertEquals(6.1, rmc.variationEastPositive)

    val west = parse<Rmc>(Checksum.append(Examples.RMC.replace(",006.1,E,", ",006.1,W,")))
    assertEquals(6.1, west.magneticVariation)
    assertEquals(CompassPoint.WEST, west.variationDirection)
    assertEquals(-6.1, west.variationEastPositive)
  }

  @Test
  fun aWesterlyZeroVariationStaysWesterly() {
    // Encoding the direction in the sign lost this case: -0.0 >= 0.0 is true in IEEE arithmetic,
    // so a westerly zero read back as easterly and re-encoded as "0.0,E".
    val line = "\$GPRMC,225446,A,4916.45,N,12311.12,W,000.5,054.7,191194,000.0,W*7B"
    val zeroWest = parse<Rmc>(line)
    assertEquals(0.0, zeroWest.magneticVariation)
    assertEquals(CompassPoint.WEST, zeroWest.variationDirection)
    assertTrue(zeroWest.toNmeaString().contains(",0.0,W,"), zeroWest.toNmeaString())
    assertEquals(zeroWest, SentenceRegistry.Default.parse(zeroWest.toNmeaString()).sentenceOrNull())
  }

  @Test
  fun readsSentenceWithNoFix() {
    val noFix = parse<Rmc>("\$GNRMC,001031.00,V,,,,,,,100117,,,N*66")
    assertNull(noFix.position)
    assertEquals(DataStatus.VOID, noFix.status)
    assertEquals(LocalDate(2017, 1, 10), noFix.date)
  }

  /**
   * The three worked examples from https://aprs.gids.nl/nmea/, checksums verified. Each states in
   * prose what its fields mean, so they pin the reading rather than just the parsing.
   */
  @Test
  fun matchesTheReferenceWorkedExamples() {
    val southernHemisphere =
      parse<Rmc>("\$GPRMC,081836,A,3751.65,S,14507.36,E,000.0,360.0,130998,011.3,E*62")
    assertEquals(CompassPoint.SOUTH, southernHemisphere.position!!.latitudeHemisphere)
    assertEquals(-(37.0 + 51.65 / 60.0), southernHemisphere.position!!.latitude, 1e-12)
    assertEquals(145.0 + 7.36 / 60.0, southernHemisphere.position!!.longitude, 1e-12)
    assertEquals(LocalDate(1998, 9, 13), southernHemisphere.date)
    assertEquals(11.3, southernHemisphere.magneticVariation)

    // "225446 Time of fix 22:54:46 UTC ... 4916.45,N Latitude 49 deg. 16.45 min North
    //  ... 191194 Date of fix 19 November 1994 ... 020.3,E Magnetic variation 20.3 deg East"
    val documented =
      parse<Rmc>("\$GPRMC,225446,A,4916.45,N,12311.12,W,000.5,054.7,191194,020.3,E*68")
    assertEquals(LocalTime(22, 54, 46), documented.time)
    assertEquals(49.0 + 16.45 / 60.0, documented.position!!.latitude, 1e-12)
    assertEquals(-(123.0 + 11.12 / 60.0), documented.position!!.longitude, 1e-12)
    assertEquals(LocalDate(1994, 11, 19), documented.date)
    assertEquals(0.5, documented.speedKnots)
    assertEquals(54.7, documented.courseTrue)
    assertEquals(20.3, documented.magneticVariation)

    val westerly = parse<Rmc>("\$GPRMC,220516,A,5133.82,N,00042.24,W,173.8,231.8,130694,004.2,W*70")
    assertEquals(4.2, westerly.magneticVariation)
    assertEquals(CompassPoint.WEST, westerly.variationDirection)
    assertEquals(173.8, westerly.speedKnots)
    assertEquals(231.8, westerly.courseTrue)
  }

  @Test
  fun theVariationSignMeansMagneticIsTrueMinusVariation() {
    // The reference puts it as "easterly variation subtracts from true course", so an easterly
    // variation must be positive for that subtraction to be the right one. This pins the meaning,
    // which a sign check alone would not.
    val easterly = parse<Rmc>("\$GPRMC,225446,A,4916.45,N,12311.12,W,000.5,054.7,191194,020.3,E*68")
    val magnetic = easterly.courseTrue!! - easterly.variationEastPositive!!
    assertEquals(54.7 - 20.3, magnetic, 1e-9)
    assertTrue(magnetic < easterly.courseTrue!!, "easterly variation must subtract")

    val westerly = parse<Rmc>("\$GPRMC,220516,A,5133.82,N,00042.24,W,173.8,231.8,130694,004.2,W*70")
    assertTrue(
      westerly.courseTrue!! - westerly.variationEastPositive!! > westerly.courseTrue!!,
      "westerly variation must add",
    )
  }

  @Test
  fun variationWithoutADirectionIsDroppedRatherThanSigned() {
    // An earlier revision failed the sentence here. It now drops the unusable pair and keeps the
    // fix, which is what a Saab R4 and a Motorola T805 in the sample logs need -- both put their
    // FAA mode in the direction field. Signing it by guessing would report a variation the device
    // never sent, so the pair goes rather than the sentence.
    val rmc =
      parse<Rmc>(Checksum.append("\$GPRMC,225446,A,4916.45,N,12311.12,W,000.5,054.7,191194,020.3,"))
    assertNull(rmc.magneticVariation, "020.3 with no E/W cannot be signed")
    assertNull(rmc.variationDirection)
    assertEquals(0.5, rmc.speedKnots, "everything else in the sentence survives")
    assertEquals(54.7, rmc.courseTrue)
    assertNotNull(rmc.position)
  }

  @Test
  fun readsTheSaabAndMotorolaVariationQuirk() {
    // Real lines from the gpsd corpus. Both receivers put an FAA mode where the variation
    // direction belongs; the Motorola also sends a variation magnitude of 0 alongside it.
    val saab = parse<Rmc>("\$GPRMC,130711.00,A,5012.790800,N,00806.879600,W,0.5,3.0,010611,,A*6A")
    assertNull(saab.magneticVariation)
    assertNull(saab.variationDirection)
    assertNotNull(saab.position)

    val motorola =
      parse<Rmc>("\$GPRMC,212614.879,A,4839.9488,N,00214.8863,E,0.56,344.41,181207,0,A*77")
    assertNull(
      motorola.magneticVariation,
      "a 0 magnitude with no usable direction is still unusable",
    )
    assertEquals(0.56, motorola.speedKnots)
  }

  @Test
  fun bothVariationFieldsEmptyIsFine() {
    // The common case: most receivers do not report variation at all.
    val none = parse<Rmc>("\$GNRMC,001031.00,A,4404.13993,N,12118.86023,W,0.146,,100117,,,A*7B")
    assertNull(none.magneticVariation)
    assertNull(none.variationDirection)
    assertEquals(0.146, none.speedKnots)
    assertNull(none.courseTrue)
  }
}

class VtgTest {

  private val vtg = parse<Vtg>(Examples.VTG)

  @Test
  fun readsEveryField() {
    assertEquals(360.0, vtg.courseTrue)
    assertEquals(348.7, vtg.courseMagnetic)
    assertEquals(16.89, vtg.speedKnots)
    assertEquals(31.28, vtg.speedKmh)
    assertEquals(FaaMode.AUTOMATIC, vtg.faaMode)
    assertTrue(!vtg.isLegacyFormat)
  }

  @Test
  fun readsThePre301Form() {
    // Four bare numbers, no T/M/N/K markers; told apart by field 1 not being 'T'.
    val legacy = parse<Vtg>("\$GPVTG,360.0,348.7,16.89,31.28")
    assertTrue(legacy.isLegacyFormat)
    assertEquals(360.0, legacy.courseTrue)
    assertEquals(348.7, legacy.courseMagnetic)
    assertEquals(16.89, legacy.speedKnots)
    assertEquals(31.28, legacy.speedKmh)
    assertNull(legacy.faaMode)
  }
}

class GsaTest {

  private val gsa = parse<Gsa>(Examples.GSA)

  @Test
  fun readsEveryField() {
    assertEquals(FixSelection.AUTOMATIC, gsa.selection)
    assertEquals(GpsFixStatus.GPS_3D, gsa.fixStatus)
    assertEquals(1.6, gsa.positionDop)
    assertEquals(1.6, gsa.horizontalDop)
    assertEquals(1.0, gsa.verticalDop)
  }

  @Test
  fun keepsTheSatelliteSlotsWhereTheyWere() {
    // "A,3,02,,,07,,09,24,26,,,," -- the gaps are channels, so compacting would move satellites
    // between them and rewrite the sentence.
    assertEquals(
      listOf("02", null, null, "07", null, "09", "24", "26", null, null, null, null),
      gsa.satelliteIds,
    )
    assertEquals(listOf("02", "07", "09", "24", "26"), gsa.satellitesUsed)
    assertEquals(Examples.GSA, gsa.toNmeaString())
  }

  @Test
  fun readsSystemIdFromNmea411() {
    val withSystemId = parse<Gsa>("\$GNGSA,A,3,80,71,73,79,69,,,,,,,,1.83,1.09,1.47,1*0A")
    assertEquals(1, withSystemId.systemId)
    assertNull(gsa.systemId, "older devices omit the field entirely")
  }
}

class GsvTest {

  private val gsv = parse<Gsv>(Examples.GSV)

  @Test
  fun readsGroupPosition() {
    assertEquals(3, gsv.sentenceCount)
    assertEquals(2, gsv.sentenceIndex)
    assertEquals(12, gsv.satellitesInView)
    assertTrue(!gsv.isFirst)
    assertTrue(!gsv.isLast)
  }

  @Test
  fun readsFourSatellites() {
    assertEquals(
      listOf(
        SatelliteInfo("15", elevation = 56, azimuth = 182, noise = 51),
        SatelliteInfo("17", elevation = 38, azimuth = 163, noise = 47),
        SatelliteInfo("18", elevation = 63, azimuth = 58, noise = 50),
        SatelliteInfo("21", elevation = 53, azimuth = 329, noise = 47),
      ),
      gsv.satellites,
    )
  }

  @Test
  fun keepsASatelliteThatHasNoSkyPositionYet() {
    // "02,,,26" is a satellite with a signal but no elevation or azimuth. Requiring those
    // dropped the satellite outright, losing what the receiver was reporting.
    val partial = parse<Gsv>("\$GPGSV,3,3,12,02,,,26,24,,,00,26,30,047,48,06,,,25*43")
    assertEquals(4, partial.satellites.size)
    assertEquals(
      SatelliteInfo("02", elevation = null, azimuth = null, noise = 26),
      partial.satellites[0],
    )
    assertEquals(listOf("02", "24", "26", "06"), partial.satellites.map { it.id })
  }

  @Test
  fun untrackedSatelliteHasNoNoiseRatherThanZero() {
    // The Java implementation substituted 0 dB here, which cannot be told from a real 0 reading.
    val untracked = parse<Gsv>("\$GLGSV,3,3,09,88,07,028,*51")
    assertEquals(1, untracked.satellites.size)
    assertNull(untracked.satellites[0].noise)
  }

  @Test
  fun readsShortFinalSentenceOfAGroup() {
    val last = parse<Gsv>("\$GPGSV,3,3,11,22,42,067,42,24,14,311,43,27,05,244,00,,,,*4D")
    assertEquals(3, last.satellites.size)
    assertTrue(last.isLast)
  }
}

class ZdaTest {

  private val zda = parse<Zda>(Examples.ZDA)

  @Test
  fun readsEveryField() {
    assertEquals(LocalTime(3, 29, 15), zda.time)
    assertEquals(LocalDate(2004, 8, 7), zda.date)
    assertEquals(UtcOffset(hours = 0), zda.localZoneOffset)
  }

  @Test
  fun yearIsFourDigitsSoNoPivotIsNeeded() {
    val old = parse<Zda>("\$GPZDA,160012.71,11,03,1904,-1,00*77")
    assertEquals(LocalDate(1904, 3, 11), old.date)
  }

  @Test
  fun zoneMinutesTakeTheSignOfTheHours() {
    val negative = parse<Zda>("\$GPZDA,160012.71,11,03,2004,-1,30*7E")
    assertEquals(UtcOffset(hours = -1, minutes = -30), negative.localZoneOffset)
  }

  @Test
  fun combinesDateAndTime() {
    assertEquals(LocalDate(2004, 8, 7), zda.dateTime!!.date)
    assertEquals(LocalTime(3, 29, 15), zda.dateTime!!.time)
  }
}

class SentenceRoundTripTest {

  private val examples =
    listOf(
      Examples.GGA,
      Examples.GLL,
      Examples.RMC,
      Examples.VTG,
      Examples.GSA,
      Examples.GSV,
      Examples.ZDA,
    )

  @Test
  fun reparsingRenderedOutputGivesAnEqualSentence() {
    // The guarantee is parse(render(s)) == s. Rendering does not reproduce the input byte for
    // byte, because decoded values are re-formatted: "000.0" comes back as "0.0".
    for (line in examples) {
      val sentence = SentenceRegistry.Default.parse(line).sentenceOrNull()!!
      val rendered = sentence.toNmeaString()
      val reparsed = SentenceRegistry.Default.parse(rendered).sentenceOrNull()
      assertEquals(sentence, reparsed, "round trip of $line via $rendered")
    }
  }

  @Test
  fun renderedOutputIsAValidSentence() {
    for (line in examples) {
      val rendered = SentenceRegistry.Default.parse(line).sentenceOrNull()!!.toNmeaString()
      assertIs<ParseResult.Ok>(SentenceRegistry.Default.parse(rendered), "invalid: $rendered")
      assertTrue(rendered.startsWith("\$GP"), rendered)
      assertTrue(rendered.length <= 82, "over the 82 byte limit: $rendered")
    }
  }

  @Test
  fun unportedTypesStillParseAsUnknown() {
    assertIs<UnknownSentence>(
      SentenceRegistry.Default.parse("!AIVDM,1,1,,A,13aEOK?P00PD2wVMdLDRhgvL289?,0*26")
        .sentenceOrNull()
    )
  }
}
