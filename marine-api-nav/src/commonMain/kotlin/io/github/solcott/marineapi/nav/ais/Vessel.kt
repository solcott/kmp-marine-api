package io.github.solcott.marineapi.nav.ais

import io.github.solcott.marineapi.ais.AisExtendedPositionReportB
import io.github.solcott.marineapi.ais.AisLongRangePositionReport
import io.github.solcott.marineapi.ais.AisMessage
import io.github.solcott.marineapi.ais.AisPositionReport
import io.github.solcott.marineapi.ais.AisPositionReportB
import io.github.solcott.marineapi.ais.AisStaticAndVoyageData
import io.github.solcott.marineapi.ais.AisStaticDataReport
import io.github.solcott.marineapi.ais.AisStaticDataReportB
import io.github.solcott.marineapi.ais.AisVesselPositionMessage
import io.github.solcott.marineapi.ais.EpfdType
import io.github.solcott.marineapi.ais.EstimatedArrival
import io.github.solcott.marineapi.ais.NavigationalStatus
import io.github.solcott.marineapi.ais.ShipDimensions
import io.github.solcott.marineapi.ais.describeShipType
import io.github.solcott.marineapi.nmea.Position

/**
 * One vessel's whole picture, fused from every AIS message she has sent.
 *
 * AIS deliberately splits a target across message types on different schedules: where she is
 * arrives every 2 to 10 seconds, and who she is arrives every 6 minutes. Neither half is useful
 * alone -- a position with no name is an unlabelled dot, and a name with no position is not on the
 * chart at all -- so joining them by MMSI is the first thing every consumer of this library has had
 * to write for itself. See [aisTargets].
 *
 * **A report overwrites the fields it carries and leaves the rest alone**, which is not the same as
 * overwriting the non-null ones. A Class B position report carries no navigational status, so a
 * status learned earlier survives it; the same report does carry a heading, so a Class B unit with
 * no heading sensor correctly clears one. Reporting "not available" is a statement, and treating it
 * as silence would leave a vessel showing a course she had stopped reporting.
 *
 * **Blank text does not overwrite.** An unset AIS name field decodes to an empty string, and a
 * vessel that has sent her name once and then sends a type 24 with the field unfilled has not
 * become anonymous.
 *
 * No timestamp is a property here, deliberately: [TargetRegistry.ageOf] answers how old a target
 * is, and keeping the clock out of the data class means two equal reports compare equal, so
 * `distinctUntilChanged` and a `Set` behave the way a caller expects.
 *
 * @property mmsi the station's identity, which is also what fused these reports together
 * @property name vessel name, up to 20 characters, or `null` until she sends one
 * @property callSign radio call sign
 * @property imoNumber the IMO number, which unlike the MMSI stays with the hull for life
 * @property shipType the ship type code as sent; [shipTypeDescription] renders it
 * @property dimensions the vessel's extent, measured from her positioning antenna
 * @property destination free text entered by hand, so abbreviated and often stale
 * @property eta estimated arrival at [destination], with no year -- see [EstimatedArrival]
 * @property maximumDraught metres, what decides whether she can enter a port
 * @property epfd the kind of positioning device her fix comes from
 * @property position where she last reported being, or `null` if she reports no fix
 * @property isPositionAccurate her own claim that the fix is better than 10 metres. A claim.
 * @property isPositionCoarse `true` when [position] came from a type 27 long-range report, which
 *   carries tenths of a minute rather than ten-thousandths -- about 185 metres of resolution rather
 *   than 20 centimetres. Worth knowing before drawing a berth-scale plot with one.
 * @property speedOverGround knots
 * @property courseOverGround degrees true, which is where she is going
 * @property heading degrees true the bow points, which is not the same thing: the difference is how
 *   far wind and tide are setting her sideways
 * @property rateOfTurn degrees per minute, negative to port
 * @property navigationalStatus what she says she is doing, set by hand and so routinely stale
 * @property utcSecond second of the UTC minute her fix was taken
 * @property isClassB `true` once she has sent any Class B message. Class B is the smaller, cheaper
 *   transponder on leisure and small commercial craft: it transmits less often, at lower power, and
 *   gives way to Class A for airtime, so a Class B target is both less complete and more likely to
 *   be missed altogether.
 */
public data class Vessel(
  val mmsi: Mmsi,
  val name: String? = null,
  val callSign: String? = null,
  val imoNumber: Int? = null,
  val shipType: Int? = null,
  val dimensions: ShipDimensions? = null,
  val destination: String? = null,
  val eta: EstimatedArrival? = null,
  val maximumDraught: Double? = null,
  val epfd: EpfdType? = null,
  val position: Position? = null,
  val isPositionAccurate: Boolean = false,
  val isPositionCoarse: Boolean = false,
  val speedOverGround: Double? = null,
  val courseOverGround: Double? = null,
  val heading: Int? = null,
  val rateOfTurn: Double? = null,
  val navigationalStatus: NavigationalStatus? = null,
  val utcSecond: Int? = null,
  val isClassB: Boolean = false,
) {

  /** [shipType] rendered, or `null` until she has sent one. */
  public val shipTypeDescription: String?
    get() = shipType?.let { describeShipType(it) }

  /** Overall length in metres, or `null` until she has sent usable dimensions. */
  public val length: Int?
    get() = dimensions?.length

  /** Overall beam in metres, or `null` until she has sent usable dimensions. */
  public val beam: Int?
    get() = dimensions?.beam

  /**
   * True once this target has reported both a position and how she is moving.
   *
   * The precondition for [closestApproachTo]: a target with no velocity cannot be projected
   * forward, so no closest approach can be computed for her at all.
   */
  public val isUnderWay: Boolean
    get() = position != null && speedOverGround != null && courseOverGround != null

  /**
   * This vessel with [message] applied, or `this` unchanged if the message says nothing about her.
   *
   * Split into the two halves AIS itself is split into: where she is, on a schedule of seconds, and
   * who she is, on one of minutes. A type 19 report is both, and so appears in the first half
   * filling in fields from the second.
   *
   * Types 4, 9 and 21 are not vessels -- a base station, an aircraft and a buoy -- and are filtered
   * out before they reach here rather than being merged into one.
   */
  internal fun updatedBy(message: AisMessage): Vessel =
    when (message) {
      is AisVesselPositionMessage -> movedBy(message)
      else -> describedBy(message)
    }

  private fun movedBy(message: AisVesselPositionMessage): Vessel =
    when (message) {
      is AisPositionReport ->
        copy(
          position = message.position,
          isPositionAccurate = message.isAccurate,
          isPositionCoarse = false,
          speedOverGround = message.speedOverGround,
          courseOverGround = message.courseOverGround,
          heading = message.heading,
          rateOfTurn = message.rateOfTurn,
          navigationalStatus = message.navigationalStatus,
          utcSecond = message.utcSecond,
        )
      is AisPositionReportB ->
        copy(
          position = message.position,
          isPositionAccurate = message.isAccurate,
          isPositionCoarse = false,
          speedOverGround = message.speedOverGround,
          courseOverGround = message.courseOverGround,
          heading = message.heading,
          utcSecond = message.utcSecond,
          isClassB = true,
        )
      is AisExtendedPositionReportB ->
        copy(
          position = message.position,
          isPositionAccurate = message.isAccurate,
          isPositionCoarse = false,
          speedOverGround = message.speedOverGround,
          courseOverGround = message.courseOverGround,
          heading = message.heading,
          utcSecond = message.utcSecond,
          name = message.name.orKeep(name),
          shipType = message.shipType,
          dimensions = message.dimensions,
          epfd = message.epfd,
          isClassB = true,
        )
      // Type 27 is deliberately allowed to replace a precise position with a coarse one: it is the
      // newer report, and isPositionCoarse is how a caller finds out which kind it now holds. Rate
      // of turn, heading and the second of the fix are absent from the message rather than
      // unavailable in it, so they survive.
      is AisLongRangePositionReport ->
        copy(
          position = message.position,
          isPositionAccurate = message.isAccurate,
          isPositionCoarse = true,
          speedOverGround = message.speedOverGround,
          courseOverGround = message.courseOverGround,
          navigationalStatus = message.navigationalStatus,
        )
      // A type 9 search and rescue aircraft is a vessel position message and is not a vessel.
      else -> this
    }

  private fun describedBy(message: AisMessage): Vessel =
    when (message) {
      is AisStaticAndVoyageData ->
        copy(
          imoNumber = message.imoNumber,
          callSign = message.callSign.orKeep(callSign),
          name = message.name.orKeep(name),
          shipType = message.shipType,
          dimensions = message.dimensions,
          epfd = message.epfd,
          eta = message.eta.takeUnless { it.isEmpty } ?: eta,
          maximumDraught = message.maximumDraught,
          destination = message.destination.orKeep(destination),
        )
      // Part A of a Class B static report carries a name and nothing else at all.
      is AisStaticDataReport -> copy(name = message.name.orKeep(name), isClassB = true)
      is AisStaticDataReportB ->
        copy(
          shipType = message.shipType,
          callSign = message.callSign.orKeep(callSign),
          dimensions = message.dimensions,
          isClassB = true,
        )
      else -> this
    }

  /** This text unless it is blank, in which case whatever was already known. */
  private fun String.orKeep(previous: String?): String? = ifBlank { null } ?: previous
}

/**
 * True if this message describes a vessel, and so contributes to a [Vessel].
 *
 * Excludes the position reports that are not vessels -- type 4 base stations, type 9 SAR aircraft
 * and type 21 navigation aids -- as well as the message types that carry no station report at all.
 * The MMSI's own [Mmsi.stationClass] is a second, independent filter and does not always agree: a
 * buoy sending a type 1 report is a misconfigured transponder, and it happens.
 */
internal fun AisMessage.describesAVessel(): Boolean =
  this is AisPositionReport ||
    this is AisPositionReportB ||
    this is AisExtendedPositionReportB ||
    this is AisLongRangePositionReport ||
    this is AisStaticAndVoyageData ||
    this is AisStaticDataReport ||
    this is AisStaticDataReportB
