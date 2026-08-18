package io.github.solcott.marineapi.ais

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDateTime

/**
 * Payloads are the fixtures of the Java suite's own AIS tests, so every value here was checked
 * against what the implementation being replaced was verified with. Where the two disagree the
 * disagreement is called out on the assertion.
 */
private object Payloads {
  const val POSITION_REPORT = "13u?etPv2;0n:dDPwUM1U1Cb069D"
  const val BASE_STATION = "400TcdiuiT7VDR>3nIfr6>i00000"
  const val STATIC_AND_VOYAGE =
    "58wt8Ui`g??r21`7S=:22058<v05Htp000000015>8OA;0skeQ8823mDm3kP00000000000"
  const val SAR_AIRCRAFT = "95M2oQ@41Tr4L4H@eRvQ;2h20000"
  const val CLASS_B = "B6CdCm0t3`tba35f@V9faHi7kP06"
  const val AID_TO_NAVIGATION = "E1c2;q@b44ah4ah0h:2ab@70VRpU<Bgpm4:gP50HH`Th`QF51CQ1A83PCAH0"
  const val STATIC_DATA_A = "H1c2;qA@PU>0U>060<h5=>0:1Dp"
  const val STATIC_DATA_B = "H1c2;qDTijklmno31<<C970`43<1"
  const val LONG_RANGE = "Kk:qFP0?fhT8=7m@"

  /** From `ais-nmea-type6-fid55.log`; gpsd's `.chk` gives mmsi 244620320 and mmsi1 2268402. */
  const val BINARY_ACKNOWLEDGE = "73aBL800RW?;"
}

private inline fun <reified T : AisMessage> decode(payload: String, fillBits: Int = 0): T {
  val result = AisRegistry.Default.decode(payload, fillBits)
  assertIs<AisResult.Ok>(result, "failed to decode: $result")
  return assertIs<T>(result.message)
}

class AisPositionReportTest {

  private val report = decode<AisPositionReport>(Payloads.POSITION_REPORT)

  @Test
  fun readsAScheduledPositionReport() {
    assertEquals(1, report.messageType)
    assertEquals(0, report.repeatIndicator)
    assertEquals(265_547_250, report.mmsi)
    assertEquals(NavigationalStatus.UNDER_WAY_USING_ENGINE, report.navigationalStatus)
    assertEquals(13.9, report.speedOverGround!!, 1e-9)
    assertEquals(40.4, report.courseOverGround!!, 1e-9)
    assertEquals(41, report.heading)
    assertEquals(53, report.utcSecond)
    assertEquals(ManeuverIndicator.NOT_AVAILABLE, report.maneuver)
  }

  @Test
  fun readsThePositionAsAValue() {
    val position = assertNotNull(report.position)
    assertEquals(57.6603533, position.latitude, 1e-7)
    assertEquals(11.8329767, position.longitude, 1e-7)
    assertNull(position.altitude, "AIS position reports carry no altitude")
  }

  @Test
  fun readsTheRateOfTurn() {
    // The wire value is the square root of the rate, scaled, so a small code is a small turn.
    assertEquals(-2.9, report.rateOfTurn!!, 0.1)
    assertEquals(-8, report.rateOfTurnCode)
  }

  @Test
  fun distinguishesLowAndHighAccuracy() {
    assertFalse(report.isAccurate)
    assertTrue(decode<AisPositionReport>("15S0t`001TlGn>TNurwroHgD05;H").isAccurate)
  }

  @Test
  fun decodesAllThreeSchedulingVariantsTheSameWay() {
    // Types 1, 2 and 3 differ only in why they were sent, so one class reads all three.
    assertEquals(listOf(1, 2, 3), AisPositionReport.TYPES)
    assertTrue(AisPositionReport.TYPES.all { it in AisRegistry.Default.types })
  }
}

class AisBaseStationReportTest {

  private val report = decode<AisBaseStationReport>(Payloads.BASE_STATION)

  @Test
  fun readsTheTimeAndPositionOfAShoreStation() {
    assertEquals(4, report.messageType)
    assertEquals(601_011, report.mmsi)
    assertEquals(LocalDateTime(2012, 6, 8, 7, 38, 20), report.utc)
    val position = assertNotNull(report.position)
    assertEquals(-29.870835, position.latitude, 1e-6)
    assertEquals(31.033513, position.longitude, 1e-6)
  }

  @Test
  fun readsTheAccuracyBitTheStandardPutsIt() {
    // Bit 78. The Java implementation read bit 77, the last bit of the seconds field, and so
    // reported this station -- which claims high accuracy -- as low.
    assertTrue(report.isAccurate)
  }
}

class AisStaticAndVoyageDataTest {

  private val report = decode<AisStaticAndVoyageData>(Payloads.STATIC_AND_VOYAGE, fillBits = 2)

  @Test
  fun readsWhoTheVesselIs() {
    assertEquals(5, report.messageType)
    assertEquals(603_916_439, report.mmsi)
    assertEquals(0, report.aisVersion)
    assertEquals(439_303_422, report.imoNumber)
    assertEquals("   ARCO AVON", report.name)
    assertEquals("  ZA83R", report.callSign)
    assertEquals(69, report.shipType)
  }

  @Test
  fun stripsTheTerminatorAndTrailingPaddingButKeepsWhatCameBefore() {
    // '@' ends a field and trailing spaces are padding, so both go. Leading spaces stay: gpsd keeps
    // them, and once '@' has marked where the data ends, anything before it is something the
    // station chose to send. This transponder pads on the left, and both fields show it.
    assertEquals("  ZA83R", report.callSign)
    assertEquals("  HOUSTON", report.destination)
    // The Java implementation trimmed these three fields of this one message type and no others.
    assertEquals("   ARCO AVON", report.name)
  }

  @Test
  fun readsWhereSheIsGoingAndHowDeepSheSits() {
    assertEquals(EstimatedArrival(month = 3, day = 23, hour = 19, minute = 45), report.eta)
    assertFalse(report.eta.isEmpty)
    assertEquals(13.2, report.maximumDraught!!, 1e-9)
    assertEquals("  HOUSTON", report.destination)
  }

  @Test
  fun readsHerDimensionsRelativeToTheAntenna() {
    assertEquals(
      ShipDimensions(toBow = 113, toStern = 31, toPort = 17, toStarboard = 11),
      report.dimensions,
    )
    assertEquals(144, report.dimensions.length, "bow plus stern, not either alone")
    assertEquals(28, report.dimensions.beam)
  }

  @Test
  fun readsTheDataTerminalFlagTheRightWayRound() {
    // The bit is 0, which means ready. The Java method named isDteReady returned the raw bit and
    // so answered false for a vessel that had said it was ready.
    assertEquals(true, report.isDteReady)
  }

  @Test
  fun describesTheShipTypeCode() {
    assertEquals("Passenger ship, no additional information", describeShipType(69))
    assertEquals("Cargo ship, general", describeShipType(70))
    assertEquals("Tug", describeShipType(52), "50 to 59 ignore the two-digit scheme")
    assertEquals("Vessel, sailing", describeShipType(36))
  }
}

class AisSarAircraftPositionReportTest {

  private val report = decode<AisSarAircraftPositionReport>(Payloads.SAR_AIRCRAFT)

  @Test
  fun readsAnAircraftPosition() {
    assertEquals(9, report.messageType)
    assertEquals(366_000_005, report.mmsi)
    assertEquals(16, report.altitude)
    assertEquals(30.0, report.courseOverGround!!, 1e-9)
    assertEquals(11, report.utcSecond)
    assertTrue(report.isAccurate)
    val position = assertNotNull(report.position)
    assertEquals(29.20575, position.latitude, 1e-5)
    assertEquals(-82.91646, position.longitude, 1e-5)
  }

  @Test
  fun readsSpeedInWholeKnots() {
    // Every other message type reports tenths of a knot. This one does not, and reading it as
    // tenths would put a search and rescue aircraft at 10 knots.
    assertEquals(100.0, report.speedOverGround!!, 1e-9)
  }

  @Test
  fun readsTheFlagsAtTheEndOfTheMessage() {
    // The Java implementation read these three at bits 42, 145 and 146 rather than 142, 146 and
    // 147: the bit ranges had their ends transposed and one carried 43 where 142 belongs.
    assertFalse(report.isDteReady, "bit 142 is set, and set means not ready")
    assertFalse(report.isAssigned)
    assertFalse(report.hasRaim)
  }
}

class AisClassBTest {

  private val report = decode<AisPositionReportB>(Payloads.CLASS_B)

  @Test
  fun readsAClassBPositionReport() {
    assertEquals(18, report.messageType)
    assertEquals(423_302_100, report.mmsi)
    assertEquals(1.4, report.speedOverGround!!, 1e-9)
    assertEquals(177.0, report.courseOverGround!!, 1e-9)
    assertEquals(177, report.heading)
    assertEquals(34, report.utcSecond)
    val position = assertNotNull(report.position)
    assertEquals(40.005283333, position.latitude, 1e-9)
    assertEquals(53.010996667, position.longitude, 1e-9)
  }

  @Test
  fun readsTheAccuracyBitTheStandardPutsIt() {
    // Bit 56, not 55. The Java implementation read the bit before it -- the last bit of the speed
    // field -- and its own test fixture pinned the wrong answer: this vessel claims high accuracy.
    assertTrue(report.isAccurate)
  }

  @Test
  fun readsTheEquipmentFlags() {
    assertTrue(report.isClassBCarrierSotdma)
    assertTrue(report.hasDisplay)
    assertTrue(report.hasDscCapability)
    assertTrue(report.isBandFlagSet)
    assertTrue(report.canAcceptMessage22)
    assertFalse(report.isAssigned)
    assertFalse(report.hasRaim)
  }
}

class AisAidToNavigationTest {

  private val report = decode<AisAidToNavigationReport>(Payloads.AID_TO_NAVIGATION)

  @Test
  fun readsAMarkAndItsPosition() {
    assertEquals(21, report.messageType)
    assertEquals(112_233_445, report.mmsi)
    assertEquals(AidType.REFERENCE_POINT, report.aidType)
    assertEquals(EpfdType.GPS, report.epfd)
    assertEquals(9, report.utcSecond)
    val position = assertNotNull(report.position)
    assertEquals(-38.220167, position.latitude, 1e-6)
    assertEquals(145.181, position.longitude, 1e-3)
  }

  @Test
  fun joinsTheNameWithItsExtension() {
    // A name too long for the 20-character field spills into an extension at the end of the
    // message. The Java implementation returned the two separately and left joining to the caller.
    assertEquals("THIS IS A TEST NAME1EXTENDED NAME", report.name)
  }

  @Test
  fun saysWhetherTheMarkIsWhereItShouldBe() {
    assertTrue(report.isOffPosition, "this buoy has dragged")
    assertFalse(report.isVirtual, "and it is a real one")
    assertTrue(report.isAssigned)
    assertFalse(report.hasRaim)
  }
}

class AisStaticDataReportTest {

  @Test
  fun readsPartAAsItsOwnType() {
    val partA = decode<AisStaticDataReport>(Payloads.STATIC_DATA_A, fillBits = 2)
    assertEquals(24, partA.messageType)
    assertEquals(112_233_445, partA.mmsi)
    assertEquals("THIS IS A CLASS B UN", partA.name)
  }

  @Test
  fun readsPartBAsItsOwnType() {
    // The two halves arrive independently and may be minutes apart, so they are separate types
    // rather than one class with half its fields left at zero.
    val partB = decode<AisStaticDataReportB>(Payloads.STATIC_DATA_B)
    assertEquals(36, partB.shipType)
    assertEquals("123", partB.vendorId)
    assertEquals(13, partB.unitModelCode)
    assertEquals(220_599, partB.serialNumber)
    assertEquals("CALLSIG", partB.callSign)
    assertEquals(
      ShipDimensions(toBow = 5, toStern = 4, toPort = 3, toStarboard = 12),
      partB.dimensions,
    )
  }

  @Test
  fun bothPartsShareTheirMessageTypeAndTheirMmsi() {
    val partA = decode<AisStaticDataReport>(Payloads.STATIC_DATA_A, fillBits = 2)
    val partB = decode<AisStaticDataReportB>(Payloads.STATIC_DATA_B)
    assertEquals(partA.mmsi, partB.mmsi, "which is what lets a receiver pair them up")
    assertEquals(partA.messageType, partB.messageType)
  }

  @Test
  fun rejectsAPartNumberThatIsNeither() {
    // Only 0 and 1 are defined, and the rest of the layout depends on which it is.
    // 'I' at index 6 puts 0b10 in the part-number bits, where 'A' put 0b00.
    val bogus = AisRegistry.Default.decode("H1c2;qI@PU>0U>060<h5=>0:1Dp", 2)
    assertTrue("part 0 or part 1" in assertIs<AisResult.Malformed>(bogus).reason)
  }
}

class AisLongRangePositionReportTest {

  private val report = decode<AisLongRangePositionReport>(Payloads.LONG_RANGE)

  @Test
  fun readsACoarsePosition() {
    assertEquals(27, report.messageType)
    assertEquals(3, report.repeatIndicator)
    assertEquals(212_752_000, report.mmsi)
    val position = assertNotNull(report.position)
    assertEquals(56.36333333333334, position.latitude, 1e-9)
    assertEquals(-7.3566666666666665, position.longitude, 1e-9)
  }

  @Test
  fun readsSpeedAndCourseAtWholeUnitResolution() {
    // Tenths went the same way the coordinate resolution did, to fit the message into 96 bits.
    assertEquals(15.0, report.speedOverGround!!, 1e-9)
    assertEquals(340.0, report.courseOverGround!!, 1e-9)
  }

  @Test
  fun readsTheNavigationalStatusFromTheRightBits() {
    // The Java implementation gave this field a bit range whose ends were the wrong way round,
    // which made its loop exit immediately and return 0 for every type 27 message ever decoded.
    // This payload happens to be 0 as well, so the value below is not the evidence -- the range is.
    assertEquals(NavigationalStatus.UNDER_WAY_USING_ENGINE, report.navigationalStatus)
  }

  @Test
  fun carriesNoTimeOfFix() {
    assertNull(report.utcSecond, "type 27 has no timestamp field at all")
  }
}

/** A field as a fixed-width binary string, for assembling a payload the corpus does not contain. */
private fun bits(value: Int, width: Int): String = value.toString(2).padStart(width, '0')

/**
 * Bits as six-bit payload characters: the transport encoding, not the six-bit ASCII of text fields.
 *
 * [AisBinaryAcknowledgeTest.readsAllFourAcknowledgementSlots] checks this against a real capture
 * before relying on it, so a synthetic payload cannot pass by being wrong in the same way twice.
 */
private fun payloadOf(bits: String): String =
  bits.chunked(6).joinToString("") { chunk ->
    val value = chunk.toInt(2)
    (if (value < 40) value + 48 else value + 56).toChar().toString()
  }

class AisBinaryAcknowledgeTest {

  private val message = decode<AisBinaryAcknowledge>(Payloads.BINARY_ACKNOWLEDGE)

  @Test
  fun readsEveryField() {
    assertEquals(7, message.messageType)
    assertEquals(0, message.repeatIndicator)
    assertEquals(244620320, message.mmsi)
    // gpsd's record for this payload gives mmsi1 as 2268402 and emits no sequence numbers at all,
    // so the 3 is the two bits after the MMSI gpsd does confirm.
    assertEquals(listOf(AisAcknowledgement(2268402, 3)), message.acknowledgements)
  }

  @Test
  fun reportsOnlyTheAcknowledgementSlotsThatArrived() {
    // 72 bits: header, two spare bits and one slot. gpsd prints the other three as "mmsi2":0 and
    // so on, but an MMSI of 0 is not a station.
    assertEquals(1, message.acknowledgements.size)
  }

  @Test
  fun readsAllFourAcknowledgementSlots() {
    // No four-slot type 7 is in the corpus, so the stride is checked against a payload assembled
    // here. The assembly is only trustworthy if it reproduces the capture that is in the corpus:
    val header = bits(7, 6) + bits(0, 2) + bits(244620320, 30) + bits(0, 2)
    assertEquals(
      Payloads.BINARY_ACKNOWLEDGE,
      payloadOf(header + bits(2268402, 30) + bits(3, 2)),
      "the synthetic encoding disagrees with the capture it is modelled on",
    )

    val slots = (1..4).joinToString("") { bits(it, 30) + bits(it - 1, 2) }
    val four = decode<AisBinaryAcknowledge>(payloadOf(header + slots))
    assertEquals(
      listOf(
        AisAcknowledgement(1, 0),
        AisAcknowledgement(2, 1),
        AisAcknowledgement(3, 2),
        AisAcknowledgement(4, 3),
      ),
      four.acknowledgements,
    )
  }

  @Test
  fun ignoresSlotsBeyondTheFourTheStandardAllows() {
    val header = bits(7, 6) + bits(0, 2) + bits(244620320, 30) + bits(0, 2)
    val slots = (1..5).joinToString("") { bits(it, 30) + bits(it - 1, 2) }
    // Five slots run to 200 bits, which is not a character boundary; the last four bits are fill.
    val overlong = decode<AisBinaryAcknowledge>(payloadOf(header + slots + "0000"), fillBits = 4)
    assertEquals(4, overlong.acknowledgements.size)
  }

  @Test
  fun acknowledgesNothingWhenTheSlotsNeverArrived() {
    // Truncated to 42 bits. Permissive by design, as everywhere else here: the sending station is
    // still identified, and the alternative is discarding a message that read fine as far as it
    // went.
    val truncated = decode<AisBinaryAcknowledge>(Payloads.BINARY_ACKNOWLEDGE.take(7))
    assertEquals(244620320, truncated.mmsi)
    assertEquals(emptyList(), truncated.acknowledgements)
  }
}

class AisRegistryTest {

  @Test
  fun reportsAnUnsupportedTypeRatherThanFailing() {
    // Type 8 is a binary broadcast: application-defined content this library does not decode. The
    // type and the payload are still there for an application that knows what to do with it.
    val result = AisRegistry.Default.decode("801uc0qNTPP0>PUPPP1BUPP0")
    val unsupported = assertIs<AisResult.Unsupported>(result)
    assertEquals(8, unsupported.messageType)
    assertEquals("801uc0qNTPP0>PUPPP1BUPP0", unsupported.payload.payload)
  }

  @Test
  fun reportsAPayloadTooShortToRead() {
    val result = AisRegistry.Default.decode("13u")
    assertTrue("too short" in assertIs<AisResult.Malformed>(result).reason)
  }

  @Test
  fun reportsAPayloadThatIsNotSixBitEncodedAtAll() {
    val result = AisRegistry.Default.decode("13u?XXXX")
    assertTrue("six-bit" in assertIs<AisResult.Malformed>(result).reason)
  }

  @Test
  fun decodesAMessageWhoseLengthIsNotWhatTheTypeSaysItShouldBe() {
    // Real transponders pad and truncate. The Java implementation checked the length against the
    // type and threw, discarding a type 5 whose name and destination read perfectly well.
    val truncated = Payloads.STATIC_AND_VOYAGE.dropLast(2)
    val message = decode<AisStaticAndVoyageData>(truncated, fillBits = 0)
    assertEquals("   ARCO AVON", message.name)
    assertEquals("  HOUSTON", message.destination, "text is read as far as the payload goes")
    assertNull(message.isDteReady, "the last bit of the message never arrived")
  }

  @Test
  fun canBeNarrowedAndExtended() {
    assertTrue(1 in AisRegistry.Default.types)
    assertFalse(1 in AisRegistry.Default.without(1).types)
    assertEquals(emptySet(), AisRegistry.Empty.types)

    val custom = AisRegistry.Empty.with(1, AisMessageFactory(AisPositionReport::from))
    assertIs<AisResult.Ok>(custom.decode(Payloads.POSITION_REPORT))
    assertIs<AisResult.Unsupported>(custom.decode(Payloads.CLASS_B))
  }
}
