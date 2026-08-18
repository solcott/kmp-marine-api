package io.github.solcott.marineapi.nmea.io

import io.github.solcott.marineapi.nmea.GpsdCorpusTest
import io.github.solcott.marineapi.nmea.SentenceRegistry
import io.github.solcott.marineapi.nmea.sentenceOrNull
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

/**
 * Compares [positions] against gpsd's own fused fix, over the whole corpus.
 *
 * Weaker evidence than [io.github.solcott.marineapi.ais.GpsdAisCheckTest], and the reason is worth
 * stating rather than glossing. An AIS message is a bit layout with exactly one right answer, so
 * that test can demand agreement on every field. A *fix* is an opinion: which sentences belong to
 * one cycle, whether a cycle with no velocity counts, which sentence wins when two in the same
 * cycle carry different coordinates. gpsd is a daemon fusing a live feed that knows per-driver
 * which sentence ends a cycle and withholds output until it trusts the stream; this is a library
 * handed a `Source` with none of that context.
 *
 * So this checks what is not a matter of opinion: **a capture gpsd can get a fix out of, this
 * library must get a fix out of too.** Losing a whole receiver is a bug. Disagreeing about which of
 * two valid coordinates in one cycle to report is not.
 */
class GpsdFixCheckTest {

  @Test
  fun everyCaptureGpsdCanFixIsOneThisLibraryCanFix() {
    val lost = mutableListOf<String>()
    var capturesWithFixes = 0

    for ((file, theirs, ours) in comparisons()) {
      if (theirs.isEmpty()) continue
      capturesWithFixes++
      if (ours.isEmpty() && file.name !in SIMULATOR_CAPTURES) {
        lost += "${file.name}: gpsd found ${theirs.size} fixes, we found none"
      }
    }

    // The assertion that earns the corpus. It caught sounder.log, whose 122 $ECGLL positions were
    // being discarded because the cycle carried no velocity sentence, and mr-350p.log's 14 GGA
    // fixes for the same reason.
    assertEquals(emptyList(), lost, "captures where gpsd finds fixes and this library finds none")
    assertEquals(90, capturesWithFixes, "captures gpsd gets a fix out of")
  }

  @Test
  fun theCoordinatesAgreeWhereBothReportTheSameCycle() {
    var shared = 0
    var theirsTotal = 0

    for ((_, theirs, ours) in comparisons()) {
      theirsTotal += theirs.size
      for ((lat, lon) in theirs) {
        if (ours.any { abs(it.first - lat) <= TOLERANCE && abs(it.second - lon) <= TOLERANCE }) {
          shared++
        }
      }
    }

    // Not all of them, and the shortfall is not error. A cycle can hold a GGA and a GLL carrying
    // different coordinates -- ericsson-gru04 has 4111.59923 and 4111.59880 in one -- and the two
    // implementations do not always pick the same one. Pinned rather than demanded outright, so a
    // real coordinate bug, which would move this sharply, is still visible.
    assertEquals(4899, theirsTotal, "fixes gpsd reported across the corpus")
    assertTrue(shared >= theirsTotal * 9 / 10, "only $shared of $theirsTotal gpsd fixes matched")
  }

  /** Each capture, gpsd's fix coordinates for it, and ours. */
  private fun comparisons():
    List<Triple<File, List<Pair<Double, Double>>, List<Pair<Double, Double>>>> =
    GpsdCorpusTest.corpusFiles().mapNotNull { file ->
      val check = File(file.parentFile, "${file.name}.chk")
      if (!check.exists()) return@mapNotNull null
      val ours = runBlocking {
        GpsdCorpusTest.sentenceLinesOf(file)
          .asFlow()
          .mapNotNull { SentenceRegistry.Default.parse(it).sentenceOrNull() }
          .positions()
          .toList()
          .mapNotNull { fix -> fix.position?.let { it.latitude to it.longitude } }
      }
      Triple(file, gpsdPositionsOf(check), ours)
    }

  private companion object {
    /** gpsd prints nine decimal places; anything past a millionth of a degree is rounding. */
    const val TOLERANCE = 1e-6

    /**
     * The two captures where gpsd reports a fix and this library deliberately does not.
     *
     * Both are receivers in **simulator mode**: `GGA` quality 8, `RMC` and `GLL` status `V`, FAA
     * mode `S`. Every one of those fields is the device saying the same thing -- this is generated
     * data, do not navigate on it. gpsd reports the position anyway; a library whose job is to say
     * where a vessel is should not call that a fix.
     *
     * Anyone who does want it can read the sentences directly: `filterIsInstance<Gga>()` hands back
     * the position and a [io.github.solcott.marineapi.nmea.GpsFixQuality.SIMULATED] to go with it.
     */
    val SIMULATOR_CAPTURES = setOf("GPSmap-76S.log", "garmin-geko201.log")
  }
}

/**
 * The coordinates of every fix gpsd reported for this capture.
 *
 * gpsd emits a `TPV` for every cycle whether or not it has a fix -- `{"class":"TPV","mode":1}` is
 * how it says "no fix", carrying no coordinates. Those are skipped: this library says the same
 * thing by reporting nothing at all, and the comparison is about the fixes that exist.
 */
private fun gpsdPositionsOf(check: File): List<Pair<Double, Double>> =
  check
    .readLines()
    .asSequence()
    .map { it.trim() }
    .filter { it.startsWith("{") && "\"class\":\"TPV\"" in it }
    .mapNotNull { line ->
      val lat = Regex("\"lat\":(-?[\\d.]+)").find(line)?.groupValues?.get(1)?.toDouble()
      val lon = Regex("\"lon\":(-?[\\d.]+)").find(line)?.groupValues?.get(1)?.toDouble()
      if (lat != null && lon != null) lat to lon else null
    }
    .toList()
