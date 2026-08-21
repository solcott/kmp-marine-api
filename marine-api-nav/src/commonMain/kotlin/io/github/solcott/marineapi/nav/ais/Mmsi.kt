package io.github.solcott.marineapi.nav.ais

import kotlin.jvm.JvmInline

/**
 * A Maritime Mobile Service Identity: the nine-digit number every AIS station transmits.
 *
 * A wrapper around the [Int] the messages carry, because the number is nine *digits* and an integer
 * is not. A coast station's identity is `00MIDxxxx` and an ordinary vessel's is `MIDxxxxxx`, so
 * `002442000` and `244200000` are different stations that happen to differ by two leading zeros --
 * and printing the first as `2442000` loses the two digits that say what kind of station it is.
 * [toString] pads; the raw [number] is there for anything that needs to index by it.
 *
 * The digits are structured rather than serial. [stationClass] reads the prefix, which says what
 * kind of station this is, and [mid] reads the Maritime Identification Digits, which say which
 * administration issued the identity -- so a target can be labelled with its flag state before it
 * has ever sent its name. Both are defined by ITU-R M.585.
 *
 * **Nothing is validated.** The MMSI field is 30 bits wide, which reaches 1,073,741,823, so a
 * corrupted message can carry a ten-digit number that is not an MMSI at all. Rejecting it here
 * would turn a bad bit into an exception thrown out of a flow that is decoding thousands of
 * messages a minute; instead [stationClass] answers [StationClass.UNKNOWN] and [mid] answers
 * `null`.
 */
@JvmInline
public value class Mmsi(public val number: Int) {

  /** The nine digits, zero-padded. A number too long to be an MMSI is left as it is. */
  override fun toString(): String = number.toString().padStart(MMSI_DIGITS, '0')

  /**
   * What kind of station this identity belongs to, from its leading digits.
   *
   * Worth reading before treating a target as a ship: `99MIDxxxx` is a buoy, `970MIDxxx` is a
   * search-and-rescue transmitter on a liferaft, and `972xxxxxx` is a man-overboard beacon on a
   * lifejacket. All three appear in a position report that otherwise looks like a small vessel
   * moving slowly, and only this tells them apart.
   */
  public val stationClass: StationClass
    get() {
      val digits = toString()
      if (digits.length != MMSI_DIGITS) return StationClass.UNKNOWN
      return when {
        digits.startsWith("111") -> StationClass.SAR_AIRCRAFT
        digits.startsWith("970") -> StationClass.AIS_SART
        digits.startsWith("972") -> StationClass.MOB_DEVICE
        digits.startsWith("974") -> StationClass.EPIRB
        digits.startsWith("98") -> StationClass.AUXILIARY_CRAFT
        digits.startsWith("99") -> StationClass.NAVIGATION_AID
        digits.startsWith("00") -> StationClass.COAST_STATION
        digits.startsWith("0") -> StationClass.GROUP
        digits.startsWith("8") -> StationClass.HANDHELD
        digits[0] in '2'..'7' -> StationClass.SHIP
        // Anything else is unassigned: a leading 1 that is not 111, or a leading 9 that is none of
        // the four ranges above.
        else -> StationClass.UNKNOWN
      }
    }

  /**
   * The three Maritime Identification Digits, or `null` if this identity carries none.
   *
   * **The digits are in a different place for each kind of station** -- first three for a ship,
   * third to fifth for a coast station or a buoy, fourth to sixth for an aircraft -- which is the
   * reason to go through this rather than take the first three and hope. A man-overboard beacon and
   * an EPIRB carry no MID at all: their identities are issued from a global pool rather than by an
   * administration.
   */
  public val mid: Int?
    get() {
      val digits = toString()
      if (digits.length != MMSI_DIGITS) return null
      val start =
        when (stationClass) {
          StationClass.SHIP -> 0
          StationClass.GROUP,
          StationClass.HANDHELD -> 1
          StationClass.COAST_STATION,
          StationClass.NAVIGATION_AID,
          StationClass.AUXILIARY_CRAFT -> 2
          StationClass.SAR_AIRCRAFT,
          StationClass.AIS_SART -> 3
          StationClass.MOB_DEVICE,
          StationClass.EPIRB,
          StationClass.UNKNOWN -> return null
        }
      return digits.substring(start, start + MID_DIGITS).toInt().takeIf { it in MID_RANGE }
    }

  /**
   * The administration that issued this identity, or `null` when the [mid] is absent or unassigned.
   *
   * This is the target's **flag state, not its position**: a Panamanian-flagged ship in the North
   * Sea reports a MID of 351 wherever she is. Territories that hold their own MID are named
   * separately from the state that administers them, since that is how the ITU assigns them.
   */
  public val flagState: String?
    get() = mid?.let { MID_COUNTRIES[it] }

  private companion object {
    const val MMSI_DIGITS = 9
    const val MID_DIGITS = 3

    /** MIDs run from 201 to 775; a number outside that is not an assignment. */
    val MID_RANGE = 200..799
  }
}

/**
 * What kind of station an [Mmsi] identifies, from the shape of its digits.
 *
 * Only [SHIP], [AUXILIARY_CRAFT] and [HANDHELD] are things with a hull. The rest report positions
 * that look like vessel positions and are not, which is why a plot that draws every AIS target as a
 * ship is wrong in a way that matters: a virtual buoy marking a new wreck must not be steered for
 * as though it were a ship that will get out of the way.
 */
public enum class StationClass {
  /** An ordinary vessel: `MIDxxxxxx`. */
  SHIP,
  /** A shore station: `00MIDxxxx`. */
  COAST_STATION,
  /** A group of ships called together, e.g. a fleet or a rescue coordination area: `0MIDxxxxx`. */
  GROUP,
  /** A search and rescue aircraft: `111MIDxxx`. */
  SAR_AIRCRAFT,
  /** A handheld VHF set with DSC and GNSS, carried rather than fitted: `8MIDxxxxx`. */
  HANDHELD,
  /** A tender, lifeboat or similar working from a parent ship: `98MIDxxxx`. */
  AUXILIARY_CRAFT,
  /** A buoy, beacon, lighthouse or offshore structure, real or virtual: `99MIDxxxx`. */
  NAVIGATION_AID,
  /** A search and rescue transmitter, carried in a liferaft: `970MIDxxx`. */
  AIS_SART,
  /** A man-overboard beacon, carried on a lifejacket: `972xxxxxx`. */
  MOB_DEVICE,
  /** An emergency position indicating radio beacon with an AIS transmitter: `974xxxxxx`. */
  EPIRB,
  /** Not one of the assigned forms. A corrupted message, or a range the ITU has not allocated. */
  UNKNOWN,
}
