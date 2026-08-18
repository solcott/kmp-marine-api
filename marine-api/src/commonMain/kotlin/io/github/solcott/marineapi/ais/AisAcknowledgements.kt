package io.github.solcott.marineapi.ais

/**
 * One addressed message being acknowledged: the station that sent it and which of its messages.
 *
 * A sender numbers the addressed messages it transmits 0 to 3 in rotation, and the recipient echoes
 * that number back so the sender knows which of up to four outstanding messages arrived. The pair
 * is only meaningful together -- an MMSI without a sequence number does not identify a message.
 *
 * @property mmsi the station whose message is being acknowledged
 * @property sequenceNumber which of that station's four in-flight messages, 0 to 3
 */
public data class AisAcknowledgement(val mmsi: Int, val sequenceNumber: Int)

/**
 * Confirmation that addressed binary messages were received: AIS message type 7.
 *
 * Sent by a station in reply to a type 6 addressed binary message, naming up to four senders and
 * the sequence number each of them used. It carries no position and no vessel data at all -- it is
 * pure link-layer bookkeeping for the addressed side of AIS, which is why the great majority of
 * type 7 traffic comes from base stations acknowledging shore-bound reports.
 *
 * The four acknowledgement slots are **not padded out**. A station acknowledging one message sends
 * a 72-bit payload and stops; one acknowledging three sends 136 bits. So the count is carried by
 * the length of the message rather than by any field in it, and [acknowledgements] holds only the
 * slots that actually arrived. gpsd zero-fills the absent ones and prints `"mmsi3":0`; an MMSI of 0
 * is not a station, so there is nothing to gain by reproducing that here.
 *
 * The sequence numbers are decoded but **not** covered by the gpsd cross-check: gpsd's type 7
 * record emits `mmsi1` through `mmsi4` and no sequence numbers at all. The MMSI ranges either side
 * of each one are checked against gpsd across the corpus, which pins where the two spare bits
 * between them sit.
 *
 * Example: `!AIVDM,1,1,,B,73aBL800RW?;,0*77`
 *
 * @property acknowledgements the messages being acknowledged, in the order sent, at most four
 */
public data class AisBinaryAcknowledge(
  override val messageType: Int,
  override val repeatIndicator: Int,
  override val mmsi: Int,
  val acknowledgements: List<AisAcknowledgement>,
) : AisMessage {

  public companion object {
    /** Message type decoded by this class. */
    public const val TYPE: Int = 7

    /** First bit of the first acknowledgement slot; bits 38 and 39 are spare. */
    private const val FIRST_SLOT = 40

    /** An MMSI and its two-bit sequence number. */
    private const val SLOT_BITS = 32

    private const val MMSI_BITS = 30

    /** The standard allows no more than four, whatever the payload length suggests. */
    private const val MAX_SLOTS = 4

    /** Reads a binary acknowledgement. */
    public fun from(bits: Sixbit): AisBinaryAcknowledge =
      AisBinaryAcknowledge(
        messageType = bits.uintAt(0, 6),
        repeatIndicator = bits.uintAt(6, 8),
        mmsi = bits.uintAt(8, 38),
        acknowledgements = bits.acknowledgementsAt(FIRST_SLOT),
      )

    /**
     * As many acknowledgement slots as the payload carries, up to four.
     *
     * A slot that is only partly present is dropped rather than read from whatever bits arrived:
     * the sequence number is the last two bits of the slot, and a truncated one would silently
     * acknowledge the wrong message.
     */
    private fun Sixbit.acknowledgementsAt(from: Int): List<AisAcknowledgement> {
      val slots = mutableListOf<AisAcknowledgement>()
      var start = from
      while (slots.size < MAX_SLOTS && size >= start + SLOT_BITS) {
        slots +=
          AisAcknowledgement(
            mmsi = uintAt(start, start + MMSI_BITS),
            sequenceNumber = uintAt(start + MMSI_BITS, start + SLOT_BITS),
          )
        start += SLOT_BITS
      }
      return slots
    }
  }
}
