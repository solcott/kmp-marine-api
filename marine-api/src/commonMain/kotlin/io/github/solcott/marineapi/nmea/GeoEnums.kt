package io.github.solcott.marineapi.nmea

/** Hemisphere or relative direction indicator: `N`, `E`, `S` or `W`. */
public enum class CompassPoint(override val code: Char) : CharCoded {
  NORTH('N'),
  EAST('E'),
  SOUTH('S'),
  WEST('W'),
}

/** Geodetic datum a position is expressed in. */
public enum class Datum {
  /** World Geodetic System 1984, what modern receivers report. */
  WGS84,
  /** North American Datum 1983. */
  NAD83,
  /** North American Datum 1927. */
  NAD27,
}

/** Relative direction, as used for steering and rudder fields. */
public enum class Direction(override val code: Char) : CharCoded {
  LEFT('L'),
  RIGHT('R'),
}

/**
 * Which north a bearing or heading is measured from.
 *
 * Sentences that report a bearing follow it with a `T`/`M` field saying which. Unlike the fixed
 * markers in BOD or HDT, this one genuinely varies from field to field within a single sentence --
 * APB carries three of them -- so it is read rather than assumed.
 */
public enum class BearingReference(override val code: Char) : CharCoded {
  /** Referenced to true north. */
  TRUE('T'),
  /** Referenced to magnetic north. */
  MAGNETIC('M'),
}

/** Side of a vessel. */
public enum class Side(override val code: Char) : CharCoded {
  PORT('P'),
  STARBOARD('S'),
}

/**
 * Unit of measurement.
 *
 * The codes are case-sensitive: `F` is fathoms while `f` is feet, a distinction the format relies
 * on in depth sentences.
 */
public enum class Units(override val code: Char) : CharCoded {
  BARS('B'),
  CELSIUS('C'),
  FATHOMS('F'),
  FEET('f'),
  INCHES('I'),
  KILOMETERS('K'),
  METER('M'),
  NAUTICAL_MILES('N'),
  STATUTE_MILES('S'),
}
