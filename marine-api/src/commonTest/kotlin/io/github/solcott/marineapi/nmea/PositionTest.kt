package io.github.solcott.marineapi.nmea

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PositionTest {

  @Test
  fun derivesHemisphereFromSign() {
    val northEast = Position(60.19, 25.03)
    assertEquals(CompassPoint.NORTH, northEast.latitudeHemisphere)
    assertEquals(CompassPoint.EAST, northEast.longitudeHemisphere)

    val southWest = Position(-60.19, -25.03)
    assertEquals(CompassPoint.SOUTH, southWest.latitudeHemisphere)
    assertEquals(CompassPoint.WEST, southWest.longitudeHemisphere)

    // Zero is north and east, matching the Java implementation's >= 0 test.
    assertEquals(CompassPoint.NORTH, Position(0.0, 0.0).latitudeHemisphere)
    assertEquals(CompassPoint.EAST, Position(0.0, 0.0).longitudeHemisphere)
  }

  @Test
  fun rejectsOutOfBoundsCoordinates() {
    assertFailsWith<IllegalArgumentException> { Position(91.0, 0.0) }
    assertFailsWith<IllegalArgumentException> { Position(-91.0, 0.0) }
    assertFailsWith<IllegalArgumentException> { Position(0.0, 181.0) }
    assertFailsWith<IllegalArgumentException> { Position(0.0, -181.0) }
  }

  @Test
  fun altitudeIsAbsentRatherThanZero() {
    // The Java implementation defaulted altitude to 0.0, which cannot be told apart from a
    // reported sea-level fix.
    assertNull(Position(60.0, 25.0).altitude)
    assertEquals(0.0, Position(60.0, 25.0, altitude = 0.0).altitude)
  }

  @Test
  fun measuresDistanceAlongTheEquator() {
    // One degree of longitude at the equator is 60 nautical miles by the radius this uses.
    val distance = Position(0.0, 0.0).distanceTo(Position(0.0, 1.0))
    assertEquals(60 * 1852.0, distance, 1.0)
  }

  @Test
  fun measuresDistanceBetweenKnownPoints() {
    val helsinki = Position(60.19, 25.03)
    val stockholm = Position(59.33, 18.06)
    val distance = helsinki.distanceTo(stockholm)
    // 401385.8 m, cross-checked against an independent haversine using the same radius.
    assertEquals(401_385.8, distance, 0.1)
    assertEquals(distance, stockholm.distanceTo(helsinki), 1e-6)
    assertEquals(0.0, helsinki.distanceTo(helsinki), 1e-9)
  }

  @Test
  fun isAValue() {
    assertEquals(Position(60.19, 25.03), Position(60.19, 25.03))
    assertEquals(Position(60.19, 25.03).hashCode(), Position(60.19, 25.03).hashCode())
    assertEquals(Position(60.19, 25.03, 10.0), Position(60.19, 25.03).copy(altitude = 10.0))
  }

  @Test
  fun rendersReadably() {
    assertEquals("[60.1900000 N, 025.0300000 E]", Position(60.19, 25.03).toString())
    assertEquals("[60.1900000 N, 025.0300000 E, 5.0 m]", Position(60.19, 25.03, 5.0).toString())
    assertEquals("[60.1900000 S, 025.0300000 W]", Position(-60.19, -25.03).toString())
  }

  @Test
  fun convertsToWaypoint() {
    val waypoint = Position(60.19, 25.03).toWaypoint("RUSKI", "Ruskeasuo")
    assertEquals("RUSKI", waypoint.id)
    assertEquals("Ruskeasuo", waypoint.description)
    assertEquals(Position(60.19, 25.03), waypoint.position)
  }
}

class DegreesTest {

  @Test
  fun parsesCoordinateFields() {
    // 6011.552 is 60 degrees and 11.552 minutes.
    assertEquals(60.0 + 11.552 / 60.0, Degrees.parse("6011.552"), 1e-12)
    assertEquals(25.0 + 1.941 / 60.0, Degrees.parse("02501.941"), 1e-12)
    // gpsd's worked examples.
    assertEquals(45.0 + 33.35 / 60.0, Degrees.parse("4533.35"), 1e-12)
    assertEquals(167.0 + 8.033 / 60.0, Degrees.parse("16708.033"), 1e-12)
  }

  @Test
  fun parsesFieldCarryingOnlyMinutes() {
    // Fewer than three digits before the point means no degrees at all.
    assertEquals(11.552 / 60.0, Degrees.parse("11.552"), 1e-12)
  }

  @Test
  fun rejectsNonCoordinates() {
    assertFailsWith<IllegalArgumentException> { Degrees.parse("abcd.ef") }
  }

  @Test
  fun formatsCoordinateFields() {
    assertEquals("6011.552", Degrees.format(60.0 + 11.552 / 60.0, degreeDigits = 2))
    assertEquals("02501.941", Degrees.format(25.0 + 1.941 / 60.0, degreeDigits = 3))
  }

  @Test
  fun formatDropsTheSign() {
    // NMEA carries the hemisphere in a separate field.
    assertEquals("6011.552", Degrees.format(-(60.0 + 11.552 / 60.0), degreeDigits = 2))
  }

  @Test
  fun coordinateRoundTrips() {
    for (field in listOf("6011.552", "0000.000", "8959.999")) {
      assertEquals(field, Degrees.format(Degrees.parse(field), degreeDigits = 2))
    }
    for (field in listOf("02501.941", "00000.000", "17959.999")) {
      assertEquals(field, Degrees.format(Degrees.parse(field), degreeDigits = 3))
    }
  }

  @Test
  fun appliesHemisphere() {
    assertEquals(60.0, Degrees.applyHemisphere(60.0, CompassPoint.NORTH))
    assertEquals(-60.0, Degrees.applyHemisphere(60.0, CompassPoint.SOUTH))
    assertEquals(25.0, Degrees.applyHemisphere(25.0, CompassPoint.EAST))
    assertEquals(-25.0, Degrees.applyHemisphere(25.0, CompassPoint.WEST))
  }
}
