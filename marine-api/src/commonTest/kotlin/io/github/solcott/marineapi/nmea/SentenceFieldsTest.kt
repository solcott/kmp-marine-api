package io.github.solcott.marineapi.nmea

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

private const val RMC =
  "\$GPRMC,120044.567,A,6011.552,N,02501.941,E,000.0,360.0,160705,006.1,E,A,S*74"

/** Field accessors, exercised through a real sentence rather than a synthetic field list. */
class SentenceFieldsTest {

  private val fields =
    (SentenceRegistry.Default.parse(RMC).sentenceOrNull() as UnknownSentence).data

  @Test
  fun readsTypedFields() {
    assertEquals(LocalTime(12, 0, 44, 567_000_000), fields.timeAt(0))
    assertEquals(LocalDate(2005, 7, 16), fields.dateAt(8))
    assertEquals(0.0, fields.doubleAt(6))
    assertEquals(360.0, fields.doubleAt(7))
    assertEquals("6011.552", fields.stringAt(2))
  }

  @Test
  fun readsCodedFields() {
    assertEquals(DataStatus.ACTIVE, fields.codedAt(1, DataStatus.entries))
    assertEquals(CompassPoint.NORTH, fields.codedAt(3, CompassPoint.entries))
    assertEquals(FaaMode.AUTOMATIC, fields.codedAt(11, FaaMode.entries))
    assertEquals(NavStatus.SIMULATOR, fields.codedAt(12, NavStatus.entries))
  }

  @Test
  fun readsPosition() {
    val position = fields.positionAt(2, 3, 4, 5)
    assertEquals(60.0 + 11.552 / 60.0, position!!.latitude, 1e-12)
    assertEquals(25.0 + 1.941 / 60.0, position.longitude, 1e-12)
  }

  @Test
  fun positionIsNullWhenTheReceiverHasNoFix() {
    val noFix = fieldsOf("\$GPRMC,120044,V,,,,,,,160705,,,N")
    assertNull(noFix.positionAt(2, 3, 4, 5))
  }

  @Test
  fun unrecognisedCodeIsReportedWithTheValidOnes() {
    val bad = fieldsOf("\$GPRMC,120044,X,,,,,,,160705,,,*4C")
    val failure = assertFailsWith<NmeaFieldException> { bad.codedAt(1, DataStatus.entries) }
    assertTrue("one of [A, V]" in failure.message!!, failure.message!!)
    assertTrue("GPRMC field 1" in failure.message!!, failure.message!!)
  }

  @Test
  fun malformedTypedFieldIsReported() {
    val bad = fieldsOf("\$GPRMC,notatime,A,,,,,,,notadate,,,")
    assertFailsWith<NmeaFieldException> { bad.timeAt(0) }
    assertFailsWith<NmeaFieldException> { bad.dateAt(8) }
  }

  @Test
  fun readsTrailingFieldList() {
    assertEquals(listOf("A", "S"), fields.stringsFrom(11))
    assertEquals(emptyList(), fields.stringsFrom(99))
  }

  @Test
  fun reportsFieldCount() {
    assertEquals(13, fields.size)
  }

  private fun fieldsOf(sentence: String): SentenceFields =
    (SentenceRegistry.Default.parse(Checksum.append(sentence)).sentenceOrNull() as UnknownSentence)
      .data
}

class ValueTypesTest {

  @Test
  fun satelliteInfoValidatesItsRanges() {
    val satellite = SatelliteInfo("03", elevation = 45, azimuth = 180, noise = 40)
    assertEquals("03", satellite.id)
    assertFailsWith<IllegalArgumentException> { satellite.copy(elevation = 91) }
    assertFailsWith<IllegalArgumentException> { satellite.copy(azimuth = 361) }
    assertFailsWith<IllegalArgumentException> { satellite.copy(noise = 100) }
  }

  @Test
  fun satelliteNoiseIsAbsentWhenNotTracked() {
    assertNull(SatelliteInfo("03", 45, 180).noise)
  }

  @Test
  fun measurementKnowsWhenItIsEmpty() {
    assertTrue(Measurement().isEmpty)
    assertTrue(!Measurement(type = "C").isEmpty)
    assertTrue(!Measurement(value = 0.0).isEmpty)
  }
}
