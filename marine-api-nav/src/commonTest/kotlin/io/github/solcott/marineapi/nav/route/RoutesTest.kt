package io.github.solcott.marineapi.nav.route

import io.github.solcott.marineapi.nmea.Position
import io.github.solcott.marineapi.nmea.RouteType
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.sentence.R00
import io.github.solcott.marineapi.nmea.sentence.Rte
import io.github.solcott.marineapi.nmea.sentence.Wpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

/**
 * Sentences are built rather than parsed: these operators take a `Flow<Sentence>`, so going through
 * the parser would only be testing the layer underneath again.
 */
private fun rte(
  index: Int,
  count: Int,
  vararg waypointIds: String,
  routeId: String? = "ROUTE1",
  talker: TalkerId = TalkerId.GP,
  type: RouteType? = RouteType.COMPLETE,
): Rte =
  Rte(
    talker = talker,
    sentenceCount = count,
    sentenceIndex = index,
    routeType = type,
    routeId = routeId,
    waypointIds = waypointIds.toList(),
  )

private fun wpl(id: String, latitude: Double = 60.0, longitude: Double = 25.0): Wpl =
  Wpl(talker = TalkerId.GP, position = Position(latitude, longitude), waypointId = id)

private suspend fun routesOf(vararg sentences: Sentence): List<Route> =
  sentences.toList().asFlow().routes().toList()

class RouteReassemblyTest {

  @Test
  fun joinsAGroupSplitAcrossThreeSentences() = runTest {
    val routes =
      routesOf(rte(1, 3, "MELIN", "RUSKI"), rte(2, 3, "KNUDAN"), rte(3, 3, "PORVOO", "HELSNK"))
    assertEquals(
      listOf("MELIN", "RUSKI", "KNUDAN", "PORVOO", "HELSNK"),
      routes.single().waypointIds,
    )
    assertEquals("ROUTE1", routes.single().routeId)
    assertEquals(RouteType.COMPLETE, routes.single().type)
  }

  @Test
  fun emitsASingleSentenceRouteWithoutWaitingForMore() = runTest {
    assertEquals(listOf("MELIN"), routesOf(rte(1, 1, "MELIN")).single().waypointIds)
  }

  @Test
  fun discardsAGroupWhoseSentencesArriveOutOfOrder() = runTest {
    // Half a route is not a short route -- it is a route with legs missing from the middle.
    assertTrue(routesOf(rte(1, 3, "MELIN"), rte(3, 3, "KNUDAN")).isEmpty())
  }

  @Test
  fun discardsAGroupThatRestartsPartwayThrough() = runTest {
    val routes = routesOf(rte(1, 3, "MELIN"), rte(1, 2, "PORVOO"), rte(2, 2, "HELSNK"))
    assertEquals(listOf(listOf("PORVOO", "HELSNK")), routes.map { it.waypointIds })
  }

  @Test
  fun discardsAGroupWhoseRouteNameChangesMidway() = runTest {
    // A plotter switching routes mid-transmission numbers the new one as though it continued the
    // old, and splicing them would produce a route nobody planned.
    assertTrue(
      routesOf(rte(1, 2, "MELIN", routeId = "ROUTE1"), rte(2, 2, "PORVOO", routeId = "ROUTE2"))
        .isEmpty()
    )
  }

  @Test
  fun discardsAGroupWhoseSentenceCountChangesMidway() = runTest {
    assertTrue(routesOf(rte(1, 2, "MELIN"), rte(2, 3, "PORVOO")).isEmpty())
  }

  @Test
  fun discardsASentenceThatCannotSayWhereInItsGroupItBelongs() = runTest {
    assertTrue(
      routesOf(rte(1, 2, "MELIN"), Rte(TalkerId.GP, waypointIds = listOf("PORVOO"))).isEmpty()
    )
  }

  @Test
  fun keepsTheRoutesOfTwoTalkersApart() = runTest {
    val routes =
      routesOf(
        rte(1, 2, "MELIN", talker = TalkerId.GP),
        rte(1, 2, "PORVOO", talker = TalkerId.II),
        rte(2, 2, "RUSKI", talker = TalkerId.GP),
        rte(2, 2, "HELSNK", talker = TalkerId.II),
      )
    assertEquals(
      listOf(listOf("MELIN", "RUSKI"), listOf("PORVOO", "HELSNK")),
      routes.map { it.waypointIds },
    )
  }

  @Test
  fun dropsTheEmptyFieldsARouteSentencePadsItselfWith() = runTest {
    val padded = Rte(TalkerId.GP, 1, 1, null, "R", listOf("MELIN", null, "RUSKI"))
    assertEquals(listOf("MELIN", "RUSKI"), routesOf(padded).single().waypointIds)
  }

  @Test
  fun readsAGarminR00AsAWholeRouteWithNoName() = runTest {
    val route = routesOf(R00(TalkerId.GP, listOf("MELIN", "RUSKI"))).single()
    assertEquals(listOf("MELIN", "RUSKI"), route.waypointIds)
    assertNull(route.routeId)
    assertNull(route.type)
  }

  @Test
  fun ignoresAnR00WithNothingInIt() = runTest {
    assertTrue(routesOf(R00(TalkerId.GP, listOf("", ""))).isEmpty())
  }
}

class RouteResolutionTest {

  @Test
  fun resolvesWaypointsAgainstThePositionsAlreadySeen() = runTest {
    val route =
      routesOf(wpl("MELIN", 60.0, 25.0), wpl("RUSKI", 61.0, 26.0), rte(1, 1, "MELIN", "RUSKI"))
        .single()
    assertEquals(Position(60.0, 25.0), route.waypoints[0].position)
    assertEquals(Position(61.0, 26.0), route.waypoints[1].position)
    assertTrue(route.isFullyResolved)
    assertEquals(emptyList(), route.unresolvedWaypointIds)
  }

  @Test
  fun keepsAWaypointNoPositionHasArrivedFor() = runTest {
    // Dropping it would renumber every leg after the gap.
    val route = routesOf(wpl("MELIN"), rte(1, 1, "MELIN", "RUSKI", "KNUDAN")).single()
    assertEquals(listOf("MELIN", "RUSKI", "KNUDAN"), route.waypointIds)
    assertEquals(listOf("RUSKI", "KNUDAN"), route.unresolvedWaypointIds)
    assertNull(route.waypoints[1].waypoint)
    assertTrue(!route.isFullyResolved)
  }

  @Test
  fun resolvesOnASecondPassOnceTheWaypointsHaveArrived() = runTest {
    // A plotter is free to send the route before the catalogue, and the positions it sends
    // afterwards do not go stale, so the next copy of the same route comes back whole.
    val routes = routesOf(rte(1, 1, "MELIN"), wpl("MELIN", 60.0, 25.0), rte(1, 1, "MELIN"))
    assertEquals(listOf(false, true), routes.map { it.isFullyResolved })
  }

  @Test
  fun ignoresAWaypointSentenceCarryingNoPosition() = runTest {
    val route = routesOf(Wpl(TalkerId.GP, waypointId = "MELIN"), rte(1, 1, "MELIN")).single()
    assertNull(route.waypoints.single().position)
  }

  @Test
  fun measuresTheRouteAlongItsLegs() = runTest {
    // One degree of latitude is 60 nautical miles by the earth radius this library uses, so two
    // legs of a degree each are 120.
    val route =
      routesOf(
          wpl("A", 60.0, 25.0),
          wpl("B", 61.0, 25.0),
          wpl("C", 62.0, 25.0),
          rte(1, 1, "A", "B", "C"),
        )
        .single()
    val length = route.lengthNauticalMiles
    assertTrue(length != null && length > 119.9 && length < 120.1, "was $length")
  }

  @Test
  fun refusesToMeasureARouteWithAGapInIt() = runTest {
    // A distance-to-run that is quietly too short is worse than none at all.
    val route = routesOf(wpl("A", 60.0, 25.0), rte(1, 1, "A", "B")).single()
    assertNull(route.lengthNauticalMiles)
  }
}
