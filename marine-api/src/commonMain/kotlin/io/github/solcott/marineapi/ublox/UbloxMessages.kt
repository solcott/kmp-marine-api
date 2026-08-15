package io.github.solcott.marineapi.ublox

import io.github.solcott.marineapi.nmea.CompassPoint
import io.github.solcott.marineapi.nmea.Degrees
import io.github.solcott.marineapi.nmea.NmeaDateTime
import io.github.solcott.marineapi.nmea.Position
import io.github.solcott.marineapi.nmea.sentence.Ubx
import kotlinx.datetime.LocalTime

/**
 * A decoded u-blox proprietary message.
 *
 * u-blox receivers add their own `$PUBX` sentences alongside standard NMEA, carrying detail the
 * standard sentences have no field for -- accuracy estimates in metres, per-satellite carrier lock
 * times. What the fields mean depends on [messageId], which is why the [Ubx] sentence keeps them as
 * text and this layer interprets them.
 *
 * @property messageId the u-blox message type
 */
public interface UbloxMessage {
  public val messageId: Int
}

/** How a u-blox receiver arrived at its fix. */
public enum class UbloxNavigationStatus(public val code: String) {
  /** No fix at all. */
  NO_FIX("NF"),
  /** Position carried forward from the last fix by dead reckoning alone. */
  DEAD_RECKONING_ONLY("DR"),
  /** Position but no altitude, from satellites alone. */
  STAND_ALONE_2D("G2"),
  /** Position and altitude, from satellites alone. */
  STAND_ALONE_3D("G3"),
  /** Differentially corrected, position only. */
  DIFFERENTIAL_2D("D2"),
  /** Differentially corrected, position and altitude. */
  DIFFERENTIAL_3D("D3"),
  /** Satellites and dead reckoning together. */
  COMBINED_GPS_AND_DEAD_RECKONING("RK"),
  /** Enough satellites to keep time but not to fix a position. */
  TIME_ONLY("TT");

  public companion object {
    /** The status for [code], or `null` if the receiver sent something else. */
    public fun fromCode(code: String): UbloxNavigationStatus? = entries.find { it.code == code }
  }
}

/** Whether a satellite contributed to the fix. */
public enum class UbloxSatelliteStatus(public val code: Char) {
  /** Tracked but not used. */
  NOT_USED('-'),
  /** Used in the position solution. */
  USED_IN_SOLUTION('U'),
  /** Not used, but its orbit is known, so it can be used as soon as it is wanted. */
  NOT_USED_EPHEMERIS_AVAILABLE('e');

  public companion object {
    /** The status for [code], or `null` if the receiver sent something else. */
    public fun fromCode(code: Char): UbloxSatelliteStatus? = entries.find { it.code == code }
  }
}

/**
 * One satellite as a u-blox receiver reports it.
 *
 * More than [io.github.solcott.marineapi.nmea.SatelliteInfo] carries, which is the reason this
 * message type exists alongside GSV: [status] says whether the satellite was actually used, and
 * [carrierLockSeconds] how long the receiver has held phase lock on it.
 *
 * @property id satellite number
 * @property elevation degrees above the horizon, or `null` if the receiver reports none
 * @property azimuth degrees true, or `null` if the receiver reports none
 * @property signalStrength carrier-to-noise ratio in dB-Hz, or `null` if the satellite is not being
 *   tracked
 * @property status whether the satellite was used in the fix
 * @property carrierLockSeconds how long the receiver has been locked to this satellite's carrier
 */
public data class UbloxSatelliteInfo(
  val id: String,
  val elevation: Int? = null,
  val azimuth: Int? = null,
  val signalStrength: Int? = null,
  val status: UbloxSatelliteStatus? = null,
  val carrierLockSeconds: Int? = null,
)

/**
 * A u-blox position, velocity and time report: `$PUBX,00`.
 *
 * The receiver's own summary of its fix, with more of it in one sentence than the standard set
 * manages between them: GGA has no speed, RMC no altitude, and neither has an accuracy estimate at
 * all. Every field is optional, because a receiver still searching sends the sentence anyway.
 *
 * @property utcTime time of the fix
 * @property position where the receiver is, with [Position.altitude] filled in from the sentence's
 *   own altitude field
 * @property navigationStatus how the fix was arrived at
 * @property horizontalAccuracy estimated horizontal error in metres. The standard sentences report
 *   dilution of precision instead, which is a geometry factor rather than a distance.
 * @property verticalAccuracy estimated vertical error in metres
 * @property speedOverGround km/h
 * @property courseOverGround degrees true
 * @property verticalVelocity metres per second, positive downwards
 * @property differentialAge seconds since the last differential correction, or `null` when the fix
 *   is not differentially corrected
 * @property horizontalDop horizontal dilution of precision
 * @property verticalDop vertical dilution of precision
 * @property timeDop time dilution of precision
 * @property satellitesUsed satellites contributing to the fix
 */
public data class UbloxPositionVelocityTime(
  override val messageId: Int,
  val utcTime: LocalTime? = null,
  val position: Position? = null,
  val navigationStatus: UbloxNavigationStatus? = null,
  val horizontalAccuracy: Double? = null,
  val verticalAccuracy: Double? = null,
  val speedOverGround: Double? = null,
  val courseOverGround: Double? = null,
  val verticalVelocity: Double? = null,
  val differentialAge: Int? = null,
  val horizontalDop: Double? = null,
  val verticalDop: Double? = null,
  val timeDop: Double? = null,
  val satellitesUsed: Int? = null,
) : UbloxMessage {

  public companion object {
    /** u-blox message id decoded by this class. */
    public const val ID: Int = 0

    private const val UTC_TIME = 1
    private const val LATITUDE = 2
    private const val LATITUDE_HEMISPHERE = 3
    private const val LONGITUDE = 4
    private const val LONGITUDE_HEMISPHERE = 5
    private const val ALTITUDE = 6
    private const val NAVIGATION_STATUS = 7
    private const val HORIZONTAL_ACCURACY = 8
    private const val VERTICAL_ACCURACY = 9
    private const val SPEED_OVER_GROUND = 10
    private const val COURSE_OVER_GROUND = 11
    private const val VERTICAL_VELOCITY = 12
    private const val DIFFERENTIAL_AGE = 13
    private const val HDOP = 14
    private const val VDOP = 15
    private const val TDOP = 16
    private const val SATELLITES_USED = 17

    /** Reads a `$PUBX,00` message from its sentence. */
    public fun from(sentence: Ubx): UbloxPositionVelocityTime =
      UbloxPositionVelocityTime(
        messageId = sentence.messageId,
        utcTime =
          sentence.stringAt(UTC_TIME)?.let {
            runCatching { NmeaDateTime.parseTime(it) }.getOrNull()
          },
        position =
          sentence.positionAt(
            LATITUDE,
            LATITUDE_HEMISPHERE,
            LONGITUDE,
            LONGITUDE_HEMISPHERE,
            sentence.doubleAt(ALTITUDE),
          ),
        navigationStatus =
          sentence.stringAt(NAVIGATION_STATUS)?.let { UbloxNavigationStatus.fromCode(it) },
        horizontalAccuracy = sentence.doubleAt(HORIZONTAL_ACCURACY),
        verticalAccuracy = sentence.doubleAt(VERTICAL_ACCURACY),
        speedOverGround = sentence.doubleAt(SPEED_OVER_GROUND),
        courseOverGround = sentence.doubleAt(COURSE_OVER_GROUND),
        verticalVelocity = sentence.doubleAt(VERTICAL_VELOCITY),
        differentialAge = sentence.intAt(DIFFERENTIAL_AGE),
        horizontalDop = sentence.doubleAt(HDOP),
        verticalDop = sentence.doubleAt(VDOP),
        timeDop = sentence.doubleAt(TDOP),
        satellitesUsed = sentence.intAt(SATELLITES_USED),
      )
  }
}

/**
 * A u-blox satellite status report: `$PUBX,03`.
 *
 * One sentence describing every satellite the receiver can see, where GSV needs a group of them and
 * carries less about each. A receiver tracking 31 satellites produces a sentence far longer than
 * NMEA's 82-byte limit, which is one reason [io.github.solcott.marineapi.nmea.SentenceRegistry]
 * does not enforce that limit.
 *
 * @property satellites what the receiver can see, one entry per satellite
 */
public data class UbloxSatelliteStatusReport(
  override val messageId: Int,
  val satellites: List<UbloxSatelliteInfo> = emptyList(),
) : UbloxMessage {

  /** How many satellites the sentence said it would describe. */
  public val trackedSatellites: Int
    get() = satellites.size

  public companion object {
    /** u-blox message id decoded by this class. */
    public const val ID: Int = 3

    private const val SATELLITE_COUNT = 1
    private const val FIRST_SATELLITE = 2
    private const val FIELDS_PER_SATELLITE = 6
    private const val STATUS_OFFSET = 1
    private const val AZIMUTH_OFFSET = 2
    private const val ELEVATION_OFFSET = 3
    private const val SIGNAL_STRENGTH_OFFSET = 4
    private const val CARRIER_LOCK_OFFSET = 5

    /** Reads a `$PUBX,03` message from its sentence. */
    public fun from(sentence: Ubx): UbloxSatelliteStatusReport {
      // The count field says how many follow, but the sentence is what it is: a receiver whose
      // count disagrees with the fields it sent would have produced a list padded with satellites
      // that were never described. Reading only as many as the sentence carries costs nothing and
      // cannot invent one.
      val declared = sentence.intAt(SATELLITE_COUNT) ?: 0
      val available =
        (sentence.fields.size - FIRST_SATELLITE).coerceAtLeast(0) / FIELDS_PER_SATELLITE
      val satellites =
        (0 until minOf(declared, available)).mapNotNull { index ->
          val base = FIRST_SATELLITE + index * FIELDS_PER_SATELLITE
          val id = sentence.stringAt(base) ?: return@mapNotNull null
          UbloxSatelliteInfo(
            id = id,
            status =
              sentence.stringAt(base + STATUS_OFFSET)?.singleOrNull()?.let {
                UbloxSatelliteStatus.fromCode(it)
              },
            azimuth = sentence.intAt(base + AZIMUTH_OFFSET),
            elevation = sentence.intAt(base + ELEVATION_OFFSET),
            signalStrength = sentence.intAt(base + SIGNAL_STRENGTH_OFFSET),
            carrierLockSeconds = sentence.intAt(base + CARRIER_LOCK_OFFSET),
          )
        }
      return UbloxSatelliteStatusReport(messageId = sentence.messageId, satellites = satellites)
    }
  }
}

/**
 * Reads a position from four fields of a UBX sentence.
 *
 * The same `ddmm.mmmm` encoding the standard sentences use, but reached through [Ubx]'s raw fields
 * rather than through [io.github.solcott.marineapi.nmea.SentenceFields], because a UBX sentence's
 * layout is not known until its message id has been read.
 */
private fun Ubx.positionAt(
  latitudeIndex: Int,
  latitudeHemisphereIndex: Int,
  longitudeIndex: Int,
  longitudeHemisphereIndex: Int,
  altitude: Double?,
): Position? {
  val latitudeField = stringAt(latitudeIndex) ?: return null
  val longitudeField = stringAt(longitudeIndex) ?: return null
  val latitudeHemisphere =
    hemisphereAt(latitudeHemisphereIndex, LATITUDE_HEMISPHERES) ?: return null
  val longitudeHemisphere =
    hemisphereAt(longitudeHemisphereIndex, LONGITUDE_HEMISPHERES) ?: return null

  val latitude = runCatching { Degrees.parse(latitudeField) }.getOrNull() ?: return null
  val longitude = runCatching { Degrees.parse(longitudeField) }.getOrNull() ?: return null

  return Position(
    latitude = Degrees.applyHemisphere(latitude, latitudeHemisphere),
    longitude = Degrees.applyHemisphere(longitude, longitudeHemisphere),
    altitude = altitude,
  )
}

private fun Ubx.hemisphereAt(index: Int, allowed: List<CompassPoint>): CompassPoint? =
  stringAt(index)?.singleOrNull()?.let { code -> allowed.find { it.code == code } }

private val LATITUDE_HEMISPHERES = listOf(CompassPoint.NORTH, CompassPoint.SOUTH)
private val LONGITUDE_HEMISPHERES = listOf(CompassPoint.EAST, CompassPoint.WEST)
