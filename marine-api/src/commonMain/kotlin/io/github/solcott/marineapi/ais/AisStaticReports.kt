package io.github.solcott.marineapi.ais

/**
 * Who a Class A vessel is and where she is going: AIS message type 5.
 *
 * The counterpart to the position reports. A vessel sends this every six minutes, and it is what
 * turns a moving dot on a chart into a named ship with a destination. At 424 bits it does not fit
 * in one sentence, so it always arrives as two fragments -- see
 * [io.github.solcott.marineapi.ais.aisMessages].
 *
 * @property aisVersion which revision of the standard the transponder implements; 0 is the original
 * @property imoNumber the vessel's IMO number, which unlike the MMSI stays with the hull for life.
 *   `null` when the vessel reports none, which is normal below 100 gross tons.
 * @property callSign radio call sign, up to 7 characters
 * @property name vessel name, up to 20 characters
 * @property shipType what kind of vessel, as the code sent; [describeShipType] renders it
 * @property dimensions the vessel's extent, measured from its positioning antenna
 * @property epfd the kind of positioning device the vessel's fix comes from
 * @property eta estimated time of arrival at [destination]
 * @property maximumDraught metres, to a tenth, or `null` if the vessel reports none. What decides
 *   whether she can enter a port.
 * @property destination free text, up to 20 characters, and entered by hand -- so abbreviated,
 *   inconsistent and often left over from the last voyage.
 * @property isDteReady whether the vessel's data terminal is ready for higher-level messages, or
 *   `null` when the message stopped short of the flag. It is the last bit of the message and
 *   transponders that send 422 bits rather than 424 are common, so the alternative to `null` here
 *   is discarding a name and a destination that read perfectly well. The bit also means the
 *   opposite of its name -- 0 is ready -- and the Java implementation returned it unaltered from a
 *   method it called `isDteReady`, inverting the answer.
 */
public data class AisStaticAndVoyageData(
  override val messageType: Int,
  override val repeatIndicator: Int,
  override val mmsi: Int,
  val aisVersion: Int,
  val imoNumber: Int?,
  val callSign: String,
  val name: String,
  val shipType: Int,
  val dimensions: ShipDimensions,
  val epfd: EpfdType?,
  val eta: EstimatedArrival,
  val maximumDraught: Double?,
  val destination: String,
  val isDteReady: Boolean?,
) : AisMessage {

  public companion object {
    /** Message type decoded by this class. */
    public const val TYPE: Int = 5

    /** Bits in a complete type 5 message. */
    public const val LENGTH: Int = 424

    /** Reads a static and voyage related data report. */
    public fun from(bits: Sixbit): AisStaticAndVoyageData =
      AisStaticAndVoyageData(
        messageType = bits.uintAt(0, 6),
        repeatIndicator = bits.uintAt(6, 8),
        mmsi = bits.uintAt(8, 38),
        aisVersion = bits.uintAt(38, 40),
        imoNumber = bits.uintAt(40, 70).takeIf { it != 0 },
        callSign = bits.stringAt(70, 112),
        name = bits.stringAt(112, 232),
        shipType = bits.uintAt(232, 240),
        dimensions = bits.shipDimensionsAt(240),
        epfd = bits.codedAt(270, 274, EpfdType.entries),
        eta = bits.estimatedArrivalAt(274),
        maximumDraught = bits.uintAt(294, 302).takeIf { it != 0 }?.let { it / 10.0 },
        destination = bits.stringAt(302, 422),
        isDteReady = bits.booleanAtOrNull(422)?.not(),
      )
  }
}

/**
 * When a vessel expects to arrive, as AIS reports it: a month, a day and a time, with no year.
 *
 * A year would make this a date, and the format does not carry one -- an ETA more than a few months
 * out is not useful enough to be worth the bits. Any part may be absent on its own, since the
 * vessel enters them by hand and often enters some of them.
 *
 * @property month 1 to 12, or `null`
 * @property day 1 to 31, or `null`
 * @property hour 0 to 23, or `null`
 * @property minute 0 to 59, or `null`
 */
public data class EstimatedArrival(
  val month: Int?,
  val day: Int?,
  val hour: Int?,
  val minute: Int?,
) {
  /** True if the vessel gave no part of an arrival time at all. */
  public val isEmpty: Boolean
    get() = month == null && day == null && hour == null && minute == null
}

/** The four ETA fields, 4, 5, 5 and 6 bits from [from]. */
internal fun Sixbit.estimatedArrivalAt(from: Int): EstimatedArrival =
  EstimatedArrival(
    month = uintAt(from, from + 4).takeIf { it in 1..12 },
    day = uintAt(from + 4, from + 9).takeIf { it in 1..31 },
    hour = uintAt(from + 9, from + 14).takeIf { it in 0..23 },
    minute = uintAt(from + 14, from + 20).takeIf { it in 0..59 },
  )

/**
 * A Class B vessel's name: AIS message type 24, part A.
 *
 * Class B splits its static data across two messages that share a type code and are told apart by a
 * part number. Part A is the name and nothing else. The two halves arrive independently and may be
 * minutes apart, which is why they are separate types here rather than one class with half its
 * fields empty -- the implementation this replaces used a single class and left the other half at
 * zero, so a caller could not tell an unset field from a real one.
 *
 * @property name vessel name, up to 20 characters
 */
public data class AisStaticDataReport(
  override val messageType: Int,
  override val repeatIndicator: Int,
  override val mmsi: Int,
  val name: String,
) : AisMessage {

  public companion object {
    /** Message type decoded by this class and by [AisStaticDataReportB]. */
    public const val TYPE: Int = 24

    /** Reads part A of a static data report. */
    public fun from(bits: Sixbit): AisStaticDataReport =
      AisStaticDataReport(
        messageType = bits.uintAt(0, 6),
        repeatIndicator = bits.uintAt(6, 8),
        mmsi = bits.uintAt(8, 38),
        name = bits.stringAt(40, 160),
      )
  }
}

/**
 * A Class B vessel's type, dimensions and equipment: AIS message type 24, part B.
 *
 * The other half of what [AisStaticDataReport] begins.
 *
 * @property shipType what kind of vessel, as the code sent; [describeShipType] renders it
 * @property vendorId the transponder manufacturer's three-character code
 * @property unitModelCode the manufacturer's model number for the unit
 * @property serialNumber the unit's serial number
 * @property callSign radio call sign, up to 7 characters
 * @property dimensions the vessel's extent, measured from its positioning antenna
 */
public data class AisStaticDataReportB(
  override val messageType: Int,
  override val repeatIndicator: Int,
  override val mmsi: Int,
  val shipType: Int,
  val vendorId: String,
  val unitModelCode: Int,
  val serialNumber: Int,
  val callSign: String,
  val dimensions: ShipDimensions,
) : AisMessage {

  public companion object {
    /** Reads part B of a static data report. */
    public fun from(bits: Sixbit): AisStaticDataReportB =
      AisStaticDataReportB(
        messageType = bits.uintAt(0, 6),
        repeatIndicator = bits.uintAt(6, 8),
        mmsi = bits.uintAt(8, 38),
        shipType = bits.uintAt(40, 48),
        vendorId = bits.stringAt(48, 66),
        unitModelCode = bits.uintAt(66, 70),
        serialNumber = bits.uintAt(70, 90),
        callSign = bits.stringAt(90, 132),
        dimensions = bits.shipDimensionsAt(132),
      )
  }
}

/**
 * Reads whichever part of a type 24 report this payload carries.
 *
 * The part number is two bits at 38, and the two parts have different layouts from there on.
 */
internal fun staticDataReportFrom(bits: Sixbit): AisMessage =
  when (val part = bits.uintAt(38, 40)) {
    0 -> AisStaticDataReport.from(bits)
    1 -> AisStaticDataReportB.from(bits)
    else -> throw IllegalArgumentException("A type 24 report is part 0 or part 1, not $part")
  }
