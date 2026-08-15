package io.github.solcott.marineapi.nmea

import io.github.solcott.marineapi.nmea.sentence.Apb
import io.github.solcott.marineapi.nmea.sentence.Bod
import io.github.solcott.marineapi.nmea.sentence.Dbt
import io.github.solcott.marineapi.nmea.sentence.Dpt
import io.github.solcott.marineapi.nmea.sentence.Gga
import io.github.solcott.marineapi.nmea.sentence.Gll
import io.github.solcott.marineapi.nmea.sentence.Gsa
import io.github.solcott.marineapi.nmea.sentence.Gsv
import io.github.solcott.marineapi.nmea.sentence.Hdg
import io.github.solcott.marineapi.nmea.sentence.Hdm
import io.github.solcott.marineapi.nmea.sentence.Hdt
import io.github.solcott.marineapi.nmea.sentence.Mtw
import io.github.solcott.marineapi.nmea.sentence.Mwd
import io.github.solcott.marineapi.nmea.sentence.Mwv
import io.github.solcott.marineapi.nmea.sentence.Rmb
import io.github.solcott.marineapi.nmea.sentence.Rmc
import io.github.solcott.marineapi.nmea.sentence.Rte
import io.github.solcott.marineapi.nmea.sentence.Vbw
import io.github.solcott.marineapi.nmea.sentence.Vdr
import io.github.solcott.marineapi.nmea.sentence.Vhw
import io.github.solcott.marineapi.nmea.sentence.Vlw
import io.github.solcott.marineapi.nmea.sentence.Vpw
import io.github.solcott.marineapi.nmea.sentence.Vtg
import io.github.solcott.marineapi.nmea.sentence.Vwr
import io.github.solcott.marineapi.nmea.sentence.Vwt
import io.github.solcott.marineapi.nmea.sentence.Wpl
import io.github.solcott.marineapi.nmea.sentence.Xte
import io.github.solcott.marineapi.nmea.sentence.Zda

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
     * The sentence types are being ported in batches; anything not yet ported parses as
     * [UnknownSentence] rather than failing.
     */
    public val Default: SentenceRegistry =
      SentenceRegistry(
        mapOf(
          Apb.ID to SentenceFactory(Apb::from),
          Bod.ID to SentenceFactory(Bod::from),
          Dbt.ID to SentenceFactory(Dbt::from),
          Dpt.ID to SentenceFactory(Dpt::from),
          Gga.ID to SentenceFactory(Gga::from),
          Gll.ID to SentenceFactory(Gll::from),
          Gsa.ID to SentenceFactory(Gsa::from),
          Gsv.ID to SentenceFactory(Gsv::from),
          Hdg.ID to SentenceFactory(Hdg::from),
          Hdm.ID to SentenceFactory(Hdm::from),
          Hdt.ID to SentenceFactory(Hdt::from),
          Mtw.ID to SentenceFactory(Mtw::from),
          Mwd.ID to SentenceFactory(Mwd::from),
          Mwv.ID to SentenceFactory(Mwv::from),
          Rmb.ID to SentenceFactory(Rmb::from),
          Rmc.ID to SentenceFactory(Rmc::from),
          Rte.ID to SentenceFactory(Rte::from),
          Vbw.ID to SentenceFactory(Vbw::from),
          Vdr.ID to SentenceFactory(Vdr::from),
          Vhw.ID to SentenceFactory(Vhw::from),
          Vlw.ID to SentenceFactory(Vlw::from),
          Vpw.ID to SentenceFactory(Vpw::from),
          Vtg.ID to SentenceFactory(Vtg::from),
          Vwr.ID to SentenceFactory(Vwr::from),
          Vwt.ID to SentenceFactory(Vwt::from),
          Wpl.ID to SentenceFactory(Wpl::from),
          Xte.ID to SentenceFactory(Xte::from),
          Zda.ID to SentenceFactory(Zda::from),
        )
      )

    /** An empty registry, for parsing into [UnknownSentence] only. */
    public val Empty: SentenceRegistry = SentenceRegistry(emptyMap())
  }
}
