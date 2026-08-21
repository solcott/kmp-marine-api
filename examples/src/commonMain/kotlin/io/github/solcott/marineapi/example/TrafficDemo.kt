package io.github.solcott.marineapi.example

import io.github.solcott.marineapi.ais.aisMessages
import io.github.solcott.marineapi.ais.messages
import io.github.solcott.marineapi.nav.ais.ClosestApproach
import io.github.solcott.marineapi.nav.ais.OwnShip
import io.github.solcott.marineapi.nav.ais.TargetRegistry
import io.github.solcott.marineapi.nav.ais.Vessel
import io.github.solcott.marineapi.nav.ais.aisTargets
import io.github.solcott.marineapi.nav.ais.closestApproachTo
import io.github.solcott.marineapi.nmea.io.nmeaSentences
import io.github.solcott.marineapi.nmea.io.positions
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.io.Source

/**
 * The same AIS feed as [demoAis], read as traffic rather than as messages.
 *
 * The difference is the whole point of `:marine-api-nav`. [demoAis] keeps a `mutableMapOf` of MMSI
 * to name so that a position report can be labelled with a name that arrived minutes earlier --
 * every consumer of the parser ends up writing that map, because AIS transmits where a vessel is
 * every few seconds and who she is every six minutes. [aisTargets] is that map done properly:
 * merged by MMSI, expiring, and covering all seven of the report types rather than the two the
 * hand-rolled version bothered with.
 *
 * It then does the thing the join exists for. A [ClosestApproach] is where and when a target will
 * pass own ship if both hold their course and speed, and it is what a watchkeeper actually acts on
 * -- a target three miles off closing at twenty knots matters, and one a mile off opening does not.
 *
 * **The feed is read twice**, which is why every demo takes a `() -> Source` factory rather than a
 * `Source`. Own ship's position comes from the receiver's own `GGA` and `RMC` and the targets come
 * from the `!AIVDM` sentences interleaved with them, and one pass cannot have two endings. A live
 * program never has this problem -- it holds the last fix as it goes -- but a log has to be
 * rewound. A log carrying no fix of its own has nothing to measure a closest approach against, and
 * the plot is printed without one.
 */
suspend fun demoTraffic(open: () -> Source) {
  val ownShip = open().nmeaSentences().positions().mapNotNull { OwnShip.from(it) }.lastOrNull()

  val traffic = TargetRegistry()
  open().nmeaSentences().aisMessages().messages().aisTargets(traffic).collect()

  if (ownShip == null) {
    println("no fix of our own in this feed, so no closest approach")
  } else {
    println(
      "own ship ${ownShip.position}, ${ownShip.speedOverGround} kn" +
        " on ${ownShip.courseOverGround.toInt()} true"
    )
  }
  println("${traffic.size} targets")
  println()

  // Closest first, which is the order a watchkeeper wants them in. A target that has sent only her
  // name has no approach to compute and sorts to the end.
  traffic.vessels
    .map { it to ownShip?.let { own -> it.closestApproachTo(own) } }
    .sortedBy { (_, approach) -> approach?.distance ?: Double.MAX_VALUE }
    .forEach { (vessel, approach) -> println(describeTarget(vessel, approach)) }
}

/** One line of the traffic plot. */
private fun describeTarget(vessel: Vessel, approach: ClosestApproach?): String {
  val name = (vessel.name ?: "").fit(NAME_WIDTH)
  val flag = vessel.flagOrStationClass().fit(FLAG_WIDTH)
  val speed = vessel.speedOverGround?.let { " $it kn" } ?: " speed not reported"
  val motion = vessel.position?.let { "$it$speed" } ?: "no position reported"
  val cpa =
    approach?.let {
      val timing =
        if (it.isOpening) "opening" else "in ${it.timeToClosestApproach.toHoursAndMinutes()}"
      "  CPA ${it.distanceNauticalMiles.toHundredths()} nm $timing"
    } ?: ""
  return "${vessel.mmsi}  $name  $flag  $motion$cpa"
}

/**
 * The flag state, or what kind of station this is when the identity carries no MID.
 *
 * A target whose MMSI says it is a buoy or a man-overboard beacon is worth labelling as one: it
 * sends position reports that look exactly like a small vessel moving slowly.
 */
private fun Vessel.flagOrStationClass(): String =
  mmsi.flagState ?: mmsi.stationClass.name.lowercase().replace('_', ' ')

private const val NAME_WIDTH = 20

private const val FLAG_WIDTH = 16
