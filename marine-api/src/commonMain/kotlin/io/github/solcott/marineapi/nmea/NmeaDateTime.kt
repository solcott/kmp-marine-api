package io.github.solcott.marineapi.nmea

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
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
    // orNull rather than the throwing constructor so the message names the field that carried the
    // bad value; kotlinx-datetime's own message names only the component it rejected.
    return requireNotNull(LocalDate.orNull(expandYear(year), month, day)) {
      "Date field is not a valid date: \"$field\""
    }
  }

  /**
   * Parses an `hhmmss[.ss]` field as an **elapsed time**, not a time of day.
   *
   * The same six digits, a different meaning. ZTG's "time remaining" and ZFO's "elapsed time" are
   * durations, and reading them as a [LocalTime] would both misdescribe them and break at the first
   * leg longer than a day -- a passage of 30 hours is a perfectly ordinary thing to have left to
   * run, and `LocalTime` cannot hold it.
   *
   * @throws IllegalArgumentException if [field] is not an elapsed time.
   */
  public fun parseElapsed(field: String): Duration {
    require(field.length >= 6) { "Elapsed time must be at least 6 characters: \"$field\"" }
    val hours = field.substring(0, 2).toIntOrNull()
    val minutes = field.substring(2, 4).toIntOrNull()
    val seconds = field.substring(4).toDoubleOrNull()
    require(hours != null && minutes != null && seconds != null) {
      "Elapsed time is not numeric: \"$field\""
    }
    return hours.hours + minutes.minutes + seconds.seconds
  }

  /** Formats [elapsed] as the `hhmmss` field ZTG and ZFO carry. */
  public fun formatElapsed(elapsed: Duration): String =
    elapsed.toComponents { hours, minutes, seconds, _ ->
      NmeaFormat.integer(hours.toInt(), 2) +
        NmeaFormat.integer(minutes, 2) +
        NmeaFormat.integer(seconds, 2)
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
    // See parseDate: orNull to keep the field in the message.
    return requireNotNull(LocalTime.orNull(hour, minute, wholeSeconds, nanoseconds.toInt())) {
      "Time field is not a valid time: \"$field\""
    }
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
   *
   * @throws IllegalArgumentException if the two fields together are not a UTC offset.
   */
  public fun utcOffset(hours: Int?, minutes: Int?): UtcOffset? {
    if (hours == null || minutes == null) return null
    val magnitude = minutes.absoluteValueOf()
    val signed = if (hours < 0) -magnitude else magnitude
    // See parseDate. Still throws rather than returning null: a ZDA whose zone fields will not
    // read is a malformed sentence, not a sentence with an absent offset.
    return requireNotNull(UtcOffset.orNull(hours = hours, minutes = signed)) {
      "Not a UTC offset: $hours hours, $minutes minutes"
    }
  }

  private fun Int.absoluteValueOf(): Int = if (this < 0) -this else this
}
