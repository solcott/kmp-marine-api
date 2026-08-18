package io.github.solcott.marineapi.nmea

/**
 * Assembles an NMEA sentence from its parts, appending the checksum.
 *
 * A `null` field is written as an empty one, which is what NMEA uses for "no data". Trailing nulls
 * are kept rather than trimmed: a receiver that reports fewer fields than the format allows is
 * saying something different from one that reports them empty, and dropping them would change the
 * field count a reader sees.
 *
 * ```
 * buildNmea(TalkerId.GP, "GLL", listOf("6011.552", "N", "02501.941", "E", "120045", "A"))
 * // $GPGLL,6011.552,N,02501.941,E,120045,A*26
 * ```
 */
public fun buildNmea(
  talker: TalkerId,
  id: String,
  fields: List<String?>,
  beginChar: Char = Nmea.BEGIN_CHAR,
): String {
  val body = fields.joinToString(",") { it ?: "" }
  return Checksum.append("$beginChar$talker$id,$body")
}

/** Renders a coded value as its NMEA character, or `null` when there is no value. */
internal fun CharCoded?.field(): String? = this?.code?.toString()

/** Renders an integer-coded value as its NMEA digits, or `null` when there is no value. */
internal fun IntCoded?.field(): String? = this?.code?.toString()

/**
 * Renders a number for an NMEA field, keeping every significant decimal up to [maxDecimals].
 *
 * The width of a numeric NMEA field is device-dependent, so there is no correct fixed precision to
 * write: rounding to a guessed number of decimals would silently discard data a receiver reported.
 * Trailing zeros are dropped and one decimal is always kept, so 16.89 stays 16.89 and 360.0 stays
 * 360.0.
 *
 * `Double.toString` is not usable here: Kotlin/JS renders 5.0 as `5`, so a sentence built on JS
 * would differ from the same sentence built on the JVM.
 */
internal fun Double?.field(maxDecimals: Int = 6): String? =
  if (this == null) null else formatTrimmed(this, maxDecimals)

/** Formats [value] with at most [maxDecimals] decimals, trailing zeros removed but never all. */
internal fun formatTrimmed(value: Double, maxDecimals: Int = 6): String {
  val text = NmeaFormat.decimal(value, 1, maxDecimals).trimEnd('0')
  return if (text.endsWith('.')) text + "0" else text
}
