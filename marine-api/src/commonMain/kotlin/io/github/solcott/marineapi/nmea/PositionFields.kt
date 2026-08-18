package io.github.solcott.marineapi.nmea

import kotlin.math.abs
import kotlin.math.floor

/**
 * Conversion between decimal degrees and the `ddmm.mmmm` form NMEA writes coordinates in.
 *
 * The two digits immediately left of the decimal point are whole minutes; everything further left
 * is whole degrees. Longitude carries an extra degree digit, but nothing in the encoding depends on
 * that, so the same conversion serves both.
 */
public object Degrees {

  /**
   * Parses a `ddmm.mmmm` or `dddmm.mmmm` coordinate into decimal degrees, always positive.
   *
   * The hemisphere lives in a separate field; apply it with [applyHemisphere].
   *
   * @throws IllegalArgumentException if [field] is not a coordinate.
   */
  public fun parse(field: String): Double {
    val dotIndex = field.indexOf('.')
    // A field with fewer than three digits before the point carries no degrees, only minutes.
    val degreesText = if (dotIndex > 2) field.substring(0, dotIndex - 2) else "0"
    val minutesText = if (dotIndex > 2) field.substring(dotIndex - 2) else field

    val degrees = degreesText.toIntOrNull()
    val minutes = minutesText.toDoubleOrNull()
    require(degrees != null && minutes != null) { "Not a coordinate: \"$field\"" }

    return degrees + minutes / 60.0
  }

  /** Fewest minute decimals written, matching what most receivers emit. */
  public const val MIN_MINUTE_DECIMALS: Int = 3

  /**
   * Most minute decimals written.
   *
   * Eight, because that is what survey-grade receivers send: 704 coordinate fields in the gpsd
   * corpus carry eight, from RTK hardware like the u-blox ZED-F9P. This was seven until that corpus
   * arrived, which silently rounded those fields and stopped them re-encoding to themselves. The
   * eighth decimal of a minute is about 19 micrometres, well past what any receiver resolves, but
   * the device sent it and dropping it is not this library's call.
   */
  public const val MAX_MINUTE_DECIMALS: Int = 8

  /**
   * Formats decimal [degrees] as `ddmm.mmm`, with [degreeDigits] digits of degrees.
   *
   * Pass 2 for a latitude and 3 for a longitude. The sign is dropped: NMEA carries the hemisphere
   * in its own field.
   *
   * How many minute decimals a sentence carries is model-dependent, so with [minuteDecimals] left
   * at `null` every significant decimal is written, between [MIN_MINUTE_DECIMALS] and
   * [MAX_MINUTE_DECIMALS]. A fixed three would quietly discard precision -- receivers commonly
   * report four or more, and `3748.4051` would be written back as `3748.405`.
   */
  public fun format(degrees: Double, degreeDigits: Int, minuteDecimals: Int? = null): String {
    val magnitude = abs(degrees)
    val whole = floor(magnitude).toInt()
    val minutes = (magnitude - whole) * 60.0

    val text =
      if (minuteDecimals != null) {
        NmeaFormat.decimal(minutes, 2, minuteDecimals)
      } else {
        val trimmed = NmeaFormat.decimal(minutes, 2, MAX_MINUTE_DECIMALS).trimEnd('0')
        val decimals = trimmed.length - trimmed.indexOf('.') - 1
        if (decimals < MIN_MINUTE_DECIMALS) {
          NmeaFormat.decimal(minutes, 2, MIN_MINUTE_DECIMALS)
        } else {
          trimmed
        }
      }

    return NmeaFormat.integer(whole, degreeDigits) + text
  }

  /** Applies a hemisphere indicator to positive [degrees], negating for south and west. */
  public fun applyHemisphere(degrees: Double, hemisphere: CompassPoint): Double =
    when (hemisphere) {
      CompassPoint.NORTH,
      CompassPoint.EAST -> degrees
      CompassPoint.SOUTH,
      CompassPoint.WEST -> -degrees
    }
}

/**
 * Reads a position from the four fields that carry it: latitude, its hemisphere, longitude, and its
 * hemisphere.
 *
 * Returns `null` if any of the four is empty, which is how a receiver reports having no fix.
 *
 * @throws NmeaFieldException if a field is present but not a coordinate, or if a hemisphere
 *   indicator is on the wrong axis -- `N` for a longitude, say.
 */
public fun SentenceFields.positionAt(
  latitudeIndex: Int,
  latitudeHemisphereIndex: Int,
  longitudeIndex: Int,
  longitudeHemisphereIndex: Int,
  altitude: Double? = null,
): Position? {
  val latitudeField = stringAt(latitudeIndex) ?: return null
  val longitudeField = stringAt(longitudeIndex) ?: return null
  val latitudeHemisphere = codedAt(latitudeHemisphereIndex, LATITUDE_HEMISPHERES) ?: return null
  val longitudeHemisphere = codedAt(longitudeHemisphereIndex, LONGITUDE_HEMISPHERES) ?: return null

  val latitude = runCatching {
    Degrees.parse(latitudeField)
  }
    .getOrElse { throw NmeaFieldException("$talker$id field $latitudeIndex: ${it.message}") }
  val longitude = runCatching {
    Degrees.parse(longitudeField)
  }
    .getOrElse { throw NmeaFieldException("$talker$id field $longitudeIndex: ${it.message}") }

  return Position(
    latitude = Degrees.applyHemisphere(latitude, latitudeHemisphere),
    longitude = Degrees.applyHemisphere(longitude, longitudeHemisphere),
    altitude = altitude,
  )
}

/**
 * Renders a position as the four fields that carry it, or four empty ones when there is no
 * position.
 *
 * Latitude takes two degree digits and longitude three; getting that backwards produces a sentence
 * that still parses but puts the vessel somewhere else entirely, so the rule lives here rather than
 * being repeated at every sentence that writes a position.
 */
internal fun positionFields(position: Position?): List<String?> =
  listOf(
    position?.let { Degrees.format(it.latitude, degreeDigits = 2) },
    position?.latitudeHemisphere.field(),
    position?.let { Degrees.format(it.longitude, degreeDigits = 3) },
    position?.longitudeHemisphere.field(),
  )

private val LATITUDE_HEMISPHERES = listOf(CompassPoint.NORTH, CompassPoint.SOUTH)
private val LONGITUDE_HEMISPHERES = listOf(CompassPoint.EAST, CompassPoint.WEST)
