package io.github.solcott.marineapi.ais

import io.github.solcott.marineapi.nmea.Position
import kotlinx.datetime.LocalDateTime

/**
 * A shore station reporting the time and its own position: AIS message types 4 and 11.
 *
 * Type 4 is a base station's regular broadcast; type 11 is the same report sent by a mobile station
 * in reply to being asked for UTC. Base stations know where they are to survey accuracy and are the
 * time reference other stations synchronise to, which is why this is the one position report built
 * around a full date.
 *
 * @property utc the station's date and time, or `null` if it reports none. Unlike every other time
 *   in AIS this is complete, with a year.
 * @property epfd the kind of positioning device the fix came from, usually [EpfdType.SURVEYED] for
 *   a base station that was placed rather than located
 * @property hasRaim whether the receiver's integrity monitoring is in use
 */
public data class AisBaseStationReport(
  override val messageType: Int,
  override val repeatIndicator: Int,
  override val mmsi: Int,
  val utc: LocalDateTime?,
  override val isAccurate: Boolean,
  override val position: Position?,
  val epfd: EpfdType?,
  val hasRaim: Boolean,
) : AisPositionMessage {

  public companion object {
    /** Message types decoded by this class. */
    public val TYPES: List<Int> = listOf(4, 11)

    /** Reads a base station report or a UTC response. */
    public fun from(bits: Sixbit): AisBaseStationReport =
      AisBaseStationReport(
        messageType = bits.uintAt(0, 6),
        repeatIndicator = bits.uintAt(6, 8),
        mmsi = bits.uintAt(8, 38),
        utc = bits.utcDateTimeAt(38),
        // Bit 78. The implementation this replaces read bit 77, the last bit of the seconds field.
        isAccurate = bits.booleanAt(78),
        position = bits.positionAt(79, 107, 107, 134),
        epfd = bits.codedAt(134, 138, EpfdType.entries),
        hasRaim = bits.booleanAt(148),
      )

    /**
     * The six date and time fields starting at [from], as one value.
     *
     * Any one of them may be out of range -- a station with no time source sends a year of 0 -- and
     * a date missing a part is not a date, so the whole thing reads as `null` rather than being
     * assembled from whatever happened to be valid.
     */
    private fun Sixbit.utcDateTimeAt(from: Int): LocalDateTime? {
      val year = uintAt(from, from + 14)
      val month = uintAt(from + 14, from + 18)
      val day = uintAt(from + 18, from + 23)
      val hour = uintAt(from + 23, from + 28)
      val minute = uintAt(from + 28, from + 34)
      val second = uintAt(from + 34, from + 40)
      if (year == 0 || month !in 1..12 || day !in 1..31) return null
      if (hour > 23 || minute > 59 || second > 59) return null
      return runCatching { LocalDateTime(year, month, day, hour, minute, second) }.getOrNull()
    }
  }
}

/**
 * Where a search and rescue aircraft is: AIS message type 9.
 *
 * The one AIS position report with an altitude, because it is the one sent by something that flies.
 * It has no heading and no navigational status, and its speed is over the ground in knots like
 * every other AIS speed rather than an airspeed.
 *
 * @property altitude metres above sea level, or `null` if the aircraft reports none. 4094 metres or
 *   higher is reported as exactly 4094, the top of the scale.
 * @property speedOverGround knots, or `null` if the aircraft reports none. Whole knots here, not
 *   the tenths every other message type uses -- an aircraft needs the range more than the
 *   resolution.
 * @property isDteReady whether the aircraft's data terminal is ready for higher-level messages. The
 *   bit means the opposite of its name: 0 is ready. The Java implementation returned it unaltered
 *   from a method called `getDTEFlag`, and its counterpart on the type 5 message was called
 *   `isDteReady` while still returning the raw bit, so it reported a vessel as not ready precisely
 *   when it had said it was.
 * @property isAssigned whether the station is operating in assigned rather than autonomous mode
 * @property hasRaim whether the receiver's integrity monitoring is in use
 */
public data class AisSarAircraftPositionReport(
  override val messageType: Int,
  override val repeatIndicator: Int,
  override val mmsi: Int,
  val altitude: Int?,
  override val speedOverGround: Double?,
  override val isAccurate: Boolean,
  override val position: Position?,
  override val courseOverGround: Double?,
  override val utcSecond: Int?,
  val isDteReady: Boolean,
  val isAssigned: Boolean,
  val hasRaim: Boolean,
) : AisVesselPositionMessage {

  public companion object {
    /** Message type decoded by this class. */
    public const val TYPE: Int = 9

    /** An altitude of 4095 means the aircraft reports none. */
    private const val ALTITUDE_UNAVAILABLE = 4095

    /** A speed of 1023 means the aircraft reports none, as elsewhere -- but the unit differs. */
    private const val SPEED_UNAVAILABLE = 1023

    /** Reads a search and rescue aircraft position report. */
    public fun from(bits: Sixbit): AisSarAircraftPositionReport =
      AisSarAircraftPositionReport(
        messageType = bits.uintAt(0, 6),
        repeatIndicator = bits.uintAt(6, 8),
        mmsi = bits.uintAt(8, 38),
        altitude = bits.uintAt(38, 50).takeIf { it != ALTITUDE_UNAVAILABLE },
        // Whole knots, unlike every other AIS speed field.
        speedOverGround = bits.uintAt(50, 60).takeIf { it != SPEED_UNAVAILABLE }?.toDouble(),
        isAccurate = bits.booleanAt(60),
        position = bits.positionAt(61, 89, 89, 116),
        courseOverGround = bits.courseOverGroundAt(116, 128),
        utcSecond = bits.utcSecondAt(128, 134),
        // Bits 142, 146 and 147. The implementation this replaces read 42, 145 and 146: the bit
        // ranges for the last three flags of this message had their ends transposed, and the DTE
        // one carried 43 where 142 belongs, which put it inside the altitude field.
        isDteReady = !bits.booleanAt(142),
        isAssigned = bits.booleanAt(146),
        hasRaim = bits.booleanAt(147),
      )
  }
}

/**
 * Where a navigation aid is, and whether it is still there: AIS message type 21.
 *
 * Buoys, beacons, lighthouses and offshore structures. The interesting field is [isOffPosition]: a
 * lit buoy that has dragged its mooring is a hazard rather than a mark, and this is how it says so.
 *
 * @property aidType what kind of mark this is
 * @property name the mark's name, up to 34 characters. Long names are split across two fields in
 *   the message; this is both, joined.
 * @property dimensions the mark's extent, measured from its antenna
 * @property isOffPosition `true` if the aid has drifted from its charted position
 * @property isVirtual `true` if there is no physical mark at all and the aid exists only as this
 *   broadcast -- used to mark a new wreck before a buoy can be laid
 * @property isAssigned whether the station is operating in assigned rather than autonomous mode
 * @property hasRaim whether the receiver's integrity monitoring is in use
 */
public data class AisAidToNavigationReport(
  override val messageType: Int,
  override val repeatIndicator: Int,
  override val mmsi: Int,
  val aidType: AidType?,
  val name: String,
  override val isAccurate: Boolean,
  override val position: Position?,
  val dimensions: ShipDimensions,
  val epfd: EpfdType?,
  val utcSecond: Int?,
  val isOffPosition: Boolean,
  val isVirtual: Boolean,
  val isAssigned: Boolean,
  val hasRaim: Boolean,
) : AisPositionMessage {

  public companion object {
    /** Message type decoded by this class. */
    public const val TYPE: Int = 21

    /** The name field proper, before the extension that carries any of it that did not fit. */
    private const val NAME_END = 163

    private const val NAME_EXTENSION_START = 272

    /** The furthest the extension can reach; a message carrying less simply stops sooner. */
    private const val NAME_EXTENSION_END = 360

    /** Reads an aid-to-navigation report. */
    public fun from(bits: Sixbit): AisAidToNavigationReport =
      AisAidToNavigationReport(
        messageType = bits.uintAt(0, 6),
        repeatIndicator = bits.uintAt(6, 8),
        mmsi = bits.uintAt(8, 38),
        aidType = bits.codedAt(38, 43, AidType.entries),
        name = bits.aidNameAt(43),
        isAccurate = bits.booleanAt(163),
        position = bits.positionAt(164, 192, 192, 219),
        dimensions = bits.shipDimensionsAt(219),
        epfd = bits.codedAt(249, 253, EpfdType.entries),
        utcSecond = bits.utcSecondAt(253, 259),
        isOffPosition = bits.booleanAt(259),
        isVirtual = bits.booleanAt(269),
        isAssigned = bits.booleanAt(270),
        hasRaim = bits.booleanAt(268),
      )

    /**
     * The mark's name, with the extension field appended.
     *
     * The extension is optional and variable-length -- a message carrying none simply stops at bit
     * 272 -- so [Sixbit.stringAt] reading only as far as the payload goes is what makes this one
     * expression. The Java implementation returned the two fields separately and left joining them
     * to the caller.
     */
    private fun Sixbit.aidNameAt(from: Int): String =
      stringAt(from, NAME_END) + stringAt(NAME_EXTENSION_START, NAME_EXTENSION_END)
  }
}
