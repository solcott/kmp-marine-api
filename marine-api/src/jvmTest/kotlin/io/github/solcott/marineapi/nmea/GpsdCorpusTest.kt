package io.github.solcott.marineapi.nmea

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Runs the parser over the device logs vendored from gpsd.
 *
 * Roughly 90 receivers and instruments, 22,269 sentences. This is the corpus that answers "does
 * this cope with what hardware actually sends", as distinct from the reference tables, which say
 * what hardware is supposed to send. It has already paid for itself twice: it found an
 * IllegalArgumentException escaping `parse`, and it produced the evidence for splitting coded
 * fields into load-bearing and advisory.
 *
 * See `resources/data/gpsd/README.md` for provenance and the BSD-2-Clause notice.
 */
class GpsdCorpusTest {

  /**
   * Lines each file contains that cannot be parsed, and why.
   *
   * Counted per file rather than listed line by line: at 120 lines a literal list is unreadable and
   * nobody would notice it drifting. A count changing in either direction fails the test, which is
   * the property that matters -- a fix should lower one of these deliberately, and a regression
   * should never raise one silently.
   */
  private val expectedFailures =
    mapOf(
      // A proprietary Broadcom/Google status sentence whose checksum field is the literal "00".
      "gpsdf.log" to 46,
      // Genuinely wrong checksums, verified by hand against an independent implementation.
      "firefly-II.log" to 23,
      // A TN200 with no fix reports 36000.0000,N as its latitude and flags the sentence void.
      // 360 degrees is not a latitude, so the line is reported rather than believed. gpsd accepts
      // it -- its only check is that the number is finite.
      "tn200.log" to 19,
      "tn200-all.log" to 18,
      // SiRF firmware banners: "$PSRFTXTVersion GSW3.2.2..." with no comma anywhere. NMEA fields
      // are comma-delimited, so there is no tag to separate from the body.
      "blumax-gps009.log" to 8,
      // april6_2019.log used to be here, for one RMC carrying "2208-1" where the ddmmyy date
      // belongs. It parses now: RMC's date is advisory, so a corrupt one nulls the date rather
      // than discarding a position and a speed that are both fine. gpsd keeps that position too,
      // which is what prompted the change.
      // Truncated or empty checksum fields: "*7", "*".
      "garmin48.log" to 1,
      "garmin-geko201.log" to 1,
      // Two sentences spliced together mid-line, which a serial capture can produce.
      "com-1289.log" to 1,
      "isync.log" to 1,
      "triton400.log" to 1,
    )

  @Test
  fun everyLineParsesExceptTheDocumentedFailures() {
    val actual = mutableMapOf<String, Int>()
    val samples = mutableMapOf<String, MutableList<String>>()
    var total = 0

    for (file in corpusFiles()) {
      for (line in sentenceLinesOf(file)) {
        total++
        when (val result = SentenceRegistry.Default.parse(line)) {
          is ParseResult.Ok -> Unit
          is ParseResult.BadChecksum -> {
            actual[file.name] = (actual[file.name] ?: 0) + 1
            samples.getOrPut(file.name) { mutableListOf() }.add("checksum: $line")
          }
          is ParseResult.Malformed -> {
            actual[file.name] = (actual[file.name] ?: 0) + 1
            samples.getOrPut(file.name) { mutableListOf() }.add("${result.reason}: $line")
          }
        }
      }
    }

    assertTrue(total > 20_000, "expected the whole corpus, read $total lines")
    if (actual != expectedFailures) {
      val unexpected = actual.keys - expectedFailures.keys
      fail(
        buildString {
          appendLine("failure counts changed")
          appendLine("  expected: ${expectedFailures.toSortedMap()}")
          appendLine("  actual:   ${actual.toSortedMap()}")
          for (name in unexpected) {
            appendLine("  new failures in $name:")
            samples[name]?.take(5)?.forEach { appendLine("    $it") }
          }
        }
      )
    }
  }

  @Test
  fun renderingEveryParsedSentenceReproducesItsValue() {
    var checked = 0
    for (file in corpusFiles()) {
      for (line in sentenceLinesOf(file)) {
        val sentence = SentenceRegistry.Default.parse(line).sentenceOrNull() ?: continue
        if (sentence is UnknownSentence) continue
        val rendered = sentence.toNmeaString()
        val reparsed = SentenceRegistry.Default.parse(rendered).sentenceOrNull()
        if (sentence != reparsed) {
          fail(
            "round trip changed the value\n  file: ${file.name}\n  from: $line\n" +
              "  via:  $rendered\n  was:  $reparsed"
          )
        }
        checked++
      }
    }
    assertTrue(checked > 15_000, "expected most of the corpus to be ported types, got $checked")
  }

  @Test
  fun theCorpusCoversTheseSentenceTypes() {
    // Which ported types this corpus actually exercises. ZDA, HDT, XDR and ROT are the four it
    // added: before it arrived they were registered with no real device data behind them at all.
    // GRS, MSS and THS joined them when the gap against gpsd's sentence list was closed -- all
    // three were being emitted by receivers here and parsed as UnknownSentence until then -- and
    // with them the four proprietary types, ASHR from an Ashtech attitude sensor and GRME, GRMM
    // and GRMZ from Garmin receivers. Seven of the 34 types added in that round have real device
    // data behind them; SampleDataTest names the other 27.
    // VDM and VDO joined them once AIS was ported -- the corpus carries several AIS captures, and
    // they had been counted as unrecognised until there was something to recognise them.
    assertEquals(
      setOf(
        "ASHR",
        "BOD",
        "BWC",
        "DBT",
        "DTM",
        "DPT",
        "GBS",
        "GGA",
        "GLL",
        "GNS",
        "GRME",
        "GRMM",
        "GRMZ",
        "GSA",
        "GST",
        "GSV",
        "GRS",
        "HDM",
        "HDT",
        "MSS",
        "MTW",
        "RMB",
        "RMC",
        "ROT",
        "RTE",
        "THS",
        "TXT",
        "VDM",
        "VDO",
        "VTG",
        "XDR",
        "XTE",
        "ZDA",
      ),
      typeCoverage().keys,
    )
  }

  @Test
  fun theCorpusIsTheExpectedSize() {
    // A file lost in a merge or a rebase would otherwise quietly shrink the evidence base.
    assertEquals(103, corpusFiles().size)
  }

  companion object {
    /**
     * The vendored logs, read as files.
     *
     * Enumerating a directory rather than naming 103 resources one by one. Under `jvmTest` the
     * processed resources are a real directory on the classpath, so this resolves; it would not
     * work from inside a jar, which is why it stays in a test.
     */
    fun corpusFiles(): List<File> {
      val url =
        GpsdCorpusTest::class.java.getResource("/data/gpsd")
          ?: fail("missing test resource directory: /data/gpsd")
      return File(url.toURI())
        .listFiles { file -> file.extension == "log" }
        .orEmpty()
        .sortedBy { it.name }
    }

    /**
     * How many sentences of each type the corpus holds, counting only types the registry knows.
     *
     * [SampleDataTest] unions this with its own coverage to work out which ported types still have
     * no real device data behind them.
     */
    fun typeCoverage(): Map<String, Int> {
      val counts = mutableMapOf<String, Int>()
      for (file in corpusFiles()) {
        for (line in sentenceLinesOf(file)) {
          val sentence = SentenceRegistry.Default.parse(line).sentenceOrNull() ?: continue
          if (sentence is UnknownSentence) continue
          counts[sentence.id] = (counts[sentence.id] ?: 0) + 1
        }
      }
      return counts
    }

    /** Sentence lines of [file], skipping gpsd's `#` headers and any blank lines. */
    fun sentenceLinesOf(file: File): List<String> =
      file.readLines().map { it.trim() }.filter { it.isNotEmpty() && Nmea.isBeginChar(it[0]) }
  }
}
