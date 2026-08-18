package io.github.solcott.marineapi.ais

import io.github.solcott.marineapi.nmea.sentence.AisSentence

/** Builds an [AisMessage] from a decoded payload. */
public fun interface AisMessageFactory {
  public fun create(bits: Sixbit): AisMessage
}

/**
 * The result of decoding an AIS payload.
 *
 * A separate type from [io.github.solcott.marineapi.nmea.ParseResult] because the failures are
 * different in kind: an AIS payload arrives inside a sentence whose checksum has already been
 * verified, so it is not corrupt in the way a line of NMEA can be. What goes wrong instead is that
 * the message type has no decoder, or that a fragment sequence never completed.
 */
public sealed interface AisResult {

  /** The payload the result came from, encoded as it arrived. */
  public val payload: Sixbit

  /** The message decoded successfully. */
  public data class Ok(override val payload: Sixbit, val message: AisMessage) : AisResult

  /**
   * The message type has no decoder in this registry.
   *
   * Not a failure so much as a gap: AIS defines 27 types and this library implements the ones that
   * describe vessels and marks. [messageType] and [payload] are still available, so an application
   * that cares about one of the rest can decode it itself.
   */
  public data class Unsupported(override val payload: Sixbit, val messageType: Int) : AisResult

  /** The payload was decoded as far as its type but the message itself would not read. */
  public data class Malformed(override val payload: Sixbit, val reason: String) : AisResult
}

/** The message if it decoded, or `null`. */
public fun AisResult.messageOrNull(): AisMessage? = (this as? AisResult.Ok)?.message

/**
 * Maps AIS message types to the code that decodes them.
 *
 * The lambda registration replaces the Java implementation's reflective `Constructor.newInstance`,
 * for the same reasons it did in [io.github.solcott.marineapi.nmea.SentenceRegistry]: reflection
 * does not work on Kotlin/Native or JS, and a factory function can do things a constructor of a
 * fixed shape cannot -- type 24 needs to choose a class based on a field inside the payload, which
 * the Java factory had no way to express.
 *
 * ```
 * val message = AisRegistry.Default.decode(sentence.payload, sentence.fillBits).messageOrNull()
 * ```
 */
public class AisRegistry private constructor(private val factories: Map<Int, AisMessageFactory>) {

  /** Message types this registry can decode. */
  public val types: Set<Int>
    get() = factories.keys

  /** A copy of this registry that also decodes [type], replacing any existing entry. */
  public fun with(type: Int, factory: AisMessageFactory): AisRegistry =
    AisRegistry(factories + (type to factory))

  /** A copy of this registry with [type] removed. */
  public fun without(type: Int): AisRegistry = AisRegistry(factories - type)

  /** Decodes an already-assembled payload. */
  public fun decode(payload: String, fillBits: Int = 0): AisResult {
    val bits =
      try {
        Sixbit(payload, fillBits)
      } catch (e: IllegalArgumentException) {
        return AisResult.Malformed(Sixbit("0"), e.message ?: "Invalid payload")
      }
    return decode(bits)
  }

  /**
   * Decodes a payload.
   *
   * The message length is not checked against what the type should be. Real transponders pad
   * messages out and truncate them, and the Java implementation rejected both outright -- a type 5
   * of 422 bits rather than 424 threw rather than yielding a name and a destination that were
   * perfectly readable. A field that runs off the end of a short payload does fail, and that is
   * reported as [AisResult.Malformed].
   */
  public fun decode(bits: Sixbit): AisResult {
    if (bits.size < MINIMUM_HEADER_BITS) {
      return AisResult.Malformed(bits, "Payload is ${bits.size} bits, too short to carry an MMSI")
    }
    val type = bits.uintAt(0, 6)
    val factory = factories[type] ?: return AisResult.Unsupported(bits, type)
    return try {
      AisResult.Ok(bits, factory.create(bits))
    } catch (e: IllegalArgumentException) {
      AisResult.Malformed(bits, e.message ?: "Invalid message")
    }
  }

  public companion object {

    /** Type, repeat indicator and MMSI: the least a payload must carry to be worth reading. */
    private const val MINIMUM_HEADER_BITS = 38

    /** Every AIS message type this library decodes. */
    public val Default: AisRegistry =
      AisRegistry(
        buildMap {
          for (type in AisPositionReport.TYPES) put(
            type,
            AisMessageFactory(AisPositionReport::from),
          )
          for (type in AisBaseStationReport.TYPES) {
            put(type, AisMessageFactory(AisBaseStationReport::from))
          }
          put(AisBinaryAcknowledge.TYPE, AisMessageFactory(AisBinaryAcknowledge::from))
          put(AisStaticAndVoyageData.TYPE, AisMessageFactory(AisStaticAndVoyageData::from))
          put(
            AisSarAircraftPositionReport.TYPE,
            AisMessageFactory(AisSarAircraftPositionReport::from),
          )
          put(AisPositionReportB.TYPE, AisMessageFactory(AisPositionReportB::from))
          put(AisExtendedPositionReportB.TYPE, AisMessageFactory(AisExtendedPositionReportB::from))
          put(AisAidToNavigationReport.TYPE, AisMessageFactory(AisAidToNavigationReport::from))
          put(AisStaticDataReport.TYPE, AisMessageFactory(::staticDataReportFrom))
          put(AisLongRangePositionReport.TYPE, AisMessageFactory(AisLongRangePositionReport::from))
        }
      )

    /** A registry that decodes nothing, for building one up from scratch. */
    public val Empty: AisRegistry = AisRegistry(emptyMap())
  }
}

/**
 * Joins the fragments of one AIS message into a single payload.
 *
 * A message longer than a sentence can carry is split across up to nine of them, each holding a
 * slice of the same six-bit stream, and only the last fragment's fill bits count -- the others end
 * on a character boundary by construction.
 */
internal fun joinFragments(fragments: List<AisSentence>): Sixbit =
  Sixbit(fragments.joinToString("") { it.payload }, fragments.last().fillBits)
