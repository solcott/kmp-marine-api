package io.github.solcott.marineapi.nmea

/** How a radar target was acquired. */
public enum class AcquisitionType(override val code: Char) : CharCoded {
  AUTO('A'),
  MANUAL('M'),
  REPORTED('R'),
}

/** Tracking state of a radar target. */
public enum class TargetStatus(override val code: Char) : CharCoded {
  QUERY('Q'),
  LOST('L'),
  TRACKING('T'),
}

/** Orientation of a radar display. */
public enum class DisplayRotation(override val code: Char) : CharCoded {
  COURSE_UP('C'),
  HEAD_UP('H'),
  NORTH_UP('N'),
}

/** Source a speed or course measurement is referenced to. */
public enum class ReferenceSystem(override val code: Char) : CharCoded {
  BOTTOM_TRACKING_LOG('B'),
  MANUALLY_ENTERED('M'),
  WATER_REFERENCED('W'),
  RADAR_TRACKING('R'),
  POSITIONING_SYSTEM_GROUND_REFERENCE('P'),
}

/** Steering mode of a heading or track control system. */
public enum class SteeringMode(override val code: Char) : CharCoded {
  MANUAL('M'),
  STANDALONE('S'),
  HEADING_CONTROL('H'),
  TRACK_CONTROL('T'),
  RUDDER_CONTROL('R'),
}

/** How a turn is being commanded. */
public enum class TurnMode(override val code: Char) : CharCoded {
  RADIUS_CONTROLLED('R'),
  TURN_RATE_CONTROLLED('T'),
  NOT_CONTROLLED('N'),
}

/**
 * Route type in the RTE sentence.
 *
 * Unusually for NMEA, these codes are lower case.
 */
public enum class RouteType(override val code: Char) : CharCoded {
  /** Complete route, all waypoints. */
  ACTIVE('c'),
  /** Working route, the remaining waypoints. */
  WORKING('w'),
}
