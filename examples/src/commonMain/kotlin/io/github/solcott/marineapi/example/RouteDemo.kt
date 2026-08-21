package io.github.solcott.marineapi.example

import io.github.solcott.marineapi.nav.route.NavigationState
import io.github.solcott.marineapi.nav.route.Route
import io.github.solcott.marineapi.nav.route.navigation
import io.github.solcott.marineapi.nav.route.routes
import io.github.solcott.marineapi.nmea.io.nmeaSentences
import kotlinx.io.Source

/**
 * The plan and the progress: the route a plotter is following, and the leg it is steering.
 *
 * Try it on `marine-api/src/jvmTest/resources/data/Garmin-GPS76_route.txt`, which is a real
 * handheld working through a three-waypoint route.
 *
 * **Watch the first two lines of output.** A route arrives as an `RTE` group carrying nothing but
 * waypoint *names*, and the `WPL` sentences that say where those names are come afterwards -- so
 * the first route out of this log has three waypoints, no coordinates and no length, and the
 * second, identical group a few seconds later is fully resolved and can be measured. An unresolved
 * id is kept rather than dropped, because dropping one silently renumbers every leg after the gap;
 * and `lengthNauticalMiles` is `null` rather than the length of the legs it could measure, because
 * a distance-to-run that is quietly too short is worse than no distance at all.
 *
 * The second section is `navigation()`, which is a different shape of problem. Eight sentence types
 * answer overlapping parts of one question and no device sends all of them -- this Garmin sends
 * `RMB` and nothing else, an autopilot would send `APB` and `XTE`, a plotter might send `BWC` and
 * `ZTG` -- so the operator fuses whichever arrive into one value and a consumer reads the same
 * properties either way. State carries between update cycles, since a device sending `ZTG` once
 * every ten cycles must not read as having no time-to-go nine times out of ten, and is thrown away
 * the instant the destination changes, since on a new leg the old range and bearing are not stale,
 * they are wrong.
 */
suspend fun demoRoute(open: () -> Source) {
  println("-- routes --")
  var found = 0
  open().nmeaSentences().routes().collect { route ->
    found++
    println("  ${describeRoute(route)}")
  }
  if (found == 0) println("  no complete RTE group in this feed")

  println()
  println("-- the leg being steered --")
  var legs = 0
  open().nmeaSentences().navigation().collect { state ->
    legs++
    println("  ${describeNavigation(state)}")
  }
  if (legs == 0) println("  nothing in this feed describes an active leg")
}

/** A route as a list of names, plus its length once every name has a position. */
private fun describeRoute(route: Route): String {
  val name = (route.routeId ?: "unnamed").fit(ROUTE_NAME_WIDTH)
  val type = (route.type?.name?.lowercase() ?: "type not given").fit(ROUTE_TYPE_WIDTH)
  val length =
    route.lengthNauticalMiles?.let { "${it.toHundredths()} nm" }
      ?: ("no length yet, still waiting on a WPL for " +
        route.unresolvedWaypointIds.joinToString(", "))
  return "$name  $type  ${route.waypointIds.joinToString(" -> ")}  $length"
}

/** One update cycle's worth of the active leg, with the fields the device did not send left out. */
private fun describeNavigation(state: NavigationState): String {
  val leg =
    listOfNotNull(state.originWaypointId, state.destinationWaypointId ?: "?")
      .joinToString(" -> ")
      .fit(LEG_WIDTH)

  val parts =
    listOfNotNull(
      state.crossTrack?.let {
        // The magnitude is unsigned in every sentence that carries it, so the side travels beside
        // it as its own field. Without both, a mile off track could be a mile either way.
        "${it.nauticalMiles.toHundredths()} nm off track, steer ${it.steerTo.name.lowercase()}"
      },
      state.distanceNauticalMiles?.let { "${it.toHundredths()} nm to run" },
      state.bearingTrue?.let { "bearing ${it.toWholeDegrees()} true" },
      // Not the vessel's speed. A boat making six knots ACROSS the leg rather than along it closes
      // at nearly nothing, and this is the number an ETA has to come from.
      state.closingVelocityKnots?.let { "closing at ${it.toHundredths()} kn" },
      state.timeToGo?.let { "${it.toHoursAndMinutes()} to go" },
      if (state.hasArrived) "ARRIVED" else null,
    )

  return "$leg  ${parts.joinToString("  ")}"
}

private const val ROUTE_NAME_WIDTH = 14

private const val ROUTE_TYPE_WIDTH = 14

private const val LEG_WIDTH = 14
