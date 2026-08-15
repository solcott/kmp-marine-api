package io.github.solcott.marineapi.nmea

import kotlin.test.Test
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
