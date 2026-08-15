package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.BearingReference
import io.github.solcott.marineapi.nmea.Checksum
import io.github.solcott.marineapi.nmea.DataStatus
import io.github.solcott.marineapi.nmea.Direction
import io.github.solcott.marineapi.nmea.FaaMode
import io.github.solcott.marineapi.nmea.ParseResult
import io.github.solcott.marineapi.nmea.RouteType
import io.github.solcott.marineapi.nmea.SentenceRegistry
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.Units
import io.github.solcott.marineapi.nmea.sentenceOrNull
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BodTest {

  // The EXAMPLE constant of the Java suite's BODTest, trailing empty origin and all.
  private val bod = parse<Bod>("\$GPBOD,234.9,T,228.8,M,RUSKI,*1D")

  @Test
  fun readsBothBearingsAndTheDestination() {
    assertEquals(234.9, bod.bearingTrue)
    assertEquals(228.8, bod.bearingMagnetic)
    assertEquals("RUSKI", bod.destinationWaypointId)
    assertNull(bod.originWaypointId, "GOTO mode leaves the origin empty")
  }

  @Test
  fun readsTheActiveLegForm() {
    // gpsd's second worked example: a route is active, so both waypoints are named. Its printed
    // checksum is wrong (see checksumsInTheReferenceExamplesAreNotAllCorrect), so the body is
    // taken from the reference and the checksum computed.
    val leg = parse<Bod>(Checksum.append("\$GPBOD,097.0,T,103.2,M,POINTB,POINTA"))
    assertEquals("POINTB", leg.destinationWaypointId)
    assertEquals("POINTA", leg.originWaypointId)
  }

  @Test
  fun readsTheGotoFormWithNoOriginFieldAtAll() {
    // gpsd's first worked example stops after the destination -- five fields, not six.
    val goto = parse<Bod>(Checksum.append("\$GPBOD,099.3,T,105.6,M,POINTB"))
    assertEquals("POINTB", goto.destinationWaypointId)
    assertNull(goto.originWaypointId)
  }

  @Test
  fun writesTheFixedMarkers() {
    assertEquals(
      Checksum.append("\$GPBOD,234.9,T,228.8,M,RUSKI,"),
      Bod(TalkerId.GP, 234.9, 228.8, "RUSKI").toNmeaString(),
    )
  }
}

class WplTest {

  // The EXAMPLE constant of the Java suite's WPLTest.
  private val wpl = parse<Wpl>("\$GPWPL,5536.200,N,01436.500,E,RUSKI*1F")

  @Test
  fun readsNameAndPosition() {
    assertEquals("RUSKI", wpl.waypointId)
    val position = wpl.position
    assertTrue(position != null && abs(position.latitude - 55.60333333) < 1e-6, "$position")
    assertTrue(position != null && abs(position.longitude - 14.60833333) < 1e-6, "$position")
  }

  @Test
  fun pairsThemIntoAWaypoint() {
    val waypoint = wpl.waypoint
    assertEquals("RUSKI", waypoint?.id)
    assertEquals(wpl.position, waypoint?.position)
  }

  @Test
  fun keepsTheNameWhenThePositionIsMissing() {
    // A Waypoint needs both, so this one has none -- but the name survives on its own property
    // rather than the whole sentence being discarded.
    val nameOnly = parse<Wpl>(Checksum.append("\$GPWPL,,,,,RUSKI"))
    assertEquals("RUSKI", nameOnly.waypointId)
    assertNull(nameOnly.position)
    assertNull(nameOnly.waypoint)
  }

  @Test
  fun roundTripsExactly() {
    assertEquals(
      "\$GPWPL,5536.200,N,01436.500,E,RUSKI*1F",
      parse<Wpl>("\$GPWPL,5536.200,N,01436.500,E,RUSKI*1F").toNmeaString(),
    )
  }
}

class RteTest {

  // The EXAMPLE constant of the Java suite's RTETest.
  private val rte = parse<Rte>("\$GPRTE,1,1,c,0,MELIN,RUSKI,KNUDAN*25")

  @Test
  fun readsTheRouteAndItsWaypoints() {
    assertEquals(1, rte.sentenceCount)
    assertEquals(1, rte.sentenceIndex)
    assertEquals(RouteType.COMPLETE, rte.routeType)
    assertEquals("0", rte.routeId)
    assertEquals(listOf("MELIN", "RUSKI", "KNUDAN"), rte.waypoints)
  }

  @Test
  fun knowsWhereItSitsInTheGroup() {
    assertTrue(rte.isFirst)
    assertTrue(rte.isLast)

    val middle = parse<Rte>(Checksum.append("\$GPRTE,3,2,w,0,MELIN"))
    assertFalse(middle.isFirst)
    assertFalse(middle.isLast)
    assertEquals(RouteType.WORKING, middle.routeType)
  }

  @Test
  fun readsARouteWithNoWaypoints() {
    // gpsd's worked example.
    val empty = parse<Rte>("\$GPRTE,1,1,c,0*07")
    assertEquals("0", empty.routeId)
    assertEquals(emptyList(), empty.waypoints)
  }

  @Test
  fun keepsEmptyWaypointSlotsSoTheSentenceReEncodes() {
    // The Java implementation dropped empty fields, which silently renumbered every waypoint
    // after the gap and made the sentence render differently from how it arrived.
    val line = Checksum.append("\$GPRTE,1,1,c,0,MELIN,,KNUDAN")
    val gapped = parse<Rte>(line)
    assertEquals(listOf("MELIN", null, "KNUDAN"), gapped.waypointIds)
    assertEquals(listOf("MELIN", "KNUDAN"), gapped.waypoints)
    assertEquals(line, gapped.toNmeaString())
  }

  @Test
  fun readsARouteWhoseTypeCodeIsUnrecognised() {
    // The route type says whether the list is the whole route or the remaining legs. It qualifies
    // nothing else in the sentence, so an unrecognised code leaves it unknown rather than
    // discarding a route that is otherwise perfectly readable.
    val odd = parse<Rte>(Checksum.append("\$GPRTE,1,1,x,0,MELIN"))
    assertNull(odd.routeType)
    assertEquals(listOf("MELIN"), odd.waypoints)
    assertEquals("0", odd.routeId)
  }
}

class XteTest {

  // The EXAMPLE constant of the Java suite's XTETest.
  private val xte = parse<Xte>("\$IIXTE,A,A,5.36,R,N*67")

  @Test
  fun readsEveryField() {
    assertEquals(DataStatus.ACTIVE, xte.status)
    assertEquals(DataStatus.ACTIVE, xte.cycleLockStatus)
    assertEquals(5.36, xte.magnitude)
    assertEquals(Direction.RIGHT, xte.steerTo)
    assertEquals(Units.NAUTICAL_MILES, xte.units)
    assertNull(xte.faaMode, "the FAA mode field arrived in NMEA 2.3")
  }

  @Test
  fun readsTheNmea23ModeField() {
    // gpsd's worked example: a warning with no data, from a simulator.
    val warning = parse<Xte>("\$GPXTE,V,V,,,N,S*43")
    assertEquals(DataStatus.VOID, warning.status)
    assertEquals(DataStatus.VOID, warning.cycleLockStatus)
    assertNull(warning.magnitude)
    assertNull(warning.steerTo)
    assertEquals(FaaMode.SIMULATED, warning.faaMode)
  }

  @Test
  fun roundTripsExactly() {
    assertEquals("\$GPXTE,V,V,,,N,S*43", parse<Xte>("\$GPXTE,V,V,,,N,S*43").toNmeaString())
  }
}

class ApbTest {

  // gpsd's worked example, which is also the Java suite's EXAMPLE. Both print the checksum as
  // *82; it is actually *3C, so it is recomputed here.
  private val apb = parse<Apb>(Checksum.append("\$GPAPB,A,A,0.10,R,N,V,V,011,M,DEST,011,M,011,M"))

  @Test
  fun readsAllFourteenFields() {
    assertEquals(DataStatus.ACTIVE, apb.status)
    assertEquals(DataStatus.ACTIVE, apb.cycleLockStatus)
    assertEquals(0.10, apb.crossTrackError)
    assertEquals(Direction.RIGHT, apb.steerTo)
    assertEquals(Units.NAUTICAL_MILES, apb.crossTrackUnits)
    assertEquals(DataStatus.VOID, apb.arrivalCircleEntered)
    assertEquals(DataStatus.VOID, apb.perpendicularPassed)
    assertEquals(11.0, apb.bearingOriginToDestination)
    assertEquals(BearingReference.MAGNETIC, apb.bearingOriginToDestinationReference)
    assertEquals("DEST", apb.destinationWaypointId)
    assertEquals(11.0, apb.bearingPositionToDestination)
    assertEquals(BearingReference.MAGNETIC, apb.bearingPositionToDestinationReference)
    assertEquals(11.0, apb.headingToDestination)
    assertEquals(BearingReference.MAGNETIC, apb.headingToDestinationReference)
  }

  @Test
  fun derivesTheArrivalFlags() {
    assertFalse(apb.hasEnteredArrivalCircle)
    assertFalse(apb.hasPassedPerpendicular)

    val arrived = parse<Apb>(Checksum.append("\$GPAPB,A,A,0.10,R,N,A,A,011,T,DEST,011,T,011,T"))
    assertTrue(arrived.hasEnteredArrivalCircle)
    assertTrue(arrived.hasPassedPerpendicular)
    assertEquals(BearingReference.TRUE, arrived.bearingOriginToDestinationReference)
  }

  @Test
  fun readsTheThreeBearingReferencesIndependently() {
    // Nothing requires the three to agree, so each is read rather than inferred from the first.
    val mixed = parse<Apb>(Checksum.append("\$GPAPB,A,A,0.10,R,N,V,V,011,T,DEST,012,M,013,T"))
    assertEquals(BearingReference.TRUE, mixed.bearingOriginToDestinationReference)
    assertEquals(BearingReference.MAGNETIC, mixed.bearingPositionToDestinationReference)
    assertEquals(BearingReference.TRUE, mixed.headingToDestinationReference)
  }

  @Test
  fun acceptsKilometresForCrossTrackError() {
    // The unit field is documented as N only, but the implementation this replaces accepted K,
    // so a device emitting it is not turned into a parse failure.
    assertEquals(
      Units.KILOMETERS,
      parse<Apb>(Checksum.append("\$GPAPB,A,A,0.10,R,K,V,V,011,M,DEST,011,M,011,M"))
        .crossTrackUnits,
    )
  }

  @Test
  fun rejectsAUnitThatIsNotADistance() {
    // Units carries nine codes; C for Celsius is not one an autopilot can mean here.
    val result =
      SentenceRegistry.Default.parse(
        Checksum.append("\$GPAPB,A,A,0.10,R,C,V,V,011,M,DEST,011,M,011,M")
      )
    val malformed = assertIs<ParseResult.Malformed>(result)
    assertTrue("one of [N, K]" in malformed.reason, malformed.reason)
  }
}

class RmbTest {

  // The EXAMPLE constant of the Java suite's RMBTest.
  private val rmb = parse<Rmb>("\$GPRMB,A,0.00,R,,RUSKI,5536.200,N,01436.500,E,432.3,234.9,,V*58")

  @Test
  fun readsEveryField() {
    assertEquals(DataStatus.ACTIVE, rmb.status)
    assertEquals(0.0, rmb.crossTrackError)
    assertEquals(Direction.RIGHT, rmb.steerTo)
    assertNull(rmb.originWaypointId, "GOTO mode leaves the origin empty")
    assertEquals("RUSKI", rmb.destinationWaypointId)
    assertEquals(432.3, rmb.range)
    assertEquals(234.9, rmb.bearing)
    assertNull(rmb.velocity)
    assertEquals(DataStatus.VOID, rmb.arrivalStatus)
    assertFalse(rmb.hasArrived)
  }

  @Test
  fun readsTheDestinationPosition() {
    val destination = rmb.destination
    assertEquals("RUSKI", destination?.id)
    val position = destination?.position
    assertTrue(position != null && abs(position.latitude - 55.60333333) < 1e-6, "$position")
    assertTrue(position != null && abs(position.longitude - 14.60833333) < 1e-6, "$position")
  }

  @Test
  fun readsTheNmea23ModeFieldTheJavaImplementationIgnored() {
    // gpsd documents a fifteenth field from NMEA 2.3 on; the parser this replaces stopped at
    // fourteen and could never see it.
    val line =
      Checksum.append("\$GPRMB,A,0.66,L,003,004,4917.24,N,12309.57,W,001.3,052.5,000.5,V,A")
    assertEquals(FaaMode.AUTOMATIC, parse<Rmb>(line).faaMode)
  }

  @Test
  fun readsGpsdsWorkedExample() {
    val gpsd =
      parse<Rmb>(
        Checksum.append("\$GPRMB,A,0.66,L,003,004,4917.24,N,12309.57,W,001.3,052.5,000.5,V")
      )
    assertEquals(0.66, gpsd.crossTrackError)
    assertEquals(Direction.LEFT, gpsd.steerTo)
    assertEquals("003", gpsd.originWaypointId)
    assertEquals("004", gpsd.destinationWaypointId)
    assertEquals(1.3, gpsd.range)
    assertEquals(52.5, gpsd.bearing)
    assertEquals(0.5, gpsd.velocity)
  }

  @Test
  fun reportsArrival() {
    val line = Checksum.append("\$GPRMB,A,0.00,R,,RUSKI,5536.200,N,01436.500,E,432.3,234.9,,A")
    assertTrue(parse<Rmb>(line).hasArrived)
  }

  @Test
  fun isDistinctFromRmc() {
    // Two sentences one letter apart that report entirely different things.
    val navigation = SentenceRegistry.Default.parse(rmb.toNmeaString()).sentenceOrNull()
    assertIs<Rmb>(navigation)
  }
}

/**
 * Pins a hazard in the reference material this batch was written against.
 *
 * Several of the worked examples on gpsd's NMEA page carry a checksum that does not match their own
 * body -- and the APB one is copied verbatim into the Java implementation's javadoc, so the error
 * has already propagated once. A fixture pasted from the reference would fail here rather than at
 * the assertion the test was actually written to make, which is confusing enough to be worth
 * stating outright.
 */
class ReferenceExampleChecksumTest {

  @Test
  fun checksumsInTheReferenceExamplesAreNotAllCorrect() {
    val printed =
      mapOf(
        "\$GPBOD,097.0,T,103.2,M,POINTB,POINTA" to "52",
        "\$GPBOD,099.3,T,105.6,M,POINTB" to "01",
        "\$GPRMB,A,0.66,L,003,004,4917.24,N,12309.57,W,001.3,052.5,000.5,V" to "0B",
        "\$GPAPB,A,A,0.10,R,N,V,V,011,M,DEST,011,M,011,M" to "82",
      )

    for ((body, checksum) in printed) {
      val result = SentenceRegistry.Default.parse("$body*$checksum")
      val bad = assertIs<ParseResult.BadChecksum>(result, "$body*$checksum unexpectedly parsed")
      assertEquals(checksum, bad.actual)
    }
  }

  @Test
  fun theOtherReferenceExamplesAreCorrect() {
    // The failures above are specific to those four, not a sign the checksum code is wrong.
    for (line in
      listOf(
        "\$GPXTE,V,V,,,N,S*43",
        "\$GPRTE,1,1,c,0*07",
        "\$WIMWD,302.4,T,289.6,M,10.5,N,5.4,M*6F",
        "\$GPHDT,274.07,T*03",
      )) {
      assertIs<ParseResult.Ok>(SentenceRegistry.Default.parse(line), line)
    }
  }
}
