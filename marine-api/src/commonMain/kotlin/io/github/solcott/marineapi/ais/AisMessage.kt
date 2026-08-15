package io.github.solcott.marineapi.ais

import io.github.solcott.marineapi.nmea.Position
import io.github.solcott.marineapi.nmea.fromCode
import kotlin.math.abs

/**
 * A decoded AIS message.
 *
 * AIS defines 27 message types over one bit stream. Every one of them opens with the same three
 * fields, which is what this interface is; everything past bit 38 depends on [messageType] and
 * lives on the concrete types. Decode a payload into one with [AisRegistry].
 *
 * **Absent values are `null`.** An AIS field that has no value says so with a reserved number:
 * speed over ground of 1023, course of 3600, heading of 511, a latitude of 91 degrees. Those are
 * real values in the bit stream and nonsense as measurements, so they are reported as `null` here
 * rather than passed on. The implementation this replaces returned 102.3 knots, 360.0 degrees and
 * 91.0 degrees respectively, alongside a parallel family of `hasSpeedOverGround()`-style methods
 * for asking whether the number just returned meant anything.
 *
 * @property messageType which of the 27 message types this is
 * @property repeatIndicator how many times this message has been relayed; 3 means do not relay
 *   again
 * @property mmsi the station's Maritime Mobile Service Identity, its nine-digit identifier
 */
public interface AisMessage {
  public val messageType: Int
  public val repeatIndicator: Int
  public val mmsi: Int
}

/**
 * An AIS message reporting where a station is.
 *
 * Common ground between the position report types -- 1, 2, 3, 18, 19 and 27 for vessels, 4 for base
 * stations, 9 for search-and-rescue aircraft and 21 for navigation aids -- so that a chart plotter
 * can draw all of them without caring which it has.
 *
 * @property position where the station is, or `null` if it reports no fix. [Position.altitude] is
 *   always absent: AIS is a surface system and the one message type carrying an altitude,
 *   [AisSarAircraftPositionReport], keeps it in its own property.
 * @property isAccurate `true` if the station claims a fix better than 10 metres. A claim, not a
 *   measurement.
 */
public interface AisPositionMessage : AisMessage {
  public val position: Position?
  public val isAccurate: Boolean
}

/**
 * An AIS message reporting how a vessel is moving as well as where it is.
 *
 * @property speedOverGround knots, or `null` if the station reports none. A vessel making 102.2
 *   knots or more reports exactly 102.2, the top of the scale.
 * @property courseOverGround degrees true, or `null` if the station reports none
 * @property utcSecond second of the UTC minute the fix was taken, 0 to 59, or `null`. Values 60 to
 *   63 are not seconds: they say the positioning system is unavailable, in manual input mode, in
 *   dead reckoning mode, or inoperative.
 */
public interface AisVesselPositionMessage : AisPositionMessage {
  public val speedOverGround: Double?
  public val courseOverGround: Double?
  public val utcSecond: Int?
}

// ---------------------------------------------------------------------------------------------
// Field decoding shared across the message types.
//
// Each of these turns one bit range into a value, mapping the reserved "not available" numbers to
// null. They are the whole of the difference between a message class here and the corresponding
// parser in the implementation this replaces, which stored every field as its raw integer and
// converted on the way out.
// ---------------------------------------------------------------------------------------------

/** Coordinates in most message types are 1/10000 of a minute. */
internal const val COORDINATE_SCALE: Int = 60 * 10000

/** Type 27 trades resolution for range and uses tenths of a minute instead. */
internal const val COARSE_COORDINATE_SCALE: Int = 60 * 10

/** Reported speed at or above this is the top of the scale rather than a measurement. */
private const val SPEED_UNAVAILABLE = 1023

private const val COURSE_UNAVAILABLE = 3600
private const val HEADING_UNAVAILABLE = 511
private const val RATE_OF_TURN_UNAVAILABLE = -128
private const val RATE_OF_TURN_LIMIT = 126

/** Converts a rate-of-turn code to degrees per minute; the constant is from the AIS standard. */
private const val RATE_OF_TURN_COEFFICIENT = 4.733

private const val SECONDS_IN_MINUTE = 60

/**
 * The position at the given bit ranges, or `null` if the station reports none.
 *
 * A station with no fix sends 91 degrees of latitude and 181 of longitude, which are outside the
 * range a coordinate can take and so unmistakable. Coordinates that are merely impossible rather
 * than reserved -- which faulty transponders do send -- are discarded the same way, because
 * [Position] will not hold them and a whole message is worth more than one bad field.
 */
internal fun Sixbit.positionAt(
  longitudeFrom: Int,
  longitudeTo: Int,
  latitudeFrom: Int,
  latitudeTo: Int,
  scale: Int = COORDINATE_SCALE,
): Position? {
  val longitude = intAt(longitudeFrom, longitudeTo).toDouble() / scale
  val latitude = intAt(latitudeFrom, latitudeTo).toDouble() / scale
  if (abs(latitude) > 90.0 || abs(longitude) > 180.0) return null
  return Position(latitude = latitude, longitude = longitude)
}

/** Speed over ground in knots, or `null` when the station reports none. */
internal fun Sixbit.speedOverGroundAt(from: Int, to: Int): Double? =
  uintAt(from, to).takeIf { it != SPEED_UNAVAILABLE }?.let { it / 10.0 }

/** Course over ground in degrees true, or `null` when the station reports none. */
internal fun Sixbit.courseOverGroundAt(from: Int, to: Int): Double? =
  uintAt(from, to).takeIf { it != COURSE_UNAVAILABLE && it < COURSE_UNAVAILABLE }?.let { it / 10.0 }

/** True heading in whole degrees, or `null` when the station reports none. */
internal fun Sixbit.headingAt(from: Int, to: Int): Int? =
  uintAt(from, to).takeIf { it != HEADING_UNAVAILABLE }

/**
 * Second of the UTC minute, or `null` when the field carries a status instead of a time.
 *
 * 60 to 63 mean the positioning system is unavailable, in manual mode, in dead reckoning, or
 * inoperative -- all of which are worth knowing but none of which is a second.
 */
internal fun Sixbit.utcSecondAt(from: Int, to: Int): Int? =
  uintAt(from, to).takeIf { it < SECONDS_IN_MINUTE }

/**
 * Rate of turn in degrees per minute, negative to port, or `null` when it is not being measured.
 *
 * The wire value is the square root of the rate, scaled, so that a single byte can span 708 degrees
 * per minute at useful resolution near zero. Both -128 (no information) and +/-127 (turning faster
 * than 5 degrees in 30 seconds, rate not measured) come back as `null`; [rateOfTurnCodeAt] tells
 * them apart.
 */
internal fun Sixbit.rateOfTurnAt(from: Int, to: Int): Double? {
  val code = intAt(from, to)
  if (abs(code) > RATE_OF_TURN_LIMIT) return null
  val root = code / RATE_OF_TURN_COEFFICIENT
  return if (code < 0) -(root * root) else root * root
}

/** The rate-of-turn field as sent, or `null` when it reports no information at all. */
internal fun Sixbit.rateOfTurnCodeAt(from: Int, to: Int): Int? =
  intAt(from, to).takeIf { it != RATE_OF_TURN_UNAVAILABLE }

/** The enum entry for the code in the given bits, or `null` if the code is not one of them. */
internal inline fun <reified T> Sixbit.codedAt(from: Int, to: Int, entries: Iterable<T>): T?
  where T : Enum<T>, T : io.github.solcott.marineapi.nmea.IntCoded =
  entries.fromCode(uintAt(from, to))
