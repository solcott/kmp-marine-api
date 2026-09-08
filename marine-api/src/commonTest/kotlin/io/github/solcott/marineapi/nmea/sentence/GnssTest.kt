package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.Checksum
import io.github.solcott.marineapi.nmea.CompassPoint
import io.github.solcott.marineapi.nmea.FaaMode
import io.github.solcott.marineapi.nmea.NavStatus
import io.github.solcott.marineapi.nmea.SentenceRegistry
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.sentenceOrNull
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GnsTest {

  // A real line from the gpsd corpus: five constellations, thirteen fields.
  private val gns =
    parse<Gns>("\$GNGNS,043539.00,4333.190206,N,00127.618380,E,AAAAN,14,1.3,250.8,50.0,,,V*62")

  @Test
  fun readsEveryField() {
    assertEquals(TalkerId.GN, gns.talker)
    assertEquals(4, gns.time?.hour)
    assertEquals(35, gns.time?.minute)
    assertEquals("AAAAN", gns.modeIndicator)
    assertEquals(14, gns.satelliteCount)
    assertEquals(1.3, gns.horizontalDilution)
    assertEquals(250.8, gns.altitude)
    assertEquals(50.0, gns.geoidalSeparation)
    assertNull(gns.dgpsAge)
    assertNull(gns.dgpsStationId)
    assertNotNull(gns.position)
    assertTrue(abs(gns.position.latitude - 43.55317010) < 1e-6, "${gns.position}")
  }

  @Test
  fun readsTheNavStatusTheJavaImplementationCouldNotSee() {
    // The parser this replaces declared twelve fields and stopped one short.
    assertEquals(NavStatus.NOT_VALID, gns.navStatus)
  }

  @Test
  fun decodesOneModePerConstellation() {
    assertEquals(
      listOf(
        FaaMode.AUTOMATIC,
        FaaMode.AUTOMATIC,
        FaaMode.AUTOMATIC,
        FaaMode.AUTOMATIC,
        FaaMode.NONE,
      ),
      gns.modes,
    )
  }

  @Test
  fun acceptsIndicatorsOfAnyLength() {
    // The corpus holds two, three, four and five character indicators from different receivers,
    // so nothing here assumes a fixed count or a fixed constellation order.
    val twoSystems =
      parse<Gns>("\$GNGNS,014035.00,4332.69262,S,17235.48549,E,RR,13,0.9,25.63,11.24,,*70")
    assertEquals("RR", twoSystems.modeIndicator)
    assertEquals(listOf(FaaMode.RTK_FIXED, FaaMode.RTK_FIXED), twoSystems.modes)
    assertEquals(CompassPoint.SOUTH, twoSystems.position?.latitudeHemisphere)
    assertNull(twoSystems.navStatus, "this one has only twelve fields")

    val threeSystems =
      parse<Gns>(Checksum.append("\$GNGNS,112257.00,3844.24011,N,00908.43828,W,NDD,03,10.5,,"))
    assertEquals(listOf(FaaMode.NONE, FaaMode.DGPS, FaaMode.DGPS), threeSystems.modes)
  }

  @Test
  fun keepsAnIndicatorItCannotFullyDecode() {
    // An unknown constellation code leaves that one entry null; the raw string survives intact so
    // the sentence still re-encodes to itself.
    val line =
      Checksum.append("\$GNGNS,043539.00,4333.190206,N,00127.618380,E,AAZ,14,1.3,250.8,50.0,,,V")
    val odd = parse<Gns>(line)
    assertEquals("AAZ", odd.modeIndicator)
    assertEquals(listOf(FaaMode.AUTOMATIC, FaaMode.AUTOMATIC, null), odd.modes)
    // Value round trip, not text: 043539.00 renders as 043539 and the coordinate loses a
    // trailing zero, neither of which changes what the sentence says.
    assertEquals(odd, SentenceRegistry.Default.parse(odd.toNmeaString()).sentenceOrNull())
  }
}

/** Fields in a rendered sentence, excluding the tag and the checksum. */
private fun fieldCountOf(sentence: String): Int =
  sentence.substringBefore('*').substringAfter(',').split(',').size

class GstTest {

  // gpsd's worked example, which is also close to the Java suite's EXAMPLE.
  private val gst = parse<Gst>("\$GPGST,182141.000,15.5,15.3,7.2,21.8,0.9,0.5,0.8*54")

  @Test
  fun readsTheErrorEllipse() {
    assertEquals(15.5, gst.rmsResidual)
    assertEquals(15.3, gst.semiMajorError)
    assertEquals(7.2, gst.semiMinorError)
    assertEquals(21.8, gst.errorEllipseOrientation)
    assertEquals(0.9, gst.latitudeError)
    assertEquals(0.5, gst.longitudeError)
    assertEquals(0.8, gst.altitudeError)
    assertEquals(18, gst.time?.hour)
  }

  @Test
  fun readsARealSentenceWithMostFieldsEmpty() {
    // From the corpus: this receiver reports the RMS residual and the three axis errors only.
    val sparse = parse<Gst>("\$GNGST,003956.00,235111,,,,2.3,3.5,4.0*46")
    assertEquals(235111.0, sparse.rmsResidual)
    assertNull(sparse.semiMajorError)
    assertEquals(2.3, sparse.latitudeError)
    assertEquals(4.0, sparse.altitudeError)
  }
}

class GbsTest {

  @Test
  fun readsTheErrorEstimatesWithNoFaultedSatellite() {
    // A real u-blox line: it reports the 1-sigma errors but has singled out no satellite.
    val gbs = parse<Gbs>("\$GNGBS,003956.00,2.3,3.5,4.0,,,,,,*55")
    assertEquals(2.3, gbs.latitudeError)
    assertEquals(3.5, gbs.longitudeError)
    assertEquals(4.0, gbs.altitudeError)
    assertNull(gbs.satelliteId)
    assertNull(gbs.probability)
  }

  @Test
  fun readsTheNmea410FieldsTheJavaImplementationCouldNotSee() {
    // The parser this replaces declared eight fields; u-blox sends ten.
    val gbs = parse<Gbs>(Checksum.append("\$GPGBS,125027.00,23.43,13.91,34.01,12,0.5,1.2,0.3,1,1"))
    assertEquals("12", gbs.satelliteId)
    assertEquals(0.5, gbs.probability)
    assertEquals(1.2, gbs.bias)
    assertEquals(0.3, gbs.biasStandardDeviation)
    assertEquals(1, gbs.systemId)
    assertEquals(1, gbs.signalId)
  }

  @Test
  fun doesNotInventTheNmea410FieldsThatWereNotThere() {
    // Eight fields in, eight fields out. Appending two empties would claim the receiver named a
    // constellation it never mentioned. Not an exact string comparison: 125027.00 renders as
    // 125027, since a time with no fractional part is written without one.
    val older = parse<Gbs>(Checksum.append("\$GPGBS,125027.00,23.43,13.91,34.01,,,,"))
    assertNull(older.systemId)
    assertNull(older.signalId)
    assertEquals(8, fieldCountOf(older.toNmeaString()))

    val newer = parse<Gbs>(Checksum.append("\$GPGBS,125027.00,23.43,13.91,34.01,,,,,1,1"))
    assertEquals(10, fieldCountOf(newer.toNmeaString()))
  }
}

class DtmTest {

  @Test
  fun readsTheShortFormMostReceiversSend() {
    // The only DTM in the corpus, and gpsd's worked example: two fields and nothing else.
    val dtm = parse<Dtm>("\$GPDTM,W84,C*52")
    assertEquals("W84", dtm.datumCode)
    assertEquals("C", dtm.datumSubCode)
    assertNull(dtm.latitudeOffset)
    assertNull(dtm.datumName)
  }

  @Test
  fun readsTheOffsets() {
    // The Java suite's EXAMPLE constant.
    val dtm = parse<Dtm>("\$GPDTM,W84,,0.000000,N,0.000000,E,0.0,W84*6F")
    assertEquals("W84", dtm.datumCode)
    assertNull(dtm.datumSubCode)
    assertEquals(0.0, dtm.latitudeOffset)
    assertEquals(CompassPoint.NORTH, dtm.latitudeOffsetDirection)
    assertEquals(0.0, dtm.longitudeOffset)
    assertEquals(CompassPoint.EAST, dtm.longitudeOffsetDirection)
    assertEquals(0.0, dtm.altitudeOffset)
    assertEquals("W84", dtm.datumName)
  }
}

class TxtTest {

  @Test
  fun readsAMessage() {
    // The Java suite's EXAMPLE constant.
    val txt = parse<Txt>("\$GPTXT,01,01,TARG1,Message*35")
    assertEquals(1, txt.messageCount)
    assertEquals(1, txt.messageIndex)
    assertEquals("TARG1", txt.identifier)
    assertEquals("Message", txt.message)
  }

  @Test
  fun readsARealUbloxBanner() {
    val txt = parse<Txt>("\$GNTXT,01,01,00,PDTINFO*1F")
    assertEquals("00", txt.identifier, "u-blox puts a two-digit severity here, not a name")
    assertEquals("PDTINFO", txt.message)
  }

  @Test
  fun keepsCommasInsideTheMessage() {
    // A real u-blox diagnostic. The message is free text that happens to contain commas, so
    // reading only as far as the next delimiter would truncate it to a single character.
    val line = "\$GNTXT,01,01,01,0,000222,0000,08A0,08A0,-37.124,0*62"
    val txt = parse<Txt>(line)
    assertEquals("0,000222,0000,08A0,08A0,-37.124,0", txt.message)
    assertEquals(line, txt.toNmeaString())
  }
}

class BwcTest {

  @Test
  fun readsBearingDistanceAndWaypoint() {
    // gpsd's second worked example.
    val bwc =
      parse<Bwc>(
        Checksum.append("\$GPBWC,220516,5130.02,N,00046.34,W,213.8,T,218.0,M,0004.6,N,EGLM")
      )
    assertEquals(22, bwc.time?.hour)
    assertEquals(213.8, bwc.bearingTrue)
    assertEquals(218.0, bwc.bearingMagnetic)
    assertEquals(4.6, bwc.distanceNauticalMiles)
    assertEquals("EGLM", bwc.waypointId)
    assertEquals("EGLM", bwc.waypoint?.id)
    assertTrue(abs(bwc.waypointPosition!!.latitude - 51.5003333) < 1e-6, "${bwc.waypointPosition}")
  }

  @Test
  fun readsARealSentenceWithNoActiveWaypoint() {
    // From the corpus. A receiver with nothing to steer to still emits the markers and the mode.
    val idle = parse<Bwc>("\$GPBWC,125106,,,,,,T,,M,,N,,S*68")
    assertNull(idle.waypointPosition)
    assertNull(idle.waypointId)
    assertNull(idle.waypoint)
    assertEquals(FaaMode.SIMULATED, idle.faaMode)
    assertEquals(12, idle.time?.hour)
  }

  @Test
  fun isDistinctFromBod() {
    // BWC replaced BOD in NMEA 4.00 and says considerably more, so they stay separate types.
    assertTrue(
      SentenceRegistry.Default.parse("\$GPBWC,125106,,,,,,T,,M,,N,,S*68").sentenceOrNull() is Bwc
    )
    assertTrue(
      SentenceRegistry.Default.parse("\$GPBOD,234.9,T,228.8,M,RUSKI,*1D").sentenceOrNull() is Bod
    )
  }
}
