package io.github.solcott.marineapi.nmea

/**
 * Outcome of parsing one line of NMEA input.
 *
 * A stream of NMEA is routinely dirty -- partial first lines, electrical noise, sentences from
 * devices speaking a different NMEA version -- so a failed line is ordinary data rather than an
 * exceptional condition. Every failure carries the offending line so it can be logged or counted.
 */
public sealed interface ParseResult {

  /** The line this result came from, without its line terminator. */
  public val line: String

  /** Parsed successfully. */
  public class Ok(override val line: String, public val sentence: Sentence) : ParseResult {
    override fun toString(): String = "Ok($sentence)"
  }

  /** The line carried a checksum that does not match its content. */
  public class BadChecksum(
    override val line: String,
    /** Checksum computed from the sentence body. */
    public val expected: String,
    /** Checksum the sentence actually carried. */
    public val actual: String,
  ) : ParseResult {
    override fun toString(): String = "BadChecksum(expected=$expected, actual=$actual, line=$line)"
  }

  /**
   * The line is not a well-formed sentence, or a field held a value of the wrong type.
   *
   * [reason] describes what failed, e.g. `GPGGA field 8 is not a number: "abc"`.
   */
  public class Malformed(override val line: String, public val reason: String) : ParseResult {
    override fun toString(): String = "Malformed($reason, line=$line)"
  }

  public companion object
}

/** The parsed sentence, or `null` for any failure. */
public fun ParseResult.sentenceOrNull(): Sentence? = (this as? ParseResult.Ok)?.sentence

/**
 * The parsed sentence, or throws [NmeaParseException] describing the failure.
 *
 * For callers who would rather not branch, typically when parsing input known to be well-formed.
 */
public fun ParseResult.getOrThrow(): Sentence =
  when (this) {
    is ParseResult.Ok -> sentence
    is ParseResult.BadChecksum ->
      throw NmeaParseException(
        "Checksum mismatch: expected $expected but read $actual in \"$line\""
      )
    is ParseResult.Malformed -> throw NmeaParseException("$reason in \"$line\"")
  }

/** Thrown by [getOrThrow] when a line could not be parsed. */
public class NmeaParseException(message: String) : RuntimeException(message)
