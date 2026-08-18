package io.github.solcott.marineapi.nmea

/**
 * An enumeration whose NMEA field representation is a single character, such as `A` for
 * [DataStatus.ACTIVE].
 */
public interface CharCoded {
  /** The character written to, and read from, the NMEA field. */
  public val code: Char
}

/**
 * An enumeration whose NMEA field representation is a small integer, such as `1` for
 * [GpsFixQuality.NORMAL].
 */
public interface IntCoded {
  /** The integer written to, and read from, the NMEA field. */
  public val code: Int
}

/** The entry whose [CharCoded.code] is [code], or `null` if none matches. */
public fun <T : CharCoded> Iterable<T>.fromCode(code: Char): T? = firstOrNull { it.code == code }

/** The entry whose [IntCoded.code] is [code], or `null` if none matches. */
public fun <T : IntCoded> Iterable<T>.fromCode(code: Int): T? = firstOrNull { it.code == code }
