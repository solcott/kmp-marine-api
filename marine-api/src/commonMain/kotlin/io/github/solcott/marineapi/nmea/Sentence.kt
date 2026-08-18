package io.github.solcott.marineapi.nmea

/**
 * A parsed NMEA 0183 sentence.
 *
 * Implementations are immutable value types. Optional data is modelled with nullable properties: an
 * empty NMEA field reads as `null` rather than raising, because empty is how the format says "no
 * valid data for this field".
 *
 * This interface is deliberately **not** sealed. Sealed subtypes would have to live in this module,
 * which would rule out the third-party sentence types that [SentenceRegistry] exists to support.
 * Unrecognised sentences arrive as [UnknownSentence] rather than being dropped.
 */
public interface Sentence {

  /** Talker that sent this sentence. */
  public val talker: TalkerId

  /** Three-character type code, e.g. `GGA`. */
  public val id: String

  /** Start delimiter: `$` for ordinary sentences, `!` for encapsulated ones such as AIS. */
  public val beginChar: Char
    get() = Nmea.BEGIN_CHAR

  /**
   * Renders this sentence in NMEA form with a freshly computed checksum, without a line terminator.
   *
   * The result round-trips: parsing the returned string yields an equal sentence.
   */
  public fun toNmeaString(): String
}

/**
 * A sentence whose type code is not registered, kept intact rather than discarded.
 *
 * This is what makes an unknown or proprietary sentence usable without writing a parser for it --
 * the fields are available positionally, and [toNmeaString] reproduces the original.
 */
public class UnknownSentence(private val fields: SentenceFields) : Sentence {

  override val talker: TalkerId
    get() = fields.talker

  override val id: String
    get() = fields.id

  override val beginChar: Char
    get() = fields.beginChar

  /** Positional access to the raw fields. */
  public val data: SentenceFields
    get() = fields

  override fun toNmeaString(): String = Checksum.append(fields.toString())

  override fun equals(other: Any?): Boolean =
    this === other || (other is UnknownSentence && other.fields.toString() == fields.toString())

  override fun hashCode(): Int = fields.toString().hashCode()

  override fun toString(): String = toNmeaString()
}
