package io.github.solcott.marineapi.nav.route

import io.github.solcott.marineapi.nmea.Position
import io.github.solcott.marineapi.nmea.RouteType
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.Waypoint
import io.github.solcott.marineapi.nmea.sentence.R00
import io.github.solcott.marineapi.nmea.sentence.Rte
import io.github.solcott.marineapi.nmea.sentence.Wpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Shared with Navigation.kt, which normalises distances to the same unit. */
internal const val METRES_PER_NAUTICAL_MILE = 1852.0

/**
 * One stop on a route: the name the route gave it, and where it is if that is known yet.
 *
 * The two halves arrive in different sentences. `RTE` names the waypoints and `WPL` says where each
 * one is, and a plotter is free to send them in either order, or to send a route naming a waypoint
 * it never describes. So [position] is nullable and an unresolved point is **kept** rather than
 * dropped -- a route of five waypoints with the third one missing is still a five-waypoint route,
 * and silently returning four of them would renumber every leg after the gap.
 *
 * @property id the waypoint's name, as the route sentence spelled it
 * @property position where it is, or `null` if no `WPL` for this name had arrived
 */
public data class RoutePoint(val id: String, val position: Position? = null) {

  /** [id] and [position] as a [Waypoint], or `null` while the position is still unknown. */
  public val waypoint: Waypoint?
    get() = position?.let { Waypoint(id, it) }
}

/**
 * A route: an ordered list of waypoints, reassembled from however many sentences carried it.
 *
 * @property waypoints the stops in the order the route visits them, resolved where possible
 * @property routeId the route's name, `null` from an `R00`, which has nowhere to put one
 * @property type whether the sentence claimed to list the whole route or only the legs left to run
 */
public data class Route(
  val waypoints: List<RoutePoint> = emptyList(),
  val routeId: String? = null,
  val type: RouteType? = null,
) {

  /** The waypoint names in order, whether or not their positions are known. */
  public val waypointIds: List<String>
    get() = waypoints.map { it.id }

  /**
   * Names of the waypoints no `WPL` has described yet.
   *
   * Empty is the normal steady state; non-empty most often means the route arrived before the
   * waypoints did, and is worth re-reading on a later route rather than treating as an error.
   */
  public val unresolvedWaypointIds: List<String>
    get() = waypoints.filter { it.position == null }.map { it.id }

  /** True when every waypoint has a position, which is when the route can be drawn or measured. */
  public val isFullyResolved: Boolean
    get() = waypoints.isNotEmpty() && waypoints.all { it.position != null }

  /**
   * Total great-circle distance along the legs, or `null` unless the route [isFullyResolved].
   *
   * `null` rather than the length of the legs that happen to resolve: a route with a gap in it has
   * a shorter measurable path than its real one, and a distance-to-run that is quietly too small is
   * worse than no distance at all.
   */
  public val lengthNauticalMiles: Double?
    get() {
      if (!isFullyResolved) return null
      return waypoints
        .zipWithNext { from, to -> from.position!!.distanceTo(to.position!!) }
        .sum()
        .div(METRES_PER_NAUTICAL_MILE)
    }
}

/**
 * Every complete route in this flow, with its waypoints resolved against the `WPL`s seen so far.
 *
 * ```
 * source.nmeaSentences().routes().collect { chart.draw(it.waypoints) }
 * ```
 *
 * A route longer than one sentence is split across several `RTE`s, so the full list only exists
 * once the group is complete. Reassembly follows `satellites()` in `nmea/io` exactly, because it is
 * the same problem: a group is emitted only when its sentences arrive **in order and all present**,
 * and one that is interrupted, arrives out of order, restarts partway through or changes its route
 * name mid-group is discarded rather than patched. Half a route is not a short route; it is a route
 * with legs missing from the middle, and steering it would take the vessel somewhere nobody chose.
 *
 * Groups are tracked per talker, so two devices sending routes at once do not merge into one.
 *
 * `WPL` sentences accumulate for the lifetime of the flow and are never discarded, because a
 * waypoint's position does not go stale the way a fix does -- a plotter sends the whole waypoint
 * catalogue once on connection and then only the route. A route is resolved against what has
 * arrived **by the time it completes**, so a feed that sends its `RTE` first yields a route with
 * [Route.unresolvedWaypointIds] and the next copy of the same route resolves.
 *
 * `R00` is reassembled too. It is Garmin's `RTE` with the numbering, the route name and the
 * complete-or-working flag stripped out, so it is always one sentence and always a whole route --
 * it just cannot say which route it is.
 */
public fun Flow<Sentence>.routes(): Flow<Route> = flow {
  val routes = RouteCycle()
  collect { sentence -> routes.add(sentence)?.let { emit(it) } }
}

/** The `RTE` groups in progress, one per talker, and every waypoint position seen so far. */
private class RouteCycle {

  private val groups = mutableMapOf<TalkerId, MutableList<Rte>>()
  private val positions = mutableMapOf<String, Position>()

  fun add(sentence: Sentence): Route? =
    when (sentence) {
      is Wpl -> {
        // The waypoint property is null unless the sentence carried both a name and a position,
        // and a name with no position resolves nothing.
        sentence.waypoint?.let { positions[it.id] = it.position }
        null
      }
      is Rte -> addToGroup(sentence)
      is R00 ->
        // R00's reader pads missing fields to the empty string rather than dropping them, so the
        // blanks are filtered here; an R00 of nothing but blanks is not a route.
        sentence.waypointIds
          .filter { it.isNotEmpty() }
          .takeIf { it.isNotEmpty() }
          ?.let { resolve(it) }
      else -> null
    }

  private fun addToGroup(rte: Rte): Route? {
    val count = rte.sentenceCount
    val index = rte.sentenceIndex
    if (count == null || index == null || index < 1 || index > count) {
      // A sentence that cannot say where in its group it belongs cannot be reassembled, and it
      // invalidates the group it arrived in the middle of.
      groups.remove(rte.talker)
      return null
    }

    val group =
      if (index == 1) {
        mutableListOf(rte).also { groups[rte.talker] = it }
      } else {
        val started = groups[rte.talker]
        if (started == null || started.size != index - 1 || !started.first().agreesWith(rte)) {
          groups.remove(rte.talker)
          return null
        }
        started.also { it += rte }
      }

    if (group.size < count) return null
    groups.remove(rte.talker)
    return resolve(
      ids = group.flatMap { it.waypoints },
      routeId = group.first().routeId,
      type = group.first().routeType,
    )
  }

  private fun resolve(
    ids: List<String>,
    routeId: String? = null,
    type: RouteType? = null,
  ): Route = Route(ids.map { RoutePoint(it, positions[it]) }, routeId, type)
}

/**
 * Whether a continuation sentence belongs to the group this one started.
 *
 * The sentence count has to match, as it does for `GSV`. The route id has to match as well, which
 * `GSV` has no equivalent of: a plotter switching routes mid-transmission produces a second
 * sentence that is numbered as though it continued the first, and joining them would splice two
 * routes into one that was never planned.
 */
private fun Rte.agreesWith(next: Rte): Boolean =
  sentenceCount == next.sentenceCount && routeId == next.routeId
