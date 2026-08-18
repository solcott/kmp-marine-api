package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.Nmea
import io.github.solcott.marineapi.nmea.NmeaFieldException
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceFields
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.buildNmea

/**
 * An AIS sentence: the NMEA envelope that carries an AIS message.
 *
 * AIS is a separate system from NMEA 0183 that borrows its sentence format for transport. The
 * sentence says nothing about a vessel; it carries a [payload] of six-bit-encoded data that has to
 * be decoded separately, and which may be split across several sentences. So this layer is only
 * about the envelope -- how much of a message is here, and where it fits.
 *
 * Both AIS sentence types, [Vdm] and [Vdo], have the same six fields and differ only in who sent
 * the message, so they share this interface rather than each having their own.
 *
 * Encapsulated sentences begin with `!` rather than `$`.
 *
 * @property fragmentCount how many sentences this message is split across
 * @property fragmentNumber which of them this is, counting from 1
 * @property messageId sequential id tying the fragments of one message together, empty on a message
 *   that fits in a single sentence
 * @property radioChannel AIS radio channel, `A` or `B` (also seen as `1` and `2`)
 * @property payload the six-bit encoded message data
 * @property fillBits padding bits added to the end of [payload] to reach a six-bit boundary, 0 to 5
 */
public interface AisSentence : Sentence {

  public val fragmentCount: Int
  public val fragmentNumber: Int
  public val messageId: String?
  public val radioChannel: String?
  public val payload: String
  public val fillBits: Int

  override val beginChar: Char
    get() = Nmea.ALTERNATIVE_BEGIN_CHAR

  /** True if the message is split across more than one sentence. */
  public val isFragmented: Boolean
    get() = fragmentCount > 1

  /** True if this is the first sentence of its message. */
  public val isFirstFragment: Boolean
    get() = fragmentNumber == 1

  /** True if this is the last sentence of its message. */
  public val isLastFragment: Boolean
    get() = fragmentNumber == fragmentCount

  /**
   * Whether this sentence carries the continuation of the message [previous] belongs to.
   *
   * The two must agree on [fragmentCount] and this one must come later in the sequence. Beyond
   * that, the test is deliberately looser for the sentence immediately following: an adjacent
   * fragment need match on only *one* of [radioChannel] and [messageId], while a fragment further
   * along must match on both. Interleaved messages on one channel are what the message id
   * distinguishes, and simultaneous messages on two channels are what the channel distinguishes, so
   * either identifies a neighbour on its own -- and a receiver that leaves one of them empty, which
   * happens, can still be followed.
   *
   * A gap between fragments is not repaired: [previous] is a sentence, not a partial message, so a
   * caller reassembling one has to keep the sequence itself. [io.github.solcott.marineapi.ais]
   * does.
   */
  public fun continues(previous: AisSentence): Boolean {
    if (fragmentCount != previous.fragmentCount) return false
    if (fragmentNumber <= previous.fragmentNumber) return false
    return if (fragmentNumber == previous.fragmentNumber + 1) {
      radioChannel == previous.radioChannel || messageId == previous.messageId
    } else {
      radioChannel == previous.radioChannel && messageId == previous.messageId
    }
  }
}

/**
 * AIS report received from another vessel or station.
 *
 * Example: `!AIVDM,1,1,,A,403OviQuMGCqWrRO9>E6fE700@GO,0*4D`
 *
 * The common case: everything the receiver hears over the air. Decode [payload] with
 * [io.github.solcott.marineapi.ais.AisRegistry] to get the message itself.
 */
public data class Vdm(
  override val talker: TalkerId,
  override val fragmentCount: Int,
  override val fragmentNumber: Int,
  override val messageId: String? = null,
  override val radioChannel: String? = null,
  override val payload: String,
  override val fillBits: Int,
) : AisSentence {

  init {
    requireAisEnvelope(fragmentCount, fragmentNumber, payload, fillBits)
  }

  override val id: String
    get() = ID

  override fun toNmeaString(): String = buildAisNmea(this, ID)

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "VDM"

    /** Reads a VDM sentence from its fields. */
    public fun from(fields: SentenceFields): Vdm =
      with(fields.aisEnvelope()) {
        Vdm(
          talker = fields.talker,
          fragmentCount = fragmentCount,
          fragmentNumber = fragmentNumber,
          messageId = messageId,
          radioChannel = radioChannel,
          payload = payload,
          fillBits = fillBits,
        )
      }
  }
}

/**
 * AIS report describing the vessel that sent it -- the "own vessel" report.
 *
 * Example: `!AIVDO,1,1,,B,H1c2;qA@PU>0U>060<h5=>0:1Dp,2*7D`
 *
 * Identical in form to [Vdm] and decoded the same way. The distinction matters to a chart plotter,
 * which draws own-vessel reports as the boat it is fitted to rather than as traffic.
 */
public data class Vdo(
  override val talker: TalkerId,
  override val fragmentCount: Int,
  override val fragmentNumber: Int,
  override val messageId: String? = null,
  override val radioChannel: String? = null,
  override val payload: String,
  override val fillBits: Int,
) : AisSentence {

  init {
    requireAisEnvelope(fragmentCount, fragmentNumber, payload, fillBits)
  }

  override val id: String
    get() = ID

  override fun toNmeaString(): String = buildAisNmea(this, ID)

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "VDO"

    /** Reads a VDO sentence from its fields. */
    public fun from(fields: SentenceFields): Vdo =
      with(fields.aisEnvelope()) {
        Vdo(
          talker = fields.talker,
          fragmentCount = fragmentCount,
          fragmentNumber = fragmentNumber,
          messageId = messageId,
          radioChannel = radioChannel,
          payload = payload,
          fillBits = fillBits,
        )
      }
  }
}

/** The six envelope fields, read once for both AIS sentence types. */
internal class AisEnvelope(
  val fragmentCount: Int,
  val fragmentNumber: Int,
  val messageId: String?,
  val radioChannel: String?,
  val payload: String,
  val fillBits: Int,
)

private const val FRAGMENT_COUNT = 0
private const val FRAGMENT_NUMBER = 1
private const val MESSAGE_ID = 2
private const val RADIO_CHANNEL = 3
private const val PAYLOAD = 4
private const val FILL_BITS = 5

/** Largest number of padding bits a sentence can declare: a six-bit character less one. */
private const val MAX_FILL_BITS = 5

/**
 * Reads the AIS envelope.
 *
 * Four of the six fields are required rather than nullable, which is a departure from how this
 * library treats NMEA fields generally. They are not optional data: without the fragment count and
 * number a message cannot be reassembled, and without the payload there is nothing to reassemble. A
 * sentence missing any of them is corrupt, not terse, and is reported as such.
 */
internal fun SentenceFields.aisEnvelope(): AisEnvelope =
  AisEnvelope(
    fragmentCount = requiredIntAt(FRAGMENT_COUNT, "fragment count"),
    fragmentNumber = requiredIntAt(FRAGMENT_NUMBER, "fragment number"),
    messageId = stringAt(MESSAGE_ID),
    radioChannel = stringAt(RADIO_CHANNEL),
    payload = stringAt(PAYLOAD) ?: throw NmeaFieldException("AIS sentence carries no payload"),
    fillBits = requiredIntAt(FILL_BITS, "fill bits"),
  )

private fun SentenceFields.requiredIntAt(index: Int, name: String): Int =
  intAt(index) ?: throw NmeaFieldException("AIS sentence has no $name")

/** Rejects an envelope that cannot describe a real fragment of a real message. */
internal fun requireAisEnvelope(
  fragmentCount: Int,
  fragmentNumber: Int,
  payload: String,
  fillBits: Int,
) {
  require(fragmentCount >= 1) { "An AIS message has at least one fragment, got $fragmentCount" }
  require(fragmentNumber in 1..fragmentCount) {
    "Fragment $fragmentNumber is not one of $fragmentCount"
  }
  require(payload.isNotEmpty()) { "An AIS sentence carries a payload" }
  require(fillBits in 0..MAX_FILL_BITS) { "Fill bits are 0 to $MAX_FILL_BITS, got $fillBits" }
}

private fun buildAisNmea(sentence: AisSentence, id: String): String =
  buildNmea(
    sentence.talker,
    id,
    listOf(
      sentence.fragmentCount.toString(),
      sentence.fragmentNumber.toString(),
      sentence.messageId,
      sentence.radioChannel,
      sentence.payload,
      sentence.fillBits.toString(),
    ),
    beginChar = Nmea.ALTERNATIVE_BEGIN_CHAR,
  )
