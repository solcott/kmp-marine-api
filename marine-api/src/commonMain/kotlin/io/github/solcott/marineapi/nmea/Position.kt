package io.github.solcott.marineapi.nmea

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A geographic position: latitude and longitude in degrees, with optional altitude.
 *
 * Negative latitude is south and negative longitude is west, so the value alone carries the
 * hemisphere; [latitudeHemisphere] and [longitudeHemisphere] derive the NMEA indicator characters
 * from it.
 *
 * @property latitude degrees, -90 to 90
 * @property longitude degrees, -180 to 180
 * @property altitude metres above [datum], or `null` when the sentence does not report one. The
 *   Java implementation defaulted this to 0.0, which is a real altitude and indistinguishable from
 *   a reported sea-level fix.
 * @property datum the coordinate system these degrees are expressed in
 */
public data class Position(
  val latitude: Double,
  val longitude: Double,
  val altitude: Double? = null,
  val datum: Datum = Datum.WGS84,
) {

  init {
    require(latitude in -90.0..90.0) { "Latitude out of bounds [-90..90]: $latitude" }
    require(longitude in -180.0..180.0) { "Longitude out of bounds [-180..180]: $longitude" }
  }

  /** [CompassPoint.NORTH] for a non-negative [latitude], otherwise [CompassPoint.SOUTH]. */
  public val latitudeHemisphere: CompassPoint
    get() = if (latitude >= 0.0) CompassPoint.NORTH else CompassPoint.SOUTH

  /** [CompassPoint.EAST] for a non-negative [longitude], otherwise [CompassPoint.WEST]. */
  public val longitudeHemisphere: CompassPoint
    get() = if (longitude >= 0.0) CompassPoint.EAST else CompassPoint.WEST

  /**
   * Great-circle distance to [other] in metres, by the haversine formula.
   *
   * Uses an earth radius of 6366.70702 km, the value for which one degree is exactly 60 nautical
   * miles, carried over from the Java implementation. That is a navigational convention rather than
   * a physical measurement -- the IUGG mean radius is 6371.009 km -- so distances differ from a
   * geodesic calculation by roughly 0.07%, and by more over long distances because the earth is not
   * a sphere.
   */
  public fun distanceTo(other: Position): Double {
    val deltaLatitude = (other.latitude - latitude).toRadians()
    val deltaLongitude = (other.longitude - longitude).toRadians()

    val a =
      sin(deltaLatitude / 2) * sin(deltaLatitude / 2) +
        cos(latitude.toRadians()) *
          cos(other.latitude.toRadians()) *
          sin(deltaLongitude / 2) *
          sin(deltaLongitude / 2)

    return EARTH_RADIUS_METRES * 2 * atan2(sqrt(a), sqrt(1 - a))
  }

  /** This position as a named waypoint. */
  public fun toWaypoint(id: String, description: String = ""): Waypoint =
    Waypoint(id = id, position = this, description = description)

  override fun toString(): String {
    val latitudeText = NmeaFormat.decimal(kotlin.math.abs(latitude), 2, 7)
    val longitudeText = NmeaFormat.decimal(kotlin.math.abs(longitude), 3, 7)
    // formatTrimmed rather than interpolating: Kotlin/JS prints 5.0 as "5", which would make this
    // class's toString platform-dependent.
    val altitudeText = altitude?.let { ", ${formatTrimmed(it)} m" } ?: ""
    return "[$latitudeText ${latitudeHemisphere.code}, " +
      "$longitudeText ${longitudeHemisphere.code}$altitudeText]"
  }

  private fun Double.toRadians(): Double = this * PI / 180.0

  public companion object {
    /**
     * Earth radius in metres for which one degree of arc is exactly 60 nautical miles: `1.852 *
     * 60 * 360 / (2 * PI)` km.
     */
    public const val EARTH_RADIUS_METRES: Double = 6_366_707.02
  }
}

/**
 * A named position, as carried by route and waypoint sentences.
 *
 * The Java implementation made this a subclass of `Position` that also held a creation timestamp
 * taken from the system clock. The timestamp described when the object was constructed rather than
 * anything in the sentence, so it is gone; a waypoint now simply has a position.
 */
public data class Waypoint(
  /** Identifier as it appears in the sentence, e.g. `RUSKI`. */
  val id: String,
  /** Where the waypoint is. */
  val position: Position,
  /** Human-readable description, empty when the sentence carries none. */
  val description: String = "",
)

/**
 * One satellite's entry in a GSV sentence.
 *
 * Only the id is required. Receivers routinely report a satellite whose sky position they have not
 * worked out yet -- `02,,,26` is a satellite with a signal but no elevation or azimuth -- and 98
 * such quadruples appear in this project's own sample logs. Requiring those fields would mean
 * discarding a satellite the receiver is telling us about.
 *
 * @property id satellite PRN, kept as a string because the field is zero-padded and its numbering
 *   depends on the constellation
 * @property elevation degrees above the horizon, -90 to 90. gpsd's reference documents the negative
 *   half of that range; the Java implementation rejected it, which would fail on a receiver
 *   reporting a satellite below the horizon.
 * @property azimuth degrees true, 0 to 360. The format says 000 to 359, but 360 is accepted because
 *   the Java implementation accepted it and rejecting a whole sentence over it would lose data.
 * @property noise signal-to-noise ratio in dB, 0 to 99, or `null` when the satellite is in view but
 *   not tracked. The Java implementation substituted 0 there, which is indistinguishable from a
 *   tracked satellite with no usable signal.
 */
public data class SatelliteInfo(
  val id: String,
  val elevation: Int? = null,
  val azimuth: Int? = null,
  val noise: Int? = null,
) {
  init {
    require(elevation == null || elevation in -90..90) {
      "Elevation out of bounds [-90..90]: $elevation"
    }
    require(azimuth == null || azimuth in 0..360) { "Azimuth out of bounds [0..360]: $azimuth" }
    require(noise == null || noise in 0..99) { "Noise out of bounds [0..99]: $noise" }
  }
}

/**
 * A single transducer reading from an XDR sentence.
 *
 * Every field is optional because XDR carries a variable number of quadruples and a device may
 * leave any of them empty.
 *
 * @property type transducer type code, e.g. `C` for temperature
 * @property value the reading
 * @property units unit code the reading is in
 * @property name transducer name
 */
public data class Measurement(
  val type: String? = null,
  val value: Double? = null,
  val units: String? = null,
  val name: String? = null,
) {
  /** True when the sentence carried nothing at all for this transducer. */
  public val isEmpty: Boolean
    get() = type == null && value == null && units == null && name == null
}
