package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.CompassPoint
import io.github.solcott.marineapi.nmea.DataStatus
import io.github.solcott.marineapi.nmea.Degrees
import io.github.solcott.marineapi.nmea.FaaMode
import io.github.solcott.marineapi.nmea.NavStatus
import io.github.solcott.marineapi.nmea.NmeaDateTime
import io.github.solcott.marineapi.nmea.NmeaFieldException
import io.github.solcott.marineapi.nmea.Position
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceFields
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.buildNmea
import io.github.solcott.marineapi.nmea.field
import io.github.solcott.marineapi.nmea.positionAt
import kotlin.math.abs
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Recommended minimum navigation information: the sentence that carries position, speed, course and
 * date together.
 *
 * Example: `$GPRMC,120044.567,A,6011.552,N,02501.941,E,000.0,360.0,160705,006.1,E,A,S*74`
 *
 * A [status] of [DataStatus.VOID] means the receiver has a fix that failed an internal quality
 * test, such as an elevation mask or a dilution-of-precision limit, rather than no fix at all.
 *
 * @property time UTC of the position fix
 * @property date UTC date; two-digit years are expanded around [NmeaDateTime.PIVOT_YEAR]
 * @property position latitude and longitude; `null` when the receiver has no fix
 * @property speedKnots speed over ground, knots
 * @property courseTrue track made good, degrees true
 * @property magneticVariation degrees, **positive east**. NMEA carries a magnitude and a separate
 *   `E`/`W` field rather than a signed number, so the sign is this library's choice. East-positive
 *   is the usual convention for magnetic declination, and it is the one that makes the relation
 *   `magnetic = true - variation` hold -- which is what [the reference](https://aprs.gids.nl/nmea/)
 *   means by "easterly variation subtracts from true course". The Java implementation used the
 *   opposite sign, negating easterly variation, so a caller moving across will see values flip.
 * @property faaMode FAA mode indicator, added in NMEA 2.3
 * @property navStatus navigational status, added in NMEA 4.1
 */
public data class Rmc(
  override val talker: TalkerId,
  val time: LocalTime? = null,
  val status: DataStatus? = null,
  val position: Position? = null,
  val speedKnots: Double? = null,
  val courseTrue: Double? = null,
  val date: LocalDate? = null,
  val magneticVariation: Double? = null,
  val faaMode: FaaMode? = null,
  val navStatus: NavStatus? = null,
) : Sentence {

  override val id: String
    get() = ID

  /** Which way [magneticVariation] points, or `null` when the sentence reports none. */
  public val variationDirection: CompassPoint?
    get() = magneticVariation?.let { if (it >= 0.0) CompassPoint.EAST else CompassPoint.WEST }

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        time?.let { NmeaDateTime.formatTime(it) },
        status.field(),
        position?.let { Degrees.format(it.latitude, degreeDigits = 2) },
        position?.latitudeHemisphere.field(),
        position?.let { Degrees.format(it.longitude, degreeDigits = 3) },
        position?.longitudeHemisphere.field(),
        speedKnots.field(),
        courseTrue.field(),
        date?.let { NmeaDateTime.formatDate(it) },
        magneticVariation?.let { abs(it).field() },
        variationDirection.field(),
        faaMode.field(),
        navStatus.field(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "RMC"

    private const val TIME = 0
    private const val STATUS = 1
    private const val LATITUDE = 2
    private const val LATITUDE_HEMISPHERE = 3
    private const val LONGITUDE = 4
    private const val LONGITUDE_HEMISPHERE = 5
    private const val SPEED = 6
    private const val COURSE = 7
    private const val DATE = 8
    private const val MAGNETIC_VARIATION = 9
    private const val VARIATION_HEMISPHERE = 10
    private const val FAA_MODE = 11
    private const val NAV_STATUS = 12

    /** Reads an RMC sentence from its fields. */
    public fun from(fields: SentenceFields): Rmc {
      val variation = fields.doubleAt(MAGNETIC_VARIATION)
      val direction =
        fields.codedAt(VARIATION_HEMISPHERE, listOf(CompassPoint.EAST, CompassPoint.WEST))
      // A magnitude with no E/W field cannot be signed. Guessing a direction would turn a
      // malformed sentence into a plausible-looking heading error, so report it instead.
      if (variation != null && direction == null) {
        throw NmeaFieldException(
          "${fields.talker}$ID field $VARIATION_HEMISPHERE: magnetic variation " +
            "$variation has no E/W direction"
        )
      }
      return Rmc(
        talker = fields.talker,
        time = fields.timeAt(TIME),
        status = fields.codedAt(STATUS, DataStatus.entries),
        position =
          fields.positionAt(LATITUDE, LATITUDE_HEMISPHERE, LONGITUDE, LONGITUDE_HEMISPHERE),
        speedKnots = fields.doubleAt(SPEED),
        courseTrue = fields.doubleAt(COURSE),
        date = fields.dateAt(DATE),
        magneticVariation =
          variation?.let { if (direction == CompassPoint.WEST) -abs(it) else abs(it) },
        faaMode = fields.codedAt(FAA_MODE, FaaMode.entries),
        navStatus = fields.codedAt(NAV_STATUS, NavStatus.entries),
      )
    }
  }
}
