package io.github.solcott.marineapi.nmea

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NmeaFormatTest {

  /**
   * Expected values produced by running `java.text.DecimalFormat` with the pattern the Java
   * implementation built in `SentenceParser.setDoubleValue(index, value, leading, decimals)`. These
   * are recorded output, not predictions, so they pin behavioural parity rather than an assumption
   * about how `DecimalFormat` rounds.
   */
  private val decimalFormatParity =
    listOf(
      // value, integerDigits, fractionDigits, expected
      Triple(2.675, 1 to 2, "2.67"), // exact value is below the halfway point
      Triple(0.5, 1 to 0, "0"), // exact tie, rounds to even
      Triple(1.5, 1 to 0, "2"),
      Triple(2.5, 1 to 0, "2"), // ties to even, not away from zero
      Triple(-2.5, 1 to 0, "-2"),
      Triple(0.125, 1 to 2, "0.12"), // representable exactly, tie to even
      Triple(0.135, 1 to 2, "0.14"), // exact value is above the halfway point
      Triple(45.5558, 2 to 7, "45.5558000"),
      Triple(7.5, 2 to 3, "07.500"),
      Triple(123.456, 2 to 1, "123.5"), // more integer digits than requested are kept
      Triple(0.0, 2 to 3, "00.000"),
      Triple(-1.5, 2 to 1, "-01.5"), // the sign does not count toward the width
      Triple(1.0, 2 to 0, "01"),
      Triple(60.06570833, 2 to 7, "60.0657083"),
      Triple(19.67142167, 3 to 7, "019.6714217"),
      Triple(359.95, 3 to 1, "359.9"), // scaling by 10 and rounding would give 360.0
      Triple(0.04, 2 to 1, "00.0"),
      Triple(-0.04, 2 to 1, "-00.0"), // sign survives even when the digits round to zero
      Triple(9.9999, 1 to 3, "10.000"), // carry grows the integer part
      Triple(99.999, 2 to 2, "100.00"),
      Triple(1e-7, 1 to 7, "0.0000001"),
      Triple(1.0e7, 1 to 1, "10000000.0"),
      Triple(0.001, 1 to 2, "0.00"),
    )

  @Test
  fun matchesDecimalFormat() {
    for ((value, digits, expected) in decimalFormatParity) {
      val (integerDigits, fractionDigits) = digits
      assertEquals(
        expected,
        NmeaFormat.decimal(value, integerDigits, fractionDigits),
        "decimal($value, $integerDigits, $fractionDigits)",
      )
    }
  }

  @Test
  fun formatsLatitudeAndLongitudeFields() {
    // ddmm.mmmmmmm and dddmm.mmmmmmm, the shapes NMEA position fields take.
    assertEquals("6011.5520000", NmeaFormat.decimal(6011.552, 4, 7))
    assertEquals("02501.9410000", NmeaFormat.decimal(2501.941, 5, 7))
  }

  @Test
  fun integerPadsWithLeadingZeros() {
    assertEquals("00", NmeaFormat.integer(0, 2))
    assertEquals("07", NmeaFormat.integer(7, 2))
    assertEquals("123", NmeaFormat.integer(123, 2)) // never truncates
    assertEquals("-07", NmeaFormat.integer(-7, 3))
    assertEquals("42", NmeaFormat.integer(42, 0))
  }

  @Test
  fun rejectsValuesThatCannotBeWritten() {
    assertFailsWith<IllegalArgumentException> { NmeaFormat.decimal(Double.NaN, 2, 1) }
    assertFailsWith<IllegalArgumentException> { NmeaFormat.decimal(Double.POSITIVE_INFINITY, 2, 1) }
    assertFailsWith<IllegalArgumentException> { NmeaFormat.decimal(1.0, -1, 1) }
  }

  @Test
  fun roundTripsThroughParsing() {
    // Whatever is written must read back as the same value at that precision.
    for (value in listOf(0.0, 1.0, 45.5558, 6011.552, 359.95, 0.001, 123.456)) {
      val text = NmeaFormat.decimal(value, 1, 7)
      assertEquals(value, text.toDouble(), 1e-7, "round trip of $value via \"$text\"")
    }
  }
}
