package io.github.solcott.marineapi.nmea.io

import io.github.solcott.marineapi.nmea.GpsdCorpusTest
import io.github.solcott.marineapi.nmea.Nmea
import io.github.solcott.marineapi.nmea.ParseResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.Source

/**
 * Reads the whole conformance corpus through the flow API.
 *
 * The unit tests for [NmeaLineReader] use terminators chosen by hand. This runs it over 103 real
 * captures instead, which is where the awkward cases actually live: several are terminated with a
 * lone `CR` throughout, one mixes `CR` and `CRLF` within a single file, and the last line of a
 * capture often has no terminator at all.
 */
class CorpusFlowTest {

  private fun sourceOf(bytes: ByteArray): Source = Buffer().also { it.write(bytes) }

  @Test
  fun theFlowReadsTheSameLinesTheFileReaderDoes() = runTest {
    // The corpus tests read these files with java.io, which splits on CR, LF and CRLF. The flow
    // has to agree with it on every one, or the two are testing different data.
    var checked = 0
    for (file in GpsdCorpusTest.corpusFiles()) {
      val viaFile = GpsdCorpusTest.sentenceLinesOf(file)
      val viaFlow =
        sourceOf(file.readBytes())
          .nmeaResults()
          .lines()
          .toList()
          .map { it.trim() }
          .filter { it.isNotEmpty() && Nmea.isBeginChar(it[0]) }
      assertEquals(viaFile, viaFlow, "line splitting disagrees for ${file.name}")
      checked += viaFile.size
    }
    assertEquals(22_269, checked, "expected the whole corpus")
  }

  @Test
  fun theFlowReportsNonNmeaLinesInsteadOfSkippingThem() {
    // GpsdCorpusTest filters to lines that begin with $ or ! before parsing. The flow does not:
    // it drops a blank line and reports everything else, so a caller can tell a dead feed from a
    // feed carrying something other than NMEA.
    //
    // Restricted to the lines that look like sentences, the two agree on all 120 documented
    // failures. Unrestricted, the flow finds six more, and they are exactly the lines in these
    // captures that are not NMEA at all: three gpsd JSON control records, a truncated GSV
    // fragment with no tag, and one sentence with a stray `r` before the `$`.
    runTest {
      var nmeaLooking = 0
      var everything = 0
      val extra = mutableListOf<String>()
      for (file in GpsdCorpusTest.corpusFiles()) {
        for (result in sourceOf(file.readBytes()).nmeaResults().toList()) {
          val line = result.line.trim()
          if (line.startsWith("#")) continue
          if (result is ParseResult.Ok) continue
          everything++
          if (Nmea.isBeginChar(line[0])) nmeaLooking++ else extra += line
        }
      }
      // One more than GpsdCorpusTest's 120, and the difference is deliberate: that test trims
      // each line before parsing, and one capture carries a sentence with a stray leading space.
      // A sentence begins with `$`, so the flow reports it rather than tidying it up first.
      assertEquals(121, nmeaLooking)
      assertEquals(126, everything)
      assertEquals(5, extra.size)
      assertEquals(3, extra.count { it.startsWith("{") }, "gpsd JSON control records")
    }
  }

  @Test
  fun narrowingToOneTypeAcrossTheCorpusMatchesTheKnownCount() = runTest {
    // ZDA is one of the four types the corpus added; 631 of them across every file.
    var count = 0
    for (file in GpsdCorpusTest.corpusFiles()) {
      count +=
        sourceOf(file.readBytes())
          .nmeaSentences()
          .filterIsInstance<io.github.solcott.marineapi.nmea.sentence.Zda>()
          .toList()
          .size
    }
    assertEquals(631, count)
  }

  @Test
  fun aCarriageReturnOnlyCaptureIsSplitCorrectly() = runTest {
    // Garmin-GPS76_route.txt is terminated with a lone CR from end to end -- no LF anywhere. It
    // is the case an LF-only splitter turns into one enormous line, and it is why this library
    // does not use kotlinx.io.readLine.
    val bytes =
      CorpusFlowTest::class.java.getResourceAsStream("/data/Garmin-GPS76_route.txt")!!.readBytes()
    assertTrue(bytes.none { it == '\n'.code.toByte() }, "this fixture should carry no LF at all")

    val results = sourceOf(bytes).nmeaResults().toList()
    assertTrue(results.size > 90, "split into ${results.size} lines")
    assertTrue(results.all { it is ParseResult.Ok }, "every line of this capture parses")
  }
}
