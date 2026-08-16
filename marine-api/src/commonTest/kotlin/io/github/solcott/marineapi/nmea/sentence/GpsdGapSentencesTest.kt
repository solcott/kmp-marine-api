package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.Checksum
import io.github.solcott.marineapi.nmea.CompassPoint
import io.github.solcott.marineapi.nmea.DataStatus
import io.github.solcott.marineapi.nmea.Direction
import io.github.solcott.marineapi.nmea.FaaMode
import io.github.solcott.marineapi.nmea.GpsFixStatus
import io.github.solcott.marineapi.nmea.SentenceRegistry
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.Units
import io.github.solcott.marineapi.nmea.sentenceOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalTime

/**
 * The types added to close the gap against gpsd's sentence list.
 *
 * Fixtures for `GRS`, `THS`, `MSS` and the four proprietary types are lines from real receivers in
 * the conformance corpus. The rest have no device data anywhere in this project and are built from
 * gpsd's field tables, which `SampleDataTest` records as the weaker evidence it is.
 */
class GapSentenceTest {

  @Test
  fun readsRangeResidualsFromAQuectelL70() {
    // Verbatim from quectel-L70.log, which GpsdCorpusTest also re-encodes.
    val grs = parse<Grs>("\$GPGRS,150119.000,1,-0.33,-2.59,3.03,-0.09,-2.98,7.12,-15.6,17.0,,,,*5A")
    assertEquals(LocalTime(15, 1, 19), grs.time)
    assertEquals(true, grs.computedAfterFix, "1 means recomputed from the fix, not used in it")
    assertEquals(listOf(-0.33, -2.59, 3.03, -0.09, -2.98, 7.12, -15.6, 17.0), grs.reportedResiduals)
    assertEquals(12, grs.residuals.size, "twelve slots, four of them empty")
    assertNull(grs.residuals[8], "an empty slot is not a residual of zero")
  }

  @Test
  fun readsTrueHeadingAndAgreesWithTheHdtBesideIt() {
    // From skytraq-PX1172RH_DS.log, where these two are adjacent. That pairing is the evidence for
    // the layout: gpsd does not document THS at all.
    val ths = parse<Ths>("\$GNTHS,244.28,A*11")
    val hdt = parse<Hdt>("\$GNHDT,244.28,T*13")
    assertEquals(244.28, ths.heading)
    assertEquals(hdt.heading, ths.heading, "the receiver sends the same heading in both")
    assertEquals(DataStatus.ACTIVE, ths.status, "what HDT has no way of saying")
  }

  @Test
  fun readsBeaconReceiverStatus() {
    // From tn200-all.log: a receiver hearing nothing on a 200 bps beacon.
    val mss = parse<Mss>("\$GPMSS,0,0,0.000000,200,*5A")
    assertEquals(0.0, mss.signalStrength)
    assertEquals(0.0, mss.signalToNoise)
    assertEquals(0.0, mss.beaconFrequency)
    assertEquals(200, mss.beaconBitRate)
    assertNull(mss.channelNumber)
  }

  @Test
  fun keepsTheAlmanacsOrbitalFieldsAsHexText() {
    // gpsd: "Fields 5 through 15 are dumped as raw hex." Turning them into numbers would assert a
    // scaling and a sign convention the sentence does not carry.
    val alm =
      parse<Alm>(
        Checksum.append(
          "\$GPALM,32,1,01,1122,00,441d,4e,16be,fd5e,a10c9f,4a2da4,686e81,58cbe1,0a4,001"
        )
      )
    assertEquals(32, alm.sentenceCount)
    assertEquals("01", alm.satelliteId, "text, so the leading zero survives a round trip")
    assertEquals(1122, alm.gpsWeek)
    assertEquals("441d", alm.eccentricity)
    assertEquals(11, alm.orbitalFields.size)
    assertEquals("001", alm.clockParameterF1)
  }

  @Test
  fun readsAVariableLengthScanList() {
    val sfi = parse<Sfi>(Checksum.append("\$CTSFI,2,1,020230,m,021500,m"))
    assertEquals(2, sfi.sentenceCount)
    assertEquals(
      listOf(ScannedFrequency("020230", 'm'), ScannedFrequency("021500", 'm')),
      sfi.frequencies,
    )

    val single = parse<Sfi>(Checksum.append("\$CTSFI,1,1,020230,m"))
    assertEquals(1, single.frequencies.size)
  }

  @Test
  fun readsARouteAsBareWaypointNames() {
    val route = parse<R00>(Checksum.append("\$GPR00,MELIN,RUSKI,KNUDAN"))
    assertEquals(listOf("MELIN", "RUSKI", "KNUDAN"), route.waypointIds)
  }

  @Test
  fun distinguishesTheTwoWaysOfArriving() {
    // A vessel that passes wide of a mark trips the perpendicular without ever entering the circle,
    // which is what stops a route stalling on a waypoint that was never quite reached.
    val aam = parse<Aam>(Checksum.append("\$GPAAM,V,A,0.10,N,DEST"))
    assertEquals(DataStatus.VOID, aam.circleEntered, "never got within the circle")
    assertEquals(DataStatus.ACTIVE, aam.perpendicularPassed, "but did pass the mark")
    assertEquals(0.10, aam.arrivalCircleRadius)
    assertEquals("DEST", aam.waypointId)
  }

  @Test
  fun readsARhumbLineBearingTheSameWayAsAGreatCircleOne() {
    // BWR and BWC are field for field identical; only the kind of course differs.
    val line = "\$GPBWR,220516,5130.02,N,00046.34,W,213.8,T,218.0,M,0004.6,N,EGLM,A"
    val bwr = parse<Bwr>(Checksum.append(line))
    val bwc = parse<Bwc>(Checksum.append(line.replace("BWR", "BWC")))
    assertEquals(bwc.waypointPosition, bwr.waypointPosition)
    assertEquals(bwc.bearingTrue, bwr.bearingTrue)
    assertEquals(bwc.distanceNauticalMiles, bwr.distanceNauticalMiles)
    assertEquals("EGLM", bwr.waypointId)
    assertEquals(FaaMode.AUTOMATIC, bwr.faaMode)
  }

  @Test
  fun readsTheLoranFormOfTheRecommendedMinimum() {
    val rma =
      parse<Rma>(Checksum.append("\$GPRMA,A,4916.45,N,12311.12,W,1.5,2.5,000.5,054.7,020.3,E"))
    assertEquals(DataStatus.ACTIVE, rma.status)
    assertEquals(49.0 + 16.45 / 60.0, rma.position!!.latitude, 1e-9)
    assertEquals(1.5, rma.timeDifferenceA)
    assertEquals(0.5, rma.speedOverGround)
    assertEquals(054.7, rma.trackMadeGood)
    // Unsigned magnitude beside its direction, the same as RMC and HDG, and for the same reason.
    assertEquals(20.3, rma.magneticVariation)
    assertEquals(CompassPoint.EAST, rma.variationDirection)
    assertEquals(20.3, rma.variationEastPositive)
  }

  @Test
  fun readsElapsedTimesAsDurationsNotTimesOfDay() {
    // A leg with 30 hours left to run is ordinary, and LocalTime cannot hold it.
    val ztg = parse<Ztg>(Checksum.append("\$GPZTG,092204,300000,DEST"))
    assertEquals(30.hours, ztg.timeRemaining)
    assertEquals(LocalTime(9, 22, 4), ztg.time)
    assertEquals("DEST", ztg.destinationWaypointId)

    val zfo = parse<Zfo>(Checksum.append("\$GPZFO,092204,003500,ORIGIN"))
    assertEquals(35.minutes, zfo.elapsedTime)
    assertEquals("ORIGIN", zfo.originWaypointId)
  }

  @Test
  fun readsTheThreeDepthReferencePointsAsThreeTypes() {
    // The same three units measured from the transducer, the keel and the surface. Which one you
    // have is the difference between having water under you and not.
    val transducer = parse<Dbt>(Checksum.append("\$SDDBT,7.8,f,2.4,M,1.3,F"))
    val keel = parse<Dbk>(Checksum.append("\$SDDBK,7.8,f,2.4,M,1.3,F"))
    val surface = parse<Dbs>(Checksum.append("\$SDDBS,7.8,f,2.4,M,1.3,F"))
    assertEquals(2.4, transducer.depthMeters)
    assertEquals(2.4, keel.depthMeters)
    assertEquals(2.4, surface.depthMeters)
    assertTrue(keel != surface as Any, "different types, so they cannot be confused for each other")
  }

  @Test
  fun readsCatchSensorsAndKeepsSilenceDistinctFromEmpty() {
    val tfi = parse<Tfi>(Checksum.append("\$IITFI,1,0,2"))
    assertEquals(listOf(CatchStatus.ON, CatchStatus.OFF, CatchStatus.NO_ANSWER), tfi.sensors)
    assertTrue(tfi.isFilling)
    // A sensor that did not answer has not said the net is empty there.
    assertTrue(tfi.sensors[2] != CatchStatus.OFF)
  }

  @Test
  fun readsGarminsPositionErrorInMetres() {
    // The reason to read a proprietary sentence at all: the standard set reports dilution of
    // precision, which is a geometry factor. This is a distance.
    val pgrme = parse<Pgrme>("\$PGRME,1.7,M,2.4,M,3.0,M*2D") // verbatim from the corpus
    assertEquals(TalkerId.P, pgrme.talker)
    assertEquals(1.7, pgrme.horizontalError)
    assertEquals(2.4, pgrme.verticalError)
    assertEquals(3.0, pgrme.sphericalError)
  }

  @Test
  fun readsGarminsAltitudeInFeetNotMetres() {
    val pgrmz = parse<Pgrmz>(Checksum.append("\$PGRMZ,246,f,3"))
    assertEquals(246.0, pgrmz.altitudeFeet, "feet, and the property name says so")
    assertEquals(GpsFixStatus.GPS_3D, pgrmz.fixStatus)

    // A 2D fix has no vertical solution, so its altitude is an assumption.
    val flat = parse<Pgrmz>(Checksum.append("\$PGRMZ,246,f,2"))
    assertEquals(GpsFixStatus.GPS_2D, flat.fixStatus)
  }

  @Test
  fun readsTheDatumGarminIsReportingIn() {
    // Both forms occur in the corpus, and the space in "WGS 84" is part of the name.
    assertEquals("WGS 84", parse<Pgrmm>("\$PGRMM,WGS 84*06").datumName)
    assertEquals("NAD83", parse<Pgrmm>("\$PGRMM,NAD83*29").datumName)
  }

  @Test
  fun readsRollAndPitchThatNoStandardSentenceCarries() {
    val pashr =
      parse<Pashr>(
        Checksum.append("\$PASHR,123816.80,312.95,T,-0.83,-0.42,-0.01,0.234,0.224,0.298,1,0")
      )
    assertEquals(LocalTime(12, 38, 16, 800_000_000), pashr.time)
    assertEquals(312.95, pashr.heading)
    assertEquals(-0.83, pashr.roll)
    assertEquals(-0.42, pashr.pitch)
    assertEquals(-0.01, pashr.heave)
    assertEquals(0.298, pashr.headingAccuracy)
    assertEquals(1, pashr.aidingStatus)
  }

  @Test
  fun everyNewTypeIsRegisteredAndNotMerelyWritten() {
    // Writing the data class is the easy half. A type that is never registered parses as
    // UnknownSentence and nothing else fails.
    val added =
      listOf(
        "AAM",
        "ALM",
        "APA",
        "BWR",
        "BWW",
        "DBK",
        "DBS",
        "FSI",
        "GRS",
        "HFB",
        "HSC",
        "ITS",
        "MSK",
        "MSS",
        "R00",
        "RLM",
        "RMA",
        "SFI",
        "STN",
        "TDS",
        "TFI",
        "THS",
        "TPC",
        "TPR",
        "TPT",
        "WCV",
        "WNC",
        "XTR",
        "ZFO",
        "ZTG",
        "ASHR",
        "GRME",
        "GRMM",
        "GRMZ",
      )
    assertEquals(34, added.size)
    assertTrue(
      added.all { it in SentenceRegistry.Default.types },
      "unregistered: ${added.filterNot { it in SentenceRegistry.Default.types }}",
    )
    assertEquals(90, SentenceRegistry.Default.types.size)
  }

  @Test
  fun anObsoleteSystemStillParsesAsUnknownRatherThanFailing() {
    // Decca, Loran-C, Omega and Transit are documented by gpsd and deliberately not implemented:
    // nothing transmits them. They are not errors, though -- an unregistered type keeps its fields.
    val decca = SentenceRegistry.Default.parse(Checksum.append("\$GPDCN,1,2,3"))
    assertIs<io.github.solcott.marineapi.nmea.UnknownSentence>(decca.sentenceOrNull())
  }

  @Test
  fun steerDirectionAndUnitsStayStrictBecauseTheyScaleTheError() {
    // Load-bearing: the direction is the sign of the cross-track error and the unit is its scale.
    val apa = parse<Apa>(Checksum.append("\$GPAPA,A,A,0.10,R,N,V,V,011,M,DEST"))
    assertEquals(Direction.RIGHT, apa.steerDirection)
    assertEquals(Units.NAUTICAL_MILES, apa.crossTrackUnits)

    val xtr = parse<Xtr>(Checksum.append("\$GPXTR,0.10,L,N"))
    assertEquals(Direction.LEFT, xtr.steerDirection)
  }
}
