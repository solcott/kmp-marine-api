package io.github.solcott.marineapi.nmea

import kotlin.test.Test
import kotlin.test.fail

/**
 * Finds fields a sentence type reads but does not expose.
 *
 * A field that is decoded into no property is invisible: it parses without error and then vanishes,
 * so no test of the resulting value can catch it. Re-encoding does catch it -- whatever the type
 * cannot represent comes back empty. So for every sentence in the sample logs this compares the
 * input fields with the re-encoded ones and reports any that lost their value.
 *
 * Structural markers are exempt: they carry no data, only shape.
 */
class FieldCoverageTest {

  private val samples =
    listOf(
      "/data/sample1.txt",
      "/data/Garmin-GPS76.txt",
      "/data/Garmin-GPS15H.txt",
      "/data/Navibe-GM720.txt",
      "/data/AISsample.txt",
      "/data/AIS-VDM-VDO.txt",
    )

  @Test
  fun everyPopulatedFieldSurvivesReEncoding() {
    // type -> field index -> an input value that was dropped
    val dropped = sortedMapOf<String, MutableMap<Int, String>>()

    for (resource in samples) {
      for (line in linesOf(resource)) {
        if (line.isBlank() || !Nmea.isBeginChar(line[0])) continue
        val sentence = SentenceRegistry.Default.parse(line).sentenceOrNull() ?: continue
        if (sentence is UnknownSentence) continue

        val before = fieldsOf(line)
        val after = fieldsOf(sentence.toNmeaString())

        for (index in before.indices) {
          val original = before[index]
          if (original.isEmpty()) continue
          val roundTripped = after.getOrNull(index).orEmpty()
          if (roundTripped.isEmpty()) {
            dropped.getOrPut(sentence.id) { sortedMapOf() }.putIfAbsent(index, original)
          }
        }
      }
    }

    if (dropped.isNotEmpty()) {
      val report =
        dropped.entries.joinToString("\n") { (type, fields) ->
          "  $type: " + fields.entries.joinToString(", ") { "field ${it.key} (\"${it.value}\")" }
        }
      fail("fields read but not exposed:\n$report")
    }
  }

  private fun fieldsOf(sentence: String): List<String> =
    sentence
      .substringBefore(Nmea.CHECKSUM_DELIMITER)
      .substringAfter(Nmea.FIELD_DELIMITER)
      .split(Nmea.FIELD_DELIMITER)

  private fun linesOf(resource: String): List<String> {
    val stream = javaClass.getResourceAsStream(resource) ?: fail("missing test resource: $resource")
    return stream.bufferedReader().readLines()
  }
}
