package io.github.solcott.marineapi.ais

/** Bits each payload character encodes. */
private const val BITS_PER_CHAR = 6

/**
 * Lowest and highest ASCII codes a six-bit payload character can take, and the gap between them.
 */
private const val FIRST_CHAR = 0x30
private const val LAST_LOW_CHAR = 0x57
private const val FIRST_HIGH_CHAR = 0x60
private const val LAST_CHAR = 0x77

/** Subtracted from a payload character to recover its value; the high range is offset further. */
private const val LOW_OFFSET = 0x30
private const val HIGH_OFFSET = 0x38

/** Values below this in a decoded string are the upper-case block, which sits at 0x40 in ASCII. */
private const val CONTENT_SHIFT_LIMIT = 0x20
private const val CONTENT_SHIFT = 0x40

/** Padding character AIS strings are filled with. */
private const val PAD_CHAR = '@'

/**
 * The six-bit encoded payload of an AIS message, read as bits, integers and strings.
 *
 * AIS packs its messages into a bit stream and then encodes that stream six bits to a character so
 * it survives an NMEA sentence, which cannot carry arbitrary bytes. Every field of every message
 * type is defined as a bit range into this stream, so decoding a message means little more than
 * knowing which ranges to ask for.
 *
 * **Bit ranges are half-open and zero-based**: `[from, to)`, `to - from` bits wide, counting the
 * first bit of the message as 0. That is what the message definitions in the AIS specification use.
 * The Java implementation this replaces reached the same values by a more roundabout route -- it
 * built a `java.util.BitSet` whose indices were one-based, then read it with a loop whose lower
 * bound was exclusive -- and its documentation described the arguments as inclusive on both ends,
 * which they were not.
 *
 * Nothing is decoded up front. The payload is kept as its original text and bits are computed from
 * it on demand, so constructing this allocates only the object: no bit set, no intermediate array.
 * That also keeps it multiplatform, `BitSet` being a JVM class.
 *
 * @property payload the encoded text, exactly as the sentence carried it
 * @property fillBits padding bits added to the end of [payload] to reach a character boundary, and
 *   so not part of the message
 */
public class Sixbit(public val payload: String, public val fillBits: Int = 0) {

  init {
    require(payload.isNotEmpty()) { "An AIS payload is not empty" }
    require(fillBits in 0 until BITS_PER_CHAR) {
      "Fill bits are 0 to ${BITS_PER_CHAR - 1}, got $fillBits"
    }
    val invalid = payload.indexOfFirst { !isValidChar(it) }
    require(invalid < 0) {
      "Payload character '${payload[invalid]}' at $invalid is not six-bit encoded"
    }
  }

  /** Bits of message this payload carries, [fillBits] excluded. */
  public val size: Int
    get() = payload.length * BITS_PER_CHAR - fillBits

  /** The bit at [index], counting from 0. */
  public fun booleanAt(index: Int): Boolean {
    require(index in 0 until payload.length * BITS_PER_CHAR) { "Bit $index is outside the payload" }
    val value = valueOf(payload[index / BITS_PER_CHAR])
    val shift = BITS_PER_CHAR - 1 - index % BITS_PER_CHAR
    return (value shr shift) and 1 == 1
  }

  /** The bit at [index], or `null` if the payload is too short to carry it. */
  public fun booleanAtOrNull(index: Int): Boolean? =
    if (index in 0 until payload.length * BITS_PER_CHAR) booleanAt(index) else null

  /**
   * Bits `[from, to)` as an unsigned integer, most significant bit first.
   *
   * Ranges up to 31 bits wide; the widest field AIS defines is 30, an MMSI.
   */
  public fun uintAt(from: Int, to: Int): Int {
    requireRange(from, to)
    var value = 0
    for (index in from until to) {
      value = (value shl 1) or if (booleanAt(index)) 1 else 0
    }
    return value
  }

  /**
   * Bits `[from, to)` as a two's-complement signed integer of that width.
   *
   * One function for what the Java implementation spelled out as five -- `getAs8BitInt`,
   * `getAs17BitInt`, `getAs18BitInt`, `getAs27BitInt`, `getAs28BitInt` -- each hard-coding the two
   * powers of two that its own width implies. The width is `to - from` and the arithmetic follows
   * from it.
   */
  public fun intAt(from: Int, to: Int): Int {
    val value = uintAt(from, to)
    val width = to - from
    val signBit = 1 shl (width - 1)
    return if (value >= signBit) value - (signBit shl 1) else value
  }

  /**
   * Bits `[from, to)` as text, six bits per character.
   *
   * AIS text is a 64-character subset of ASCII in its own order, and fields are fixed width. `@` is
   * the terminator, so it and everything after it is dropped; devices also pad with spaces, and on
   * both sides -- one transponder in the reference fixtures sends its call sign as `" ZA83R"` -- so
   * surrounding whitespace goes too.
   *
   * A range running past the end of the payload is read as far as the payload goes, rather than
   * failing the way a numeric one does. Text is the one kind of field where the part that arrived
   * is still worth having: type 21's name extension is variable-length by design, and truncated
   * messages are common enough that half a vessel name beats no message at all.
   *
   * An unset field therefore comes back as an empty string, where the Java implementation returned
   * twenty `@` signs: its strip loop looked for the last character that was not padding and left
   * the string untouched when there wasn't one. It also applied its trim to three fields of one
   * message type and not to the same fields elsewhere.
   */
  public fun stringAt(from: Int, to: Int): String {
    require(from in 0..to) { "Bit range [$from, $to) runs backwards" }
    val end = minOf(to, payload.length * BITS_PER_CHAR)
    val text = StringBuilder()
    var index = from
    while (index + BITS_PER_CHAR <= end) {
      text.append(contentChar(uintAt(index, index + BITS_PER_CHAR)))
      index += BITS_PER_CHAR
    }
    return text.toString().substringBefore(PAD_CHAR).trim()
  }

  private fun requireRange(from: Int, to: Int) {
    require(from in 0..to) { "Bit range [$from, $to) runs backwards" }
    require(to <= payload.length * BITS_PER_CHAR) {
      "Bit range [$from, $to) runs past the ${payload.length * BITS_PER_CHAR}-bit payload"
    }
  }

  override fun toString(): String = payload

  override fun equals(other: Any?): Boolean =
    other is Sixbit && payload == other.payload && fillBits == other.fillBits

  override fun hashCode(): Int = payload.hashCode() * 31 + fillBits

  public companion object {

    /**
     * Whether [char] is one of the 64 characters a payload may use.
     *
     * The encoding takes ASCII `0x30`-`0x77` and skips `0x58`-`0x5F`, which is why it is two ranges
     * rather than one.
     */
    public fun isValidChar(char: Char): Boolean {
      val code = char.code
      return code in FIRST_CHAR..LAST_CHAR && (code <= LAST_LOW_CHAR || code >= FIRST_HIGH_CHAR)
    }

    /** The six-bit value a payload character stands for. */
    private fun valueOf(char: Char): Int =
      if (char.code < FIRST_HIGH_CHAR) char.code - LOW_OFFSET else char.code - HIGH_OFFSET

    /**
     * The character a six-bit value stands for *inside* a message.
     *
     * A different mapping from the one payload characters use: this is the six-bit ASCII of table
     * 44 in ITU-R M.1371, used for ship names, call signs and destinations within the decoded bit
     * stream, not for the transport encoding around it.
     */
    private fun contentChar(value: Int): Char =
      if (value < CONTENT_SHIFT_LIMIT) (value + CONTENT_SHIFT).toChar() else value.toChar()
  }
}
