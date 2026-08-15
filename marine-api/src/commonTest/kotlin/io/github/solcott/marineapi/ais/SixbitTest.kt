package io.github.solcott.marineapi.ais

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The payload of `!AIVDM,1,1,,A,13u?etPv2;0n:dDPwUM1U1Cb069D,0*23`, a type 1 position report. */
private const val POSITION_REPORT = "13u?etPv2;0n:dDPwUM1U1Cb069D"

class SixbitTest {

  private val sixbit = Sixbit(POSITION_REPORT)

  @Test
  fun countsTheBitsItCarries() {
    assertEquals(28 * 6, sixbit.size)
    assertEquals(168, sixbit.size, "a type 1 position report is 168 bits")
  }

  @Test
  fun subtractsTheFillBitsFromTheLength() {
    // The last character of a padded payload is only partly message, and the padding is not part
    // of any field.
    assertEquals(166, Sixbit(POSITION_REPORT, fillBits = 2).size)
  }

  @Test
  fun readsTheFieldsOfARealPositionReport() {
    // Bit ranges are half-open and zero-based, so these are the ranges the AIS specification gives
    // for a type 1 message, used unaltered.
    assertEquals(1, sixbit.uintAt(0, 6), "message type")
    assertEquals(0, sixbit.uintAt(6, 8), "repeat indicator")
    assertEquals(265_547_250, sixbit.uintAt(8, 38), "MMSI")
    assertEquals(0, sixbit.uintAt(38, 42), "navigational status")
    assertEquals(139, sixbit.uintAt(50, 60), "speed over ground, tenths of a knot")
    assertEquals(404, sixbit.uintAt(116, 128), "course over ground, tenths of a degree")
    assertEquals(41, sixbit.uintAt(128, 137), "true heading")
    assertEquals(53, sixbit.uintAt(137, 143), "time stamp")
  }

  @Test
  fun readsASignedFieldOfAnyWidth() {
    // Rate of turn is 8 bits two's-complement; longitude 28 and latitude 27. One function covers
    // all three, where the Java implementation had a separate method hard-coding each width.
    assertEquals(-8, sixbit.intAt(42, 50), "rate of turn")
    assertEquals(7_099_786, sixbit.intAt(61, 89), "longitude in 1/10000 minutes")
    assertEquals(34_596_212, sixbit.intAt(89, 116), "latitude in 1/10000 minutes")
  }

  @Test
  fun signExtendsOnlyWhenTheTopBitIsSet() {
    // 0b1000 in four bits is -8, in five bits it is 8. The width is the whole of the difference.
    val bits = Sixbit("8")
    assertEquals(8, bits.uintAt(0, 6))
    assertEquals(8, bits.intAt(0, 6), "0b001000 has room to spare")
    assertEquals(-8, bits.intAt(2, 6), "the same 0b1000 read as four bits, where it is negative")
  }

  @Test
  fun readsASingleBit() {
    // Position accuracy is one bit at index 60. The Java source carried a FIXME doubting this
    // index; it was correct, and both of these payloads agree with it.
    assertFalse(sixbit.booleanAt(60), "this receiver reports low accuracy")
    assertTrue(Sixbit("15S0t`001TlGn>TNurwroHgD05;H").booleanAt(60), "and this one reports high")
  }

  @Test
  fun readsTextSixBitsPerCharacter() {
    // The first fragment of a type 5 static report: a container ship reporting who she is.
    val staticReport = Sixbit("55?MbV02;H;s<HtKR20EHE:0@T4@Dn2222222216L961O5Gf0NSQEp6ClRp8")
    assertEquals(5, staticReport.uintAt(0, 6), "message type")
    assertEquals(351_759_000, staticReport.uintAt(8, 38), "MMSI")
    assertEquals("EVER DIADEM", staticReport.stringAt(112, 232), "vessel name, padded to 20")
    assertEquals("3FOF8", staticReport.stringAt(70, 112), "call sign, padded with spaces")
  }

  @Test
  fun stripsThePaddingFromAFieldThatWasNeverSet() {
    // An unset AIS string is all '@'. The Java implementation returned those characters verbatim
    // when every one of them was padding -- its strip loop only ever found the last non-'@'.
    // Payload '0' is the six-bit value 0, which is content '@'. The two alphabets are different:
    // a payload '@' is the value 16, and decodes to content 'P'.
    val allPadding = Sixbit("0000000000")
    assertEquals("", allPadding.stringAt(0, 60))
    assertEquals("PPPPPPPPPP", Sixbit("@@@@@@@@@@").stringAt(0, 60), "'@' in, 'P' out")
  }

  @Test
  fun rejectsAPayloadItCannotDecode() {
    // The encoding is ASCII 0x30-0x77 with 0x58-0x5F cut out of the middle.
    assertTrue(Sixbit.isValidChar('0') && Sixbit.isValidChar('w'))
    assertFalse(Sixbit.isValidChar('X'), "0x58 falls in the gap")
    assertFalse(Sixbit.isValidChar('_'), "0x5F is the last of the gap")
    assertTrue(Sixbit.isValidChar('`'), "0x60 is where it resumes")

    val invalid = assertFailsWith<IllegalArgumentException> { Sixbit("13u?X") }
    assertTrue("not six-bit encoded" in invalid.message!!, invalid.message!!)
  }

  @Test
  fun rejectsAnEmptyPayloadAndImpossibleFillBits() {
    assertFailsWith<IllegalArgumentException> { Sixbit("") }
    assertFailsWith<IllegalArgumentException> { Sixbit(POSITION_REPORT, fillBits = -1) }
    assertFailsWith<IllegalArgumentException> { Sixbit(POSITION_REPORT, fillBits = 6) }
  }

  @Test
  fun rejectsARangeOutsideThePayload() {
    assertFailsWith<IllegalArgumentException> { sixbit.uintAt(0, 169) }
    assertFailsWith<IllegalArgumentException> { sixbit.uintAt(10, 5) }
    assertFailsWith<IllegalArgumentException> { sixbit.booleanAt(168) }
  }

  @Test
  fun isAValueWithTheEncodedTextIntact() {
    assertEquals(Sixbit(POSITION_REPORT), Sixbit(POSITION_REPORT))
    assertEquals(Sixbit(POSITION_REPORT).hashCode(), Sixbit(POSITION_REPORT).hashCode())
    assertEquals(POSITION_REPORT, sixbit.payload, "the original text survives decoding")
  }
}
