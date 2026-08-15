package io.github.solcott.marineapi.ublox

import io.github.solcott.marineapi.nmea.Checksum
import io.github.solcott.marineapi.nmea.ParseResult
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceRegistry
import io.github.solcott.marineapi.nmea.sentence.Ubx
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime

/** The Java suite's `UBXMessage00Test` and `UBXMessage03Test` fixtures. */
private object Fixtures {
  const val POSITION =
    "\$PUBX,00,202920.00,1932.33821,N,15555.72641,W,451.876,G3,3.3,4.0,0.177,0.00,-0.035,,1.11,1.39,1.15,17,0,0*62"

  const val SATELLITES =
    "\$PUBX,03,31,5,U,063,15,18,000,12,U,100,36,40,064,14,-,257,05,,000,18,-,219,67,20,000,20,e,180,19,21,000,21,e,223,25,21,000,24,e,161,10,29,052,25,U,067,61,36,023,26,-,317,07,,000,29,U,005,43,41,064,31,-,303,35,16,000,32,-,236,04,19,000,214,-,126,-2,,000,215,-,218,00,,000,219,-,172,03,23,000,221,U,332,72,21,000,222,U,032,27,33,064,224,e,355,51,39,064,234,U,094,70,28,024,235,U,334,45,32,064,241,U,130,22,41,064,47,U,116,15,39,064,57,U,040,21,18,000,65,-,072,-2,,000,66,-,127,01,,000,77,e,149,23,,000,78,U,128,78,25,000,79,U,336,43,36,000,81,e,325,52,,000,82,-,256,32,15,000,88,U,023,21,37,004*33"
}

private fun ubxOf(line: String): Ubx {
  val result = SentenceRegistry.Default.parse(line)
  return assertIs<Ubx>(assertIs<ParseResult.Ok>(result, "fixture did not parse: $result").sentence)
}

class UbloxPositionVelocityTimeTest {

  private val message = assertIs<UbloxPositionVelocityTime>(ubxOf(Fixtures.POSITION).ubloxMessage())

  @Test
  fun readsTheFix() {
    assertEquals(0, message.messageId)
    assertEquals(LocalTime(20, 29, 20), message.utcTime)
    assertEquals(UbloxNavigationStatus.STAND_ALONE_3D, message.navigationStatus)
    assertEquals(17, message.satellitesUsed)
  }

  @Test
  fun readsThePositionWithItsAltitude() {
    // The altitude has its own field here rather than being part of the coordinates, but it
    // belongs to this fix, so it goes where Position keeps one.
    val position = assertNotNull(message.position)
    assertEquals(19.538970166666665, position.latitude, 1e-9)
    assertEquals(-155.92877350, position.longitude, 1e-6)
    assertEquals(451.876, position.altitude)
  }

  @Test
  fun readsTheAccuracyEstimatesTheStandardSentencesHaveNoFieldFor() {
    // Metres, not a dilution-of-precision factor. This is the reason to read a $PUBX,00 at all
    // when GGA and RMC are already on the wire.
    assertEquals(3.3, message.horizontalAccuracy)
    assertEquals(4.0, message.verticalAccuracy)
    assertEquals(1.11, message.horizontalDop)
    assertEquals(1.39, message.verticalDop)
    assertEquals(1.15, message.timeDop)
  }

  @Test
  fun readsTheVelocity() {
    assertEquals(0.177, message.speedOverGround)
    assertEquals(0.00, message.courseOverGround)
    assertEquals(-0.035, message.verticalVelocity, "negative is upwards")
  }

  @Test
  fun leavesAnEmptyFieldNull() {
    // The differential-age field is empty in this fixture, because the fix is not differentially
    // corrected. The Java accessor threw for this.
    assertNull(message.differentialAge)
  }
}

class UbloxSatelliteStatusReportTest {

  private val message =
    assertIs<UbloxSatelliteStatusReport>(ubxOf(Fixtures.SATELLITES).ubloxMessage())

  @Test
  fun readsEverySatelliteInOneSentence() {
    assertEquals(3, message.messageId)
    assertEquals(31, message.trackedSatellites, "the sentence describes 31")
    assertEquals(31, message.satellites.size)
  }

  @Test
  fun readsTheFirstAndLastOfThem() {
    assertEquals(
      UbloxSatelliteInfo(
        id = "5",
        elevation = 15,
        azimuth = 63,
        signalStrength = 18,
        status = UbloxSatelliteStatus.USED_IN_SOLUTION,
        carrierLockSeconds = 0,
      ),
      message.satellites.first(),
    )
    assertEquals("88", message.satellites.last().id)
    assertEquals(4, message.satellites.last().carrierLockSeconds)
  }

  @Test
  fun distinguishesTheThreeSatelliteStates() {
    val states = message.satellites.mapNotNull { it.status }.toSet()
    assertEquals(UbloxSatelliteStatus.entries.toSet(), states, "all three occur in this sentence")
    assertEquals(
      UbloxSatelliteStatus.NOT_USED_EPHEMERIS_AVAILABLE,
      message.satellites.first { it.id == "20" }.status,
    )
  }

  @Test
  fun leavesAnUntrackedSatellitesSignalStrengthNull() {
    // Satellite 14 has an empty signal-strength field: it is in view but not being tracked. The
    // Java implementation substituted -1, which is a plausible-looking dB-Hz value.
    val untracked = message.satellites.first { it.id == "14" }
    assertNull(untracked.signalStrength)
    assertEquals(5, untracked.elevation, "its sky position is still known")
  }

  @Test
  fun keepsANegativeElevationRatherThanDiscardingIt() {
    // Satellite 214 is below the horizon at -2 degrees, which is a real reading for a satellite
    // the receiver has an almanac for but cannot yet see.
    assertEquals(-2, message.satellites.first { it.id == "214" }.elevation)
  }

  @Test
  fun isLongerThanTheFormatAllows() {
    // 800-odd characters against NMEA's 82-byte limit. Enforcing that limit would discard this
    // sentence entirely, which is why the registry does not.
    assertTrue(Fixtures.SATELLITES.length > 500)
  }
}

class UbloxRegistryTest {

  @Test
  fun decodesNothingForAnIdItDoesNotKnow() {
    // u-blox defines more $PUBX messages than this library implements. The sentence is still
    // parsed and its fields are still there; only the interpretation is missing.
    val ubx =
      ubxOf(Checksum.append("\$PUBX,04,073731.00,091202,113851.00,1196,15D,1930035,-2660.664,43"))
    assertNull(ubx.ubloxMessage())
    assertEquals(4, ubx.messageId)
    assertEquals("073731.00", ubx.stringAt(1))
  }

  @Test
  fun canBeNarrowedAndExtended() {
    assertEquals(setOf(0, 3), UbloxRegistry.Default.ids)
    assertNull(ubxOf(Fixtures.POSITION).ubloxMessage(UbloxRegistry.Default.without(0)))

    val custom =
      UbloxRegistry.Empty.with(0, UbloxMessageFactory(UbloxPositionVelocityTime.Companion::from))
    assertIs<UbloxPositionVelocityTime>(ubxOf(Fixtures.POSITION).ubloxMessage(custom))
    assertNull(ubxOf(Fixtures.SATELLITES).ubloxMessage(custom))
  }

  @Test
  fun narrowsAFlowToOneMessageTypeWithoutReflection() = runTest {
    // What replaces AbstractUBXMessageListener and its GenericTypeResolver.
    val gga = "\$GPGGA,120044.567,6011.552,N,02501.941,E,1,00,2.0,28.0,M,19.6,M,,*63"
    val feed =
      flow<Sentence> {
        for (line in listOf(gga, Fixtures.POSITION, Fixtures.SATELLITES, Fixtures.POSITION)) {
          emit(assertIs<ParseResult.Ok>(SentenceRegistry.Default.parse(line)).sentence)
        }
      }
    assertEquals(3, feed.ubloxMessages().toList().size, "the GGA is not a u-blox message")
    assertEquals(
      2,
      feed.ubloxMessages().filterIsInstance<UbloxPositionVelocityTime>().toList().size,
    )
  }
}
