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

  /**
   * Formats decimal [degrees] as `ddmm.mmm`, with [degreeDigits] digits of degrees.
   *
   * Pass 2 for a latitude and 3 for a longitude. The sign is dropped: NMEA carries the hemisphere
   * in its own field.
   */
  public fun format(degrees: Double, degreeDigits: Int, minuteDecimals: Int = 3): String {
    val magnitude = abs(degrees)
    val whole = floor(magnitude).toInt()
    val minutes = (magnitude - whole) * 60.0
    return NmeaFormat.integer(whole, degreeDigits) + NmeaFormat.decimal(minutes, 2, minuteDecimals)
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

private val LATITUDE_HEMISPHERES = listOf(CompassPoint.NORTH, CompassPoint.SOUTH)
private val LONGITUDE_HEMISPHERES = listOf(CompassPoint.EAST, CompassPoint.WEST)
