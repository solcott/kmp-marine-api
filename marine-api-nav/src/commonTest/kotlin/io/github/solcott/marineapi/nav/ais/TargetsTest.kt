package io.github.solcott.marineapi.nav.ais

import io.github.solcott.marineapi.ais.AidType
import io.github.solcott.marineapi.ais.AisAidToNavigationReport
import io.github.solcott.marineapi.ais.AisBaseStationReport
import io.github.solcott.marineapi.ais.AisLongRangePositionReport
import io.github.solcott.marineapi.ais.AisMessage
import io.github.solcott.marineapi.ais.AisPositionReport
import io.github.solcott.marineapi.ais.AisPositionReportB
import io.github.solcott.marineapi.ais.AisStaticAndVoyageData
import io.github.solcott.marineapi.ais.AisStaticDataReport
import io.github.solcott.marineapi.ais.AisStaticDataReportB
import io.github.solcott.marineapi.ais.EpfdType
import io.github.solcott.marineapi.ais.EstimatedArrival
import io.github.solcott.marineapi.ais.NavigationalStatus
import io.github.solcott.marineapi.ais.ShipDimensions
import io.github.solcott.marineapi.nmea.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

/**
 * Messages are built directly rather than decoded from payloads: these operators take an
 * [AisMessage], so going through the decoder would only test the layer underneath again. The bit
 * decoding has its own tests, and gpsd's own output to check against.
 */
private const val MMSI = 244200000

private val ROTTERDAM = Position(51.95, 4.05)

private fun positionReport(
  mmsi: Int = MMSI,
  position: Position? = ROTTERDAM,
  speed: Double? = 12.0,
  course: Double? = 90.0,
  heading: Int? = 88,
  status: NavigationalStatus? = NavigationalStatus.UNDER_WAY_USING_ENGINE,
  rateOfTurn: Double? = 0.0,
) =
  AisPositionReport(
    messageType = 1,
    repeatIndicator = 0,
    mmsi = mmsi,
    navigationalStatus = status,
    rateOfTurn = rateOfTurn,
    rateOfTurnCode = 0,
    speedOverGround = speed,
    isAccurate = true,
    position = position,
    courseOverGround = course,
    heading = heading,
    utcSecond = 30,
    maneuver = null,
    hasRaim = false,
  )

private fun classBPositionReport(
  mmsi: Int = MMSI,
  position: Position? = ROTTERDAM,
  speed: Double? = 6.0,
  course: Double? = 180.0,
  heading: Int? = null,
) =
  AisPositionReportB(
    messageType = 18,
    repeatIndicator = 0,
    mmsi = mmsi,
    speedOverGround = speed,
    isAccurate = false,
    position = position,
    courseOverGround = course,
    heading = heading,
    utcSecond = 12,
    isClassBCarrierSotdma = false,
    hasDisplay = false,
    hasDscCapability = false,
    isBandFlagSet = false,
    canAcceptMessage22 = false,
    isAssigned = false,
    hasRaim = false,
  )

private fun staticReport(
  mmsi: Int = MMSI,
  name: String = "MAERSK EDINBURGH",
  callSign: String = "OWDS2",
  destination: String = "ROTTERDAM",
) =
  AisStaticAndVoyageData(
    messageType = 5,
    repeatIndicator = 0,
    mmsi = mmsi,
    aisVersion = 0,
    imoNumber = 9456783,
    callSign = callSign,
    name = name,
    shipType = 70,
    dimensions = ShipDimensions(toBow = 250, toStern = 116, toPort = 20, toStarboard = 28),
    epfd = EpfdType.GPS,
    eta = EstimatedArrival(month = 5, day = 12, hour = 6, minute = 30),
    maximumDraught = 14.5,
    destination = destination,
    isDteReady = true,
  )

private fun longRangeReport(mmsi: Int = MMSI, position: Position? = Position(51.9, 4.0)) =
  AisLongRangePositionReport(
    messageType = 27,
    repeatIndicator = 0,
    mmsi = mmsi,
    isAccurate = false,
    hasRaim = false,
    navigationalStatus = NavigationalStatus.UNDER_WAY_USING_ENGINE,
    position = position,
    speedOverGround = 12.0,
    courseOverGround = 90.0,
    isCurrent = false,
  )

private fun aidReport(offPosition: Boolean, virtual: Boolean = false, name: String = "NOORD 4") =
  AisAidToNavigationReport(
    messageType = 21,
    repeatIndicator = 0,
    mmsi = 992442000,
    aidType = AidType.BEACON_PORT_HAND,
    name = name,
    isAccurate = true,
    position = ROTTERDAM,
    dimensions = ShipDimensions(0, 0, 0, 0),
    epfd = EpfdType.GPS,
    utcSecond = 20,
    isOffPosition = offPosition,
    isVirtual = virtual,
    isAssigned = false,
    hasRaim = false,
  )

/**
 * A time source the test drives by hand.
 *
 * `TimeSource.Monotonic` cannot be wound forward, and a test of a ten-minute expiry that waits ten
 * minutes is not a test anyone will run.
 */
private class FakeTimeSource : TimeSource {
  var now: Duration = Duration.ZERO

  override fun markNow(): TimeMark =
    object : TimeMark {
      private val takenAt = now

      override fun elapsedNow(): Duration = now - takenAt
    }
}

class VesselMergeTest {

  @Test
  fun joinsAStaticReportToThePositionThatArrivedBeforeIt() {
    // The whole point of the type: AIS sends where she is every few seconds and who she is every
    // six minutes, and neither half is useful alone.
    val registry = TargetRegistry()
    registry.update(positionReport())
    val vessel = registry.update(staticReport())!!

    assertEquals("MAERSK EDINBURGH", vessel.name)
    assertEquals(ROTTERDAM, vessel.position)
    assertEquals(12.0, vessel.speedOverGround)
    assertEquals("Cargo ship, general", vessel.shipTypeDescription)
    assertEquals(366, vessel.length)
    assertEquals(48, vessel.beam)
  }

  @Test
  fun joinsAPositionToTheStaticReportThatArrivedBeforeIt() {
    val registry = TargetRegistry()
    registry.update(staticReport())
    val vessel = registry.update(positionReport())!!

    assertEquals("MAERSK EDINBURGH", vessel.name)
    assertEquals("ROTTERDAM", vessel.destination)
    assertEquals(ROTTERDAM, vessel.position)
  }

  @Test
  fun keepsAFieldTheNewReportDoesNotCarry() {
    // A Class B position report carries no navigational status at all, so one learned earlier
    // survives it. Absent is not the same as unavailable.
    val registry = TargetRegistry()
    registry.update(positionReport(status = NavigationalStatus.AT_ANCHOR))
    val vessel = registry.update(classBPositionReport())!!

    assertEquals(NavigationalStatus.AT_ANCHOR, vessel.navigationalStatus)
    assertEquals(180.0, vessel.courseOverGround)
  }

  @Test
  fun clearsAFieldTheNewReportCarriesAsUnavailable() {
    // The same report does carry a heading, and a Class B unit with no heading sensor is saying so.
    // Treating that as silence would leave her showing a heading she had stopped reporting.
    val registry = TargetRegistry()
    registry.update(positionReport(heading = 88))
    val vessel = registry.update(classBPositionReport(heading = null))!!

    assertNull(vessel.heading)
  }

  @Test
  fun doesNotLetABlankNameOverwriteAKnownOne() {
    // An unset AIS text field decodes to an empty string. A vessel that sends her name once and
    // then sends a type 24 with the field unfilled has not become anonymous.
    val registry = TargetRegistry()
    registry.update(staticReport(name = "MAERSK EDINBURGH"))
    val vessel = registry.update(AisStaticDataReport(24, 0, MMSI, name = ""))!!

    assertEquals("MAERSK EDINBURGH", vessel.name)
  }

  @Test
  fun assemblesAClassBTargetFromBothHalvesOfItsStaticReport() {
    // Type 24 splits a Class B vessel's name from her type and dimensions, and the two halves can
    // be minutes apart.
    val registry = TargetRegistry()
    registry.update(AisStaticDataReport(24, 0, MMSI, name = "SEA BREEZE"))
    val vessel =
      registry.update(
        AisStaticDataReportB(
          messageType = 24,
          repeatIndicator = 0,
          mmsi = MMSI,
          shipType = 37,
          vendorId = "ACM",
          unitModelCode = 1,
          serialNumber = 12345,
          callSign = "MDZQ7",
          dimensions = ShipDimensions(toBow = 8, toStern = 4, toPort = 2, toStarboard = 2),
        )
      )!!

    assertEquals("SEA BREEZE", vessel.name)
    assertEquals("MDZQ7", vessel.callSign)
    assertEquals(12, vessel.length)
    assertTrue(vessel.isClassB)
  }

  @Test
  fun flagsAPositionThatCameFromALongRangeReport() {
    // A type 27 is tenths of a minute rather than ten-thousandths: about 185 metres of resolution.
    // It still replaces the precise position, because it is the newer report -- but a caller
    // drawing a berth-scale plot needs to know which kind it now holds.
    val registry = TargetRegistry()
    val precise = registry.update(positionReport())!!
    assertFalse(precise.isPositionCoarse)

    val coarse = registry.update(longRangeReport())!!
    assertTrue(coarse.isPositionCoarse)
    assertEquals(Position(51.9, 4.0), coarse.position)
    // Type 27 carries no heading, so the one from the type 1 survives.
    assertEquals(88, coarse.heading)
  }

  @Test
  fun clearsThePositionOfAVesselThatStopsReportingOne() {
    val registry = TargetRegistry()
    registry.update(positionReport())
    val vessel = registry.update(positionReport(position = null))!!

    assertNull(vessel.position)
    assertFalse(vessel.isUnderWay)
  }

  @Test
  fun keepsTwoVesselsApart() {
    val registry = TargetRegistry()
    registry.update(staticReport(mmsi = MMSI, name = "MAERSK EDINBURGH"))
    registry.update(staticReport(mmsi = 235100000, name = "QUEEN MARY 2"))

    assertEquals("MAERSK EDINBURGH", registry[Mmsi(MMSI)]?.name)
    assertEquals("QUEEN MARY 2", registry[Mmsi(235100000)]?.name)
    assertEquals(2, registry.size)
  }
}

class TargetRegistryTest {

  @Test
  fun ignoresAMessageThatDescribesNoVessel() {
    // A base station is not a target, and folding one into a Vessel would put a shore mast on a
    // collision-avoidance display.
    val registry = TargetRegistry()
    val result =
      registry.update(
        AisBaseStationReport(
          messageType = 4,
          repeatIndicator = 0,
          mmsi = 2442000,
          utc = null,
          isAccurate = true,
          position = ROTTERDAM,
          epfd = EpfdType.SURVEYED,
          hasRaim = false,
        )
      )

    assertNull(result)
    assertEquals(0, registry.size)
  }

  @Test
  fun forgetsATargetThatHasStoppedReporting() {
    val time = FakeTimeSource()
    val registry = TargetRegistry(expireAfter = 10.minutes, timeSource = time)
    registry.update(positionReport())

    time.now += 9.minutes
    assertEquals(1, registry.size)

    time.now += 2.minutes
    assertEquals(0, registry.size)
    assertNull(registry[Mmsi(MMSI)])
    assertNull(registry.ageOf(Mmsi(MMSI)))
  }

  @Test
  fun namesTheTargetsItDropped() {
    // A display has to be told which targets to rub out; it cannot infer that from a stream of
    // updates about the ones still there.
    val time = FakeTimeSource()
    val registry = TargetRegistry(expireAfter = 10.minutes, timeSource = time)
    registry.update(positionReport(mmsi = MMSI))
    time.now += 6.minutes
    registry.update(positionReport(mmsi = 235100000))

    time.now += 6.minutes
    assertEquals(listOf(Mmsi(MMSI)), registry.expire())
    assertEquals(1, registry.size)
  }

  @Test
  fun reportsHowStaleATargetIs() {
    // A target seen four minutes ago is still on the plot and is no longer where the plot says.
    val time = FakeTimeSource()
    val registry = TargetRegistry(timeSource = time)
    registry.update(positionReport())

    time.now += 4.minutes
    assertEquals(4.minutes, registry.ageOf(Mmsi(MMSI)))
  }

  @Test
  fun startsAFreshTargetRatherThanRevivingAnExpiredOne() {
    // The point of the expiry is that what was known then is not to be trusted now -- and the
    // cheaper transponders do reuse identities.
    val time = FakeTimeSource()
    val registry = TargetRegistry(expireAfter = 10.minutes, timeSource = time)
    registry.update(staticReport(name = "MAERSK EDINBURGH"))

    time.now += 11.minutes
    val vessel = registry.update(positionReport())!!
    assertNull(vessel.name)
  }

  @Test
  fun rejectsANonsensicalExpiry() {
    assertFailsWith<IllegalArgumentException> { TargetRegistry(expireAfter = Duration.ZERO) }
  }
}

class AisTargetsTest {

  @Test
  fun emitsTheWholeAccumulatedPictureOnEveryUpdate() = runTest {
    val targets =
      listOf<AisMessage>(positionReport(), staticReport(), positionReport(speed = 13.0))
        .asFlow()
        .aisTargets()
        .toList()

    assertEquals(3, targets.size)
    assertNull(targets[0].name)
    // The static report emits the name together with the position that arrived before it.
    assertEquals("MAERSK EDINBURGH", targets[1].name)
    assertEquals(ROTTERDAM, targets[1].position)
    assertEquals("MAERSK EDINBURGH", targets[2].name)
    assertEquals(13.0, targets[2].speedOverGround)
  }

  @Test
  fun emitsNothingForAMessageThatDescribesNoVessel() = runTest {
    val targets = listOf<AisMessage>(aidReport(offPosition = false)).asFlow().aisTargets().toList()
    assertTrue(targets.isEmpty())
  }

  @Test
  fun fillsARegistryTheCallerHolds() = runTest {
    val registry = TargetRegistry()
    listOf<AisMessage>(positionReport(), staticReport(mmsi = 235100000))
      .asFlow()
      .aisTargets(registry)
      .toList()

    assertEquals(2, registry.vessels.size)
    assertEquals("MAERSK EDINBURGH", registry[Mmsi(235100000)]?.name)
  }

  @Test
  fun startsFromNothingOnEachCollection() = runTest {
    // The registry is built inside the flow, not when the operator is called, so collecting twice
    // does not leak one collection's targets into the other's.
    val flow = listOf<AisMessage>(positionReport()).asFlow().aisTargets()
    assertEquals(1, flow.toList().size)
    assertEquals(1, flow.toList().size)
  }
}

class OffPositionAidsTest {

  @Test
  fun reportsAMarkThatHasDraggedItsMooring() = runTest {
    val aids =
      listOf<AisMessage>(aidReport(offPosition = false), aidReport(offPosition = true))
        .asFlow()
        .offPositionAids()
        .toList()

    assertEquals(1, aids.size)
    assertEquals("NOORD 4", aids.single().name)
  }

  @Test
  fun letsThroughAVirtualAidClaimingToBeOffPosition() = runTest {
    // A mark that exists only as a broadcast cannot drift, so this is a misconfigured shore
    // station -- which is itself worth seeing.
    val aids =
      listOf<AisMessage>(aidReport(offPosition = true, virtual = true))
        .asFlow()
        .offPositionAids()
        .toList()

    assertEquals(1, aids.size)
    assertTrue(aids.single().isVirtual)
  }
}
