package io.github.solcott.marineapi.ais

import io.github.solcott.marineapi.nmea.Position

/**
 * Where a vessel is and how it is moving: AIS message types 1, 2 and 3.
 *
 * The commonest message on the air. All three types carry identical fields and differ only in why
 * they were sent -- 1 is a scheduled report, 2 an assigned one, 3 a response to an interrogation --
 * so [messageType] is the only way to tell them apart, and rarely worth doing.
 *
 * @property navigationalStatus what the vessel is doing, as its master set it. Manually entered,
 *   and so routinely stale: a vessel at anchor reporting `UNDER_WAY_USING_ENGINE` is a common
 *   sight.
 * @property rateOfTurn degrees per minute, negative to port, or `null` when not measured
 * @property rateOfTurnCode the rate-of-turn field as sent, which distinguishes "not measured"
 *   (`null`) from "turning faster than 5 degrees in 30 seconds" (+/-127), both of which leave
 *   [rateOfTurn] `null`
 * @property heading degrees true the bow points, which is not [courseOverGround]: the difference
 *   between them is how far the vessel is being set sideways by wind and tide
 * @property maneuver whether the vessel is engaged in a special manoeuvre
 * @property hasRaim whether the receiver's integrity monitoring is in use
 */
public data class AisPositionReport(
  override val messageType: Int,
  override val repeatIndicator: Int,
  override val mmsi: Int,
  val navigationalStatus: NavigationalStatus?,
  val rateOfTurn: Double?,
  val rateOfTurnCode: Int?,
  override val speedOverGround: Double?,
  override val isAccurate: Boolean,
  override val position: Position?,
  override val courseOverGround: Double?,
  val heading: Int?,
  override val utcSecond: Int?,
  val maneuver: ManeuverIndicator?,
  val hasRaim: Boolean,
) : AisVesselPositionMessage {

  public companion object {
    /** Message types decoded by this class. */
    public val TYPES: List<Int> = listOf(1, 2, 3)

    /** Reads a scheduled, assigned or interrogated position report. */
    public fun from(bits: Sixbit): AisPositionReport =
      AisPositionReport(
        messageType = bits.uintAt(0, 6),
        repeatIndicator = bits.uintAt(6, 8),
        mmsi = bits.uintAt(8, 38),
        navigationalStatus = bits.codedAt(38, 42, NavigationalStatus.entries),
        rateOfTurn = bits.rateOfTurnAt(42, 50),
        rateOfTurnCode = bits.rateOfTurnCodeAt(42, 50),
        speedOverGround = bits.speedOverGroundAt(50, 60),
        isAccurate = bits.booleanAt(60),
        position = bits.positionAt(61, 89, 89, 116),
        courseOverGround = bits.courseOverGroundAt(116, 128),
        heading = bits.headingAt(128, 137),
        utcSecond = bits.utcSecondAt(137, 143),
        maneuver = bits.codedAt(143, 145, ManeuverIndicator.entries),
        hasRaim = bits.booleanAt(148),
      )
  }
}

/**
 * Where a Class B vessel is: AIS message type 18.
 *
 * Class B is the smaller, cheaper transponder carried by leisure and small commercial craft. It
 * reports position and movement but nothing about the vessel itself -- no name, no dimensions, not
 * even a navigational status -- which arrive separately in [AisStaticDataReport].
 *
 * @property heading degrees true the bow points, or `null` if the unit has no heading sensor, which
 *   many Class B units do not
 * @property isClassBCarrierSotdma `true` if the unit uses the SOTDMA carrier scheme rather than the
 *   simpler CSTDMA one
 * @property hasDisplay whether the unit is attached to a display that can show received messages
 * @property hasDscCapability whether the unit is attached to a VHF radio with digital selective
 *   calling
 * @property isBandFlagSet whether the unit can use the whole marine band rather than the default
 *   two channels
 * @property canAcceptMessage22 whether the unit accepts channel assignment by a base station
 * @property isAssigned whether the unit is operating in assigned rather than autonomous mode
 * @property hasRaim whether the receiver's integrity monitoring is in use
 */
public data class AisPositionReportB(
  override val messageType: Int,
  override val repeatIndicator: Int,
  override val mmsi: Int,
  override val speedOverGround: Double?,
  override val isAccurate: Boolean,
  override val position: Position?,
  override val courseOverGround: Double?,
  val heading: Int?,
  override val utcSecond: Int?,
  val isClassBCarrierSotdma: Boolean,
  val hasDisplay: Boolean,
  val hasDscCapability: Boolean,
  val isBandFlagSet: Boolean,
  val canAcceptMessage22: Boolean,
  val isAssigned: Boolean,
  val hasRaim: Boolean,
) : AisVesselPositionMessage {

  public companion object {
    /** Message type decoded by this class. */
    public const val TYPE: Int = 18

    /** Reads a Class B position report. */
    public fun from(bits: Sixbit): AisPositionReportB =
      AisPositionReportB(
        messageType = bits.uintAt(0, 6),
        repeatIndicator = bits.uintAt(6, 8),
        mmsi = bits.uintAt(8, 38),
        speedOverGround = bits.speedOverGroundAt(46, 56),
        // Bit 56, not 55. The implementation this replaces read the bit before it, which is the
        // last bit of the speed field, and so reported low accuracy for a vessel claiming high --
        // its own test fixture is one, and pinned the wrong answer.
        isAccurate = bits.booleanAt(56),
        position = bits.positionAt(57, 85, 85, 112),
        courseOverGround = bits.courseOverGroundAt(112, 124),
        heading = bits.headingAt(124, 133),
        utcSecond = bits.utcSecondAt(133, 139),
        isClassBCarrierSotdma = bits.booleanAt(141),
        hasDisplay = bits.booleanAt(142),
        hasDscCapability = bits.booleanAt(143),
        isBandFlagSet = bits.booleanAt(144),
        canAcceptMessage22 = bits.booleanAt(145),
        isAssigned = bits.booleanAt(146),
        hasRaim = bits.booleanAt(147),
      )
  }
}

/**
 * Where a Class B vessel is, and what she is: AIS message type 19.
 *
 * Everything [AisPositionReportB] carries, plus the name and dimensions that Class B normally has
 * to send separately. Rarely transmitted -- it is twice the length of a type 18 and takes two slots
 * -- but it saves a receiver having to correlate two message types to label a target.
 *
 * @property name vessel name, up to 20 characters
 * @property shipType what kind of vessel, as the code sent; [describeShipType] renders it
 * @property dimensions distances from the position-reporting antenna to the vessel's four sides
 * @property epfd the kind of positioning device the fix came from
 */
public data class AisExtendedPositionReportB(
  override val messageType: Int,
  override val repeatIndicator: Int,
  override val mmsi: Int,
  override val speedOverGround: Double?,
  override val isAccurate: Boolean,
  override val position: Position?,
  override val courseOverGround: Double?,
  val heading: Int?,
  override val utcSecond: Int?,
  val name: String,
  val shipType: Int,
  val dimensions: ShipDimensions,
  val epfd: EpfdType?,
  val hasRaim: Boolean,
  val isAssigned: Boolean,
) : AisVesselPositionMessage {

  public companion object {
    /** Message type decoded by this class. */
    public const val TYPE: Int = 19

    /** Reads an extended Class B position report. */
    public fun from(bits: Sixbit): AisExtendedPositionReportB =
      AisExtendedPositionReportB(
        messageType = bits.uintAt(0, 6),
        repeatIndicator = bits.uintAt(6, 8),
        mmsi = bits.uintAt(8, 38),
        speedOverGround = bits.speedOverGroundAt(46, 56),
        isAccurate = bits.booleanAt(56),
        position = bits.positionAt(57, 85, 85, 112),
        courseOverGround = bits.courseOverGroundAt(112, 124),
        heading = bits.headingAt(124, 133),
        utcSecond = bits.utcSecondAt(133, 139),
        name = bits.stringAt(143, 263),
        shipType = bits.uintAt(263, 271),
        dimensions = bits.shipDimensionsAt(271),
        epfd = bits.codedAt(301, 305, EpfdType.entries),
        hasRaim = bits.booleanAt(305),
        isAssigned = bits.booleanAt(307),
      )
  }
}

/**
 * A coarse position for long-range reception: AIS message type 27.
 *
 * Sent for satellite receivers, which cannot pick out the ordinary reports from orbit. It fits in
 * 96 bits by dropping resolution: coordinates are tenths of a minute rather than ten-thousandths,
 * about 185 metres rather than 20 centimetres, and speed and course are whole knots and whole
 * degrees.
 *
 * @property navigationalStatus what the vessel is doing. The implementation this replaces read this
 *   from a bit range whose ends were the wrong way round, which made it return
 *   [NavigationalStatus.UNDER_WAY_USING_ENGINE] for every type 27 message ever decoded.
 * @property isCurrent `false` if the position is more than five seconds old, which is the normal
 *   case for this message type
 * @property hasRaim whether the receiver's integrity monitoring is in use
 */
public data class AisLongRangePositionReport(
  override val messageType: Int,
  override val repeatIndicator: Int,
  override val mmsi: Int,
  override val isAccurate: Boolean,
  val hasRaim: Boolean,
  val navigationalStatus: NavigationalStatus?,
  override val position: Position?,
  override val speedOverGround: Double?,
  override val courseOverGround: Double?,
  val isCurrent: Boolean,
) : AisVesselPositionMessage {

  /** Type 27 carries no time of fix at all, so this is always `null`. */
  override val utcSecond: Int?
    get() = null

  public companion object {
    /** Message type decoded by this class. */
    public const val TYPE: Int = 27

    /** Speed is whole knots here, and this value means the vessel reports none. */
    private const val SPEED_UNAVAILABLE = 63

    /** Course is whole degrees here, and this value means the vessel reports none. */
    private const val COURSE_UNAVAILABLE = 511

    /** Reads a long-range position report. */
    public fun from(bits: Sixbit): AisLongRangePositionReport =
      AisLongRangePositionReport(
        messageType = bits.uintAt(0, 6),
        repeatIndicator = bits.uintAt(6, 8),
        mmsi = bits.uintAt(8, 38),
        isAccurate = bits.booleanAt(38),
        hasRaim = bits.booleanAt(39),
        navigationalStatus = bits.codedAt(40, 44, NavigationalStatus.entries),
        position = bits.positionAt(44, 62, 62, 79, scale = COARSE_COORDINATE_SCALE),
        // Whole knots and whole degrees, not tenths: the resolution went the same way as the
        // coordinates' did, to fit the message into 96 bits.
        speedOverGround = bits.uintAt(79, 85).takeIf { it != SPEED_UNAVAILABLE }?.toDouble(),
        courseOverGround = bits.uintAt(85, 94).takeIf { it != COURSE_UNAVAILABLE }?.toDouble(),
        isCurrent = !bits.booleanAt(94),
      )
  }
}

/**
 * How big a vessel is, measured from the antenna that reports its position rather than from any
 * point on the hull.
 *
 * So the vessel's length is [toBow] plus [toStern] and its beam is [toPort] plus [toStarboard],
 * while the reported position sits at the origin of the four. A value of 0 means "not available",
 * and a bow or stern reading of 511, or a port or starboard reading of 63, means the vessel is
 * larger than the field can express.
 */
public data class ShipDimensions(
  val toBow: Int,
  val toStern: Int,
  val toPort: Int,
  val toStarboard: Int,
) {
  /** Overall length in metres, or `null` if either measurement is missing. */
  public val length: Int?
    get() = if (toBow == 0 || toStern == 0) null else toBow + toStern

  /** Overall beam in metres, or `null` if either measurement is missing. */
  public val beam: Int?
    get() = if (toPort == 0 || toStarboard == 0) null else toPort + toStarboard
}

/**
 * The four dimension fields, which appear in the same order and widths in types 5, 19, 21 and 24.
 *
 * @param from the first bit of the bow measurement
 */
internal fun Sixbit.shipDimensionsAt(from: Int): ShipDimensions =
  ShipDimensions(
    toBow = uintAt(from, from + 9),
    toStern = uintAt(from + 9, from + 18),
    toPort = uintAt(from + 18, from + 24),
    toStarboard = uintAt(from + 24, from + 30),
  )
