package io.github.solcott.marineapi.nmea

/**
 * NMEA 0183 checksum: the 8-bit XOR of every character between the start delimiter and the `*`,
 * rendered as two upper-case hex digits.
 *
 * The start delimiter (`$` or `!`) and the `*` itself are excluded; field delimiters and every
 * other character in between are included.
 */
public object Checksum {

  /**
   * Calculates the checksum of [nmea], which may be given with or without an existing checksum.
   *
   * A leading start delimiter is skipped if present, and anything from the `*` onwards is ignored,
   * so this can be called on both `$GPGGA,...` and `$GPGGA,...*1A`.
   */
  public fun calculate(nmea: String): String {
    val start = if (nmea.isNotEmpty() && Nmea.isBeginChar(nmea[0])) 1 else 0
    return xor(nmea.substring(start, delimiterIndex(nmea)))
  }

  /**
   * XORs the characters of [str] and formats the result as two upper-case hex digits.
   *
   * Only the low 8 bits are kept. NMEA is 7-bit ASCII with the high bit zeroed, so this masking is
   * invisible for well-formed input, but it keeps the result two characters wide for any input --
   * the Java implementation this replaces widened to eight characters once a code point above 127
   * appeared.
   */
  public fun xor(str: String): String {
    var sum = 0
    for (char in str) {
      sum = sum xor (char.code and 0xFF)
    }
    return sum.toString(16).uppercase().padStart(2, '0')
  }

  /** Appends `*` and the calculated checksum to [nmea], replacing any checksum already present. */
  public fun append(nmea: String): String {
    val body = nmea.substring(0, delimiterIndex(nmea))
    return "$body${Nmea.CHECKSUM_DELIMITER}${calculate(body)}"
  }

  /**
   * The checksum carried by [nmea], or `null` if it has none.
   *
   * The value is returned verbatim, so a malformed or lower-case checksum comes back as written.
   */
  public fun read(nmea: String): String? {
    val index = nmea.indexOf(Nmea.CHECKSUM_DELIMITER)
    if (index <= 0) return null
    return nmea.substring(index + 1).trimEnd('\r', '\n')
  }

  /**
   * Index of the `*` in [nmea], or the length of the string when there is no checksum.
   *
   * An index of 0 counts as absent: a sentence cannot begin with the checksum delimiter.
   */
  public fun delimiterIndex(nmea: String): Int {
    val index = nmea.indexOf(Nmea.CHECKSUM_DELIMITER)
    return if (index > 0) index else nmea.length
  }
}
