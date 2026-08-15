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

/** Navigational status, as reported in NMEA 4.1 and later RMC sentences. */
public enum class NavStatus(override val code: Char) : CharCoded {
  /** Autonomous. */
  AUTONOMOUS('A'),
  /** Differential. */
  DIFFERENTIAL('D'),
  /** Estimated, dead reckoning. */
  ESTIMATED('E'),
  /** Manual input. */
  MANUAL('M'),
  /** Not valid. */
  NOT_VALID('N'),
  /** Simulator. */
  SIMULATOR('S'),
  /** Valid. */
  VALID('V'),
}
