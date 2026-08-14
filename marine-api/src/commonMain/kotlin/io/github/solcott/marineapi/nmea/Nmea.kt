package io.github.solcott.marineapi.nmea

/**
 * Structural constants of the NMEA 0183 sentence format.
 *
 * Values here follow the format description in
 * [gpsd's NMEA reference](https://gpsd.gitlab.io/gpsd/NMEA.html). The official NMEA 0183 standard
 * is a paid document and was not available; where this library's historical behaviour and the
 * public description disagree, the disagreement is called out on the declaration.
 */
public object Nmea {

  /** Start delimiter of an ordinary sentence. */
  public const val BEGIN_CHAR: Char = '$'

  /** Start delimiter of an encapsulated sentence, used by AIS (`!AIVDM`, `!AIVDO`). */
  public const val ALTERNATIVE_BEGIN_CHAR: Char = '!'

  /** Separates the checksum from the sentence body. */
  public const val CHECKSUM_DELIMITER: Char = '*'

  /** Separates data fields. */
  public const val FIELD_DELIMITER: Char = ','

  /**
   * Maximum length of a complete sentence in bytes, counting the start delimiter and the trailing
   * `CR LF`.
   *
   * The Java implementation this replaces exposed 82 as `Sentence.MAX_LENGTH` but compared it
   * against a string carrying no line terminator, so it permitted two characters more than the
   * format allows. Use [MAX_BODY_LENGTH] for that comparison instead.
   */
  public const val MAX_SENTENCE_LENGTH: Int = 82

  /**
   * Maximum length of a sentence rendered as a string: [MAX_SENTENCE_LENGTH] less the two bytes of
   * the `CR LF` terminator, which this library never includes in the string form.
   */
  public const val MAX_BODY_LENGTH: Int = MAX_SENTENCE_LENGTH - 2

  /** Prefix identifying a proprietary sentence, e.g. `$PGRMZ`. */
  public const val PROPRIETARY_PREFIX: Char = 'P'

  /** True if [char] can start a sentence. */
  public fun isBeginChar(char: Char): Boolean = char == BEGIN_CHAR || char == ALTERNATIVE_BEGIN_CHAR
}
