package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.CharCoded
import io.github.solcott.marineapi.nmea.DataStatus
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceFields
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.Units
import io.github.solcott.marineapi.nmea.buildNmea
import io.github.solcott.marineapi.nmea.field

/** What a wind angle is measured against. */
public enum class WindReference(override val code: Char) : CharCoded {
  /** Relative to the vessel's bow, the angle a masthead vane reads. */
  RELATIVE('R'),
  /** True, corrected for the vessel's own motion. */
  TRUE('T'),
}

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
  val reference: WindReference? = null,
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

    /** Reads an MWV sentence from its fields. */
    public fun from(fields: SentenceFields): Mwv =
      Mwv(
        talker = fields.talker,
        windAngle = fields.doubleAt(WIND_ANGLE),
        reference = fields.codedAt(REFERENCE, WindReference.entries),
        windSpeed = fields.doubleAt(WIND_SPEED),
        speedUnits = fields.codedAt(SPEED_UNITS, Units.entries),
        status = fields.codedAt(STATUS, DataStatus.entries),
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
