package io.github.solcott.marineapi.nmea

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Runs the parser over the recorded device logs the Java suite ships.
 *
 * Synthetic fixtures only prove the parser handles what its author imagined. These files came off
 * real receivers, so they are the closest thing available to a conformance corpus -- and the only
 * check that the sentence types being ported cope with what devices actually emit.
 *
 * This lives in `jvmTest` rather than `commonTest` because the fixtures are classpath resources and
 * `commonTest` has no way to read them.
 */
class SampleDataTest {

  private val samples =
    listOf(
      "/data/sample1.txt",
      "/data/Garmin-GPS76.txt",
      "/data/Garmin-GPS15.txt",
      "/data/Garmin-GPS76_diff.txt",
      "/data/Garmin-GPS76_goto.txt",
      "/data/Garmin-GPS76_route.txt",
      "/data/Garmin-GPS15H.txt",
      "/data/Navibe-GM720.txt",
      "/data/AISsample.txt",
      "/data/AIS-VDM-VDO.txt",
    )

  /**
   * Lines these captures contain that are genuinely not parseable, verified by hand.
   *
   * Two are corruption in the Navibe capture itself -- a truncated read and two sentences spliced
   * together mid-line, both of which a serial capture can produce. The third carries a checksum of
   * `23` where the payload computes to `24`; that AIS sentence appears throughout the wild with
   * `24`, so the digit in this file is wrong, not the parser.
   *
   * Listing them means a new failure fails the test rather than being lost in a tolerance.
   */
  private val knownBadLines =
    setOf(
      "2,14,52,083,45,07,,,00,05,,,27*71",
      "\$GPGSV,2,1,07,19,25,189,51,09,,,00,22\$GPGGA,110826,6013.0600,N,02453.5359,E,1,04,03.4," +
        "-00015.9,M,045.5,M,,*61",
      "!AIVDM,1,1,,A,13u?etPv2;0n:dDPwUM1U1Cb069D,0*23",
    )

  @Test
  fun everyLineOfEverySampleParses() {
    val failures = mutableListOf<String>()
    var total = 0

    for (resource in samples) {
      for (line in linesOf(resource)) {
        // These files carry comments and a terminal-session header alongside the sentences.
        if (line.isBlank() || !Nmea.isBeginChar(line[0])) continue
        if (line in knownBadLines) continue
        total++
        when (val result = SentenceRegistry.Default.parse(line)) {
          is ParseResult.Ok -> Unit
          is ParseResult.BadChecksum ->
            failures += "$resource: checksum ${result.actual} != ${result.expected} in $line"
          is ParseResult.Malformed -> failures += "$resource: ${result.reason} in $line"
        }
      }
    }

    assertTrue(total > 1000, "expected a substantial corpus, read $total lines")
    if (failures.isNotEmpty()) {
      fail(
        "${failures.size} of $total lines failed to parse:\n" + failures.take(20).joinToString("\n")
      )
    }
  }

  /**
   * Sentence types the corpus actually contains, and how many of each.
   *
   * Which types have real device data behind them and which rest only on the reference examples is
   * not something to re-derive by hand each time a batch lands -- getting it wrong overstates how
   * well a type has been tested. Recording it here means a corpus file dropped from [samples], or a
   * newly ported type that turns out to have real data waiting for it, both show up as a diff.
   *
   * `GRME`, `GRMM`, `GRMV`, `GRMZ` and `GRMT` are Garmin's proprietary sentences, parsed as
   * [UnknownSentence] but counted the same way.
   */
  private val expectedCoverage =
    mapOf(
      "GSA" to 1182,
      "GGA" to 1179,
      "GRME" to 1143,
      "GRMM" to 1142,
      "GSV" to 924,
      "RMC" to 798,
      "GRMV" to 381,
      "VDM" to 36,
      "GLL" to 35,
      "BOD" to 32,
      "GRMZ" to 32,
      "GRMT" to 31,
      "RMB" to 29,
      "RTE" to 26,
      "WPL" to 5,
      "MWV" to 4,
      "VDO" to 4,
      "VHW" to 3,
      "VLW" to 3,
      "VPW" to 3,
      "VTG" to 3,
      "VWR" to 3,
      "VWT" to 3,
      "XTE" to 3,
      "DBT" to 3,
      "HDM" to 3,
      "MTA" to 3,
      "MTW" to 3,
      "MWD" to 3,
      "DPT" to 1,
    )

  @Test
  fun theCorpusCoversTheseSentenceTypes() {
    val counts = mutableMapOf<String, Int>()
    for (resource in samples) {
      for (line in linesOf(resource)) {
        if (line.isBlank() || !Nmea.isBeginChar(line[0])) continue
        val id = SentenceRegistry.Default.parse(line).sentenceOrNull()?.id ?: continue
        counts[id] = (counts[id] ?: 0) + 1
      }
    }
    assertEquals(expectedCoverage, counts.toMap())
  }

  @Test
  fun everyPortedTypeWithCorpusDataIsExercised() {
    // A registered type the corpus can validate should be validated. These four appear in no
    // capture, so they rest on the reference examples and the fixtures of the suite this replaces:
    // APB and HDG are instrument sentences no GPS emits, HDT likewise, and none of these receivers
    // sends ZDA.
    val withoutCorpusData = SentenceRegistry.Default.types - expectedCoverage.keys
    assertEquals(setOf("APB", "HDG", "HDT", "ZDA"), withoutCorpusData)
  }

  @Test
  fun theKnownBadLinesAreStillReportedAsFailures() {
    // If a capture is ever cleaned up, this says so rather than leaving a stale exemption.
    for (line in knownBadLines) {
      val result = SentenceRegistry.Default.parse(line)
      assertTrue(
        result !is ParseResult.Ok,
        "this line is exempted as unparseable but now parses: $line",
      )
    }
  }

  @Test
  fun renderingEveryParsedSentenceReproducesItsValue() {
    // parse(render(s)) == s over real data, which exercises the formatters far harder than the
    // handful of fixtures in the common tests.
    var checked = 0
    for (resource in samples) {
      for (line in linesOf(resource)) {
        val sentence = SentenceRegistry.Default.parse(line).sentenceOrNull() ?: continue
        if (sentence is UnknownSentence) continue
        val rendered = sentence.toNmeaString()
        val reparsed = SentenceRegistry.Default.parse(rendered).sentenceOrNull()
        if (sentence != reparsed) {
          fail("round trip changed the value\n  from: $line\n  via:  $rendered\n  was:  $reparsed")
        }
        checked++
      }
    }
    assertTrue(checked > 100, "expected to check a meaningful number of sentences, got $checked")
  }

  private fun linesOf(resource: String): List<String> {
    val stream = javaClass.getResourceAsStream(resource) ?: fail("missing test resource: $resource")
    return stream.bufferedReader().readLines()
  }
}
