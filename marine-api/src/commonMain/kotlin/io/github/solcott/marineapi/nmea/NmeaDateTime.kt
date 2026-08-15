package io.github.solcott.marineapi.nmea

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.number

/**
 * Reads and writes the NMEA date and time field formats, as `kotlinx.datetime` values.
 *
 * The Java implementation had its own `Date` and `Time` classes because `java.util.Date` and
 * `Calendar` could not express a date without a time or a time without a zone. `LocalDate`,
 * `LocalTime` and `UtcOffset` say exactly that, so those classes are gone rather than ported.
 *
 * NMEA reports UTC. Only ZDA carries a local zone offset, and it carries it separately from the
 * time, so no type here pairs the two.
 */
public object NmeaDateTime {

  /**
   * Two-digit years greater than this map to the twentieth century, and years at or below it to the
   * twenty-first: `50` is 2050 while `51` is 1951.
   *
   * Inherited from the Java implementation, which is the only reason the boundary sits here. NMEA
   * two-digit years are inherently ambiguous and a receiver's own assumption may differ.
   */
  public const val PIVOT_YEAR: Int = 50

  /**
   * Parses a `ddmmyy` date field, as carried by RMC and others.
   *
   * A four-digit year is also accepted, since some devices emit `ddmmyyyy`.
   *
   * @throws IllegalArgumentException if the field is not a valid date.
   */
  public fun parseDate(field: String): LocalDate {
    require(field.length >= 6) { "Date field must be at least 6 characters: \"$field\"" }
    val day = field.substring(0, 2).toIntOrNull()
    val month = field.substring(2, 4).toIntOrNull()
    val year = field.substring(4).toIntOrNull()
    require(day != null && month != null && year != null) {
      "Date field is not numeric: \"$field\""
    }
    return LocalDate(expandYear(year), month, day)
  }

  /** Formats [date] as a `ddmmyy` field, the form every NMEA date field takes. */
  public fun formatDate(date: LocalDate): String =
    NmeaFormat.integer(date.day, 2) +
      NmeaFormat.integer(date.month.number, 2) +
      NmeaFormat.integer(date.year % 100, 2)

  /**
   * Expands a two-digit year around [PIVOT_YEAR]; four-digit years pass through unchanged.
   *
   * @throws IllegalArgumentException if [year] is neither a two- nor a four-digit value.
   */
  public fun expandYear(year: Int): Int {
    require(year >= 0 && year <= 9999 && !(year in 100..999)) {
      "Year must be a two or four digit value: $year"
    }
    return when {
      year >= 100 -> year
      year > PIVOT_YEAR -> 1900 + year
      else -> 2000 + year
    }
  }

  /**
   * Parses an `hhmmss` or `hhmmss.sss` time field.
   *
   * Any number of decimal places is accepted and kept to nanosecond resolution, which is finer than
   * any receiver reports.
   *
   * @throws IllegalArgumentException if the field is not a valid time.
   */
  public fun parseTime(field: String): LocalTime {
    require(field.length >= 6) { "Time field must be at least 6 characters: \"$field\"" }
    val hour = field.substring(0, 2).toIntOrNull()
    val minute = field.substring(2, 4).toIntOrNull()
    val seconds = field.substring(4).toDoubleOrNull()
    require(hour != null && minute != null && seconds != null) {
      "Time field is not numeric: \"$field\""
    }
    require(seconds >= 0.0 && seconds < 60.0) { "Seconds out of range [0..60): $seconds" }

    val wholeSeconds = seconds.toInt()
    // Round rather than truncate: 44.567 must not become 566999999 nanoseconds.
    val nanoseconds = ((seconds - wholeSeconds) * 1_000_000_000).toLong()
    return LocalTime(hour, minute, wholeSeconds, nanoseconds.toInt())
  }

  /**
   * Formats [time] as an NMEA time field.
   *
   * With [fractionDigits] left at `null` the decimals are omitted when [time] has none and written
   * to millisecond resolution when it does, so `120044` and `120044.567` both survive a parse and
   * format unchanged. Pass an explicit width for a sentence that requires one; note that a receiver
   * writing `120044.000` will read back as `120044`.
   */
  public fun formatTime(time: LocalTime, fractionDigits: Int? = null): String {
    val digits = fractionDigits ?: if (time.nanosecond == 0) 0 else 3
    val seconds = time.second + time.nanosecond / 1_000_000_000.0
    return NmeaFormat.integer(time.hour, 2) +
      NmeaFormat.integer(time.minute, 2) +
      NmeaFormat.decimal(seconds, 2, digits)
  }

  /**
   * Builds a UTC offset from the separate hour and minute fields of a ZDA sentence.
   *
   * The sign of [hours] governs: a ZDA reporting `-05` hours and `30` minutes is offset -05:30, not
   * -04:30. Returns `null` if either field is absent.
   */
  public fun utcOffset(hours: Int?, minutes: Int?): UtcOffset? {
    if (hours == null || minutes == null) return null
    val magnitude = minutes.absoluteValueOf()
    return UtcOffset(hours = hours, minutes = if (hours < 0) -magnitude else magnitude)
  }

  private fun Int.absoluteValueOf(): Int = if (this < 0) -this else this
}
