package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.AngleReference
import io.github.solcott.marineapi.nmea.DataStatus
import io.github.solcott.marineapi.nmea.Direction
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceFields
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.Units
import io.github.solcott.marineapi.nmea.buildNmea
import io.github.solcott.marineapi.nmea.field

/**
 * Wind speed and angle, as a masthead instrument reports it.
 *
 * Example: `$IIMWV,125.1,T,5.5,M,A*36`
 *
 * @property windAngle degrees, 0 to 359, measured against [reference]
 * @property reference whether [windAngle] is relative to the vessel or true
 * @property windSpeed speed in [speedUnits]
 * @property speedUnits `K` for km/h, `M` for m/s, `N` for knots
 * @property status whether the reading is valid
 */
public data class Mwv(
  override val talker: TalkerId,
  val windAngle: Double? = null,
  val reference: AngleReference? = null,
  val windSpeed: Double? = null,
  val speedUnits: Units? = null,
  val status: DataStatus? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        windAngle.field(),
        reference.field(),
        windSpeed.field(),
        speedUnits.field(),
        status.field(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "MWV"

    private const val WIND_ANGLE = 0
    private const val REFERENCE = 1
    private const val WIND_SPEED = 2
    private const val SPEED_UNITS = 3
    private const val STATUS = 4

    // The field is documented as K, M or N; it is not a free choice among every unit code.
    private val WIND_SPEED_UNITS = listOf(Units.KILOMETERS, Units.METER, Units.NAUTICAL_MILES)

    /** Reads an MWV sentence from its fields. */
    public fun from(fields: SentenceFields): Mwv =
      Mwv(
        talker = fields.talker,
        windAngle = fields.doubleAt(WIND_ANGLE),
        reference = fields.codedAt(REFERENCE, AngleReference.entries),
        windSpeed = fields.doubleAt(WIND_SPEED),
        speedUnits = fields.codedAt(SPEED_UNITS, WIND_SPEED_UNITS),
        status = fields.advisoryCodedAt(STATUS, DataStatus.entries),
      )
  }
}

/**
 * Wind direction and speed, reported against both true and magnetic north and in both units.
 *
 * Example: `$WIMWD,302.4,T,289.6,M,10.5,N,5.4,M*6F`
 *
 * The `T`, `M`, `N` and `M` fields that follow each value are fixed markers naming which value
 * precedes them, so they are written back as constants rather than exposed.
 *
 * @property directionTrue wind direction in degrees true, 0 to 359.9
 * @property directionMagnetic wind direction in degrees magnetic, 0 to 359.9
 * @property speedKnots wind speed in knots
 * @property speedMetersPerSecond wind speed in metres per second
 */
public data class Mwd(
  override val talker: TalkerId,
  val directionTrue: Double? = null,
  val directionMagnetic: Double? = null,
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
        directionTrue.field(),
        TRUE_MARKER.toString(),
        directionMagnetic.field(),
        MAGNETIC_MARKER.toString(),
        speedKnots.field(),
        KNOTS_MARKER.toString(),
        speedMetersPerSecond.field(),
        METERS_MARKER.toString(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "MWD"

    private const val TRUE_MARKER = 'T'
    private const val MAGNETIC_MARKER = 'M'
    private const val KNOTS_MARKER = 'N'
    private const val METERS_MARKER = 'M'

    private const val DIRECTION_TRUE = 0
    private const val DIRECTION_MAGNETIC = 2
    private const val SPEED_KNOTS = 4
    private const val SPEED_METERS = 6

    /** Reads an MWD sentence from its fields. */
    public fun from(fields: SentenceFields): Mwd =
      Mwd(
        talker = fields.talker,
        directionTrue = fields.doubleAt(DIRECTION_TRUE),
        directionMagnetic = fields.doubleAt(DIRECTION_MAGNETIC),
        speedKnots = fields.doubleAt(SPEED_KNOTS),
        speedMetersPerSecond = fields.doubleAt(SPEED_METERS),
      )
  }
}

/**
 * Relative -- apparent -- wind speed and angle, as felt on deck.
 *
 * Example: `$IIVWR,088,L,24.5,N,12.6,M,,*2A`
 *
 * The angle is an unsigned magnitude off the bow with [side] saying which way, rather than the
 * 0-359 sweep MWV uses. Speed is reported three times over in three units; a device commonly fills
 * in only some of them, as the example above leaves the km/h value empty.
 *
 * Apparent wind is what an anemometer measures: the vector sum of the true wind and the wind the
 * vessel makes by moving. [Vwt] carries the same layout for true wind.
 *
 * @property windAngle angle off the bow in degrees, unsigned; the side is [side]
 * @property side whether the wind is off the port ([Direction.LEFT]) or starboard bow
 * @property speedKnots wind speed in knots
 * @property speedMetersPerSecond wind speed in metres per second
 * @property speedKmh wind speed in km/h
 */
public data class Vwr(
  override val talker: TalkerId,
  val windAngle: Double? = null,
  val side: Direction? = null,
  val speedKnots: Double? = null,
  val speedMetersPerSecond: Double? = null,
  val speedKmh: Double? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    relativeWindFields(ID, talker, windAngle, side, speedKnots, speedMetersPerSecond, speedKmh)

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "VWR"

    /** Reads a VWR sentence from its fields. */
    public fun from(fields: SentenceFields): Vwr =
      Vwr(
        talker = fields.talker,
        windAngle = fields.doubleAt(WIND_ANGLE),
        side = fields.codedAt(SIDE, Direction.entries),
        speedKnots = fields.doubleAt(SPEED_KNOTS),
        speedMetersPerSecond = fields.doubleAt(SPEED_METERS),
        speedKmh = fields.doubleAt(SPEED_KMH),
      )
  }
}

/**
 * True wind speed and angle, corrected for the vessel's own motion.
 *
 * Example: `$IIVWT,088,L,24.7,N,12.6,M,,*2E`
 *
 * Field for field this is [Vwr] with the apparent wind replaced by the true wind. They are separate
 * types because a caller asking for the wind means one or the other, and confusing them puts a
 * sailor on the wrong tack.
 *
 * @property windAngle angle off the bow in degrees, unsigned; the side is [side]
 * @property side whether the wind is off the port ([Direction.LEFT]) or starboard bow
 * @property speedKnots wind speed in knots
 * @property speedMetersPerSecond wind speed in metres per second
 * @property speedKmh wind speed in km/h
 */
public data class Vwt(
  override val talker: TalkerId,
  val windAngle: Double? = null,
  val side: Direction? = null,
  val speedKnots: Double? = null,
  val speedMetersPerSecond: Double? = null,
  val speedKmh: Double? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    relativeWindFields(ID, talker, windAngle, side, speedKnots, speedMetersPerSecond, speedKmh)

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "VWT"

    /** Reads a VWT sentence from its fields. */
    public fun from(fields: SentenceFields): Vwt =
      Vwt(
        talker = fields.talker,
        windAngle = fields.doubleAt(WIND_ANGLE),
        side = fields.codedAt(SIDE, Direction.entries),
        speedKnots = fields.doubleAt(SPEED_KNOTS),
        speedMetersPerSecond = fields.doubleAt(SPEED_METERS),
        speedKmh = fields.doubleAt(SPEED_KMH),
      )
  }
}

// VWR and VWT are the same eight fields, differing only in whether the wind is apparent or true.
private const val WIND_ANGLE = 0
private const val SIDE = 1
private const val SPEED_KNOTS = 2
private const val SPEED_METERS = 4
private const val SPEED_KMH = 6

private const val KNOTS_MARKER = 'N'
private const val METERS_MARKER = 'M'
private const val KMH_MARKER = 'K'

private fun relativeWindFields(
  id: String,
  talker: TalkerId,
  windAngle: Double?,
  side: Direction?,
  speedKnots: Double?,
  speedMetersPerSecond: Double?,
  speedKmh: Double?,
): String =
  buildNmea(
    talker,
    id,
    listOf(
      windAngle.field(),
      side.field(),
      speedKnots.field(),
      KNOTS_MARKER.toString(),
      speedMetersPerSecond.field(),
      METERS_MARKER.toString(),
      speedKmh.field(),
      KMH_MARKER.toString(),
    ),
  )
