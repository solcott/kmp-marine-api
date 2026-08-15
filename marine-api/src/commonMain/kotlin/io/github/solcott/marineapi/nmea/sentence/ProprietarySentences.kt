package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.NmeaFieldException
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceFields
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.buildNmea

/**
 * u-blox proprietary sentence, the envelope for a u-blox message.
 *
 * Example:
 * `$PUBX,00,125926.00,4717.11337,N,00833.91163,E,111.500,GLL,20,15,0.007,0.00,,,,3.00,3.00,3.00,,,,,,,*7C`
 *
 * Proprietary sentences are `$P` followed by a manufacturer mnemonic instead of a talker and a type
 * code, so the whole tag here is `PUBX` and [talker] is [TalkerId.P]. What the fields mean depends
 * entirely on [messageId]: `00` is a position report, `03` a satellite status report, and u-blox
 * defines others this library does not decode. Decode the message with
 * [io.github.solcott.marineapi.ublox.UbloxRegistry].
 *
 * The fields are kept as raw text, which is the honest representation for a sentence whose layout
 * is not known until its first field has been read. That is also all the Java implementation
 * offered for it -- a bag of `getUBXFieldIntValue`-style accessors -- with the difference that
 * these are a value rather than a live view onto a mutable parser.
 *
 * @property messageId u-blox message type, e.g. 0 for a position report
 * @property fields every field of the sentence, [messageId] included at index 0, empty fields as
 *   `null`
 */
public data class Ubx(
  override val talker: TalkerId,
  val messageId: Int,
  val fields: List<String?> = emptyList(),
) : Sentence {

  override val id: String
    get() = ID

  /** Field at [index], or `null` if it is empty or past the end of the sentence. */
  public fun stringAt(index: Int): String? = fields.getOrNull(index)

  /** Field at [index] as an [Int], or `null` if it is empty, absent or not a number. */
  public fun intAt(index: Int): Int? = stringAt(index)?.toIntOrNull()

  /** Field at [index] as a [Double], or `null` if it is empty, absent or not a number. */
  public fun doubleAt(index: Int): Double? = stringAt(index)?.toDoubleOrNull()

  override fun toNmeaString(): String = buildNmea(talker, ID, fields)

  public companion object {
    /** Sentence type code. Proprietary, so it stands in for the whole `PUBX` tag. */
    public const val ID: String = "UBX"

    private const val MESSAGE_ID = 0

    /** Reads a UBX sentence from its fields. */
    public fun from(fields: SentenceFields): Ubx =
      Ubx(
        talker = fields.talker,
        messageId =
          fields.intAt(MESSAGE_ID)
            ?: throw NmeaFieldException(
              "UBX sentence has no message id, so its fields have no meaning"
            ),
        fields = List(fields.size) { fields.stringAt(it) },
      )
  }
}

/**
 * SeaTalk sentence, carrying a Raymarine SeaTalk datagram over NMEA.
 *
 * Example: `$STALK,52,A1,00,00*36`
 *
 * SeaTalk is Raymarine's own instrument bus. A SeaTalk-to-NMEA bridge wraps each datagram in this
 * sentence rather than translating it: [command] is the SeaTalk datagram type and [parameters] its
 * bytes, all as two-digit hex. Interpreting them needs a SeaTalk datagram reference, which this
 * library does not implement -- it delivers the datagram intact and stops there.
 *
 * The tag is `$STALK`: talker `ST`, type `ALK`. That is the only talker this sentence occurs with,
 * and a different one means the tag was misread.
 *
 * @property command SeaTalk datagram type, two hex digits
 * @property parameters the datagram's remaining bytes, each two hex digits
 */
public data class Stalk(
  override val talker: TalkerId = TalkerId.ST,
  val command: String,
  val parameters: List<String> = emptyList(),
) : Sentence {

  init {
    require(talker == TalkerId.ST) { "A SeaTalk sentence is always \$STALK, got \$$talker$ID" }
    require(command.isNotEmpty()) { "A SeaTalk sentence carries a command" }
  }

  override val id: String
    get() = ID

  override fun toNmeaString(): String = buildNmea(talker, ID, listOf(command) + parameters)

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "ALK"

    private const val COMMAND = 0

    /** Reads a SeaTalk sentence from its fields. */
    public fun from(fields: SentenceFields): Stalk =
      Stalk(
        talker = fields.talker,
        command =
          fields.stringAt(COMMAND) ?: throw NmeaFieldException("SeaTalk sentence has no command"),
        parameters = List(maxOf(fields.size - 1, 0)) { fields.stringAt(it + 1).orEmpty() },
      )
  }
}
