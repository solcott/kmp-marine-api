package io.github.solcott.marineapi.nmea

/** Validity of the data in a sentence: `A` for valid, `V` for a warning. */
public enum class DataStatus(override val code: Char) : CharCoded {
  /** Data is valid. */
  ACTIVE('A'),
  /** Data is invalid; the sentence carries a navigation receiver warning. */
  VOID('V'),
}

/** Quality of a GPS fix, as reported in the GGA sentence. */
public enum class GpsFixQuality(override val code: Int) : IntCoded {
  /** No fix. */
  INVALID(0),
  /** Autonomous fix. */
  NORMAL(1),
  /** Differentially corrected fix. */
  DGPS(2),
  /** Precise Positioning Service fix. */
  PPS(3),
  /** Real Time Kinematic, fixed integers. */
  RTK(4),
  /** Real Time Kinematic, floating integers. */
  FRTK(5),
  /** Dead reckoning. */
  ESTIMATED(6),
  /** Manually entered position. */
  MANUAL(7),
  /** Simulated position. */
  SIMULATED(8),
}

/** Dimensionality of a fix, as reported in the GSA sentence. */
public enum class GpsFixStatus(override val code: Int) : IntCoded {
  /** No fix available. */
  GPS_NA(1),
  /** Two-dimensional fix: position but no altitude. */
  GPS_2D(2),
  /** Three-dimensional fix. */
  GPS_3D(3),
}

/**
 * FAA mode indicator, added in NMEA 2.3 to APB, BWC, BWR, GLL, RMA, RMB, RMC, VTG, WCV and XTE.
 *
 * Per gpsd's reference the standard says this field dominates the status field: status reads `A`
 * (valid) for [AUTOMATIC] and [DGPS], and `V` (invalid) for every other mode. Sentences from before
 * NMEA 2.3 omit the field entirely, which reads as `null` rather than as a mode.
 *
 * The `C` ("caution") and `U` ("unsafe") values that some Quectel receivers emit are not modelled:
 * they are a vendor extension rather than part of the format, and would parse as an unrecognised
 * code.
 */
public enum class FaaMode(override val code: Char) : CharCoded {
  /** Autonomous fix. */
  AUTOMATIC('A'),
  /** Manually entered position. */
  MANUAL('M'),
  /** Differentially corrected fix. */
  DGPS('D'),
  /** Dead reckoning. */
  ESTIMATED('E'),
  /** Precise, no degradation such as selective availability. */
  PRECISE('P'),
  /** Real Time Kinematic, fixed integers. */
  RTK_FIXED('R'),
  /** Real Time Kinematic, floating integers. */
  RTK_FLOAT('F'),
  /** Simulated data. */
  SIMULATED('S'),
  /** No valid data. */
  NONE('N'),
}

/**
 * Navigational status: whether a fix is fit to navigate on, as opposed to how it was obtained.
 *
 * Added in NMEA 4.1 and carried by RMC and GNS. Not to be confused with [FaaMode], which shares the
 * letter `S` and means something else by it -- an earlier revision of this enum copied FaaMode's
 * codes wholesale and so read `V` as "valid" when it means the opposite.
 *
 * Treat it with suspicion. Every one of the 342 sentences carrying this field in the gpsd corpus
 * reports [NOT_VALID], including sentences whose own status field says the fix is good. gpsd
 * records the same observation and declines to parse the field at all. A receiver appears to emit
 * `V` whether or not it means it, so acting on this in isolation would reject perfectly good fixes.
 */
public enum class NavStatus(override val code: Char) : CharCoded {
  /** Safe to navigate on. */
  SAFE('S'),
  /** Usable with caution. */
  CAUTION('C'),
  /** Unsafe to navigate on. */
  UNSAFE('U'),
  /** Not valid for navigation. */
  NOT_VALID('V'),
}
