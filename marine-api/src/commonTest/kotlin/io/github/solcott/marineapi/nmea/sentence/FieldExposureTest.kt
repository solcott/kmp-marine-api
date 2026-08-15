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
      // heading true, T, heading magnetic, M, knots, N, km/h, K
      "\$IIVHW,240.5,T,234.7,M,4.9,N,9.1,K",
      // total water, N, trip water, N, total ground, N, trip ground, N
      "\$IIVLW,1958.64,N,365.2,N,2011.3,N,401.7,N",
      // long water, transverse water, status, long ground, transverse ground, status,
      // stern water, status, stern ground, status
      "\$IIVBW,11.0,02.0,A,10.0,03.0,A,05.3,A,01.0,A",
      // direction true, T, direction magnetic, M, speed, N
      "\$IIVDR,10.0,T,12.0,M,1.5,N",
      // speed knots, N, speed m/s, M
      "\$IIVPW,4.5,N,2.3,M",
      // wind angle, side, knots, N, m/s, M, km/h, K
      "\$IIVWR,088,L,24.5,N,12.6,M,45.4,K",
      "\$IIVWT,088,L,24.7,N,12.6,M,45.7,K",
      // rate of turn, status
      "\$HEROT,-0.3,A",
      // starboard angle, status, port angle, status
      "\$IIRSA,1.2,A,2.3,V",
      // heading, status, course, course reference, speed, speed reference, set, drift, units
      "\$RAOSD,35.1,A,36.0,P,10.2,P,15.3,0.1,N",
      // origin 1 range/bearing, VRM 1, EBL 1, origin 2 range/bearing, VRM 2, EBL 2,
      // cursor range/bearing, range scale, range units, display rotation
      "\$RARSD,12,90,24,45,6,270,12,315,6.5,118,96,N,N",
      // number, distance, bearing, T/R, speed, course, T/R, CPA distance, CPA time, units,
      // name, status, reference target, time, acquisition type
      "\$RATTM,11,25.3,13.7,T,7.0,20.0,T,10.1,20.2,N,NAME,Q,R,175550.24,A",
      // number, lat, N/S, lon, E/W, name, time, status, reference target
      "\$RATLL,01,3731.512,N,02436.000,E,ANDROS,163700.86,T,R",
      // three target number and label pairs
      "\$RATLB,1,SHIPONE,2,SHIPTWO,3,SHIPTHREE",
      // air temperature, C
      "\$IIMTA,21.5,C",
      // inches of mercury, I, bars, B
      "\$IIMMB,29.9870,I,1.0154,B",
      // relative humidity, absolute humidity, dew point, C
      "\$IIMHU,66.0,5.0,3.0,C",
      // pressure, I/P, pressure bars, B, air temp, C, water temp, C, relative humidity,
      // absolute humidity, dew point, C, wind true, T, wind magnetic, M, knots, N, m/s, M
      "\$IIMDA,29.9,I,1.01,B,3.2,C,4.1,C,66.0,5.0,3.0,C,295.19,T,301.2,M,5.70,N,2.93,M",
      // two transducer quadruples: type, value, unit, name
      "\$IIXDR,P,1.02481,B,Barometer,C,21.5,C,AirTemp",
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
