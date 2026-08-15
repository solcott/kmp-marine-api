package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.Checksum
import io.github.solcott.marineapi.nmea.Nmea
import io.github.solcott.marineapi.nmea.ParseResult
import io.github.solcott.marineapi.nmea.SentenceRegistry
import io.github.solcott.marineapi.nmea.sentenceOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

/**
 * Checks that no sentence type quietly discards a field.
 *
 * A field decoded into no property is invisible: it parses without complaint and then disappears,
 * and no assertion about the parsed value can detect it. Re-encoding does detect it -- anything the
 * type cannot represent comes back empty.
 *
 * The sample-log version of this check only covers fields real receivers populate, which leaves the
 * later NMEA additions and the rarely used fields untested. Every sentence here therefore has
 * *every* field filled in, including the ones no device in the corpus emits.
 */
class FieldExposureTest {

  /** One fully populated example per registered type, with each field's meaning in order. */
  private val fullyPopulated =
    listOf(
      // time, lat, N/S, lon, E/W, quality, satellites, HDOP, altitude, units, geoid, units,
      // dgps age, dgps station
      "\$GPGGA,120044.567,6011.552,N,02501.941,E,1,08,2.0,28.0,M,19.6,M,3.5,0123",
      // lat, N/S, lon, E/W, time, status, FAA mode
      "\$GPGLL,6011.552,N,02501.941,E,120045.000,A,A",
      // time, status, lat, N/S, lon, E/W, speed, course, date, variation, E/W, FAA mode, nav status
      "\$GPRMC,120044.567,A,6011.552,N,02501.941,E,0.5,54.7,160705,6.1,E,A,S",
      // course true, T, course magnetic, M, knots, N, km/h, K, FAA mode
      "\$GPVTG,360.0,T,348.7,M,16.89,N,31.28,K,A",
      // selection, fix status, twelve satellites, PDOP, HDOP, VDOP, system id
      "\$GPGSA,A,3,02,03,04,07,08,09,24,26,27,28,29,30,1.6,1.6,1.0,1",
      // count, index, in view, four quadruples of id/elevation/azimuth/SNR, signal id
      "\$GPGSV,3,2,12,15,56,182,51,17,38,163,47,18,63,058,50,21,53,329,47,1",
      // time, day, month, year, zone hours, zone minutes
      "\$GPZDA,032915.000,07,08,2004,02,30",
      // depth feet, f, depth metres, M, depth fathoms, F
      "\$SDDBT,7.8,f,2.4,M,1.3,F",
      // depth, transducer offset, maximum range
      "\$INDPT,2.3,0.5,200.0",
      // heading, deviation, E/W, variation, E/W
      "\$HCHDG,123.4,1.2,E,4.8,W",
      // heading, M
      "\$HCHDM,123.4,M",
      // heading, T
      "\$GPHDT,274.07,T",
      // temperature, C
      "\$INMTW,17.9,C",
      // direction true, T, direction magnetic, M, speed knots, N, speed m/s, M
      "\$WIMWD,302.4,T,289.6,M,10.5,N,5.4,M",
      // wind angle, reference, speed, units, status
      "\$IIMWV,125.1,T,5.5,M,A",
      // bearing true, T, bearing magnetic, M, destination, origin
      "\$GPBOD,234.9,T,228.8,M,POINTB,POINTA",
      // lat, N/S, lon, E/W, waypoint name
      "\$GPWPL,5536.200,N,01436.500,E,RUSKI",
      // count, index, route type, route id, three waypoints
      "\$GPRTE,1,1,c,0,MELIN,RUSKI,KNUDAN",
      // status, cycle lock, magnitude, steer to, units, FAA mode
      "\$IIXTE,A,A,5.36,R,N,A",
      // status, cycle lock, XTE, steer to, units, arrival circle, perpendicular, bearing
      // origin->dest, M/T, destination, bearing pos->dest, M/T, heading to steer, M/T
      "\$GPAPB,A,A,0.10,R,N,V,V,011,M,DEST,011,M,011,M",
      // status, XTE, steer to, origin, destination, lat, N/S, lon, E/W, range, bearing,
      // velocity, arrival status, FAA mode
      "\$GPRMB,A,0.66,L,003,004,4917.24,N,12309.57,W,001.3,052.5,000.5,V,A",
    )

  @Test
  fun everySentenceExposesEveryFieldItCarries() {
    val unexposed = mutableListOf<String>()

    for (body in fullyPopulated) {
      val line = Checksum.append(body)
      val result = SentenceRegistry.Default.parse(line)
      val sentence = assertIs<ParseResult.Ok>(result, "failed to parse $line: $result").sentence

      val before = fieldsOf(line)
      val after = fieldsOf(sentence.toNmeaString())

      for (index in before.indices) {
        if (before[index].isEmpty()) continue
        if (after.getOrNull(index).orEmpty().isEmpty()) {
          unexposed += "${sentence.id} field $index (\"${before[index]}\") is read but not exposed"
        }
      }
    }

    if (unexposed.isNotEmpty()) fail(unexposed.joinToString("\n"))
  }

  @Test
  fun everySentenceRoundTripsWhenFullyPopulated() {
    for (body in fullyPopulated) {
      val line = Checksum.append(body)
      val sentence = SentenceRegistry.Default.parse(line).sentenceOrNull()!!
      val reparsed = SentenceRegistry.Default.parse(sentence.toNmeaString()).sentenceOrNull()
      assertEquals(sentence, reparsed, "round trip of $line via ${sentence.toNmeaString()}")
    }
  }

  @Test
  fun theExamplesCoverEveryRegisteredType() {
    // A type added to the registry without an example here would go unaudited.
    val covered = fullyPopulated.map { it.substring(3, 6) }.toSet()
    assertEquals(SentenceRegistry.Default.types, covered)
  }

  private fun fieldsOf(sentence: String): List<String> =
    sentence
      .substringBefore(Nmea.CHECKSUM_DELIMITER)
      .substringAfter(Nmea.FIELD_DELIMITER)
      .split(Nmea.FIELD_DELIMITER)
}
