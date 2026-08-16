package io.github.solcott.marineapi.nmea.io

import io.github.solcott.marineapi.nmea.BearingReference
import io.github.solcott.marineapi.nmea.Checksum
import io.github.solcott.marineapi.nmea.FaaMode
import io.github.solcott.marineapi.nmea.GpsFixQuality
import io.github.solcott.marineapi.nmea.GpsFixStatus
import io.github.solcott.marineapi.nmea.ParseResult
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceRegistry
import io.github.solcott.marineapi.nmea.TalkerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Fixtures are the `EXAMPLE` constants of the Java suite's own tests, matching the ones the
 * sentence tests use, so the operators are exercised on the same data as the parsers underneath
 * them.
 */
private object Examples {
  const val GGA = "\$GPGGA,120044.567,6011.552,N,02501.941,E,1,00,2.0,28.0,M,19.6,M,,*63"
  const val GLL = "\$GPGLL,6011.552,N,02501.941,E,120045,A*26"
  const val RMC = "\$GPRMC,120044.567,A,6011.552,N,02501.941,E,000.0,360.0,160705,006.1,E,A,S*74"
  const val VTG = "\$GPVTG,360.0,T,348.7,M,16.89,N,31.28,K,A"
  const val GSA = "\$GPGSA,A,3,02,,,07,,09,24,26,,,,,1.6,1.6,1.0*3D"
  const val ZDA = "\$GPZDA,032915,07,08,2004,00,00*4D"

  /** A whole GSV group: eleven satellites reported in three sentences, of twelve in view. */
  val GSV_GROUP =
    listOf(
        "\$GPGSV,3,1,12,03,03,111,00,04,15,270,00,06,01,010,00,13,06,292,00",
        "\$GPGSV,3,2,12,15,56,182,51,17,38,163,47,18,63,058,50,21,53,329,47",
        "\$GPGSV,3,3,12,22,42,067,42,24,14,311,43,27,05,244,00,,,,",
      )
      .map { Checksum.append(it) }
}

/**
 * Parses each line and fails the test on a fixture that does not parse, rather than dropping it.
 */
private fun sentencesOf(vararg lines: String): Flow<Sentence> = sentencesOf(lines.toList())

private fun sentencesOf(lines: List<String>): Flow<Sentence> = flow {
  for (line in lines) {
    val result = SentenceRegistry.Default.parse(line)
    emit(assertIs<ParseResult.Ok>(result, "fixture did not parse: $result").sentence)
  }
}

class PositionsTest {

  @Test
  fun reportsAFixFromAPositionWithNoVelocityAtAll() = runTest {
    // A receiver that says where it is without saying how fast it is going has still said where it
    // is, and speedKnots is nullable for exactly that. Requiring a velocity used to discard 136
    // real positions in the conformance corpus -- a depth sounder's 122 GLL fixes and 14 GGA from
    // an mr-350p -- every one of which gpsd reports.
    val fromGga = sentencesOf(Examples.GGA).positions().toList().single()
    assertEquals(GpsFixQuality.NORMAL, fromGga.fixQuality)
    assertNull(fromGga.speedKnots, "nothing in this cycle reported a speed")

    assertEquals(1, sentencesOf(Examples.GGA, Examples.RMC).positions().toList().size)
  }

  @Test
  fun reportsNothingForACycleWithNoPositionInIt() = runTest {
    // The one thing a fix does require. A VTG carries velocity and nothing else.
    assertEquals(emptyList(), sentencesOf(Examples.VTG).positions().toList())
  }

  @Test
  fun reportsAFixFromAnRmcWithNoOtherPositionSentence() = runTest {
    // RMC carries position, velocity, date and time at once, so it is a whole cycle by itself.
    // The predecessor demanded a GGA or GLL alongside it and so reported nothing whatsoever for
    // the four receivers in the conformance corpus that send RMC and no other position sentence.
    val fix = sentencesOf(Examples.RMC).positions().toList().single()
    assertEquals(60.0 + 11.552 / 60.0, fix.position.latitude, 1e-12)
    assertEquals(0.0, fix.speedKnots)
    assertEquals(LocalDate(2005, 7, 16), fix.date)
    assertNull(fix.position.altitude, "RMC reports no altitude")
    assertNull(fix.fixQuality, "and no fix quality either -- that is GGA's field")
  }

  @Test
  fun reportsTheCycleWhenTheReceiverComesRoundAgain() = runTest {
    // Not when the cycle first looks complete: a fix cannot be attributed the altitude and date of
    // sentences that have not arrived yet, and both routinely arrive after the fix looks done.
    val cycle = listOf(Examples.GGA, Examples.RMC, Examples.VTG, Examples.ZDA)
    val fixes = sentencesOf(cycle + cycle).positions().toList()
    assertEquals(2, fixes.size)
    assertTrue(fixes.all { it.position.altitude == 28.0 }, "GGA's altitude reached both")
    assertTrue(fixes.all { it.speedKnots == 16.89 }, "VTG's speed reached both")
  }

  @Test
  fun reportsTheLastCycleWhenTheFeedEnds() = runTest {
    // A finite feed -- a replayed log -- has no sentence after its last cycle to close it, so the
    // end of the feed does. Without that the final fix of every log would be dropped.
    assertEquals(1, sentencesOf(Examples.GGA, Examples.RMC).positions().toList().size)
  }

  @Test
  fun buildsAFixFromGgaAndRmc() = runTest {
    val fix = sentencesOf(Examples.GGA, Examples.RMC).positions().toList().single()
    assertEquals(60.0 + 11.552 / 60.0, fix.position.latitude, 1e-12)
    assertEquals(28.0, fix.position.altitude, "GGA is the only source of altitude")
    assertEquals(LocalTime(12, 0, 44, 567_000_000), fix.time)
    assertEquals(LocalDate(2005, 7, 16), fix.date)
    assertEquals(0.0, fix.speedKnots)
    assertEquals(360.0, fix.courseTrue)
    assertEquals(GpsFixQuality.NORMAL, fix.fixQuality)
    assertEquals(FaaMode.AUTOMATIC, fix.faaMode)
  }

  @Test
  fun readsTheFaaModeEvenWhenGgaSuppliedThePosition() = runTest {
    // The implementation this replaces read the mode from RMC only on the branch that also took
    // the position from it, so a cycle containing a GGA -- which is most of them -- lost the mode
    // entirely.
    val withGga = sentencesOf(Examples.GGA, Examples.RMC).positions().toList().single()
    val withoutGga = sentencesOf(Examples.RMC, Examples.VTG).positions().toList().single()
    assertEquals(FaaMode.AUTOMATIC, withGga.faaMode)
    assertEquals(withoutGga.faaMode, withGga.faaMode)
  }

  @Test
  fun takesPositionFromGllWhenThereIsNoGgaAndThenHasNoAltitude() = runTest {
    val fix = sentencesOf(Examples.GLL, Examples.RMC).positions().toList().single()
    assertEquals(60.0 + 11.552 / 60.0, fix.position.latitude, 1e-12)
    assertNull(fix.position.altitude, "GLL reports no altitude and none may be invented")
  }

  @Test
  fun prefersVtgForVelocityAndFallsThroughItsBlankFields() = runTest {
    val withVtg =
      sentencesOf(Examples.GGA, Examples.RMC, Examples.VTG).positions().toList().single()
    assertEquals(16.89, withVtg.speedKnots, "VTG is the dedicated velocity sentence")
    assertEquals(360.0, withVtg.courseTrue)

    val blankVtg = Checksum.append("\$GPVTG,,T,,M,,N,,K,A")
    val fallenThrough = sentencesOf(Examples.GGA, Examples.RMC, blankVtg).positions().toList()
    assertEquals(0.0, fallenThrough.single().speedKnots, "an empty VTG field is not a zero")
  }

  @Test
  fun convertsSpeedToKilometresPerHour() = runTest {
    val fix = sentencesOf(Examples.GGA, Examples.VTG).positions().toList().single()
    assertEquals(16.89 * 1.852, fix.speedKmh!!, 1e-9)
  }

  @Test
  fun takesTheDateFromZdaWhenThereIsNoRmc() = runTest {
    // A receiver that sends GGA and VTG but no RMC reports no date -- unless it also sends ZDA,
    // whose entire purpose is the date and time. The predecessor ignored ZDA and substituted the
    // host clock's date instead.
    val withZda =
      sentencesOf(Examples.ZDA, Examples.GGA, Examples.VTG).positions().toList().single()
    assertEquals(LocalDate(2004, 8, 7), withZda.date)

    // And ZDA last, which is where 626 of the 631 ZDA sentences in the corpus sit.
    val zdaLast =
      sentencesOf(Examples.GGA, Examples.VTG, Examples.ZDA).positions().toList().single()
    assertEquals(LocalDate(2004, 8, 7), zdaLast.date)

    val withoutZda = sentencesOf(Examples.GGA, Examples.VTG).positions().toList().single()
    assertNull(withoutZda.date, "no sentence in this cycle says what day it is")
    assertNull(withoutZda.dateTime)
  }

  @Test
  fun pairsDateAndTimeOnlyWhenItHasBoth() = runTest {
    val fix = sentencesOf(Examples.GGA, Examples.RMC).positions().toList().single()
    assertEquals(LocalDate(2005, 7, 16), fix.dateTime?.date)
    assertEquals(LocalTime(12, 0, 44, 567_000_000), fix.dateTime?.time)
  }

  @Test
  fun reportsNothingWhenTheSentencesSayTheDataIsBad() = runTest {
    val voidRmc =
      Checksum.append("\$GPRMC,120044.567,V,6011.552,N,02501.941,E,000.0,360.0,160705,006.1,E,N")
    assertEquals(emptyList(), sentencesOf(Examples.GGA, voidRmc).positions().toList())

    val noFixGga =
      Checksum.append("\$GPGGA,120044.567,6011.552,N,02501.941,E,0,00,2.0,28.0,M,19.6,M,,")
    assertEquals(emptyList(), sentencesOf(noFixGga, Examples.RMC).positions().toList())

    val voidGll = Checksum.append("\$GPGLL,6011.552,N,02501.941,E,120045,V")
    assertEquals(emptyList(), sentencesOf(voidGll, Examples.RMC).positions().toList())
  }

  @Test
  fun reportsNothingForAnRmcInFaaModeNone() = runTest {
    // NMEA 2.3 made this field dominate the status field, so a device may report status A and mode
    // N at once. The predecessor had to guard this behind a field count because its accessor threw
    // for a field that was never sent; an absent mode is simply null here.
    val modeNone =
      Checksum.append("\$GPRMC,120044.567,A,6011.552,N,02501.941,E,000.0,360.0,160705,006.1,E,N")
    assertEquals(emptyList(), sentencesOf(Examples.GGA, modeNone).positions().toList())

    val legacy =
      Checksum.append("\$GPRMC,120044.567,A,6011.552,N,02501.941,E,000.0,360.0,160705,006.1,E")
    assertEquals(1, sentencesOf(Examples.GGA, legacy).positions().toList().size)
  }

  @Test
  fun reportsNothingWhenThePositionFieldsAreEmpty() = runTest {
    // A receiver still searching sends the shape of a fix with nothing in it. PositionFix.position
    // is not nullable, so the cycle is dropped rather than reported as a fix that is not one.
    val emptyGga = Checksum.append("\$GPGGA,120044.567,,,,,1,00,2.0,,M,,M,,")
    val emptyRmc = Checksum.append("\$GPRMC,120044.567,A,,,,,,,160705,,,A")
    assertEquals(emptyList(), sentencesOf(emptyGga, emptyRmc).positions().toList())
  }

  @Test
  fun aRepeatedSentenceTypeStartsANewCycle() = runTest {
    // Two GGAs with no RMC between them cannot be one cycle: the receiver moved on. Each closes a
    // cycle of its own, and the first is not silently blended into the second.
    val second =
      Checksum.append("\$GPGGA,120045.567,6012.552,N,02501.941,E,1,00,2.0,30.0,M,19.6,M,,")
    val fixes = sentencesOf(Examples.GGA, second, Examples.RMC).positions().toList()
    assertEquals(2, fixes.size)
    assertEquals(60.0 + 11.552 / 60.0, fixes[0].position.latitude, 1e-12)
    assertEquals(60.0 + 12.552 / 60.0, fixes[1].position.latitude, 1e-12)
    assertEquals(30.0, fixes[1].position.altitude, "the second GGA's altitude, not the first's")
  }

  @Test
  fun holdsOneCycleHoweverLongTheFeedRuns() = runTest {
    // A device emitting nothing but GGA accumulates nothing: each repeat closes the cycle before
    // it and replaces it, so the state held is one cycle whether the feed is three sentences or
    // three million. This is what the predecessor's expiry timer was for.
    val fixes = sentencesOf(List(500) { Examples.GGA }).positions().toList()
    assertEquals(500, fixes.size)
    assertTrue(fixes.all { it == fixes.first() }, "every cycle held exactly one GGA")
  }

  @Test
  fun reportsOneFixPerCycleOverAContinuousFeed() = runTest {
    val feed = List(3) { listOf(Examples.GGA, Examples.GSA, Examples.RMC, Examples.VTG) }.flatten()
    assertEquals(3, sentencesOf(feed).positions().toList().size)
  }

  @Test
  fun ignoresSentencesThatSayNothingAboutTheFix() = runTest {
    val noise = Checksum.append("\$IIMTW,17.5,C")
    assertEquals(1, sentencesOf(Examples.GGA, noise, Examples.RMC).positions().toList().size)
  }
}

class HeadingsTest {

  @Test
  fun readsTrueAndMagneticHeadingsApart() = runTest {
    val headings =
      sentencesOf(
          "\$GPHDT,274.07,T*03",
          Checksum.append("\$HCHDM,123.4,M"),
          Checksum.append("\$HCHDG,123.4,1.2,E,4.8,W"),
        )
        .headings()
        .toList()

    assertEquals(
      listOf(
        Heading(274.07, BearingReference.TRUE),
        Heading(123.4, BearingReference.MAGNETIC),
        Heading(123.4, BearingReference.MAGNETIC),
      ),
      headings,
    )
  }

  @Test
  fun reportsOneHeadingPerSentenceWithNoCycleToWaitFor() = runTest {
    // A vessel with both a gyro and a magnetic compass emits HDT and HDM in the same cycle, and
    // both are real readings of different things, so both are reported.
    val cycle = sentencesOf("\$GPHDT,274.07,T*03", Checksum.append("\$HCHDM,123.4,M"))
    assertEquals(2, cycle.headings().toList().size)
  }

  @Test
  fun ignoresASentenceWithNoHeadingInIt() = runTest {
    assertEquals(emptyList(), sentencesOf(Examples.GGA, Examples.RMC).headings().toList())
    assertEquals(emptyList(), sentencesOf(Checksum.append("\$HCHDG,,,,,")).headings().toList())
  }

  @Test
  fun readsTheHeadingFromASentenceDirectly() {
    val sentence = SentenceRegistry.Default.parse("\$GPHDT,274.07,T*03")
    assertEquals(
      Heading(274.07, BearingReference.TRUE),
      assertIs<ParseResult.Ok>(sentence).sentence.headingOrNull(),
    )
  }
}

class SatellitesTest {

  @Test
  fun reassemblesAWholeGsvGroup() = runTest {
    val view = sentencesOf(Examples.GSV_GROUP).satellites().toList().single()
    assertEquals(TalkerId.GP, view.talker)
    assertEquals(12, view.satellitesInView)
    assertEquals(11, view.satellites.size, "eleven of the twelve fit into three sentences")
    assertEquals(listOf("03", "04", "06", "13"), view.satellites.take(4).map { it.id })
    assertEquals("27", view.satellites.last().id)
  }

  @Test
  fun reportsNothingUntilTheGroupIsComplete() = runTest {
    assertEquals(emptyList(), sentencesOf(Examples.GSV_GROUP.dropLast(1)).satellites().toList())
  }

  @Test
  fun discardsAGroupThatArrivesOutOfOrderOrRestarts() = runTest {
    val outOfOrder = listOf(Examples.GSV_GROUP[0], Examples.GSV_GROUP[2])
    assertEquals(emptyList(), sentencesOf(outOfOrder).satellites().toList())

    val restarted =
      listOf(Examples.GSV_GROUP[0], Examples.GSV_GROUP[0]) + Examples.GSV_GROUP.drop(1)
    assertEquals(1, sentencesOf(restarted).satellites().toList().size)
  }

  @Test
  fun attachesTheGsaSentencesOfTheCycle() = runTest {
    val view = sentencesOf(listOf(Examples.GSA) + Examples.GSV_GROUP).satellites().toList().single()
    assertEquals(GpsFixStatus.GPS_3D, view.fixStatus)
    assertEquals(1.6, view.positionDop)
    assertEquals(1.6, view.horizontalDop)
    assertEquals(1.0, view.verticalDop)
    assertEquals(listOf("02", "07", "09", "24", "26"), view.satellitesUsed)
  }

  @Test
  fun reportsTheSatellitesInViewEvenWithNoGsaAtAll() = runTest {
    // The predecessor required a GSA and so reported nothing for a receiver that says what it can
    // see without yet having a fix -- the moment the answer is most worth having.
    val view = sentencesOf(Examples.GSV_GROUP).satellites().toList().single()
    assertEquals(emptyList(), view.fixes)
    assertNull(view.fixStatus)
    assertEquals(emptyList(), view.satellitesUsed)
    assertTrue(view.satellites.isNotEmpty())
  }

  @Test
  fun keepsTwoConstellationsApart() = runTest {
    // A multi-constellation receiver interleaves $GPGSV and $GLGSV, each group numbered from one.
    // The predecessor counted them all together and so never saw a group it considered complete.
    val glonass =
      listOf(
          "\$GLGSV,2,1,05,65,20,072,32,66,55,120,40,72,15,290,00,73,45,180,38",
          "\$GLGSV,2,2,05,74,10,330,00,,,,,,,,,,,,",
        )
        .map { Checksum.append(it) }

    val interleaved =
      listOf(
        Examples.GSV_GROUP[0],
        glonass[0],
        Examples.GSV_GROUP[1],
        glonass[1],
        Examples.GSV_GROUP[2],
      )

    val views = sentencesOf(interleaved).satellites().toList()
    assertEquals(2, views.size)
    assertEquals(listOf(TalkerId.GL, TalkerId.GP), views.map { it.talker })
    assertEquals(5, views.first { it.talker == TalkerId.GL }.satellites.size)
    assertEquals(11, views.first { it.talker == TalkerId.GP }.satellites.size)
  }

  @Test
  fun givesEveryConstellationOfACycleTheSameFixes() = runTest {
    val glonass =
      listOf("\$GLGSV,1,1,02,65,20,072,32,66,55,120,40,,,,,,,,").map { Checksum.append(it) }
    val cycle = listOf(Examples.GSA) + Examples.GSV_GROUP + glonass
    val views = sentencesOf(cycle).satellites().toList()
    assertEquals(2, views.size)
    assertTrue(views.all { it.fixes.size == 1 }, "one GSA, attached to both views of the cycle")
  }

  @Test
  fun startsAFreshSetOfFixesOnTheNextCycle() = runTest {
    val cycle = listOf(Examples.GSA) + Examples.GSV_GROUP
    val views = sentencesOf(cycle + cycle).satellites().toList()
    assertEquals(2, views.size)
    assertTrue(
      views.all { it.fixes.size == 1 },
      "the second cycle's GSA does not pile onto the first",
    )
  }

  @Test
  fun ignoresAGsvThatCannotSayWhereInItsGroupItBelongs() = runTest {
    val unnumbered = Checksum.append("\$GPGSV,,,12,03,03,111,00")
    assertEquals(emptyList(), sentencesOf(unnumbered).satellites().toList())
    assertEquals(1, sentencesOf(listOf(unnumbered) + Examples.GSV_GROUP).satellites().toList().size)
  }
}
