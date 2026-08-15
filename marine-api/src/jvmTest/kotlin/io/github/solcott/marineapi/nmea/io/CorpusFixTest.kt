package io.github.solcott.marineapi.nmea.io

import io.github.solcott.marineapi.nmea.GpsFixQuality
import io.github.solcott.marineapi.nmea.GpsdCorpusTest
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceRegistry
import io.github.solcott.marineapi.nmea.sentenceOrNull
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

/**
 * Runs the correlating operators over all 103 device logs.
 *
 * The unit tests feed them cycles assembled by hand. This feeds them the cycles ~90 real receivers
 * actually emit, which is where the assumptions behind a cycle boundary get tested: devices that
 * send `RMC` before their position sentence, devices that send `ZDA` after everything else, devices
 * that interleave two constellations' `GSV` groups, and devices that spend a whole capture
 * reporting that they have no fix.
 */
class CorpusFixTest {

  private fun sentencesOf(file: File): Flow<Sentence> =
    GpsdCorpusTest.sentenceLinesOf(file).asFlow().mapNotNull {
      SentenceRegistry.Default.parse(it).sentenceOrNull()
    }

  @Test
  fun everyLogYieldsTheFixesItShould() = runTest {
    var fixes = 0
    var withAltitude = 0
    var withDate = 0
    for (file in GpsdCorpusTest.corpusFiles()) {
      val found = sentencesOf(file).positions().toList()
      fixes += found.size
      withAltitude += found.count { it.position.altitude != null }
      withDate += found.count { it.date != null }
    }
    // Pinned, so that a change to the cycle rule has to be a deliberate one: these numbers move
    // together with any change to what delimits a cycle or what makes one worth reporting.
    assertEquals(1854, fixes)
    assertEquals(1587, withAltitude, "the rest came from receivers sending no GGA")
    assertEquals(1833, withDate, "the rest came from cycles carrying neither RMC nor ZDA")
  }

  @Test
  fun readsTheFourReceiversThatSendRmcAndNoOtherPositionSentence() = runTest {
    // The predecessor required a GGA or GLL in every cycle and so reported nothing at all for
    // these four. They are the reason the readiness rule changed.
    val counts =
      listOf("ait250.log", "april6_2019.log", "magellan-ec10.log", "meinberg-gps164.log")
        .associateWith { name ->
          val file = GpsdCorpusTest.corpusFiles().single { it.name == name }
          sentencesOf(file).positions().toList().size
        }
    assertEquals(
      mapOf(
        "ait250.log" to 34,
        "april6_2019.log" to 1,
        "magellan-ec10.log" to 21,
        "meinberg-gps164.log" to 70,
      ),
      counts,
    )
  }

  @Test
  fun reportsNoFixForACaptureWhereTheReceiverNeverHadOne() = runTest {
    // Sixteen logs yield nothing. Seven are receivers indoors or still searching -- every GGA
    // reports quality 0 or 8 and every RMC and GLL reports a void status -- and the rest carry no
    // position sentence at all, being AIS captures, a depth sounder and an autopilot.
    val silent =
      GpsdCorpusTest.corpusFiles()
        .filter { sentencesOf(it).positions().toList().isEmpty() }
        .map { it.name }
    assertEquals(16, silent.size, "silent logs: $silent")
    assertTrue("skytraq.log" in silent, "a full sentence set, every one of them reporting no fix")
    assertTrue("sounder.log" in silent, "a depth sounder reports no position at all")
  }

  @Test
  fun aFixIsNeverBuiltFromACycleThatSaysItIsBad() = runTest {
    // The corpus is full of void sentences; none of them may reach a fix. GGA's INVALID is
    // checked directly, and the void statuses are checked by the cycle being dropped entirely.
    for (file in GpsdCorpusTest.corpusFiles()) {
      for (fix in sentencesOf(file).positions().toList()) {
        assertTrue(
          fix.fixQuality != GpsFixQuality.INVALID,
          "${file.name} reported a fix from a GGA with no fix",
        )
      }
    }
  }

  @Test
  fun reassemblesTheSatelliteGroupsOfEveryLog() = runTest {
    var views = 0
    var satellites = 0
    for (file in GpsdCorpusTest.corpusFiles()) {
      for (view in sentencesOf(file).satellites().toList()) {
        views++
        satellites += view.satellites.size
      }
    }
    assertEquals(2201, views)
    assertEquals(20_429, satellites)
  }

  @Test
  fun doesNotTrustSatellitesInViewAsACountOfWhatArrives() = runTest {
    // eXplorist210 reports ten satellites in view and then lists twelve, so the field is neither an
    // upper nor a lower bound on what the group carries. Reassembly counts sentences, not
    // satellites, which is why this device reassembles at all.
    val file = GpsdCorpusTest.corpusFiles().single { it.name == "eXplorist210.log" }
    val views = sentencesOf(file).satellites().toList()
    assertTrue(
      views.any { it.satellitesInView == 10 && it.satellites.size == 12 },
      "expected the group that lists more satellites than it claims are in view",
    )
    assertTrue(
      views.any { it.satellitesInView == 0 && it.satellites.isEmpty() },
      "and the groups it sends while it can see nothing at all",
    )
  }

  @Test
  fun keepsEachConstellationsGroupsApartAcrossTheCorpus() = runTest {
    // Multi-constellation receivers are why groups are tracked per talker. Counting every GSV
    // together, as the predecessor did, would have found no complete group in these logs at all.
    val multi =
      GpsdCorpusTest.corpusFiles()
        .map { file -> file.name to sentencesOf(file).satellites().toList().map { it.talker.code } }
        .filter { (_, talkers) -> talkers.distinct().size > 1 }
    assertTrue(multi.size >= 8, "expected several multi-constellation captures, found $multi")
    assertTrue(
      multi.any { (_, talkers) -> "GL" in talkers && "GP" in talkers },
      "expected a capture interleaving GLONASS and GPS groups",
    )
  }

  @Test
  fun readsTheHeadingsTheCorpusCarries() = runTest {
    var headings = 0
    for (file in GpsdCorpusTest.corpusFiles()) {
      headings += sentencesOf(file).headings().toList().size
    }
    assertEquals(36, headings)
  }
}
