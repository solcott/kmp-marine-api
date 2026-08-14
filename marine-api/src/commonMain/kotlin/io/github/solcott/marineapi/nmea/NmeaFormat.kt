package io.github.solcott.marineapi.nmea

/**
 * Fixed-width decimal formatting for NMEA output fields, replacing `java.text.DecimalFormat`.
 *
 * NMEA fields are written to a fixed shape -- latitude as `ddmm.mmmmmmm`, seconds as `ss.sss` --
 * which the Java implementation produced with `DecimalFormat("00.0000000")` and friends. There is
 * no multiplatform equivalent, so the behaviour is reimplemented here.
 *
 * Two details of `DecimalFormat` are reproduced deliberately:
 *
 * * **Half-even rounding.** That is `DecimalFormat`'s default, so it is what the existing test
 *   suite was written against. It is not a rule of NMEA.
 * * **Rounding the exact value of the double, not an approximation of it.** `DecimalFormat` behaves
 *   as if it expanded the double to its full decimal expansion before rounding, so `2.675` formats
 *   as `2.67`: the nearest double to 2.675 is 2.67499999999999982..., which is below the halfway
 *   point. Rounding a shortest-form `"2.675"` would give `2.68`, and scaling the double by 100 and
 *   rounding that would give `360.0` for `359.95` where `DecimalFormat` gives `359.9`.
 *
 * So the digits are expanded exactly, via integer arithmetic on the mantissa and exponent, and the
 * rounding decision is made on those digits. That also removes any platform dependence: every
 * target produces byte-identical output, which floating-point scaling would not guarantee.
 */
public object NmeaFormat {

  /**
   * Formats [value] with at least [integerDigits] digits before the point and exactly
   * [fractionDigits] after it.
   *
   * Values needing more integer digits than requested are not truncated. A negative value keeps its
   * sign, which is not counted in [integerDigits] -- matching `DecimalFormat`, where -1.5 under
   * `"00.0"` is `-01.5`.
   *
   * @throws IllegalArgumentException if [value] is NaN or infinite, which cannot be written to an
   *   NMEA field, or if either digit count is negative.
   */
  public fun decimal(value: Double, integerDigits: Int, fractionDigits: Int): String {
    require(integerDigits >= 0) { "integerDigits must not be negative: $integerDigits" }
    require(fractionDigits >= 0) { "fractionDigits must not be negative: $fractionDigits" }
    require(!value.isNaN()) { "Cannot format NaN as an NMEA field" }
    require(!value.isInfinite()) { "Cannot format $value as an NMEA field" }

    val negative = value < 0.0 || (value == 0.0 && 1.0 / value < 0.0)
    val (integer, fraction) = roundDigits(exactDigits(kotlin.math.abs(value)), fractionDigits)

    val sign = if (negative) "-" else ""
    val whole = integer.padStart(integerDigits, '0')
    return if (fractionDigits == 0) "$sign$whole" else "$sign$whole.$fraction"
  }

  /**
   * Formats [value] padded with leading zeros to at least [digits] characters.
   *
   * Equivalent to the `String.format("%0${digits}d", value)` of the implementation this replaces; a
   * negative value's sign counts toward the width, as it does there.
   */
  public fun integer(value: Int, digits: Int): String {
    require(digits >= 0) { "digits must not be negative: $digits" }
    if (value < 0) {
      return "-" + value.toString().substring(1).padStart(maxOf(digits - 1, 0), '0')
    }
    return value.toString().padStart(digits, '0')
  }

  /**
   * Expands a non-negative finite double into the exact integer and fraction digits of its value.
   *
   * A double is an integer mantissa times a power of two, so its decimal expansion is finite and
   * can be produced exactly. For a negative binary exponent `k` the identity is `m / 2^k == (m *
   * 5^k) / 10^k`, which turns the expansion into an integer multiplication followed by placing the
   * decimal point. Digits are held as strings because the products exceed [Long] -- `5^k` alone can
   * reach several hundred digits.
   *
   * `Double.toString` is deliberately not used: it yields the shortest representation that
   * round-trips, not the exact value, and its formatting differs across Kotlin targets.
   */
  private fun exactDigits(value: Double): Pair<String, String> {
    if (value == 0.0) return Pair("0", "")

    val bits = value.toRawBits()
    val biasedExponent = ((bits ushr 52) and 0x7FF).toInt()
    val rawMantissa = bits and 0x000F_FFFF_FFFF_FFFFL

    // Subnormals carry no implicit leading bit and share the smallest exponent.
    val mantissa = if (biasedExponent == 0) rawMantissa else rawMantissa or 0x0010_0000_0000_0000L
    val exponent = if (biasedExponent == 0) -1074 else biasedExponent - 1075

    var digits = mantissa.toString()

    if (exponent >= 0) {
      repeat(exponent) { digits = multiply(digits, 2) }
      return Pair(digits, "")
    }

    val scale = -exponent
    repeat(scale) { digits = multiply(digits, 5) }

    val padded = digits.padStart(scale + 1, '0')
    val point = padded.length - scale
    return Pair(padded.substring(0, point).trimStart('0').ifEmpty { "0" }, padded.substring(point))
  }

  /** Multiplies a string of decimal digits by a single-digit [factor]. */
  private fun multiply(digits: String, factor: Int): String {
    val result = StringBuilder(digits.length + 1)
    var carry = 0
    for (i in digits.indices.reversed()) {
      val product = (digits[i] - '0') * factor + carry
      result.append(('0' + product % 10))
      carry = product / 10
    }
    while (carry > 0) {
      result.append(('0' + carry % 10))
      carry /= 10
    }
    return result.reverse().toString()
  }

  /**
   * Rounds [digits] to [fractionDigits] decimal places, half-even, carrying into the integer part.
   */
  private fun roundDigits(digits: Pair<String, String>, fractionDigits: Int): Pair<String, String> {
    val (integer, fraction) = digits
    if (fraction.length <= fractionDigits) {
      return Pair(integer, fraction.padEnd(fractionDigits, '0'))
    }

    val kept = fraction.substring(0, fractionDigits)
    val dropped = fraction.substring(fractionDigits)
    val first = dropped[0]
    val restNonZero = dropped.drop(1).any { it != '0' }

    val lastKept = if (kept.isNotEmpty()) kept.last() else integer.last()
    val roundUp =
      when {
        first > '5' -> true
        first < '5' -> false
        restNonZero -> true
        // Exactly one half: round to the even neighbour.
        else -> (lastKept - '0') % 2 == 1
      }

    if (!roundUp) return Pair(integer, kept)

    val incremented = increment(integer + kept)
    val splitAt = incremented.length - fractionDigits
    return Pair(incremented.substring(0, splitAt), incremented.substring(splitAt))
  }

  /** Adds one to a string of decimal digits, growing it if every digit carries. */
  private fun increment(digits: String): String {
    val chars = digits.toCharArray()
    for (i in chars.indices.reversed()) {
      if (chars[i] == '9') {
        chars[i] = '0'
      } else {
        chars[i] = chars[i] + 1
        return chars.concatToString()
      }
    }
    return "1" + chars.concatToString()
  }
}
