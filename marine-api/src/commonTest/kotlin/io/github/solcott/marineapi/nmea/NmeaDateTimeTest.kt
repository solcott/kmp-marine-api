package io.github.solcott.marineapi.nmea

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.UtcOffset

class NmeaDateTimeTest {

  @Test
  fun parsesDateField() {
    assertEquals(LocalDate(2005, 7, 16), NmeaDateTime.parseDate("160705"))
    assertEquals(LocalDate(2004, 8, 7), NmeaDateTime.parseDate("070804"))
  }

  @Test
  fun parsesFourDigitYear() {
    // Some devices emit ddmmyyyy; the Java implementation accepted it and so does this.
    assertEquals(LocalDate(2005, 7, 16), NmeaDateTime.parseDate("16072005"))
  }

  @Test
  fun expandsTwoDigitYearAroundThePivot() {
    // The pivot year itself belongs to the twenty-first century, the year after it to the
    // twentieth.
    assertEquals(2050, NmeaDateTime.expandYear(50))
    assertEquals(1951, NmeaDateTime.expandYear(51))
    assertEquals(2000, NmeaDateTime.expandYear(0))
    assertEquals(1999, NmeaDateTime.expandYear(99))
    assertEquals(1980, NmeaDateTime.expandYear(1980))
  }

  @Test
  fun rejectsThreeDigitYear() {
    assertFailsWith<IllegalArgumentException> { NmeaDateTime.expandYear(100) }
    assertFailsWith<IllegalArgumentException> { NmeaDateTime.expandYear(-1) }
  }

  @Test
  fun formatsDateField() {
    assertEquals("160705", NmeaDateTime.formatDate(LocalDate(2005, 7, 16)))
    assertEquals("010100", NmeaDateTime.formatDate(LocalDate(2000, 1, 1)))
    assertEquals("311299", NmeaDateTime.formatDate(LocalDate(1999, 12, 31)))
  }

  @Test
  fun dateRoundTrips() {
    for (field in listOf("160705", "010100", "311299", "290224")) {
      assertEquals(field, NmeaDateTime.formatDate(NmeaDateTime.parseDate(field)))
    }
  }

  @Test
  fun parsesTimeField() {
    assertEquals(LocalTime(12, 0, 44), NmeaDateTime.parseTime("120044"))
    assertEquals(LocalTime(12, 0, 44, 567_000_000), NmeaDateTime.parseTime("120044.567"))
    assertEquals(LocalTime(0, 0, 0), NmeaDateTime.parseTime("000000"))
    assertEquals(LocalTime(23, 59, 59, 999_000_000), NmeaDateTime.parseTime("235959.999"))
  }

  @Test
  fun rejectsOutOfRangeTime() {
    assertFailsWith<IllegalArgumentException> { NmeaDateTime.parseTime("240000") }
    assertFailsWith<IllegalArgumentException> { NmeaDateTime.parseTime("126000") }
    assertFailsWith<IllegalArgumentException> { NmeaDateTime.parseTime("120060") }
    assertFailsWith<IllegalArgumentException> { NmeaDateTime.parseTime("1200") }
  }

  @Test
  fun formatsTimeOmittingUnusedDecimals() {
    assertEquals("120044", NmeaDateTime.formatTime(LocalTime(12, 0, 44)))
    assertEquals("120044.567", NmeaDateTime.formatTime(LocalTime(12, 0, 44, 567_000_000)))
    assertEquals("120044.000", NmeaDateTime.formatTime(LocalTime(12, 0, 44), fractionDigits = 3))
  }

  @Test
  fun timeRoundTrips() {
    for (field in listOf("120044", "120044.567", "000000", "235959.999")) {
      assertEquals(field, NmeaDateTime.formatTime(NmeaDateTime.parseTime(field)))
    }
  }

  @Test
  fun buildsUtcOffsetWithTheSignOfTheHours() {
    assertEquals(UtcOffset(hours = 0, minutes = 0), NmeaDateTime.utcOffset(0, 0))
    assertEquals(UtcOffset(hours = 2, minutes = 30), NmeaDateTime.utcOffset(2, 30))
    // A ZDA reporting -5 hours 30 minutes is -05:30, not -04:30.
    assertEquals(UtcOffset(hours = -5, minutes = -30), NmeaDateTime.utcOffset(-5, 30))
    assertNull(NmeaDateTime.utcOffset(null, 30))
    assertNull(NmeaDateTime.utcOffset(2, null))
  }
}
