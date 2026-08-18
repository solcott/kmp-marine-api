package io.github.solcott.marineapi.ais

import io.github.solcott.marineapi.nmea.GpsdCorpusTest
import io.github.solcott.marineapi.nmea.ParseResult
import io.github.solcott.marineapi.nmea.SentenceRegistry
import io.github.solcott.marineapi.nmea.sentence.AisSentence
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Decodes every AIS payload in the corpus and compares it, field by field, with gpsd's own decoder.
 *
 * gpsd ships a `.log.chk` beside each capture holding the output its decoder produces for that
 * input. With `"scaled":false` the numbers are raw bit-field values, so they compare directly
 * against what [Sixbit] returns with no unit conversion in between and no room for two errors to
 * cancel.
 *
 * This is the strongest evidence in the project. The reference tables in gpsd's AIVDM document are
 * prose that has to be read correctly; this is the same project's working code, run over 1,300 real
 * messages from real transponders. **A disagreement here is a bug in this library until shown
 * otherwise.**
 */
class GpsdAisCheckTest {

  @Test
  fun everyDecodedMessageMatchesGpsd() {
    var compared = 0
    var fields = 0
    val unsupported = mutableMapOf<Int, Int>()
    val problems = mutableListOf<String>()

    for (file in GpsdCorpusTest.corpusFiles()) {
      val check = File(file.parentFile, "${file.name}.chk")
      if (!check.exists()) continue

      for ((sentences, json) in aisRecordsOf(check)) {
        val payload = sentences.joinToString("") { it.payload }
        val result = AisRegistry.Default.decode(payload, sentences.last().fillBits)

        val type = json.int("type") ?: fail("${check.name}: gpsd record has no type: $json")
        if (result is AisResult.Unsupported) {
          // gpsd decodes more message types than this library does. Those must arrive as
          // Unsupported -- reported, not silently dropped -- and must agree on the type at least.
          assertEquals(type, result.messageType, "${check.name}: message type")
          unsupported[type] = (unsupported[type] ?: 0) + 1
          continue
        }

        val message =
          (result as? AisResult.Ok)?.message
            ?: fail("${check.name}: gpsd decoded type $type, we returned $result")

        val mismatches = compare(message, json)
        if (mismatches.isNotEmpty()) {
          problems +=
            "${check.name} type $type mmsi ${json.int("mmsi")}: ${mismatches.joinToString()}"
        }
        fields += FIELDS_PER_TYPE[message.messageType] ?: 0
        compared++
      }
    }

    if (problems.isNotEmpty()) {
      fail("${problems.size} messages disagree with gpsd:\n" + problems.take(20).joinToString("\n"))
    }

    // Pinned so that losing the comparison shows up as a failure rather than as a green build that
    // checks nothing.
    assertEquals(1310, compared, "messages compared against gpsd")
    assertTrue(fields > 9_000, "expected thousands of individual field comparisons, got $fields")
    assertEquals(
      mapOf(6 to 68, 8 to 73, 17 to 5, 20 to 14, 23 to 2),
      unsupported.toSortedMap(),
      "message types gpsd decodes and this library does not",
    )
  }

  /** Every field of one message, against gpsd's numbers for it. */
  private fun compare(message: AisMessage, json: GpsdRecord): List<String> {
    val bad = mutableListOf<String>()

    fun check(name: String, gpsd: Any?, ours: Any?) {
      if (gpsd != null && gpsd != ours) bad += "$name gpsd=$gpsd ours=$ours"
    }

    check("type", json.int("type"), message.messageType)
    check("repeat", json.int("repeat"), message.repeatIndicator)
    check("mmsi", json.int("mmsi"), message.mmsi)

    when (message) {
      is AisPositionReport -> {
        check("status", json.int("status"), message.navigationalStatus?.code)
        // gpsd prints the raw code; -128 is "no turn information", which we report as null.
        check("turn", json.int("turn"), message.rateOfTurnCode ?: RATE_OF_TURN_UNAVAILABLE)
        check("speed", json.int("speed"), tenths(message.speedOverGround, SPEED_UNAVAILABLE))
        check("accuracy", json.bool("accuracy"), message.isAccurate)
        checkPosition(json, message.position, bad)
        check("course", json.int("course"), tenths(message.courseOverGround, COURSE_UNAVAILABLE))
        check("heading", json.int("heading"), message.heading ?: HEADING_UNAVAILABLE)
        check("second", json.int("second"), message.utcSecond ?: json.int("second"))
        // gpsd prints the raw code even when it is not one of the three defined values; we report
        // null for anything else. One transponder in the corpus sends 3.
        checkCoded("maneuver", json.int("maneuver"), message.maneuver?.code, 0..2, bad)
        check("raim", json.bool("raim"), message.hasRaim)
      }
      is AisPositionReportB -> {
        check("speed", json.int("speed"), tenths(message.speedOverGround, SPEED_UNAVAILABLE))
        check("accuracy", json.bool("accuracy"), message.isAccurate)
        checkPosition(json, message.position, bad)
        check("course", json.int("course"), tenths(message.courseOverGround, COURSE_UNAVAILABLE))
        check("heading", json.int("heading"), message.heading ?: HEADING_UNAVAILABLE)
        check("second", json.int("second"), message.utcSecond ?: json.int("second"))
        check("cs", json.bool("cs"), message.isClassBCarrierSotdma)
        check("display", json.bool("display"), message.hasDisplay)
        check("dsc", json.bool("dsc"), message.hasDscCapability)
        check("band", json.bool("band"), message.isBandFlagSet)
        check("msg22", json.bool("msg22"), message.canAcceptMessage22)
        check("assigned", json.bool("assigned"), message.isAssigned)
        check("raim", json.bool("raim"), message.hasRaim)
      }
      is AisExtendedPositionReportB -> {
        check("speed", json.int("speed"), tenths(message.speedOverGround, SPEED_UNAVAILABLE))
        check("accuracy", json.bool("accuracy"), message.isAccurate)
        checkPosition(json, message.position, bad)
        check("course", json.int("course"), tenths(message.courseOverGround, COURSE_UNAVAILABLE))
        check("heading", json.int("heading"), message.heading ?: HEADING_UNAVAILABLE)
        check("shipname", json.string("shipname"), message.name)
        check("shiptype", json.int("shiptype"), message.shipType)
        checkDimensions(json, message.dimensions, bad)
        check("epfd", json.int("epfd"), message.epfd?.code)
        check("raim", json.bool("raim"), message.hasRaim)
        check("assigned", json.bool("assigned"), message.isAssigned)
      }
      is AisBaseStationReport -> {
        check("accuracy", json.bool("accuracy"), message.isAccurate)
        checkPosition(json, message.position, bad)
        check("epfd", json.int("epfd"), message.epfd?.code)
        check("raim", json.bool("raim"), message.hasRaim)
        // gpsd renders the six raw fields whatever they hold, so a station with no time source
        // shows as "0000-00-00T24:60:60Z". We report null for a date that is not a date.
        json.string("timestamp")?.let { stamp ->
          val plausible =
            Regex("""^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ$""").matches(stamp) &&
              stamp.substring(0, 4) != "0000" &&
              stamp.substring(11, 13).toInt() <= 23
          if (plausible) check("timestamp", stamp, message.utc?.toString() + "Z")
          else if (message.utc != null) bad += "timestamp gpsd=$stamp ours=${message.utc}"
        }
      }
      is AisSarAircraftPositionReport -> {
        check("alt", json.int("alt"), message.altitude ?: ALTITUDE_UNAVAILABLE)
        check("speed", json.int("speed"), message.speedOverGround?.toInt() ?: SPEED_UNAVAILABLE)
        check("accuracy", json.bool("accuracy"), message.isAccurate)
        checkPosition(json, message.position, bad)
        check("course", json.int("course"), tenths(message.courseOverGround, COURSE_UNAVAILABLE))
        check("raim", json.bool("raim"), message.hasRaim)
        check("assigned", json.bool("assigned"), message.isAssigned)
      }
      is AisStaticAndVoyageData -> {
        check("ais_version", json.int("ais_version"), message.aisVersion)
        check("imo", json.int("imo"), message.imoNumber ?: 0)
        check("callsign", json.string("callsign"), message.callSign)
        check("shipname", json.string("shipname"), message.name)
        check("shiptype", json.int("shiptype"), message.shipType)
        checkDimensions(json, message.dimensions, bad)
        check("epfd", json.int("epfd"), message.epfd?.code)
        check("draught", json.int("draught"), tenths(message.maximumDraught, 0))
        check("destination", json.string("destination"), message.destination)
        // gpsd prints the raw bit: 0 means ready, which our property inverts to true.
        json.int("dte")?.let { check("dte", it == 0, message.isDteReady) }
        json.string("eta")?.let { checkEta(it, message.eta, bad) }
      }
      is AisAidToNavigationReport -> {
        check("aid_type", json.int("aid_type"), message.aidType?.code)
        check("name", json.string("name"), message.name)
        check("accuracy", json.bool("accuracy"), message.isAccurate)
        checkPosition(json, message.position, bad)
        checkDimensions(json, message.dimensions, bad)
        check("epfd", json.int("epfd"), message.epfd?.code)
        check("second", json.int("second"), message.utcSecond ?: json.int("second"))
        check("off_position", json.bool("off_position"), message.isOffPosition)
        check("virtual_aid", json.bool("virtual_aid"), message.isVirtual)
        check("raim", json.bool("raim"), message.hasRaim)
      }
      is AisStaticDataReportB -> {
        check("shiptype", json.int("shiptype"), message.shipType)
        check("model", json.int("model"), message.unitModelCode)
        check("serial", json.int("serial"), message.serialNumber)
        check("callsign", json.string("callsign"), message.callSign)
        checkDimensions(json, message.dimensions, bad)
        // gpsd reports the legacy 42-bit vendor field; ours is the modern 18-bit one, which is its
        // first three characters. See the note on AisStaticDataReportB.vendorId.
        json.string("vendorid")?.let {
          if (!it.startsWith(message.vendorId)) bad += "vendorid gpsd=$it ours=${message.vendorId}"
        }
      }
      is AisLongRangePositionReport -> {
        check("status", json.int("status"), message.navigationalStatus?.code)
        check("accuracy", json.bool("accuracy"), message.isAccurate)
        check("raim", json.bool("raim"), message.hasRaim)
        checkPosition(json, message.position, bad)
        // Whole knots and whole degrees here, not the tenths every other type uses, and the
        // coordinates are tenths of a minute rather than ten-thousandths.
        check(
          "speed",
          json.int("speed"),
          message.speedOverGround?.toInt() ?: COARSE_SPEED_UNAVAILABLE,
        )
        check(
          "course",
          json.int("course"),
          message.courseOverGround?.toInt() ?: HEADING_UNAVAILABLE,
        )
        // gpsd's "gnss" is the position-latency bit: false means the position is current.
        check("gnss", json.bool("gnss"), !message.isCurrent)
      }
      is AisBinaryAcknowledge -> {
        // gpsd always prints four slots, zero-filling the ones a short payload never carried; we
        // keep only the slots that arrived. It emits no sequence numbers at all, so those go
        // unchecked -- see the note on AisBinaryAcknowledge.
        for (slot in 0 until 4) {
          val ours = message.acknowledgements.getOrNull(slot)?.mmsi ?: 0
          check("mmsi${slot + 1}", json.int("mmsi${slot + 1}"), ours)
        }
      }
      // gpsd emits nothing for a type 24 part A: it caches the name and merges it into the record
      // it emits for part B. So a part A never reaches this comparison.
      else -> bad += "no comparison written for ${message::class.simpleName}"
    }
    return bad
  }

  private fun checkPosition(
    json: GpsdRecord,
    position: io.github.solcott.marineapi.nmea.Position?,
    bad: MutableList<String>,
  ) {
    val lon = json.int("lon") ?: return
    val lat = json.int("lat") ?: return
    // gpsd's unscaled coordinates are the raw bit-field integers, so multiply ours back rather than
    // dividing gpsd's: an integer comparison cannot hide a rounding difference.
    val scale = if (json.int("type") == AisLongRangePositionReport.TYPE) 600.0 else 600_000.0
    if (position == null) {
      // We report null where the station sent the reserved "not available" coordinates.
      if (lon != (181 * scale).toInt() && lat != (91 * scale).toInt()) {
        bad += "position gpsd=($lat,$lon) ours=null"
      }
      return
    }
    val ourLon = Math.round(position.longitude * scale).toInt()
    val ourLat = Math.round(position.latitude * scale).toInt()
    if (ourLon != lon) bad += "lon gpsd=$lon ours=$ourLon"
    if (ourLat != lat) bad += "lat gpsd=$lat ours=$ourLat"
  }

  private fun checkDimensions(
    json: GpsdRecord,
    dimensions: ShipDimensions,
    bad: MutableList<String>,
  ) {
    if (json.int("to_bow") != dimensions.toBow) {
      bad += "to_bow gpsd=${json.int("to_bow")} ours=${dimensions.toBow}"
    }
    if (json.int("to_stern") != dimensions.toStern) {
      bad += "to_stern gpsd=${json.int("to_stern")} ours=${dimensions.toStern}"
    }
    if (json.int("to_port") != dimensions.toPort) {
      bad += "to_port gpsd=${json.int("to_port")} ours=${dimensions.toPort}"
    }
    if (json.int("to_starboard") != dimensions.toStarboard) {
      bad += "to_starboard gpsd=${json.int("to_starboard")} ours=${dimensions.toStarboard}"
    }
  }

  /** Our tenths-of-a-unit value back as the integer gpsd prints, or the sentinel when absent. */
  private fun tenths(value: Double?, unavailable: Int): Int =
    if (value == null) unavailable else Math.round(value * 10).toInt()

  /**
   * gpsd renders a type 5 ETA as `MM-DDTHH:MMZ` using the raw values, so an unset one reads
   * `00-00T24:60Z`. Ours nulls each part that holds its "not available" code, so the comparison is
   * per component with those codes mapped.
   */
  private fun checkEta(gpsd: String, eta: EstimatedArrival, bad: MutableList<String>) {
    val m = Regex("""^(\d\d)-(\d\d)T(\d\d):(\d\d)Z$""").find(gpsd) ?: return
    val (month, day, hour, minute) = m.destructured
    fun part(name: String, raw: Int, ours: Int?, valid: IntRange) {
      val expected = if (raw in valid) raw else null
      if (expected != ours) bad += "eta.$name gpsd=$raw ours=$ours"
    }
    part("month", month.toInt(), eta.month, 1..12)
    part("day", day.toInt(), eta.day, 1..31)
    part("hour", hour.toInt(), eta.hour, 0..23)
    part("minute", minute.toInt(), eta.minute, 0..59)
  }

  /** A coded field: ours is null exactly when gpsd's raw value is not one the standard defines. */
  private fun checkCoded(
    name: String,
    gpsd: Int?,
    ours: Int?,
    defined: IntRange,
    bad: MutableList<String>,
  ) {
    if (gpsd == null) return
    val expected = if (gpsd in defined) gpsd else null
    if (expected != ours) bad += "$name gpsd=$gpsd ours=$ours"
  }

  private companion object {
    const val SPEED_UNAVAILABLE = 1023
    const val COURSE_UNAVAILABLE = 3600
    const val HEADING_UNAVAILABLE = 511
    const val RATE_OF_TURN_UNAVAILABLE = -128
    const val ALTITUDE_UNAVAILABLE = 4095

    /** Type 27 reports whole units, so its "not available" codes are smaller. */
    const val COARSE_SPEED_UNAVAILABLE = 63

    /**
     * Roughly how many fields each type contributes, for the "this really compared things" check.
     */
    val FIELDS_PER_TYPE =
      mapOf(
        1 to 13,
        2 to 13,
        3 to 13,
        4 to 8,
        5 to 16,
        7 to 7,
        18 to 15,
        19 to 15,
        21 to 14,
        24 to 10,
        27 to 8,
      )
  }
}

/**
 * One message and gpsd's record for it.
 *
 * Not every sentence has a companion: gpsd emits nothing for a type 24 part A, and nothing at all
 * for a sentence it cannot decode.
 */
private fun aisRecordsOf(check: File): List<Pair<List<AisSentence>, GpsdRecord>> {
  val records = mutableListOf<Pair<List<AisSentence>, GpsdRecord>>()
  val pending = mutableListOf<AisSentence>()

  for (raw in check.readLines()) {
    val line = raw.trim()
    when {
      line.startsWith("!") -> {
        val sentence =
          (SentenceRegistry.Default.parse(line) as? ParseResult.Ok)?.sentence as? AisSentence
        if (sentence == null) {
          pending.clear()
        } else {
          if (sentence.isFirstFragment) pending.clear()
          pending += sentence
        }
      }
      line.startsWith("{") -> {
        val record = GpsdRecord(line)
        // Only AIS records pair with a payload; TPV and SKY describe the NMEA sentences around it.
        if (record.string("class") == "AIS" && pending.isNotEmpty()) {
          records += pending.toList() to record
        }
        pending.clear()
      }
      // A plain NMEA sentence between AIS ones interrupts nothing, but it does mean any half
      // assembled AIS message is not going to be completed.
      line.startsWith("$") -> pending.clear()
    }
  }
  return records
}

/**
 * One line of gpsd's output.
 *
 * A regex reader rather than a JSON library: gpsd's AIS records are flat objects of strings,
 * numbers and booleans -- there is not one array among the 1,335 of them in this corpus -- so a
 * parser is more dependency than the job needs.
 */
private class GpsdRecord(private val line: String) {
  fun string(key: String): String? = Regex("\"$key\":\"([^\"]*)\"").find(line)?.groupValues?.get(1)

  fun int(key: String): Int? = Regex("\"$key\":(-?\\d+)").find(line)?.groupValues?.get(1)?.toInt()

  fun bool(key: String): Boolean? =
    Regex("\"$key\":(true|false)").find(line)?.groupValues?.get(1)?.toBooleanStrict()

  override fun toString(): String = line
}
