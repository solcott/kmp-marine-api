package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.DataStatus
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceFields
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.Units
import io.github.solcott.marineapi.nmea.buildNmea
import io.github.solcott.marineapi.nmea.field

/**
 * Water speed and heading: how fast the vessel is moving through the water, and where it is
 * pointing.
 *
 * Example: `$IIVHW,,,347,M,0.00,N,,*64`
 *
 * Speed here is relative to the water, not the ground -- a vessel holding station against a
 * three-knot current reports three knots. Compare VTG, which reports speed over ground.
 *
 * The example above is a real line from this project's sample logs, and it shows why the marker
 * fields are not worth reading: this device omits `T` and `K` when their values are absent, while
 * the same device in the same capture keeps `T` in an MWD with an empty value and keeps `T` but
 * drops `K` in a VTG. The markers name the value beside them and carry nothing else, so they are
 * written back as constants rather than echoed.
 *
 * gpsd notes that at least one manufacturer documents a different layout in which the first three
 * fields are water temperature; nothing here can tell the two apart, so this reads the standard
 * form.
 *
 * @property headingTrue heading in degrees true
 * @property headingMagnetic heading in degrees magnetic
 * @property speedKnots speed through the water in knots
 * @property speedKmh speed through the water in km/h
 */
public data class Vhw(
  override val talker: TalkerId,
  val headingTrue: Double? = null,
  val headingMagnetic: Double? = null,
  val speedKnots: Double? = null,
  val speedKmh: Double? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        headingTrue.field(),
        TRUE_MARKER.toString(),
        headingMagnetic.field(),
        MAGNETIC_MARKER.toString(),
        speedKnots.field(),
        KNOTS_MARKER.toString(),
        speedKmh.field(),
        KMH_MARKER.toString(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "VHW"

    private const val TRUE_MARKER = 'T'
    private const val MAGNETIC_MARKER = 'M'
    private const val KNOTS_MARKER = 'N'
    private const val KMH_MARKER = 'K'

    private const val HEADING_TRUE = 0
    private const val HEADING_MAGNETIC = 2
    private const val SPEED_KNOTS = 4
    private const val SPEED_KMH = 6

    /** Reads a VHW sentence from its fields. */
    public fun from(fields: SentenceFields): Vhw =
      Vhw(
        talker = fields.talker,
        headingTrue = fields.doubleAt(HEADING_TRUE),
        headingMagnetic = fields.doubleAt(HEADING_MAGNETIC),
        speedKnots = fields.doubleAt(SPEED_KNOTS),
        speedKmh = fields.doubleAt(SPEED_KMH),
      )
  }
}

/**
 * Distance travelled through the water, and over the ground.
 *
 * Example: `$IIVLW,1958.64,N,1958.64,N*4D`
 *
 * NMEA 3 added the two ground-distance values; the parser this replaces declared four fields and so
 * could never read them. Older logs -- including the one above -- carry only the water pair.
 *
 * Each distance has its own unit field. The format documents `N` throughout, but the implementation
 * this replaces accepted `K` as well, so both are read rather than turning a device that reports
 * kilometres into a parse failure.
 *
 * @property totalWaterDistance cumulative distance through the water since the log was installed
 * @property totalWaterUnits unit of [totalWaterDistance]
 * @property tripWaterDistance distance through the water since the trip counter was reset
 * @property tripWaterUnits unit of [tripWaterDistance]
 * @property totalGroundDistance cumulative distance over ground, added in NMEA 3
 * @property totalGroundUnits unit of [totalGroundDistance]
 * @property tripGroundDistance distance over ground since reset, added in NMEA 3
 * @property tripGroundUnits unit of [tripGroundDistance]
 */
public data class Vlw(
  override val talker: TalkerId,
  val totalWaterDistance: Double? = null,
  val totalWaterUnits: Units? = null,
  val tripWaterDistance: Double? = null,
  val tripWaterUnits: Units? = null,
  val totalGroundDistance: Double? = null,
  val totalGroundUnits: Units? = null,
  val tripGroundDistance: Double? = null,
  val tripGroundUnits: Units? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      buildList {
        add(totalWaterDistance.field())
        add(totalWaterUnits.field())
        add(tripWaterDistance.field())
        add(tripWaterUnits.field())
        // The ground pair is NMEA 3; appending empty fields to a sentence that arrived without
        // them would claim the device reports something it does not.
        if (hasGroundDistances) {
          add(totalGroundDistance.field())
          add(totalGroundUnits.field())
          add(tripGroundDistance.field())
          add(tripGroundUnits.field())
        }
      },
    )

  private val hasGroundDistances: Boolean
    get() =
      totalGroundDistance != null ||
        totalGroundUnits != null ||
        tripGroundDistance != null ||
        tripGroundUnits != null

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "VLW"

    private const val TOTAL_WATER = 0
    private const val TOTAL_WATER_UNITS = 1
    private const val TRIP_WATER = 2
    private const val TRIP_WATER_UNITS = 3
    private const val TOTAL_GROUND = 4
    private const val TOTAL_GROUND_UNITS = 5
    private const val TRIP_GROUND = 6
    private const val TRIP_GROUND_UNITS = 7

    /** Reads a VLW sentence from its fields. */
    public fun from(fields: SentenceFields): Vlw =
      Vlw(
        talker = fields.talker,
        totalWaterDistance = fields.doubleAt(TOTAL_WATER),
        totalWaterUnits = fields.codedAt(TOTAL_WATER_UNITS, DISTANCE_UNITS),
        tripWaterDistance = fields.doubleAt(TRIP_WATER),
        tripWaterUnits = fields.codedAt(TRIP_WATER_UNITS, DISTANCE_UNITS),
        totalGroundDistance = fields.doubleAt(TOTAL_GROUND),
        totalGroundUnits = fields.codedAt(TOTAL_GROUND_UNITS, DISTANCE_UNITS),
        tripGroundDistance = fields.doubleAt(TRIP_GROUND),
        tripGroundUnits = fields.codedAt(TRIP_GROUND_UNITS, DISTANCE_UNITS),
      )

    private val DISTANCE_UNITS = listOf(Units.NAUTICAL_MILES, Units.KILOMETERS)
  }
}

/**
 * Dual ground and water speed, along and across the vessel.
 *
 * Example: `$IIVBW,11.0,02.0,A,10.0,03.0,A,05.3,A,01.0,A*5A`
 *
 * The signs matter: a negative longitudinal speed means astern, and a negative transverse speed
 * means the vessel is being set to port. Each pair has its own validity flag, so a log with a
 * working water sensor and a failed ground sensor can say so.
 *
 * @property longitudinalWaterSpeed speed through the water along the hull, knots; negative is
 *   astern
 * @property transverseWaterSpeed speed through the water across the hull, knots; negative is to
 *   port
 * @property waterSpeedStatus whether the two water speeds are valid
 * @property longitudinalGroundSpeed speed over ground along the hull, knots; negative is astern
 * @property transverseGroundSpeed speed over ground across the hull, knots; negative is to port
 * @property groundSpeedStatus whether the two ground speeds are valid
 * @property sternTransverseWaterSpeed transverse water speed at the stern, knots; added in NMEA 3
 * @property sternWaterSpeedStatus whether [sternTransverseWaterSpeed] is valid
 * @property sternTransverseGroundSpeed transverse ground speed at the stern, knots; added in NMEA 3
 * @property sternGroundSpeedStatus whether [sternTransverseGroundSpeed] is valid
 */
public data class Vbw(
  override val talker: TalkerId,
  val longitudinalWaterSpeed: Double? = null,
  val transverseWaterSpeed: Double? = null,
  val waterSpeedStatus: DataStatus? = null,
  val longitudinalGroundSpeed: Double? = null,
  val transverseGroundSpeed: Double? = null,
  val groundSpeedStatus: DataStatus? = null,
  val sternTransverseWaterSpeed: Double? = null,
  val sternWaterSpeedStatus: DataStatus? = null,
  val sternTransverseGroundSpeed: Double? = null,
  val sternGroundSpeedStatus: DataStatus? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        longitudinalWaterSpeed.field(),
        transverseWaterSpeed.field(),
        waterSpeedStatus.field(),
        longitudinalGroundSpeed.field(),
        transverseGroundSpeed.field(),
        groundSpeedStatus.field(),
        sternTransverseWaterSpeed.field(),
        sternWaterSpeedStatus.field(),
        sternTransverseGroundSpeed.field(),
        sternGroundSpeedStatus.field(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "VBW"

    private const val LONGITUDINAL_WATER = 0
    private const val TRANSVERSE_WATER = 1
    private const val WATER_STATUS = 2
    private const val LONGITUDINAL_GROUND = 3
    private const val TRANSVERSE_GROUND = 4
    private const val GROUND_STATUS = 5
    private const val STERN_WATER = 6
    private const val STERN_WATER_STATUS = 7
    private const val STERN_GROUND = 8
    private const val STERN_GROUND_STATUS = 9

    /** Reads a VBW sentence from its fields. */
    public fun from(fields: SentenceFields): Vbw =
      Vbw(
        talker = fields.talker,
        longitudinalWaterSpeed = fields.doubleAt(LONGITUDINAL_WATER),
        transverseWaterSpeed = fields.doubleAt(TRANSVERSE_WATER),
        waterSpeedStatus = fields.codedAt(WATER_STATUS, DataStatus.entries),
        longitudinalGroundSpeed = fields.doubleAt(LONGITUDINAL_GROUND),
        transverseGroundSpeed = fields.doubleAt(TRANSVERSE_GROUND),
        groundSpeedStatus = fields.codedAt(GROUND_STATUS, DataStatus.entries),
        sternTransverseWaterSpeed = fields.doubleAt(STERN_WATER),
        sternWaterSpeedStatus = fields.codedAt(STERN_WATER_STATUS, DataStatus.entries),
        sternTransverseGroundSpeed = fields.doubleAt(STERN_GROUND),
        sternGroundSpeedStatus = fields.codedAt(STERN_GROUND_STATUS, DataStatus.entries),
      )
  }
}

/**
 * Set and drift: the direction the current is flowing towards, and how fast.
 *
 * Example: `$IIVDR,10.0,T,12.0,M,1.5,N*3A`
 *
 * "Set" is the direction, "drift" the speed. Note the direction is where the water is going, not
 * where it comes from -- the opposite convention to wind.
 *
 * @property directionTrue set in degrees true
 * @property directionMagnetic set in degrees magnetic
 * @property speedKnots drift in knots
 */
public data class Vdr(
  override val talker: TalkerId,
  val directionTrue: Double? = null,
  val directionMagnetic: Double? = null,
  val speedKnots: Double? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        directionTrue.field(),
        TRUE_MARKER.toString(),
        directionMagnetic.field(),
        MAGNETIC_MARKER.toString(),
        speedKnots.field(),
        KNOTS_MARKER.toString(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "VDR"

    private const val TRUE_MARKER = 'T'
    private const val MAGNETIC_MARKER = 'M'
    private const val KNOTS_MARKER = 'N'

    private const val DIRECTION_TRUE = 0
    private const val DIRECTION_MAGNETIC = 2
    private const val SPEED_KNOTS = 4

    /** Reads a VDR sentence from its fields. */
    public fun from(fields: SentenceFields): Vdr =
      Vdr(
        talker = fields.talker,
        directionTrue = fields.doubleAt(DIRECTION_TRUE),
        directionMagnetic = fields.doubleAt(DIRECTION_MAGNETIC),
        speedKnots = fields.doubleAt(SPEED_KNOTS),
      )
  }
}

/**
 * Speed measured parallel to the wind -- how fast the vessel is making ground directly upwind or
 * downwind.
 *
 * Example: `$IIVPW,00.00,N,,*31`
 *
 * A negative speed means downwind. This is the sentence a racing sailor's velocity-made-good
 * display is built on.
 *
 * The implementation this replaces had no parser for VPW at all, so the three VPW lines in this
 * project's own sample logs went unread.
 *
 * @property speedKnots speed parallel to the wind in knots; negative is downwind
 * @property speedMetersPerSecond the same speed in metres per second
 */
public data class Vpw(
  override val talker: TalkerId,
  val speedKnots: Double? = null,
  val speedMetersPerSecond: Double? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        speedKnots.field(),
        KNOTS_MARKER.toString(),
        speedMetersPerSecond.field(),
        METERS_MARKER.toString(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "VPW"

    private const val KNOTS_MARKER = 'N'
    private const val METERS_MARKER = 'M'

    private const val SPEED_KNOTS = 0
    private const val SPEED_METERS = 2

    /** Reads a VPW sentence from its fields. */
    public fun from(fields: SentenceFields): Vpw =
      Vpw(
        talker = fields.talker,
        speedKnots = fields.doubleAt(SPEED_KNOTS),
        speedMetersPerSecond = fields.doubleAt(SPEED_METERS),
      )
  }
}
