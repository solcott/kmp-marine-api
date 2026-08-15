package io.github.solcott.marineapi.ais

import io.github.solcott.marineapi.nmea.IntCoded

/**
 * What a vessel is doing, as reported in a position report.
 *
 * Codes 9 to 13 have been reassigned over successive revisions of the standard: the implementation
 * this replaces labelled all five "reserved", and the names here follow
 * [gpsd's AIVDM reference](https://gpsd.gitlab.io/gpsd/AIVDM.html) instead. A receiver built to the
 * older revision may still send 11 or 12 meaning nothing in particular.
 */
public enum class NavigationalStatus(override val code: Int) : IntCoded {
  /** Making way under power. */
  UNDER_WAY_USING_ENGINE(0),
  AT_ANCHOR(1),
  /** Unable to manoeuvre, and so unable to keep out of the way. */
  NOT_UNDER_COMMAND(2),
  RESTRICTED_MANOEUVRABILITY(3),
  /** So deep that she can only follow the channel. */
  CONSTRAINED_BY_DRAUGHT(4),
  MOORED(5),
  AGROUND(6),
  ENGAGED_IN_FISHING(7),
  /** Making way under sail alone. */
  UNDER_WAY_SAILING(8),
  RESERVED_HIGH_SPEED_CRAFT(9),
  RESERVED_WING_IN_GROUND(10),
  POWER_DRIVEN_TOWING_ASTERN(11),
  POWER_DRIVEN_PUSHING_AHEAD(12),
  RESERVED(13),
  /** Search and rescue transmitter, man overboard beacon or EPIRB. */
  AIS_SART(14),
  /** The receiver reports no status. */
  UNDEFINED(15),
}

/**
 * Whether a vessel is engaged in a special manoeuvre, such as passing through a traffic separation
 * scheme.
 */
public enum class ManeuverIndicator(override val code: Int) : IntCoded {
  /** The receiver reports nothing. Distinct from [NOT_ENGAGED], which is a positive statement. */
  NOT_AVAILABLE(0),
  NOT_ENGAGED(1),
  ENGAGED(2),
}

/** The kind of positioning device a station derives its fix from. */
public enum class EpfdType(override val code: Int) : IntCoded {
  UNDEFINED(0),
  GPS(1),
  GLONASS(2),
  COMBINED_GPS_GLONASS(3),
  LORAN_C(4),
  CHAYKA(5),
  INTEGRATED_NAVIGATION_SYSTEM(6),
  /** A fixed position that was measured rather than received; used by base stations. */
  SURVEYED(7),
  GALILEO(8),
  INTERNAL_GNSS(15),
}

/**
 * What kind of navigation aid a type 21 message describes.
 *
 * A *virtual* aid is one that exists only as an AIS broadcast, with no physical mark in the water
 * -- see [io.github.solcott.marineapi.ais.AisAidToNavigationReport.isVirtual].
 */
public enum class AidType(override val code: Int) : IntCoded {
  NOT_SPECIFIED(0),
  REFERENCE_POINT(1),
  RACON(2),
  /** A fixed offshore structure: a platform, a wind turbine. */
  FIXED_STRUCTURE(3),
  EMERGENCY_WRECK_MARKING_BUOY(4),
  LIGHT_WITHOUT_SECTORS(5),
  LIGHT_WITH_SECTORS(6),
  LEADING_LIGHT_FRONT(7),
  LEADING_LIGHT_REAR(8),
  BEACON_CARDINAL_NORTH(9),
  BEACON_CARDINAL_EAST(10),
  BEACON_CARDINAL_SOUTH(11),
  BEACON_CARDINAL_WEST(12),
  BEACON_PORT_HAND(13),
  BEACON_STARBOARD_HAND(14),
  BEACON_PREFERRED_CHANNEL_PORT_HAND(15),
  BEACON_PREFERRED_CHANNEL_STARBOARD_HAND(16),
  BEACON_ISOLATED_DANGER(17),
  BEACON_SAFE_WATER(18),
  BEACON_SPECIAL_MARK(19),
  CARDINAL_MARK_NORTH(20),
  CARDINAL_MARK_EAST(21),
  CARDINAL_MARK_SOUTH(22),
  CARDINAL_MARK_WEST(23),
  PORT_HAND_MARK(24),
  STARBOARD_HAND_MARK(25),
  PREFERRED_CHANNEL_PORT_HAND(26),
  PREFERRED_CHANNEL_STARBOARD_HAND(27),
  ISOLATED_DANGER(28),
  SAFE_WATER(29),
  SPECIAL_MARK(30),
  LIGHT_VESSEL(31),
}

/**
 * What kind of ship an AIS station is fitted to, as the two-digit code the message carries.
 *
 * Not an enum, because the code is compositional rather than a flat list: for most values the tens
 * digit is the category and the units digit qualifies it -- 70 is a cargo ship, 71 a cargo ship
 * carrying category X hazardous goods -- while 50 to 59 are a fixed list of special vessels that
 * ignores that scheme entirely. Flattening a hundred combinations into enum constants would obscure
 * the structure that makes them readable, so the messages keep the code as sent and this applies
 * the scheme to it.
 */
public fun describeShipType(code: Int): String {
  if (code !in 0..255) return "invalid ship type $code"
  if (code >= 200) return "reserved ship type $code"
  val category = code / 10
  val qualifier = code % 10
  return when (category) {
    5 -> SPECIAL_VESSELS[qualifier]
    3 -> "${FIRST_DIGIT[3]}, ${VESSEL_ACTIVITIES[qualifier]}"
    else -> "${FIRST_DIGIT.getOrElse(category) { "reserved" }}, ${SECOND_DIGIT[qualifier]}"
  }
}

private val FIRST_DIGIT =
  listOf(
    "Not specified",
    "Reserved for future use",
    "Wing in ground",
    "Vessel",
    "High speed craft",
    "Special vessel",
    "Passenger ship",
    "Cargo ship",
    "Tanker",
    "Other type of ship",
  )

private val SECOND_DIGIT =
  listOf(
    "general",
    "carrying category X hazardous goods",
    "carrying category Y hazardous goods",
    "carrying category Z hazardous goods",
    "carrying category OS hazardous goods",
    "reserved for future use",
    "reserved for future use",
    "reserved for future use",
    "reserved for future use",
    "no additional information",
  )

private val VESSEL_ACTIVITIES =
  listOf(
    "fishing",
    "towing",
    "towing a long or wide tow",
    "engaged in dredging or underwater operations",
    "engaged in diving operations",
    "engaged in military operations",
    "sailing",
    "pleasure craft",
    "reserved for future use",
    "reserved for future use",
  )

private val SPECIAL_VESSELS =
  listOf(
    "Pilot vessel",
    "Search and rescue vessel",
    "Tug",
    "Port tender",
    "Vessel with anti-pollution capability",
    "Law enforcement vessel",
    "Spare, for local vessels",
    "Spare, for local vessels",
    "Medical transport",
    "Ship not party to an armed conflict",
  )
