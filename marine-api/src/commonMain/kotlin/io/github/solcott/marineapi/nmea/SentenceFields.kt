package io.github.solcott.marineapi.nmea

import kotlin.time.Duration
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Thrown when a field holds a value that cannot be read as the requested type.
 *
 * This is distinct from an *empty* field, which is how NMEA represents "no data" and which the
 * accessors on [SentenceFields] return as `null`. A field that contains `abc` where a number is
 * expected is corruption, and reporting it as absent would hide it.
 *
 * [SentenceRegistry.parse] catches this and reports it as [ParseResult.Malformed]; it should not
 * escape to callers.
 */
public class NmeaFieldException(message: String) : IllegalArgumentException(message)

/**
 * The comma-separated fields of one sentence, with accessors that treat an empty field as `null`.
 *
 * Field indices are zero-based and exclude the tag, so for `$GPGGA,123519,4807.038,N,...` index 0
 * is `123519`. Indices beyond the end of the sentence read as `null` rather than failing: real
 * devices truncate trailing empty fields, and later NMEA versions append fields that older ones
 * omit.
 */
public class SentenceFields
internal constructor(
  /** Start delimiter this sentence was read with, `$` or `!`. */
  public val beginChar: Char,
  /** Talker that sent the sentence. */
  public val talker: TalkerId,
  /** Three-character sentence type code, e.g. `GGA`. */
  public val id: String,
  private val fields: List<String>,
) {

  /** Number of fields present, excluding the tag. */
  public val size: Int
    get() = fields.size

  /** True if this is a proprietary sentence, whose tag is `$P` followed by a manufacturer code. */
  public val isProprietary: Boolean
    get() = talker == TalkerId.P

  /** Raw field at [index], or `null` if it is empty or beyond the end of the sentence. */
  public fun stringAt(index: Int): String? = fields.getOrNull(index)?.takeIf { it.isNotEmpty() }

  /**
   * Field at [index] as a [Double], or `null` if empty.
   *
   * @throws NmeaFieldException if the field is present but not a number.
   */
  public fun doubleAt(index: Int): Double? {
    val raw = stringAt(index) ?: return null
    return raw.toDoubleOrNull() ?: throw NmeaFieldException(fieldError(index, raw, "a number"))
  }

  /**
   * Field at [index] as an [Int], or `null` if empty.
   *
   * @throws NmeaFieldException if the field is present but not an integer.
   */
  public fun intAt(index: Int): Int? {
    val raw = stringAt(index) ?: return null
    return raw.toIntOrNull() ?: throw NmeaFieldException(fieldError(index, raw, "an integer"))
  }

  /**
   * Field at [index] as a single [Char], or `null` if empty.
   *
   * @throws NmeaFieldException if the field holds more than one character.
   */
  public fun charAt(index: Int): Char? {
    val raw = stringAt(index) ?: return null
    if (raw.length != 1) throw NmeaFieldException(fieldError(index, raw, "a single character"))
    return raw[0]
  }

  /**
   * Field at [index] as an `hhmmss[.sss]` time of day, or `null` if empty.
   *
   * @throws NmeaFieldException if the field is present but not a time.
   */
  public fun timeAt(index: Int): LocalTime? {
    val raw = stringAt(index) ?: return null
    return runCatching { NmeaDateTime.parseTime(raw) }
      .getOrElse { throw NmeaFieldException(fieldError(index, raw, "a time")) }
  }

  /**
   * Field at [index] as a `ddmmyy` date, or `null` if empty.
   *
   * @throws NmeaFieldException if the field is present but not a date.
   */
  public fun dateAt(index: Int): LocalDate? {
    val raw = stringAt(index) ?: return null
    return runCatching { NmeaDateTime.parseDate(raw) }
      .getOrElse { throw NmeaFieldException(fieldError(index, raw, "a date")) }
  }

  /**
   * Field at [index] as an `hhmmss[.ss]` elapsed time, or `null` if empty.
   *
   * A duration rather than a time of day -- see [NmeaDateTime.parseElapsed] for why the distinction
   * matters.
   *
   * @throws NmeaFieldException if the field is present but not an elapsed time.
   */
  public fun elapsedAt(index: Int): Duration? {
    val raw = stringAt(index) ?: return null
    return runCatching { NmeaDateTime.parseElapsed(raw) }
      .getOrElse { throw NmeaFieldException(fieldError(index, raw, "an elapsed time")) }
  }

  /**
   * Field at [index] as a `ddmmyy` date, or `null` if it is empty **or unreadable**.
   *
   * The tolerant counterpart to [dateAt], for a sentence whose date is advisory by the rule
   * [advisoryCodedAt] describes: nothing else in the sentence is read differently because of it.
   * RMC is the case that matters. Its date sits six fields after its position, and one receiver in
   * the conformance corpus sends `2208-1` there -- a corrupt date attached to a position that is
   * perfectly good, which [dateAt] would discard the whole sentence over. gpsd keeps the position.
   *
   * ZDA keeps [dateAt]: a ZDA whose date will not read has nothing left worth having.
   */
  public fun advisoryDateAt(index: Int): LocalDate? {
    val raw = stringAt(index) ?: return null
    return runCatching { NmeaDateTime.parseDate(raw) }.getOrNull()
  }

  /**
   * Field at [index] as one of [entries], matched on [CharCoded.code], or `null` if empty.
   *
   * ```
   * val status = fields.codedAt(1, DataStatus.entries)
   * ```
   *
   * @throws NmeaFieldException if the field holds a code none of [entries] declares. An
   *   unrecognised code is corruption or an unsupported dialect, not absence, so it is reported
   *   rather than folded into `null`.
   */
  public fun <T : CharCoded> codedAt(index: Int, entries: Iterable<T>): T? {
    val char = charAt(index) ?: return null
    return entries.fromCode(char)
      ?: throw NmeaFieldException(fieldError(index, char.toString(), expectedOneOf(entries)))
  }

  /** Field at [index] as one of [entries], matched on [IntCoded.code], or `null` if empty. */
  public fun <T : IntCoded> intCodedAt(index: Int, entries: Iterable<T>): T? {
    val value = intAt(index) ?: return null
    return entries.fromCode(value)
      ?: throw NmeaFieldException(fieldError(index, value.toString(), expectedOneOf(entries)))
  }

  /**
   * Field at [index] as one of [entries], or `null` if it is empty **or holds a code none of
   * [entries] declares**.
   *
   * This is the tolerant counterpart of [codedAt], for *advisory* fields. A field is advisory when
   * it reports status, provenance or identity and nothing else in the sentence has to be read
   * differently because of it -- an FAA mode, a nav status, a system id. A field is load-bearing
   * when its value changes the meaning or magnitude of another field: a hemisphere flips the sign
   * of a coordinate, a unit turns 5.5 into knots or kilometres per hour, MWV's reference decides
   * whether its angle is measured from the bow or from true north. Those keep [codedAt] and still
   * fail, because reading them wrongly produces a plausible number that is simply incorrect.
   *
   * Real receivers put unexpected values in advisory fields often enough that rejecting the whole
   * sentence is the wrong trade. In this project's sample logs a Saab R4 and a Motorola T805 both
   * put their FAA mode where RMC's variation direction belongs, and a BeiDou unit puts a fourth DOP
   * where NMEA 4.11 puts GSA's system id. Failing those lines would discard a valid position, time,
   * speed and course to object to one field nobody needed.
   *
   * gpsd, the reference implementation, goes further and never fails a sentence on any field value
   * once the checksum passes -- its coordinate decoder treats an unrecognised hemisphere as north
   * without comment. That is a reasonable trade for a daemon producing a best-effort fix and a bad
   * one for a parsing library, because it converts a detectable error into a position that is wrong
   * by twice the latitude.
   */
  public fun <T : CharCoded> advisoryCodedAt(index: Int, entries: Iterable<T>): T? {
    val raw = stringAt(index) ?: return null
    return if (raw.length == 1) entries.fromCode(raw[0]) else null
  }

  /**
   * Field at [index] as an [Int], or `null` if it is empty **or not an integer**.
   *
   * The tolerant counterpart of [intAt], for the same advisory fields [advisoryCodedAt] describes.
   * GSA's system id and GSV's signal id are the cases that need it: both were appended in NMEA
   * 4.11, and a device from before that revision may put something else in the slot.
   */
  public fun advisoryIntAt(index: Int): Int? = stringAt(index)?.toIntOrNull()

  /**
   * Field at [index] as one of [entries], or `null` if it is empty **or holds a code none of
   * [entries] declares**.
   *
   * The tolerant counterpart of [intCodedAt], for the same advisory fields [advisoryCodedAt]
   * describes: GGA's fix quality and GSA's fix status say how a fix was obtained, and receivers
   * invent values there faster than any table can track them.
   */
  public fun <T : IntCoded> advisoryIntCodedAt(index: Int, entries: Iterable<T>): T? {
    val value = advisoryIntAt(index) ?: return null
    return entries.fromCode(value)
  }

  /** Every field from [first] onwards, for the sentences that carry variable-length lists. */
  public fun stringsFrom(first: Int): List<String?> =
    if (first >= fields.size) emptyList()
    else fields.subList(first, fields.size).map { it.takeIf(String::isNotEmpty) }

  private fun fieldError(index: Int, raw: String, expected: String): String =
    "$talker$id field $index is not $expected: \"$raw\""

  private fun expectedOneOf(entries: Iterable<*>): String =
    entries.joinToString(prefix = "one of [", postfix = "]") {
      when (it) {
        is CharCoded -> it.code.toString()
        is IntCoded -> it.code.toString()
        else -> it.toString()
      }
    }

  override fun toString(): String = "$beginChar$talker$id,${fields.joinToString(",")}"
}
