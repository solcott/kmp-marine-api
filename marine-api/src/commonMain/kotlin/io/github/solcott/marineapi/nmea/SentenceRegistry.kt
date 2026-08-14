package io.github.solcott.marineapi.nmea

/**
 * Builds a [Sentence] from the fields of a recognised sentence type.
 *
 * Registering a lambda is what replaces the reflective `Constructor.newInstance` of the Java
 * implementation: it works on every Kotlin target, and it lets a factory close over whatever state
 * it needs instead of being restricted to a constructor of a fixed shape.
 */
public fun interface SentenceFactory {
  public fun create(fields: SentenceFields): Sentence
}

/**
 * Maps sentence type codes to the factories that parse them, and turns lines of NMEA into
 * [ParseResult]s.
 *
 * Instances are immutable; [with] returns a new registry rather than mutating a shared one. The
 * Java implementation exposed a mutable singleton whose `reset()` re-registered every built-in
 * parser, which made registration order observable across an entire process.
 *
 * ```
 * val registry = SentenceRegistry.Default.with("XYZ") { fields -> MyXyz(fields) }
 * when (val result = registry.parse(line)) {
 *   is ParseResult.Ok -> handle(result.sentence)
 *   else -> log(result)
 * }
 * ```
 */
public class SentenceRegistry
private constructor(private val factories: Map<String, SentenceFactory>) {

  /** Type codes this registry can parse, e.g. `GGA`. */
  public val types: Set<String>
    get() = factories.keys

  /**
   * A copy of this registry that also parses [type] with [factory], replacing any existing entry.
   */
  public fun with(type: String, factory: SentenceFactory): SentenceRegistry =
    SentenceRegistry(factories + (type to factory))

  /** A copy of this registry with [type] removed, so those sentences parse as [UnknownSentence]. */
  public fun without(type: String): SentenceRegistry = SentenceRegistry(factories - type)

  /**
   * Parses one line of NMEA.
   *
   * A trailing `CR`/`LF` is accepted and ignored. A sentence whose type is not registered is
   * returned as [UnknownSentence] rather than a failure -- unrecognised is not the same as invalid,
   * and the raw fields are still useful.
   *
   * Over-long sentences are **not** rejected. The format caps a sentence at
   * [Nmea.MAX_SENTENCE_LENGTH] bytes, but real devices exceed it and dropping their output would
   * lose data that parses perfectly well.
   */
  public fun parse(line: String): ParseResult {
    val body = line.trimEnd('\r', '\n')

    if (body.isEmpty()) return ParseResult.Malformed(line, "Empty line")
    if (!Nmea.isBeginChar(body[0])) {
      return ParseResult.Malformed(
        line,
        "Does not begin with '${Nmea.BEGIN_CHAR}' or '${Nmea.ALTERNATIVE_BEGIN_CHAR}'",
      )
    }

    val actualChecksum = Checksum.read(body)
    if (actualChecksum != null) {
      val expected = Checksum.calculate(body)
      if (!expected.equals(actualChecksum, ignoreCase = true)) {
        return ParseResult.BadChecksum(line, expected, actualChecksum)
      }
    }

    val content = body.substring(0, Checksum.delimiterIndex(body))
    val firstComma = content.indexOf(Nmea.FIELD_DELIMITER)
    if (firstComma < 0) return ParseResult.Malformed(line, "No field delimiter")

    // Proprietary sentences are '$P' plus a manufacturer mnemonic; everything else is a
    // two-character talker followed by the type code. Matches SentenceId.parseStr in the
    // implementation this replaces.
    val proprietary = content.length > 1 && content[1] == Nmea.PROPRIETARY_PREFIX
    val talker =
      if (proprietary) TalkerId.P else TalkerId.of(content.substring(1, minOf(3, firstComma)))
    val typeStart = if (proprietary) 2 else 3
    if (firstComma <= typeStart) return ParseResult.Malformed(line, "Missing sentence type")
    val type = content.substring(typeStart, firstComma)

    val fields =
      SentenceFields(
        beginChar = body[0],
        talker = talker,
        id = type,
        fields = content.substring(firstComma + 1).split(Nmea.FIELD_DELIMITER),
      )

    val factory = factories[type] ?: return ParseResult.Ok(line, UnknownSentence(fields))

    return try {
      ParseResult.Ok(line, factory.create(fields))
    } catch (e: NmeaFieldException) {
      ParseResult.Malformed(line, e.message ?: "Invalid field")
    }
  }

  public companion object {
    /**
     * The registry of every sentence type this library implements.
     *
     * Empty for now: the sentence types are being ported in batches, and anything not yet ported
     * parses as [UnknownSentence].
     */
    public val Default: SentenceRegistry = SentenceRegistry(emptyMap())

    /** An empty registry, for parsing into [UnknownSentence] only. */
    public val Empty: SentenceRegistry = SentenceRegistry(emptyMap())
  }
}
