package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.GpsFixQuality
import io.github.solcott.marineapi.nmea.NmeaDateTime
import io.github.solcott.marineapi.nmea.NmeaFormat
import io.github.solcott.marineapi.nmea.Position
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceFields
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.Units
import io.github.solcott.marineapi.nmea.buildNmea
import io.github.solcott.marineapi.nmea.field
import io.github.solcott.marineapi.nmea.positionAt
import io.github.solcott.marineapi.nmea.positionFields
import kotlinx.datetime.LocalTime

/**
 * Global Positioning System fix data: time, position, and fix quality.
 *
 * One of the handful of sentences nearly every receiver emits.
 *
 * Example: `$GPGGA,120044.567,6011.552,N,02501.941,E,1,00,2.0,28.0,M,19.6,M,,*63`
 *
 * [altitude] is kept separate from [position] rather than folded into it. The sentence reports the
 * altitude with its own unit field, and while that field is always `M` in practice, silently
 * treating a foot value as metres would be worse than making the caller look.
 *
 * @property time UTC of this position report
 * @property position latitude and longitude; `null` when the receiver has no fix
 * @property fixQuality how the fix was obtained; the format says this field is never empty
 * @property satelliteCount satellites used for the fix, 0 to 12
 * @property horizontalDilution horizontal dilution of precision
 * @property altitude antenna altitude above mean sea level, in [altitudeUnits]
 * @property geoidalHeight difference between the WGS-84 ellipsoid and mean sea level; negative
 *   means the geoid is below the ellipsoid
 * @property dgpsAge seconds since the last differential correction, `null` when DGPS is not in use
 * @property dgpsStationId differential reference station, `0000` to `1023`
 */
public data class Gga(
  override val talker: TalkerId,
  val time: LocalTime? = null,
  val position: Position? = null,
  val fixQuality: GpsFixQuality? = null,
  val satelliteCount: Int? = null,
  val horizontalDilution: Double? = null,
  val altitude: Double? = null,
  val altitudeUnits: Units? = null,
  val geoidalHeight: Double? = null,
  val geoidalHeightUnits: Units? = null,
  val dgpsAge: Double? = null,
  val dgpsStationId: String? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(time?.let { NmeaDateTime.formatTime(it) }) +
        positionFields(position) +
        listOf(
          fixQuality.field(),
          satelliteCount?.let { NmeaFormat.integer(it, 2) },
          horizontalDilution.field(),
          altitude.field(),
          altitudeUnits.field(),
          geoidalHeight.field(),
          geoidalHeightUnits.field(),
          dgpsAge.field(),
          dgpsStationId,
        ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "GGA"

    private const val TIME = 0
    private const val LATITUDE = 1
    private const val LATITUDE_HEMISPHERE = 2
    private const val LONGITUDE = 3
    private const val LONGITUDE_HEMISPHERE = 4
    private const val FIX_QUALITY = 5
    private const val SATELLITE_COUNT = 6
    private const val HORIZONTAL_DILUTION = 7
    private const val ALTITUDE = 8
    private const val ALTITUDE_UNITS = 9
    private const val GEOIDAL_HEIGHT = 10
    private const val GEOIDAL_HEIGHT_UNITS = 11
    private const val DGPS_AGE = 12
    private const val DGPS_STATION_ID = 13

    /** Reads a GGA sentence from its fields. */
    public fun from(fields: SentenceFields): Gga =
      Gga(
        talker = fields.talker,
        time = fields.timeAt(TIME),
        position =
          fields.positionAt(LATITUDE, LATITUDE_HEMISPHERE, LONGITUDE, LONGITUDE_HEMISPHERE),
        fixQuality = fields.advisoryIntCodedAt(FIX_QUALITY, GpsFixQuality.entries),
        satelliteCount = fields.intAt(SATELLITE_COUNT),
        horizontalDilution = fields.doubleAt(HORIZONTAL_DILUTION),
        altitude = fields.doubleAt(ALTITUDE),
        altitudeUnits = fields.codedAt(ALTITUDE_UNITS, Units.entries),
        geoidalHeight = fields.doubleAt(GEOIDAL_HEIGHT),
        geoidalHeightUnits = fields.codedAt(GEOIDAL_HEIGHT_UNITS, Units.entries),
        dgpsAge = fields.doubleAt(DGPS_AGE),
        dgpsStationId = fields.stringAt(DGPS_STATION_ID),
      )
  }
}
